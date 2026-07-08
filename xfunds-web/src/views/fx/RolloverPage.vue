<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTradeDetail, rolloverOriginal, rolloverMarket, getCustomerAccounts, searchQuotes } from '@/api/trade'
import { getTaskDetail, completeModifyTask } from '@/api/task'
import { formatTradeDirection, formatSpecialTradeType } from '@/utils/constants'

const route = useRoute()
const router = useRouter()

// 编辑模式任务ID（退回经办后重新编辑时由待办列表传入）
const modifyTaskId = ref(null)
// 编辑模式下从任务类型推断的展期模式
const taskResolvedMode = ref(null)

// 展期模式：编辑模式优先使用任务类型推断的模式，否则取路由参数
const rolloverMode = computed(() => taskResolvedMode.value || route.query.mode || 'ORIGINAL')

// 加载状态
const loading = ref(false)
const submitting = ref(false)

// 原交易数据
const originalTrade = ref(null)

// 客户账户列表（全部）
const allAccounts = ref([])
// 轧差账户列表（按轧差货币过滤）
const nettingAccounts = ref([])

// 即期报价（市价展期使用）
const spotQuote = ref(null)

// 计算今日日期
const today = computed(() => {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
})

// 表单数据
const form = reactive({
  // 近端汇率
  nearLegCostRate: null,
  nearLegCustomerRate: null,
  // 远端汇率
  farLegCostRate: null,
  farLegCustomerRate: null,
  // 远端金额（默认等于原交易金额，可修改）
  farLegAmount: null,
  // 远端到期日（默认填充原到期日，必须修改为更远日期）
  newMaturityDate: '',
  // 远端币种1账户
  farLegCurrency1Account: '',
  // 远端币种2账户
  farLegCurrency2Account: '',
  // 远端分行收益点
  farLegBranchProfitPoint: null,
  // 轧差货币（市价展期，默认为报价货币）
  nettingCurrency: 'CNY',
  // 轧差账户（市价展期）
  nettingAccount: '',
  // 备注
  remark: ''
})

// 计算属性：原交易金额
const originalAmount = computed(() => originalTrade.value?.notionalAmount || 0)

// 计算属性：近端买卖方向（与原交易方向相反，平掉旧交易）
const nearLegDirection = computed(() => {
  const dir = originalTrade.value?.tradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})

// 计算属性：远端买卖方向（与原交易方向相同，新远期交易）
const farLegDirection = computed(() => originalTrade.value?.tradeDirection)

// 计算属性：近端交割方式（原价=无需交割，市价=差额交割）
const nearLegSettlementMethod = computed(() =>
  rolloverMode.value === 'MARKET' ? 'NET' : 'NONE'
)

// 计算属性：远端交割方式（均为全额交割）
const farLegSettlementMethod = computed(() => 'FULL')

// 计算属性：是否市价展期
const isMarketMode = computed(() => rolloverMode.value === 'MARKET')

// 计算属性：原交易到期日（近端起息日）
const originalMaturityDate = computed(() => originalTrade.value?.maturityDate || '')

// 计算属性：基础货币、报价货币
const baseCurrency = computed(() => originalTrade.value?.baseCurrency || '')
const quoteCurrency = computed(() => originalTrade.value?.quoteCurrency || '')

// 计算属性：客户损益（市价展期）
// 原BUY（近端SELL）：P&L = (近端客户汇率 - 原客户汇率) * 金额
// 原SELL（近端BUY）：P&L = (原客户汇率 - 近端客户汇率) * 金额
const customerPnl = computed(() => {
  if (!isMarketMode.value) return null
  const amount = toNumber(originalAmount.value)
  const newRate = toNumber(form.nearLegCustomerRate)
  const origRate = toNumber(originalTrade.value?.customerRate)
  const direction = originalTrade.value?.tradeDirection
  if (amount === null || newRate === null || origRate === null || !direction) return null
  if (direction === 'BUY') {
    return roundTo((newRate - origRate) * amount, 2)
  }
  if (direction === 'SELL') {
    return roundTo((origRate - newRate) * amount, 2)
  }
  return null
})

// 计算属性：轧差金额（市价展期，客户损益的绝对值）
const nettingAmount = computed(() => {
  const pnl = toNumber(customerPnl.value)
  if (pnl === null) return null
  return roundTo(Math.abs(pnl), 2)
})

// 计算属性：轧差账户余额
const nettingAccountBalance = computed(() => {
  if (!form.nettingAccount || nettingAccounts.value.length === 0) return null
  const acc = nettingAccounts.value.find(a => a.accountNo === form.nettingAccount)
  return acc ? acc.balance : null
})

