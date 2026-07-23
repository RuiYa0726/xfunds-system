<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { createOption, getOptionDetail } from '@/api/option'
import { getCustomerAccounts } from '@/api/customer'
import { getSpotQuotes } from '@/api/quote'
import { completeModifyTask } from '@/api/task'
import { useUserStore } from '@/store/user'
import CustomerSearchDialog from '@/components/CustomerSearchDialog.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 是否编辑模式（退回修改时 route.query.taskId 存在）
const isEditMode = computed(() => !!route.query.taskId)
const modifyTaskId = computed(() => route.query.taskId || '')

// 表单引用，用于校验
const formRef = ref(null)
// 客户搜索弹窗显隐
const customerDialogVisible = ref(false)
// 提交中状态
const submitting = ref(false)
// 账户加载中状态
const accountLoading = ref(false)
// 客户全部账户列表（选中客户后加载）
const allAccounts = ref([])
// 即期报价列表
const quotes = ref([])

// 获取今日日期字符串 YYYY-MM-DD
function getTodayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 期权交易表单数据
const form = reactive({
  customerId: '',
  customerName: '',
  currencyPair: 'USD/CNY',
  // baseCurrency/quoteCurrency 由 currencyPair 自动派生，不在界面展示
  baseCurrency: 'USD',
  quoteCurrency: 'CNY',
  currency1Account: '',
  currency2Account: '',
  premiumAccountId: '',
  buyerSeller: 'BUY',
  optionType: 'CALL', // 不在界面展示，由涨跌方向自动推导
  priceDirection: 'UP',
  spotRate: null,
  strikePrice: null,
  optionStyle: 'EUROPEAN',
  exerciseTimePoint: '15:00:00', // 固定为15:00:00
  tradeDate: getTodayStr(),
  maturityDate: '',
  deliveryType: 'T2',
  deliveryDate: '',
  days: null,
  premiumValueDate: getTodayStr(), // 固定为交易日
  settlementMethod: 'FULL', // 固定为全额交割
  notionalAmount: null, // 面值（币种1）= 交易金额
  premiumAmount: null,
  premiumCurrency: 'CNY',
  observationStartDate: '',
  observationEndDate: '',
  purposeCode: '',
  fxPurposeCode: ''
})

// 是否美式期权
const isAmerican = computed(() => form.optionStyle === 'AMERICAN')
// 是否欧式期权
const isEuropean = computed(() => form.optionStyle === 'EUROPEAN')

// 基础货币名称（从货币对自动派生）：USD/CNY → USD
const baseCurrencyName = computed(() => {
  const parts = (form.currencyPair || '').split('/')
  return parts.length === 2 ? parts[0].trim().toUpperCase() : ''
})

// 报价货币名称（从货币对自动派生）：USD/CNY → CNY
const quoteCurrencyName = computed(() => {
  const parts = (form.currencyPair || '').split('/')
  return parts.length === 2 ? parts[1].trim().toUpperCase() : ''
})

// 货币对下拉选项
const currencyPairOptions = [
  { value: 'USD/CNY', label: 'USD/CNY' },
  { value: 'EUR/CNY', label: 'EUR/CNY' }
]

// 表单校验规则（动态根据期权类别）
const rules = computed(() => {
  const baseName = baseCurrencyName.value
  const r = {
    customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
    currencyPair: [{ required: true, message: '请选择货币对', trigger: 'change' }],
    currency1Account: [{ required: true, message: `请选择${baseName}账户`, trigger: 'change' }],
    currency2Account: [{ required: true, message: `请选择${quoteCurrencyName.value}账户`, trigger: 'change' }],
    strikePrice: [
      { required: true, message: '请输入执行价格', trigger: 'blur' },
      { type: 'number', min: 0.00000001, message: '执行价格必须大于0', trigger: 'blur' }
    ],
    notionalAmount: [
      { required: true, message: `请输入面值（${baseName}）`, trigger: 'blur' },
      { type: 'number', min: 0.000001, message: '面值必须大于0', trigger: 'blur' }
    ],
    tradeDate: [{ required: true, message: '请选择交易日', trigger: 'change' }]
  }
  if (isAmerican.value) {
    r.observationStartDate = [{ required: true, message: '请选择观察期开始日', trigger: 'change' }]
    r.observationEndDate = [{ required: true, message: '请选择观察期结束日', trigger: 'change' }]
  } else {
    r.maturityDate = [{ required: true, message: '请选择到期日', trigger: 'change' }]
  }
  return r
})

