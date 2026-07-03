<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { createForwardTrade, updateAndResubmitForwardTrade, getTradeDetail } from '@/api/trade'
import { completeModifyTask } from '@/api/task'
import { getCustomerAccounts, getCustomerMarginAccounts } from '@/api/customer'
import { getForwardQuotes } from '@/api/quote'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import CustomerSearchDialog from '@/components/CustomerSearchDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// 表单引用，用于校验
const formRef = ref(null)
// 客户搜索弹窗显隐
const customerDialogVisible = ref(false)
// 提交中状态
const submitting = ref(false)
// 账户加载中状态
const accountLoading = ref(false)
// 牌价加载中状态
const quoteLoading = ref(false)
// 详情加载中状态
const detailLoading = ref(false)

// 编辑模式相关
const isEditMode = computed(() => !!route.query.tradeId)
const editTradeId = computed(() => route.query.tradeId || '')
const editTaskId = computed(() => route.query.taskId || '')

// 客户全部账户列表（选中客户后加载）
const allAccounts = ref([])

// 客户保证金账户列表（选中客户后加载）
const marginAccounts = ref([])

// 保证金比例：交易金额的 10%
const MARGIN_RATE = 0.10

// 远期牌价列表
const forwardQuoteList = ref([])

// 基础货币、报价货币（来自牌价跳转参数或用户选择）
const baseCurrency = ref(route.query.baseCurrency || '')
const quoteCurrency = ref(route.query.quoteCurrency || '')

// 期限（来自牌价跳转参数或用户选择：1D/SW/1M）
const term = ref(route.query.term || '')

// 牌价参数（用于计算成本汇率）
const totalBuyRate = ref(Number(route.query.totalBuyRate) || null)
const totalSellRate = ref(Number(route.query.totalSellRate) || null)

// 是否从URL参数进入（有货币对参数）
const hasQueryParams = computed(() => !!route.query.baseCurrency && !!route.query.quoteCurrency)

// 选中的牌价组合键（货币对|期限，用于下拉选择）
const selectedQuoteKey = ref('')

// 交易币对展示（只读）
const currencyPair = computed(() => `${baseCurrency.value}/${quoteCurrency.value}`)

// 加载远期牌价列表
async function loadForwardQuotes() {
  quoteLoading.value = true
  try {
    const res = await getForwardQuotes()
    forwardQuoteList.value = res.data || []

    // 如果有URL参数，找到对应的牌价并更新数据（按货币对+期限匹配）
    if (hasQueryParams.value && forwardQuoteList.value.length > 0) {
      const quote = forwardQuoteList.value.find(
        q => q.baseCurrency === baseCurrency.value &&
             q.quoteCurrency === quoteCurrency.value &&
             (term.value ? q.term === term.value : true)
      )
      if (quote) {
        selectedQuoteKey.value = getQuoteKey(quote)
        updateQuoteData(quote)
      }
    }

    // 如果没有URL参数且有牌价数据，默认选中第一个
    if (!hasQueryParams.value && forwardQuoteList.value.length > 0) {
      const firstQuote = forwardQuoteList.value[0]
      selectedQuoteKey.value = getQuoteKey(firstQuote)
      handleQuoteChange(selectedQuoteKey.value)
    }
  } catch (e) {
    forwardQuoteList.value = []
  } finally {
    quoteLoading.value = false
  }
}