// 计算属性：轧差账户余额是否不足
const nettingBalanceInsufficient = computed(() => {
  if (!isMarketMode.value) return false
  const balance = toNumber(nettingAccountBalance.value)
  const amount = toNumber(nettingAmount.value)
  if (balance === null || amount === null || amount === 0) return false
  return balance < amount
})

// 计算属性：远端到期日是否晚于原到期日
const newMaturityDateInvalid = computed(() => {
  if (!form.newMaturityDate || !originalMaturityDate.value) return false
  return form.newMaturityDate <= originalMaturityDate.value
})

// 币种1账户下拉选项（按基础货币过滤）
const currency1AccountOptions = computed(() =>
  allAccounts.value.filter(a => a.currency === baseCurrency.value)
)

// 币种2账户下拉选项（按报价货币过滤）
const currency2AccountOptions = computed(() =>
  allAccounts.value.filter(a => a.currency === quoteCurrency.value)
)

function toNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function roundTo(value, scale) {
  const num = toNumber(value)
  if (num === null) return null
  return Number(num.toFixed(scale))
}

// 计算分行收益点
// 客户买入(BUY): 分行收益点 = (客户汇率 - 成本汇率) * 1000
// 客户卖出(SELL): 分行收益点 = (成本汇率 - 客户汇率) * 1000
function calculateBranchProfitPoint(direction, customerRate, costRate) {
  const cRate = toNumber(customerRate)
  const costR = toNumber(costRate)
  if (cRate === null || costR === null || !direction) return null
  if (direction === 'BUY') {
    return roundTo((cRate - costR) * 1000, 8)
  } else {
    return roundTo((costR - cRate) * 1000, 8)
  }
}

// 自动计算远端分行收益点
function updateFarLegProfitPoint() {
  form.farLegBranchProfitPoint = calculateBranchProfitPoint(
    farLegDirection.value, form.farLegCustomerRate, form.farLegCostRate
  )
}

// 加载客户全部账户列表
async function loadAllAccounts() {
  if (!originalTrade.value?.customerId) return
  try {
    const res = await getCustomerAccounts(originalTrade.value.customerId)
    allAccounts.value = res.data || []
  } catch (e) {
    allAccounts.value = []
  }
}

// 加载轧差账户列表（按轧差货币过滤）
async function loadNettingAccounts() {
  if (!originalTrade.value?.customerId || !form.nettingCurrency) return
  try {
    const res = await getCustomerAccounts(originalTrade.value.customerId, form.nettingCurrency)
    nettingAccounts.value = res.data || []
    if (nettingAccounts.value.length > 0 && !form.nettingAccount) {
      form.nettingAccount = nettingAccounts.value[0].accountNo
    }
  } catch (e) {
    nettingAccounts.value = []
  }
}

// 加载即期报价（市价展期使用）
async function loadSpotQuote() {
  if (!originalTrade.value?.currencyPair) return
  try {
    const res = await searchQuotes('SPOT', originalTrade.value.currencyPair)
    if (res.data && res.data.length > 0) {
      spotQuote.value = res.data[0]
      fillMarketRates()
    }
  } catch (e) {
    console.error('加载即期报价失败', e)
  }
}

// 市价展期：根据方向自动填充市场汇率
// 客户买基准货币(BUY): 成本汇率=总/分S/B(totalSellRate), 客户汇率=分/客S/B(branchCustomerSellRate)
// 客户卖基准货币(SELL): 成本汇率=总/分B/S(totalBuyRate), 客户汇率=分/客B/S(branchCustomerBuyRate)
function fillMarketRates() {
  if (!spotQuote.value) return
  const totalBuyRate = toNumber(spotQuote.value.totalBuyRate)
  const totalSellRate = toNumber(spotQuote.value.totalSellRate)
  const branchCustomerBuyRate = toNumber(spotQuote.value.branchCustomerBuyRate)
  const branchCustomerSellRate = toNumber(spotQuote.value.branchCustomerSellRate)

  // 近端方向（与原交易相反）
  const nearDir = nearLegDirection.value
  if (nearDir === 'BUY') {
    form.nearLegCostRate = totalSellRate
    form.nearLegCustomerRate = branchCustomerSellRate
  } else if (nearDir === 'SELL') {
    form.nearLegCostRate = totalBuyRate
    form.nearLegCustomerRate = branchCustomerBuyRate
  }

  // 远端方向（与原交易相同）
  const farDir = farLegDirection.value
  if (farDir === 'BUY') {
    form.farLegCostRate = totalSellRate
    form.farLegCustomerRate = branchCustomerSellRate
  } else if (farDir === 'SELL') {
    form.farLegCostRate = totalBuyRate
    form.farLegCustomerRate = branchCustomerBuyRate
  }

  updateFarLegProfitPoint()
}