// 货币对变化时拆分基础货币与报价货币
function handleCurrencyPairChange(val) {
  if (!val) return
  const parts = val.split('/')
  if (parts.length === 2) {
    form.baseCurrency = parts[0].trim().toUpperCase()
    form.quoteCurrency = parts[1].trim().toUpperCase()
    // 货币变化后清空已选账户
    form.currency1Account = ''
    form.currency2Account = ''
  }
  // 自动加载即期汇率
  updateSpotRate()
}

// 币种1账户下拉选项：按基础货币过滤
const currency1AccountOptions = computed(() =>
  allAccounts.value.filter((a) => a.currency === form.baseCurrency)
)

// 币种2账户下拉选项：按报价货币过滤
const currency2AccountOptions = computed(() =>
  allAccounts.value.filter((a) => a.currency === form.quoteCurrency)
)

// 期权费账户下拉选项：只显示CNY账户
const premiumAccountOptions = computed(() =>
  allAccounts.value.filter((a) => a.currency === 'CNY')
)

// 期权费账户自动选择第一个CNY账户
watch(premiumAccountOptions, (options) => {
  if (options.length > 0 && !form.premiumAccountId) {
    form.premiumAccountId = options[0].accountId
  }
})

// 账户下拉标签：账号 + 币种 + 余额
function accountLabel(account) {
  return `${account.accountNo} | ${account.currency} | 余额 ${account.balance ?? 0}`
}

// 根据货币对从报价列表中获取即期汇率
function updateSpotRate() {
  if (!quotes.value.length || !form.currencyPair) {
    form.spotRate = null
    return
  }
  const quote = quotes.value.find((q) => q.currencyPair === form.currencyPair)
  if (quote) {
    // 即期汇率取总行买入价和卖出价的中间值作为参考
    const buy = Number(quote.totalBuyRate) || 0
    const sell = Number(quote.totalSellRate) || 0
    form.spotRate = buy && sell ? Math.round(((buy + sell) / 2) * 10000) / 10000 : (buy || sell || null)
  } else {
    form.spotRate = null
  }
}

// 加载即期报价列表
async function loadQuotes() {
  try {
    const res = await getSpotQuotes()
    quotes.value = res.data || []
    updateSpotRate()
  } catch (e) {
    // 加载失败不影响
  }
}

// 计算天数：到期日 - 交易日（仅欧式期权）
function calcDays() {
  if (isEuropean.value && form.tradeDate && form.maturityDate) {
    const start = new Date(form.tradeDate)
    const end = new Date(form.maturityDate)
    const diff = Math.round((end - start) / (1000 * 60 * 60 * 24))
    form.days = diff >= 0 ? diff : null
  } else {
    form.days = null
  }
}

// 计算交割日：欧式期权 交割日 = 到期日 + 交割类型天数
function calcDeliveryDate() {
  if (!isEuropean.value || !form.maturityDate) {
    form.deliveryDate = ''
    return
  }
  const daysMap = { T0: 0, T1: 1, T2: 2 }
  const d = new Date(form.maturityDate)
  d.setDate(d.getDate() + (daysMap[form.deliveryType] ?? 2))
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  form.deliveryDate = `${y}-${m}-${day}`
}

// 交易日变化时同步期权费交割日
function handleTradeDateChange() {
  form.premiumValueDate = form.tradeDate
  calcDays()
  calcDeliveryDate()
}

// 到期日变化时重算天数和交割日
function handleMaturityDateChange() {
  calcDays()
  calcDeliveryDate()
}

// 交割类型变化时重算交割日
function handleDeliveryTypeChange() {
  calcDeliveryDate()
}

// 涨跌方向变化时自动推导期权类型
function handlePriceDirectionChange(val) {
  form.optionType = val === 'UP' ? 'CALL' : 'PUT'
}