// 加载交易详情
async function loadTradeDetail() {
  detailLoading.value = true
  try {
    const res = await getTradeDetail(editTradeId.value)
    const detail = res.data
    if (detail && detail.master) {
      const master = detail.master
      const forwardDetail = detail.forwardDetail
      
      // 填充表单数据
      baseCurrency.value = master.baseCurrency || ''
      quoteCurrency.value = master.quoteCurrency || ''
      // 注意：term 需要从牌价中推断，或者可能需要存储在主表/子表中，这里暂时留空
      term.value = ''
      totalBuyRate.value = null
      totalSellRate.value = null

      form.customerId = master.customerId || ''
      form.customerName = master.customerName || ''
      // 远期交易子表中可能没有 currency1/2 账户，所以从主表获取或留空
      form.currency1Account = ''
      form.currency2Account = ''
      form.customerRate = master.customerRate || null
      form.costRate = master.costRate || null
      form.branchProfitPoint = master.branchProfitPoint || null
      form.tradeDirection = master.tradeDirection || 'BUY'
      form.notionalAmount = master.notionalAmount || null
      form.tradeDate = master.tradeDate || getTodayStr()
      form.valueDate = master.valueDate || ''
      form.maturityDate = master.maturityDate || ''
      form.deliveryType = master.deliveryType || 'T2'
      form.settlementMethod = 'FULL'
      form.marginAccountId = forwardDetail?.marginAccountId || ''
      form.marginAmount = forwardDetail?.marginAmount || null
      form.purposeCode = master.purposeCode || ''
      form.fxPurposeCode = master.fxPurposeCode || ''
      form.spotRate = master.spotRate || null
      
      // 加载客户账户
      if (master.customerId) {
        loadAccounts(master.customerId)
        loadMarginAccounts(master.customerId)
      }
      
      // 设置选中的牌价
      if (forwardQuoteList.value.length > 0) {
        const quote = forwardQuoteList.value.find(
          q => q.baseCurrency === baseCurrency.value &&
               q.quoteCurrency === quoteCurrency.value
        )
        if (quote) {
          selectedQuoteKey.value = getQuoteKey(quote)
          updateQuoteData(quote)
        }
      }
    }
  } catch (e) {
    ElMessage.error('加载交易详情失败')
  } finally {
    detailLoading.value = false
  }
}

// 更新牌价数据
function updateQuoteData(quote) {
  baseCurrency.value = quote.baseCurrency
  quoteCurrency.value = quote.quoteCurrency
  term.value = quote.term || ''
  totalBuyRate.value = Number(quote.totalBuyRate) || null
  totalSellRate.value = Number(quote.totalSellRate) || null

  // 如果客户汇率为空，根据方向自动填充分/客买价或分/客卖价
  // 客户买入(BUY)用分/客卖价；客户卖出(SELL)用分/客买价
  if (form.customerRate == null) {
    if (form.tradeDirection === 'BUY') {
      form.customerRate = Number(quote.branchCustomerSellRate) || null
      form.spotRate = Number(quote.branchCustomerSellRate) || null
    } else {
      form.customerRate = Number(quote.branchCustomerBuyRate) || null
      form.spotRate = Number(quote.branchCustomerBuyRate) || null
    }
  }

  // 根据期限自动计算到期日
  form.maturityDate = calculateMaturityDate(form.valueDate, term.value)
  calculateCostAndProfit()
}

// 生成牌价组合键（货币对|期限）
function getQuoteKey(quote) {
  return `${quote.currencyPair}|${quote.term}`
}

// 牌价选择变化（按货币对+期限）
function handleQuoteChange(val) {
  const quote = forwardQuoteList.value.find(q => getQuoteKey(q) === val)
  if (quote) {
    // 切换牌价时清空相关数据，让它重新自动填充
    form.customerRate = null
    form.spotRate = null
    updateQuoteData(quote)
  }
}

// 获取今日日期字符串 YYYY-MM-DD
function getTodayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 在指定日期上增加 N 天，返回 YYYY-MM-DD
function addDays(dateStr, days) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  d.setDate(d.getDate() + days)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 在指定日期上增加 N 个月，返回 YYYY-MM-DD
function addMonths(dateStr, months) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  d.setMonth(d.getMonth() + months)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 根据期限计算到期日：1D=起息日+1天，SW=起息日+7天，1M=起息日+1个月
function calculateMaturityDate(valueDateStr, termStr) {
  if (!valueDateStr || !termStr) return ''
  switch (termStr) {
    case '1D': return addDays(valueDateStr, 1)
    case 'SW': return addDays(valueDateStr, 7)
    case '1M': return addMonths(valueDateStr, 1)
    default: return ''
  }
}