// 原价展期：填充原交易汇率
function fillOriginalRates() {
  if (!originalTrade.value) return
  form.nearLegCostRate = toNumber(originalTrade.value.costRate)
  form.nearLegCustomerRate = toNumber(originalTrade.value.customerRate)
  form.farLegCostRate = toNumber(originalTrade.value.costRate)
  form.farLegCustomerRate = toNumber(originalTrade.value.customerRate)
  updateFarLegProfitPoint()
}

// 加载原交易详情
async function loadOriginalTrade() {
  const tradeId = route.query.tradeId
  if (!tradeId) {
    ElMessage.error('交易ID不能为空')
    router.back()
    return
  }

  loading.value = true
  try {
    const res = await getTradeDetail(tradeId)
    originalTrade.value = res.data.master
    // 远端金额默认等于原交易金额
    form.farLegAmount = originalTrade.value.notionalAmount
    // 远端到期日默认填充原到期日（必须修改为更远日期）
    form.newMaturityDate = originalTrade.value.maturityDate || ''
    // 轧差货币默认为报价货币
    if (originalTrade.value.quoteCurrency) {
      form.nettingCurrency = originalTrade.value.quoteCurrency
    }

    // 加载客户账户
    await loadAllAccounts()

    if (isMarketMode.value) {
      // 市价展期：加载即期报价和轧差账户
      await loadSpotQuote()
      await loadNettingAccounts()
    } else {
      // 原价展期：填充原交易汇率
      fillOriginalRates()
    }
  } catch (e) {
    ElMessage.error('加载交易详情失败')
  } finally {
    loading.value = false
  }
}