// 监听面值（交易金额）变化，自动计算期权费 = 面值 * 5%
watch(
  () => form.notionalAmount,
  (val) => {
    if (val != null && val > 0) {
      // 期权费 = 面值 * 5%，保留2位小数
      form.premiumAmount = Math.round(val * 0.05 * 100) / 100
    } else {
      form.premiumAmount = null
    }
  }
)

// 期权类别变化时清理无关字段并重算
function handleOptionStyleChange() {
  if (isAmerican.value) {
    // 美式：清理欧式专属字段
    form.maturityDate = ''
    form.deliveryDate = ''
    form.days = null
  } else {
    // 欧式：清理美式专属字段
    form.observationStartDate = ''
    form.observationEndDate = ''
    calcDays()
    calcDeliveryDate()
  }
}

// 打开客户搜索弹窗
function openCustomerDialog() {
  customerDialogVisible.value = true
}

// 客户选中回调：回填客户号与名称，并加载该客户账户列表
function handleCustomerSelect(customer) {
  form.customerId = customer.customerId
  form.customerName = customer.customerName
  form.currency1Account = ''
  form.currency2Account = ''
  form.premiumAccountId = ''
  loadAccounts(customer.customerId)
}

// 加载客户账户列表：调用 getCustomerAccounts，结果存入 allAccounts；同时默认选中期权费CNY账户
async function loadAccounts(customerId) {
  accountLoading.value = true
  try {
    const res = await getCustomerAccounts(customerId)
    allAccounts.value = res.data || []
    // 期权费账户默认选第一个CNY账户
    const cnyAccount = allAccounts.value.find((a) => a.currency === 'CNY')
    if (cnyAccount) {
      form.premiumAccountId = cnyAccount.accountId
      form.premiumCurrency = 'CNY'
    }
  } catch (e) {
    allAccounts.value = []
  } finally {
    accountLoading.value = false
  }
}

// 提交期权交易：校验表单 -> 组装数据 -> 调用接口 -> 成功后返回
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      // 美式期权：到期日 = 观察期结束日（作为最后可行权日）
      let submitData = { ...form }
      if (isAmerican.value) {
        submitData.maturityDate = form.observationEndDate
        submitData.deliveryDate = '' // 美式期权交割日在行权时确定
      }
      // 行权时点拼接为完整时间戳：交易日 15:00:00
      submitData.exerciseTimePoint = `${form.tradeDate}T15:00:00`
      // 期权费交割日 = 交易日
      submitData.premiumValueDate = form.tradeDate
      // 交割方式固定全额
      submitData.settlementMethod = 'FULL'

      const payload = {
        ...submitData,
        branchCode: userStore.userInfo.orgCode || ''
      }
      await createOption(payload)
      // 编辑模式：先完成修改任务，再跳转待办
      if (isEditMode.value && modifyTaskId.value) {
        try {
          await completeModifyTask(modifyTaskId.value)
        } catch (e) {
          // 修改任务完成失败不影响交易创建
        }
        ElMessage.success('期权交易修改提交成功')
        router.push('/fx/todo')
      } else {
        ElMessage.success('期权交易录入成功')
        router.back()
      }
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      submitting.value = false
    }
  })
}

// 取消：返回上一页
function handleCancel() {
  router.back()
}