// 远期交易表单数据
const form = reactive({
  spotRate: Number(route.query.customerRate) || null,
  customerId: '',
  customerName: '',
  currency1Account: '',
  currency2Account: '',
  customerRate: Number(route.query.customerRate) || null,
  costRate: null,
  branchProfitPoint: null,
  tradeDirection: (route.query.direction || 'buy').toUpperCase(),
  notionalAmount: null,
  tradeDate: getTodayStr(),
  valueDate: getTodayStr(),
  maturityDate: '',
  deliveryType: 'T2',
  settlementMethod: 'FULL',
  marginAccountId: '',
  marginAmount: null,
  purposeCode: '',
  fxPurposeCode: ''
})

// 表单校验规则
const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  currency1Account: [{ required: true, message: '请选择币种1账户', trigger: 'change' }],
  currency2Account: [{ required: true, message: '请选择币种2账户', trigger: 'change' }],
  notionalAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { type: 'number', min: 0.000001, message: '金额必须大于0', trigger: 'blur' }
  ],
  customerRate: [
    { required: true, message: '请输入客户汇率', trigger: 'blur' },
    { type: 'number', min: 0.00000001, message: '客户汇率必须大于0', trigger: 'blur' }
  ],
  maturityDate: [{ required: true, message: '请选择到期日', trigger: 'change' }],
  valueDate: [{ required: true, message: '请选择起息日', trigger: 'change' }]
}

// 币种1账户下拉选项：按基础货币过滤
const currency1AccountOptions = computed(() =>
  allAccounts.value.filter((a) => a.currency === baseCurrency.value)
)

// 币种2账户下拉选项：按报价货币过滤
const currency2AccountOptions = computed(() =>
  allAccounts.value.filter((a) => a.currency === quoteCurrency.value)
)

// 保证金账户下拉选项：取客户保证金账户列表
const marginAccountOptions = computed(() => marginAccounts.value)

// 账户下拉标签：账号 + 币种 + 余额
function accountLabel(account) {
  return `${account.accountNo} | ${account.currency} | 余额 ${account.balance ?? 0}`
}

// 保证金账户下拉标签：账户ID + 币种 + 可用余额（余额 - 冻结）
function marginAccountLabel(acc) {
  const balance = acc.balance ?? 0
  const frozen = acc.frozenAmount ?? 0
  const available = Number(balance) - Number(frozen)
  return `${acc.marginAccountId} | ${acc.currency} | 可用 ${available.toFixed(2)}`
}

// 计算保证金账户可用余额
function getMarginAvailable(acc) {
  if (!acc) return 0
  const balance = Number(acc.balance ?? 0)
  const frozen = Number(acc.frozenAmount ?? 0)
  return balance - frozen
}

// 选中的保证金账户对象（用于余额校验）
const selectedMarginAccount = computed(() =>
  marginAccounts.value.find(a => a.marginAccountId === form.marginAccountId)
)

// 交割类型变化时自动计算起息日：T+0 当日，T+1 次日，T+2 第三日
// 同时根据期限重算到期日
function handleDeliveryTypeChange(val) {
  const daysMap = { T0: 0, T1: 1, T2: 2 }
  form.valueDate = addDays(form.tradeDate, daysMap[val] ?? 2)
  form.maturityDate = calculateMaturityDate(form.valueDate, term.value)
}

// 交易日变化时同步重算起息日
function handleTradeDateChange() {
  handleDeliveryTypeChange(form.deliveryType)
}

// 打开客户搜索弹窗
function openCustomerDialog() {
  customerDialogVisible.value = true
}

// 自动计算成本汇率和分行收益点
// 客户买入外汇(BUY): 成本汇率=总行卖价(totalSellRate), 分行收益点=(客户汇率-成本汇率)*1000
// 客户卖出外汇(SELL): 成本汇率=总行买价(totalBuyRate), 分行收益点=(成本汇率-客户汇率)*1000
function calculateCostAndProfit() {
  if (form.tradeDirection === 'BUY') {
    form.costRate = totalSellRate.value
    if (form.customerRate != null && form.costRate != null) {
      form.branchProfitPoint = (form.customerRate - form.costRate) * 1000
    } else {
      form.branchProfitPoint = null
    }
  } else {
    form.costRate = totalBuyRate.value
    if (form.customerRate != null && form.costRate != null) {
      form.branchProfitPoint = (form.costRate - form.customerRate) * 1000
    } else {
      form.branchProfitPoint = null
    }
  }
}

