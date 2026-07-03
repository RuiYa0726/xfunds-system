<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { createOption } from '@/api/option'
import { getCustomerAccounts } from '@/api/customer'
import { useUserStore } from '@/store/user'
import CustomerSearchDialog from '@/components/CustomerSearchDialog.vue'

const router = useRouter()
const userStore = useUserStore()

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
  baseCurrency: 'USD',
  quoteCurrency: 'CNY',
  currency1Account: '',
  currency2Account: '',
  premiumAccountId: '',
  buyerSeller: 'BUY',
  optionType: 'CALL',
  priceDirection: 'UP',
  spotRate: null,
  strikePrice: null,
  optionStyle: 'EUROPEAN',
  exerciseTimePoint: '',
  tradeDate: getTodayStr(),
  maturityDate: '',
  deliveryType: 'T2',
  deliveryDate: '',
  days: null,
  premiumValueDate: '',
  settlementMethod: 'FULL',
  notionalAmount: null,
  currency1Amount: null,
  currency2Amount: null,
  premiumAmount: null,
  premiumCurrency: 'CNY',
  observationStartDate: '',
  observationEndDate: '',
  purposeCode: '',
  fxPurposeCode: ''
})

// 表单校验规则
const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  currencyPair: [{ required: true, message: '请输入货币对', trigger: 'blur' }],
  currency1Account: [{ required: true, message: '请选择币种1账户', trigger: 'change' }],
  currency2Account: [{ required: true, message: '请选择币种2账户', trigger: 'change' }],
  strikePrice: [
    { required: true, message: '请输入执行价格', trigger: 'blur' },
    { type: 'number', min: 0.00000001, message: '执行价格必须大于0', trigger: 'blur' }
  ],
  notionalAmount: [
    { required: true, message: '请输入原始签约金额', trigger: 'blur' },
    { type: 'number', min: 0.000001, message: '金额必须大于0', trigger: 'blur' }
  ],
  maturityDate: [{ required: true, message: '请选择到期日', trigger: 'change' }]
}

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
}

// 币种1账户下拉选项：按基础货币过滤
const currency1AccountOptions = computed(() =>
  allAccounts.value.filter((a) => a.currency === form.baseCurrency)
)

// 币种2账户下拉选项：按报价货币过滤
const currency2AccountOptions = computed(() =>
  allAccounts.value.filter((a) => a.currency === form.quoteCurrency)
)

// 期权费账户下拉选项：取全部账户
const premiumAccountOptions = computed(() => allAccounts.value)

// 账户下拉标签：账号 + 币种 + 余额
function accountLabel(account) {
  return `${account.accountNo} | ${account.currency} | 余额 ${account.balance ?? 0}`
}

// 计算天数：到期日 - 交易日
function calcDays() {
  if (form.tradeDate && form.maturityDate) {
    const start = new Date(form.tradeDate)
    const end = new Date(form.maturityDate)
    const diff = Math.round((end - start) / (1000 * 60 * 60 * 24))
    form.days = diff >= 0 ? diff : null
  } else {
    form.days = null
  }
}

// 交易日或到期日变化时重算天数
function handleDateChange() {
  calcDays()
}

// 交割类型变化时自动计算交割日：T+0 当日，T+1 次日，T+2 第三日
function handleDeliveryTypeChange(val) {
  const daysMap = { T0: 0, T1: 1, T2: 2 }
  const d = new Date(form.tradeDate || getTodayStr())
  d.setDate(d.getDate() + (daysMap[val] ?? 2))
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  form.deliveryDate = `${y}-${m}-${day}`
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

// 提交期权交易：校验表单 -> 组装数据 -> 调用接口 -> 成功后返回
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        ...form,
        branchCode: userStore.userInfo.orgCode || ''
      }
      await createOption(payload)
      ElMessage.success('期权交易录入成功')
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

onMounted(() => {
  // 默认按 T+2 计算交割日
  handleDeliveryTypeChange(form.deliveryType)
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
            <el-form-item label="交易货币对" prop="currencyPair">
              <el-input
                v-model="form.currencyPair"
                placeholder="如 USD/CNY"
                @change="handleCurrencyPairChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="基础货币">
              <el-input v-model="form.baseCurrency" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="报价货币">
              <el-input v-model="form.quoteCurrency" readonly />
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
                  :value="acc.accountId"
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
                clearable
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
            <el-form-item label="买卖方向" prop="buyerSeller">
              <el-radio-group v-model="form.buyerSeller">
                <el-radio value="BUY">买入</el-radio>
                <el-radio value="SELL">卖出</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权种类" prop="optionType">
              <el-radio-group v-model="form.optionType">
                <el-radio value="CALL">看涨</el-radio>
                <el-radio value="PUT">看跌</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="涨跌方向" prop="priceDirection">
              <el-radio-group v-model="form.priceDirection">
                <el-radio value="UP">涨</el-radio>
                <el-radio value="DOWN">跌</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="即期汇率">
              <el-input-number
                v-model="form.spotRate"
                :precision="8"
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
                :precision="8"
                :step="0.0001"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权类别" prop="optionStyle">
              <el-radio-group v-model="form.optionStyle">
                <el-radio value="AMERICAN">美式</el-radio>
                <el-radio value="EUROPEAN">欧式</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行权时点">
              <el-input v-model="form.exerciseTimePoint" placeholder="请输入行权时点" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交易日" prop="tradeDate">
              <el-date-picker
                v-model="form.tradeDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择交易日"
                style="width: 100%"
                @change="handleDateChange"
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
                @change="handleDateChange"
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
            <el-form-item label="交割日">
              <el-date-picker
                v-model="form.deliveryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择交割日"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="天数">
              <el-input-number
                v-model="form.days"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期权费交割日">
              <el-date-picker
                v-model="form.premiumValueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择期权费交割日"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交割方式" prop="settlementMethod">
              <el-radio-group v-model="form.settlementMethod">
                <el-radio value="FULL">全额</el-radio>
                <el-radio value="NET">差额</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原始签约金额" prop="notionalAmount">
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
            <el-form-item label="货币1金额">
              <el-input-number
                v-model="form.currency1Amount"
                :precision="2"
                :step="100"
                :min="0"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="货币2金额">
              <el-input-number
                v-model="form.currency2Amount"
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

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="观察期开始日">
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
            <el-form-item label="观察期结束日">
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
  max-width: 1100px;
}
</style>