// 编辑模式：加载已录入的交易数据回填表单
async function loadTradeForEdit(tradeId) {
  try {
    const res = await getOptionDetail(tradeId)
    const master = res.data?.master
    const option = res.data?.optionDetail
    if (!master || !option) return

    form.customerId = master.customerId || ''
    form.customerName = master.customerName || ''
    form.currencyPair = master.currencyPair || 'USD/CNY'
    form.baseCurrency = master.baseCurrency || ''
    form.quoteCurrency = master.quoteCurrency || ''
    form.buyerSeller = master.tradeDirection || 'BUY'
    form.spotRate = master.spotRate ?? null
    form.tradeDate = master.tradeDate || getTodayStr()
    form.maturityDate = master.maturityDate || ''
    form.deliveryType = master.deliveryType || 'T2'
    form.deliveryDate = master.valueDate || ''
    form.purposeCode = master.purposeCode || ''
    form.fxPurposeCode = master.fxPurposeCode || ''

    form.currency1Account = option.currency1Account || ''
    form.currency2Account = option.currency2Account || ''
    form.premiumAccountId = option.premiumAccountId || ''
    form.optionType = option.optionType || 'CALL'
    form.priceDirection = option.optionType === 'CALL' ? 'UP' : 'DOWN'
    form.strikePrice = option.strikePrice ?? null
    form.optionStyle = option.optionStyle || 'EUROPEAN'
    form.notionalAmount = option.notionalAmount ?? null
    form.premiumAmount = option.premiumAmount ?? null
    form.premiumCurrency = option.premiumCurrency || 'CNY'
    form.premiumValueDate = option.premiumValueDate || form.tradeDate
    form.observationStartDate = option.observationStartDate || ''
    form.observationEndDate = option.observationEndDate || ''
    form.days = option.days ?? null
    form.settlementMethod = option.settlementMethod || 'FULL'

    // 加载客户账户列表
    if (master.customerId) {
      await loadAccounts(master.customerId)
    }
    // 加载即期报价并更新汇率
    await loadQuotes()
    calcDays()
    calcDeliveryDate()
  } catch (e) {
    ElMessage.error('加载交易数据失败')
  }
}