// 买卖方向变化时，根据新方向从牌价重新获取客户汇率
// 客户买入(BUY)用分/客卖价；客户卖出(SELL)用分/客买价
function handleTradeDirectionChange() {
  const quote = forwardQuoteList.value.find(q => getQuoteKey(q) === selectedQuoteKey.value)
  if (quote) {
    if (form.tradeDirection === 'BUY') {
      form.customerRate = Number(quote.branchCustomerSellRate) || null
      form.spotRate = Number(quote.branchCustomerSellRate) || null
    } else {
      form.customerRate = Number(quote.branchCustomerBuyRate) || null
      form.spotRate = Number(quote.branchCustomerBuyRate) || null
    }
  }
  calculateCostAndProfit()
}

// 客户选中回调：回填客户号与名称，并加载该客户账户列表与保证金账户列表
function handleCustomerSelect(customer) {
  form.customerId = customer.customerId
  form.customerName = customer.customerName
  form.currency1Account = ''
  form.currency2Account = ''
  form.marginAccountId = ''
  loadAccounts(customer.customerId)
  loadMarginAccounts(customer.customerId)
}

// 加载客户账户列表：调用 getCustomerAccounts，结果存入 allAccounts
async function loadAccounts(customerId) {
  accountLoading.value = true
  try {
    const res = await getCustomerAccounts(customerId)
    allAccounts.value = res.data || []
  } catch (e) {
    allAccounts.value = []
  } finally {
    accountLoading.value = false
  }
}

// 加载客户保证金账户列表
async function loadMarginAccounts(customerId) {
  try {
    const res = await getCustomerMarginAccounts(customerId)
    marginAccounts.value = res.data || []
  } catch (e) {
    marginAccounts.value = []
  }
}