// 编辑模式：加载任务 payload 并回填表单
async function loadTaskAndOriginalTrade(taskId) {
  loading.value = true
  try {
    const res = await getTaskDetail(taskId)
    const task = res.data
    if (!task || !task.payload) {
      ElMessage.error('任务载荷为空')
      return
    }
    const payload = JSON.parse(task.payload)
    // 根据任务类型推断展期模式
    if (task.taskType === 'ROLLOVER_MARKET') {
      taskResolvedMode.value = 'MARKET'
    } else {
      taskResolvedMode.value = 'ORIGINAL'
    }
    // 加载原交易
    const tradeRes = await getTradeDetail(payload.originalTradeId)
    originalTrade.value = tradeRes.data.master
    // 回填表单
    form.newMaturityDate = payload.newMaturityDate || ''
    form.nearLegCostRate = payload.nearLegCostRate
    form.nearLegCustomerRate = payload.nearLegCustomerRate
    form.farLegCostRate = payload.farLegCostRate
    form.farLegCustomerRate = payload.farLegCustomerRate
    form.farLegAmount = payload.farLegAmount
    form.farLegBranchProfitPoint = payload.farLegBranchProfitPoint
    form.farLegCurrency1Account = payload.farLegCurrency1Account || ''
    form.farLegCurrency2Account = payload.farLegCurrency2Account || ''
    form.remark = payload.remark || ''
    // 市价展期额外字段
    if (taskResolvedMode.value === 'MARKET') {
      form.nettingCurrency = payload.nettingCurrency || 'CNY'
      form.nettingAccount = payload.nettingAccount || ''
    }
    // 加载客户账户
    await loadAllAccounts()
    if (taskResolvedMode.value === 'MARKET') {
      await loadNettingAccounts()
    }
  } catch (e) {
    ElMessage.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

// 账户下拉标签
function accountLabel(account) {
  return `${account.accountNo} | ${account.currency} | 余额 ${account.balance ?? 0}`
}

// 提交操作
async function handleSubmit() {
  // 校验
  if (!form.farLegAmount || form.farLegAmount <= 0) {
    ElMessage.warning('远端金额必须大于0')
    return
  }
  if (!form.newMaturityDate) {
    ElMessage.warning('请选择远端到期日')
    return
  }
  if (newMaturityDateInvalid.value) {
    ElMessage.warning('远端到期日必须晚于原到期日')
    return
  }
  if (!form.farLegCurrency1Account) {
    ElMessage.warning('请选择远端币种1账户')
    return
  }
  if (!form.farLegCurrency2Account) {
    ElMessage.warning('请选择远端币种2账户')
    return
  }
  if (isMarketMode.value) {
    if (!form.nettingAccount) {
      ElMessage.warning('请选择轧差账户')
      return
    }
    if (nettingBalanceInsufficient.value) {
      ElMessage.warning('轧差账户余额不足，无法提交')
      return
    }
  }

  submitting.value = true
  try {
    // 编辑模式：先完成修改任务，再重新发起
    if (modifyTaskId.value) {
      await completeModifyTask(modifyTaskId.value)
    }
    if (isMarketMode.value) {
      const payload = {
        tradeId: originalTrade.value.tradeId,
        newMaturityDate: form.newMaturityDate,
        nearLegCostRate: form.nearLegCostRate,
        nearLegCustomerRate: form.nearLegCustomerRate,
        farLegCostRate: form.farLegCostRate,
        farLegCustomerRate: form.farLegCustomerRate,
        farLegAmount: form.farLegAmount,
        farLegBranchProfitPoint: form.farLegBranchProfitPoint,
        farLegCurrency1Account: form.farLegCurrency1Account,
        farLegCurrency2Account: form.farLegCurrency2Account,
        nettingCurrency: form.nettingCurrency,
        nettingAccount: form.nettingAccount,
        nettingAmount: nettingAmount.value,
        customerPnl: customerPnl.value,
        remark: form.remark
      }
      await rolloverMarket(payload)
      ElMessage.success(modifyTaskId.value ? '重新提交成功' : '市价展期操作成功')
    } else {
      const payload = {
        tradeId: originalTrade.value.tradeId,
        newMaturityDate: form.newMaturityDate,
        nearLegCostRate: form.nearLegCostRate,
        nearLegCustomerRate: form.nearLegCustomerRate,
        farLegCostRate: form.farLegCostRate,
        farLegCustomerRate: form.farLegCustomerRate,
        farLegAmount: form.farLegAmount,
        farLegBranchProfitPoint: form.farLegBranchProfitPoint,
        farLegCurrency1Account: form.farLegCurrency1Account,
        farLegCurrency2Account: form.farLegCurrency2Account,
        remark: form.remark
      }
      await rolloverOriginal(payload)
      ElMessage.success(modifyTaskId.value ? '重新提交成功' : '原价展期操作成功')
    }
    router.push(modifyTaskId.value ? '/fx/todo' : '/fx/unmatured')
  } catch (e) {
    // 错误由拦截器处理
  } finally {
    submitting.value = false
  }
}

// 返回
function handleBack() {
  router.back()
}

// 监听远端客户汇率变化，重新计算远端分行收益点
watch(() => form.farLegCustomerRate, () => {
  updateFarLegProfitPoint()
})

// 监听远端成本汇率变化，重新计算远端分行收益点
watch(() => form.farLegCostRate, () => {
  updateFarLegProfitPoint()
})

// 监听轧差货币变化，重新加载轧差账户
watch(() => form.nettingCurrency, () => {
  form.nettingAccount = ''
  if (isMarketMode.value) {
    loadNettingAccounts()
  }
})

onMounted(() => {
  modifyTaskId.value = route.query.taskId || null
  if (modifyTaskId.value) {
    // 编辑模式：加载任务 payload 并回填表单
    loadTaskAndOriginalTrade(modifyTaskId.value)
  } else {
    loadOriginalTrade()
  }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span class="page-title">{{ isMarketMode ? '市价展期' : '原价展期' }}</span>
          <el-button size="small" @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-skeleton :loading="loading" animated>
        <!-- 原交易信息 -->
        <el-divider content-position="left">原交易信息</el-divider>
        <el-descriptions :column="2" border size="default">
          <el-descriptions-item label="业务编号" :label-style="{ width: '120px' }">{{ originalTrade?.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="特殊交易类型" :label-style="{ width: '120px' }">{{ formatSpecialTradeType(originalTrade?.specialTradeType) }}</el-descriptions-item>
          <el-descriptions-item label="客户号" :label-style="{ width: '120px' }">{{ originalTrade?.customerId }}</el-descriptions-item>
          <el-descriptions-item label="客户名称" :label-style="{ width: '120px' }">{{ originalTrade?.customerName }}</el-descriptions-item>
          <el-descriptions-item label="货币对" :label-style="{ width: '120px' }">{{ originalTrade?.currencyPair }}</el-descriptions-item>
          <el-descriptions-item label="交易机构" :label-style="{ width: '120px' }">{{ originalTrade?.branchName }}</el-descriptions-item>
          <el-descriptions-item label="原到期日" :label-style="{ width: '120px' }">{{ originalTrade?.maturityDate }}</el-descriptions-item>
        </el-descriptions>

        <!-- 近端交易信息 -->
        <el-divider content-position="left">近端交易信息</el-divider>
        <el-form :model="form" label-width="120px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成本汇率">
                <el-input-number
                  :model-value="form.nearLegCostRate"
                  :precision="4"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客户汇率">
                <el-input-number
                  :model-value="form.nearLegCustomerRate"
                  :precision="4"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="买卖方向">
                <el-input :model-value="formatTradeDirection(nearLegDirection)" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="金额">
                <el-input-number
                  :model-value="originalAmount"
                  :precision="2"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="交易日">
                <el-input :model-value="today" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="起息日">
                <el-input :model-value="originalMaturityDate" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="近端交割方式">
                <el-input
                  :model-value="nearLegSettlementMethod === 'NONE' ? '无需交割' : nearLegSettlementMethod === 'NET' ? '差额交割' : '全额交割'"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <!-- 市价展期：客户损益 -->
            <el-col :span="12" v-if="isMarketMode">
              <el-form-item label="客户损益">
                <el-input-number
                  :model-value="customerPnl"
                  :precision="2"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 市价展期：轧差信息（合并至近端交易信息） -->
          <template v-if="isMarketMode">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="轧差货币">
                  <el-select v-model="form.nettingCurrency" style="width: 100%">
                    <el-option label="CNY" value="CNY" />
                    <el-option label="USD" value="USD" />
                    <el-option label="EUR" value="EUR" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="轧差账户">
                  <el-select v-model="form.nettingAccount" placeholder="请选择轧差账户" style="width: 100%" clearable>
                    <el-option
                      v-for="account in nettingAccounts"
                      :key="account.accountNo"
                      :label="`${account.accountNo} (${account.accountType})`"
                      :value="account.accountNo"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="轧差金额">
                  <el-input-number
                    :model-value="nettingAmount"
                    :precision="2"
                    :controls="false"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="轧差账户余额">
                  <el-input
                    :model-value="nettingAccountBalance !== null ? nettingAccountBalance : '-'"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-alert
              v-if="nettingAmount && !nettingBalanceInsufficient"
              title="提交后市价展期近端将按差额交割方式结算轧差金额"
              type="info"
              :closable="false"
              show-icon
              style="margin-top: 8px"
            />
            <el-alert
              v-if="nettingBalanceInsufficient"
              title="轧差账户余额不足，无法提交"
              type="error"
              :closable="false"
              show-icon
              style="margin-top: 8px"
            />
          </template>
        </el-form>

        <!-- 远端交易信息 -->
        <el-divider content-position="left">远端交易信息</el-divider>
        <el-form :model="form" label-width="120px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成本汇率">
                <el-input-number
                  v-model="form.farLegCostRate"
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
              <el-form-item label="客户汇率">
                <el-input-number
                  v-model="form.farLegCustomerRate"
                  :precision="4"
                  :step="0.0001"
                  :min="0"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="买卖方向">
                <el-input :model-value="formatTradeDirection(farLegDirection)" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="金额">
                <el-input-number
                  v-model="form.farLegAmount"
                  :precision="2"
                  :step="100"
                  :min="0"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="分行收益点">
                <el-input-number
                  :model-value="form.farLegBranchProfitPoint"
                  :precision="2"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="远端交割方式">
                <el-input :model-value="'全额交割'" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="远端到期日">
                <el-date-picker
                  v-model="form.newMaturityDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择远端到期日"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert
            v-if="newMaturityDateInvalid"
            title="远端到期日必须晚于原到期日"
            type="error"
            :closable="false"
            show-icon
            style="margin-top: 8px"
          />
          <el-row :gutter="20" style="margin-top: 12px">
            <el-col :span="12">
              <el-form-item label="币种1账户">
                <el-select
                  v-model="form.farLegCurrency1Account"
                  placeholder="请选择币种1账户"
                  style="width: 100%"
                >
                  <el-option
                    v-for="acc in currency1AccountOptions"
                    :key="acc.accountNo"
                    :label="accountLabel(acc)"
                    :value="acc.accountNo"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="币种2账户">
                <el-select
                  v-model="form.farLegCurrency2Account"
                  placeholder="请选择币种2账户"
                  style="width: 100%"
                >
                  <el-option
                    v-for="acc in currency2AccountOptions"
                    :key="acc.accountNo"
                    :label="accountLabel(acc)"
                    :value="acc.accountNo"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 备注 -->
        <el-divider content-position="left">备注信息</el-divider>
        <el-form label-width="120px">
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
          </el-form-item>
        </el-form>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-button @click="handleBack">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="nettingBalanceInsufficient || newMaturityDateInvalid"
            @click="handleSubmit"
          >
            提交
          </el-button>
        </div>
      </el-skeleton>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
  height: 100%;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.action-bar {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