onMounted(() => {
  loadQuotes()
  // 默认按 T+2 计算交割日
  calcDeliveryDate()
  // 编辑模式：自动加载已录入的交易数据
  const tradeId = route.query.tradeId
  if (tradeId) {
    loadTradeForEdit(tradeId)
  }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">期权交易录入</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        class="trade-form"
      >
        <!-- 客户信息 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户号" prop="customerId">
              <el-input v-model="form.customerId" readonly placeholder="请选择客户">
                <template #append>
                  <el-button :icon="Search" @click="openCustomerDialog" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="form.customerName" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 货币对 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交易货币对" prop="currencyPair">
              <el-select
                v-model="form.currencyPair"
                placeholder="请选择货币对"
                style="width: 100%"
                @change="handleCurrencyPairChange"
              >
                <el-option
                  v-for="opt in currencyPairOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="买卖方向" prop="buyerSeller">
              <el-radio-group v-model="form.buyerSeller">
                <el-radio value="BUY">买入</el-radio>
                <el-radio value="SELL">卖出</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 账户信息 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="`${baseCurrencyName}账户`" prop="currency1Account">
              <el-select
                v-model="form.currency1Account"
                v-loading="accountLoading"
                :placeholder="`请选择${baseCurrencyName}账户`"
                style="width: 100%"
              >
                <el-option
                  v-for="acc in currency1AccountOptions"
                  :key="acc.accountId"
                  :label="accountLabel(acc)"
                  :value="acc.accountId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="`${quoteCurrencyName}账户`" prop="currency2Account">
              <el-select
                v-model="form.currency2Account"
                v-loading="accountLoading"
                :placeholder="`请选择${quoteCurrencyName}账户`"
                style="width: 100%"
              >
                <el-option
                  v-for="acc in currency2AccountOptions"
                  :key="acc.accountId"
                  :label="accountLabel(acc)"
                  :value="acc.accountId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权费账户">
              <el-select
                v-model="form.premiumAccountId"
                v-loading="accountLoading"
                placeholder="请选择期权费账户"
                disabled
                style="width: 100%"
              >
                <el-option
                  v-for="acc in premiumAccountOptions"
                  :key="acc.accountId"
                  :label="accountLabel(acc)"
                  :value="acc.accountId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="涨跌方向" prop="priceDirection">
              <el-radio-group v-model="form.priceDirection" @change="handlePriceDirectionChange">
                <el-radio value="UP">涨</el-radio>
                <el-radio value="DOWN">跌</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 汇率信息 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="即期汇率">
              <el-input-number
                v-model="form.spotRate"
                :precision="4"
                :step="0.0001"
                :min="0"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行价格" prop="strikePrice">
              <el-input-number
                v-model="form.strikePrice"
                :precision="4"
                :step="0.0001"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 期权类别与行权时点 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权类别" prop="optionStyle">
              <el-radio-group v-model="form.optionStyle" @change="handleOptionStyleChange">
                <el-radio value="AMERICAN">美式</el-radio>
                <el-radio value="EUROPEAN">欧式</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行权时点">
              <el-input model-value="15:00:00" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 交易日（公用于两种期权类别） -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交易日" prop="tradeDate">
              <el-date-picker
                v-model="form.tradeDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择交易日"
                style="width: 100%"
                @change="handleTradeDateChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交割类型" prop="deliveryType">
              <el-radio-group v-model="form.deliveryType" @change="handleDeliveryTypeChange">
                <el-radio value="T0">T+0</el-radio>
                <el-radio value="T1">T+1</el-radio>
                <el-radio value="T2">T+2</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 美式期权：观察期开始日 / 观察期结束日 -->
        <el-row v-if="isAmerican" :gutter="20">
          <el-col :span="12">
            <el-form-item label="观察期开始日" prop="observationStartDate">
              <el-date-picker
                v-model="form.observationStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择观察期开始日"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="观察期结束日" prop="observationEndDate">
              <el-date-picker
                v-model="form.observationEndDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择观察期结束日"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 欧式期权：到期日 / 交割日 -->
        <el-row v-if="isEuropean" :gutter="20">
          <el-col :span="12">
            <el-form-item label="到期日" prop="maturityDate">
              <el-date-picker
                v-model="form.maturityDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择到期日"
                style="width: 100%"
                @change="handleMaturityDateChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交割日">
              <el-date-picker
                v-model="form.deliveryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="交割日（自动计算）"
                style="width: 100%"
                disabled
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 欧式期权：天数 -->
        <el-row v-if="isEuropean" :gutter="20">
          <el-col :span="12">
            <el-form-item label="天数">
              <el-input-number
                v-model="form.days"
                :min="0"
                :controls="false"
                style="width: 100%"
                disabled
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期权费交割日">
              <el-date-picker
                v-model="form.premiumValueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="期权费交割日"
                style="width: 100%"
                disabled
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 美式期权：期权费交割日（单独一行，因为美式没有天数/交割日行） -->
        <el-row v-if="isAmerican" :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权费交割日">
              <el-date-picker
                v-model="form.premiumValueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="期权费交割日"
                style="width: 100%"
                disabled
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交割方式">
              <el-input model-value="全额交割" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 欧式期权：交割方式 + 面值 -->
        <el-row v-if="isEuropean" :gutter="20">
          <el-col :span="12">
            <el-form-item label="交割方式">
              <el-input model-value="全额交割" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="`面值（${baseCurrencyName}）`" prop="notionalAmount">
              <el-input-number
                v-model="form.notionalAmount"
                :precision="2"
                :step="100"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 美式期权：面值（单独一行） -->
        <el-row v-if="isAmerican" :gutter="20">
          <el-col :span="12">
            <el-form-item :label="`面值（${baseCurrencyName}）`" prop="notionalAmount">
              <el-input-number
                v-model="form.notionalAmount"
                :precision="2"
                :step="100"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权费金额">
              <el-input-number
                v-model="form.premiumAmount"
                :precision="2"
                :step="100"
                :min="0"
                :controls="false"
                disabled
                placeholder="自动计算：面值×5%"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期权费币种">
              <el-input v-model="form.premiumCurrency" placeholder="请输入期权费币种" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 用途编码 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用途编码">
              <el-input v-model="form.purposeCode" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结售汇用途编码">
              <el-input v-model="form.fxPurposeCode" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 提交提醒：复核通过后会冻结面值金额并扣除期权费 -->
        <el-alert
          title="提交提醒"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          <template #default>
            交易复核通过后，将冻结 {{ baseCurrencyName }} 账户对应面值的金额（行权时扣除，放弃时解冻），同时从期权费账户扣除期权费。
          </template>
        </el-alert>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 客户搜索弹窗 -->
    <CustomerSearchDialog v-model:visible="customerDialogVisible" @select="handleCustomerSelect" />
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
  height: 100%;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.trade-form {
  /* 移除 max-width 限制，让表单自适应可用空间，避免右侧待办面板挤压 */
  width: 100%;
}
</style>