// 提交远期交易：校验表单 -> 校验保证金余额 -> 组装数据 -> 调用接口 -> 成功后返回
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    // 校验保证金账户余额是否充足
    if (form.marginAccountId && form.marginAmount != null) {
      const available = getMarginAvailable(selectedMarginAccount.value)
      if (available < Number(form.marginAmount)) {
        ElMessage.error(`保证金账户余额不足：可用 ${available.toFixed(2)}，需要保证金 ${form.marginAmount}`)
        return
      }
    }
    submitting.value = true
    try {
      const payload = {
        customerId: form.customerId,
        branchCode: userStore.userInfo.orgCode || '',
        baseCurrency: baseCurrency.value,
        quoteCurrency: quoteCurrency.value,
        notionalAmount: form.notionalAmount,
        tradeDirection: form.tradeDirection,
        customerRate: form.customerRate,
        valueDate: form.valueDate,
        maturityDate: form.maturityDate,
        deliveryType: form.deliveryType,
        settlementMethod: form.settlementMethod,
        purposeCode: form.purposeCode || null,
        fxPurposeCode: form.fxPurposeCode || null,
        spotRate: form.spotRate,
        costRate: form.costRate,
        branchProfitPoint: form.branchProfitPoint,
        currency1Account: form.currency1Account,
        currency2Account: form.currency2Account,
        marginAccountId: form.marginAccountId || null,
        marginAmount: form.marginAmount,
        tradeDate: form.tradeDate
      }
      
      if (isEditMode.value) {
        await updateAndResubmitForwardTrade(editTradeId.value, payload)
        if (editTaskId.value) {
          await completeModifyTask(editTaskId.value)
        }
        ElMessage.success('远期交易编辑成功')
      } else {
        await createForwardTrade(payload)
        ElMessage.success('远期交易录入成功')
      }
      // 触发待办面板刷新
      appStore.refreshTodos()
      router.back()
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

onMounted(async () => {
  // 默认按 T+2 计算起息日
  handleDeliveryTypeChange(form.deliveryType)
  // 加载远期牌价列表（会自动计算成本汇率和分行收益点）
  await loadForwardQuotes()
  // 如果是编辑模式，加载交易详情
  if (isEditMode.value) {
    await loadTradeDetail()
  }
})

// 监听客户汇率变化，重新计算分行收益点
watch(() => form.customerRate, () => {
  calculateCostAndProfit()
})

// 监听交易金额变化，自动计算保证金金额 = 交易金额 × 10%（不可人工修改）
watch(() => form.notionalAmount, (val) => {
  if (val != null && val > 0) {
    form.marginAmount = Math.round(val * MARGIN_RATE * 100) / 100
  } else {
    form.marginAmount = null
  }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">{{ isEditMode ? '编辑远期交易' : '远期交易录入' }}</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        class="trade-form"
      >
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

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="即期汇率" prop="spotRate">
              <el-input-number
                v-model="form.spotRate"
                :precision="4"
                :step="0.0001"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交易币种">
              <el-select
                v-if="!hasQueryParams"
                v-model="selectedQuoteKey"
                v-loading="quoteLoading"
                placeholder="请选择交易币种"
                style="width: 100%"
                @change="handleQuoteChange"
              >
                <el-option
                  v-for="quote in forwardQuoteList"
                  :key="quote.quoteId"
                  :label="`${quote.currencyPair} - ${quote.term}`"
                  :value="getQuoteKey(quote)"
                />
              </el-select>
              <el-input v-else :model-value="currencyPair" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期限">
              <el-input :model-value="term" readonly placeholder="选择币种后自动填充" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="币种1账户" prop="currency1Account">
              <el-select
                v-model="form.currency1Account"
                v-loading="accountLoading"
                placeholder="请选择币种1账户"
                style="width: 100%"
              >
                <el-option
                  v-for="acc in currency1AccountOptions"
                  :key="acc.accountId"
                  :label="accountLabel(acc)"
                  :value="acc.accountNo"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种2账户" prop="currency2Account">
              <el-select
                v-model="form.currency2Account"
                v-loading="accountLoading"
                placeholder="请选择币种2账户"
                style="width: 100%"
              >
                <el-option
                  v-for="acc in currency2AccountOptions"
                  :key="acc.accountId"
                  :label="accountLabel(acc)"
                  :value="acc.accountNo"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户汇率" prop="customerRate">
              <el-input-number
                v-model="form.customerRate"
                :precision="4"
                :step="0.0001"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成本汇率">
              <el-input-number
                v-model="form.costRate"
                :precision="4"
                :step="0.0001"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分行收益点">
              <el-input-number
                v-model="form.branchProfitPoint"
                :precision="2"
                :step="0.01"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="买卖方向" prop="tradeDirection">
              <el-radio-group v-model="form.tradeDirection" @change="handleTradeDirectionChange">
                <el-radio value="BUY">买入</el-radio>
                <el-radio value="SELL">卖出</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="金额" prop="notionalAmount">
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
          <el-col :span="12">
            <el-form-item label="交易日" prop="tradeDate">
              <el-date-picker
                v-model="form.tradeDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择交易日"
                style="width: 100%"
                disabled
                @change="handleTradeDateChange"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="起息日" prop="valueDate">
              <el-date-picker
                v-model="form.valueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择起息日"
                style="width: 100%"
                disabled
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日" prop="maturityDate">
              <el-date-picker
                v-model="form.maturityDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择到期日"
                style="width: 100%"
                disabled
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交割类型" prop="deliveryType">
              <el-radio-group v-model="form.deliveryType" @change="handleDeliveryTypeChange">
                <el-radio value="T0">T+0</el-radio>
                <el-radio value="T1">T+1</el-radio>
                <el-radio value="T2">T+2</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交割方式">
              <el-input model-value="全额交割" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="保证金账户">
              <el-select
                v-model="form.marginAccountId"
                v-loading="accountLoading"
                placeholder="请选择保证金账户"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="acc in marginAccountOptions"
                  :key="acc.marginAccountId"
                  :label="marginAccountLabel(acc)"
                  :value="acc.marginAccountId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保证金金额">
              <el-input-number
                v-model="form.marginAmount"
                :precision="2"
                :step="0"
                :min="0"
                :controls="false"
                disabled
                placeholder="自动计算（交易金额×10%）"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

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
  max-width: 1000px;
}
</style>
