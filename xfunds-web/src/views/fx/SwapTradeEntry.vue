<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { createSwapTrade, updateAndResubmitSwapTrade, getTradeDetail } from '@/api/trade'
import { completeModifyTask } from '@/api/task'
import { getCustomerAccounts, getCustomerMarginAccounts } from '@/api/customer'
import { getSwapQuotes } from '@/api/quote'
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
// 掉期牌价列表
const swapQuoteList = ref([])

// 基础货币、报价货币（来自牌价跳转参数或用户选择）
const baseCurrency = ref(route.query.baseCurrency || '')
const quoteCurrency = ref(route.query.quoteCurrency || '')

// 期限（来自牌价跳转参数或用户选择：ON/TN/SN/SW/1M）
const term = ref(route.query.term || '')

// 牌价参数（用于计算成本汇率、客户汇率）
const totalBuyRate = ref(Number(route.query.totalBuyRate) || null)
const totalSellRate = ref(Number(route.query.totalSellRate) || null)
const branchCustomerBuyRate = ref(null)
const branchCustomerSellRate = ref(null)

// 是否从URL参数进入（有货币对参数）
const hasQueryParams = computed(() => !!route.query.baseCurrency && !!route.query.quoteCurrency)
// 选中的牌价组合键（货币对|期限，用于下拉选择）
const selectedQuoteKey = ref('')
// 交易币对展示（只读）
const currencyPair = computed(() => `${baseCurrency.value}/${quoteCurrency.value}`)

// 去重后的牌价列表（按货币对去重，每个货币对只保留第一个期限的牌价）
// 用于交易币种下拉选择，下拉只展示币种不带期限
const uniqueQuoteList = computed(() => {
  const seen = new Set()
  return swapQuoteList.value.filter(q => {
    if (seen.has(q.currencyPair)) return false
    seen.add(q.currencyPair)
    return true
  })
})

// 加载掉期牌价列表
async function loadSwapQuotes() {
  quoteLoading.value = true
  try {
    const res = await getSwapQuotes()
    swapQuoteList.value = res.data || []

    // 如果有URL参数，找到对应的牌价并更新数据（按货币对+期限匹配）
    if (hasQueryParams.value && swapQuoteList.value.length > 0) {
      const quote = swapQuoteList.value.find(
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
    if (!hasQueryParams.value && swapQuoteList.value.length > 0) {
      const firstQuote = swapQuoteList.value[0]
      selectedQuoteKey.value = getQuoteKey(firstQuote)
      handleQuoteChange(selectedQuoteKey.value)
    }
  } catch (e) {
    swapQuoteList.value = []
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
      const swapDetail = detail.swapDetail

      // 填充表单数据
      baseCurrency.value = master.baseCurrency || ''
      quoteCurrency.value = master.quoteCurrency || ''
      term.value = swapDetail?.term || ''
      totalBuyRate.value = null
      totalSellRate.value = null
      branchCustomerBuyRate.value = null
      branchCustomerSellRate.value = null

      form.customerId = master.customerId || ''
      form.customerName = master.customerName || ''
      form.swapType = swapDetail?.swapType || 'S_B'
      form.term = swapDetail?.term || ''
      form.tradeDate = master.tradeDate || getTodayStr()
      form.settlementMethod = 'FULL'
      form.marginAccountId = swapDetail?.marginAccountId || ''
      form.marginAmount = swapDetail?.marginAmount || null
      form.purposeCode = master.purposeCode || ''

      // 近端
      form.nearLegDirection = swapDetail?.nearLegDirection || 'BUY'
      form.nearLegAmount = swapDetail?.nearLegAmount || null
      form.nearLegValueDate = swapDetail?.nearLegValueDate || ''
      form.nearLegSettlementMethod = 'FULL'
      form.nearLegCostRate = swapDetail?.nearLegCostRate || null
      form.nearLegCustomerRate = swapDetail?.nearLegCustomerRate || null
      form.nearLegCurrency1Account = swapDetail?.nearLegCurrency1Account || ''
      form.nearLegCurrency2Account = swapDetail?.nearLegCurrency2Account || ''
      form.nearLegBranchProfitPoint = swapDetail?.nearLegBranchProfitPoint || null

      // 远端
      form.farLegDirection = swapDetail?.farLegDirection || 'SELL'
      form.farLegAmount = swapDetail?.farLegAmount || null
      form.farLegValueDate = swapDetail?.farLegValueDate || ''
      form.farLegSettlementMethod = 'FULL'
      form.farLegCostRate = swapDetail?.farLegCostRate || null
      form.farLegCustomerRate = swapDetail?.farLegCustomerRate || null
      form.farLegCurrency1Account = swapDetail?.farLegCurrency1Account || ''
      form.farLegCurrency2Account = swapDetail?.farLegCurrency2Account || ''
      form.farLegBranchProfitPoint = swapDetail?.farLegBranchProfitPoint || null

      // 兼容旧字段
      form.swapPoint = swapDetail?.swapPoint || null
      form.nearSpotRate = swapDetail?.nearSpotRate || null
      form.customerRate = master.customerRate || null
      form.costRate = master.costRate || null
      form.branchProfitPoint = master.branchProfitPoint || null

      // 加载客户账户
      if (master.customerId) {
        loadAccounts(master.customerId)
        loadMarginAccounts(master.customerId)
      }

      // 设置选中的牌价
      if (swapQuoteList.value.length > 0) {
        const quote = swapQuoteList.value.find(
          q => q.baseCurrency === baseCurrency.value &&
               q.quoteCurrency === quoteCurrency.value &&
               (form.term ? q.term === form.term : true)
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

// 更新牌价数据：设置基础/报价货币、期限、各种汇率
function updateQuoteData(quote) {
  baseCurrency.value = quote.baseCurrency
  quoteCurrency.value = quote.quoteCurrency
  term.value = quote.term || ''
  form.term = term.value
  totalBuyRate.value = Number(quote.totalBuyRate) || null
  totalSellRate.value = Number(quote.totalSellRate) || null
  branchCustomerBuyRate.value = Number(quote.branchCustomerBuyRate) || null
  branchCustomerSellRate.value = Number(quote.branchCustomerSellRate) || null

  // 根据期限自动计算近端/远端日期（不可修改）
  calculateLegDates()

  // 根据方向自动填充成本汇率、客户汇率
  fillLegRates()
}

// 生成牌价组合键（货币对|期限）
function getQuoteKey(quote) {
  return `${quote.currencyPair}|${quote.term}`
}

// 牌价选择变化（按货币对+期限）
function handleQuoteChange(val) {
  const quote = swapQuoteList.value.find(q => getQuoteKey(q) === val)
  if (quote) {
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

// 根据期限自动计算近端起息日和远端到期日
// ON: near=T+0, far=T+1
// TN: near=T+1, far=T+2
// SN: near=T+2, far=T+3
// SW: near=T+2, far=T+2+7天
// 1M: near=T+2, far=T+2+1月
function calculateLegDates() {
  const baseDate = form.tradeDate || getTodayStr()
  switch (term.value) {
    case 'ON':
      form.nearLegValueDate = addDays(baseDate, 0)
      form.farLegValueDate = addDays(baseDate, 1)
      break
    case 'TN':
      form.nearLegValueDate = addDays(baseDate, 1)
      form.farLegValueDate = addDays(baseDate, 2)
      break
    case 'SN':
      form.nearLegValueDate = addDays(baseDate, 2)
      form.farLegValueDate = addDays(baseDate, 3)
      break
    case 'SW':
      form.nearLegValueDate = addDays(baseDate, 2)
      form.farLegValueDate = addDays(form.nearLegValueDate, 7)
      break
    case '1M':
      form.nearLegValueDate = addDays(baseDate, 2)
      form.farLegValueDate = addMonths(form.nearLegValueDate, 1)
      break
    default:
      break
  }
}

// 掉期交易表单数据
const form = reactive({
  customerId: '',
  customerName: '',
  swapType: route.query.swapType || 'S_B',
  term: route.query.term || '',
  tradeDate: getTodayStr(),
  settlementMethod: 'FULL',
  marginAccountId: '',
  marginAmount: null,
  purposeCode: '',
  // 近端
  // S/B(分行近卖远买)→客户近买远卖→nearLegDirection=BUY, farLegDirection=SELL
  // B/S(分行近买远卖)→客户近卖远买→nearLegDirection=SELL, farLegDirection=BUY
  nearLegDirection: (route.query.swapType || 'S_B') === 'S_B' ? 'BUY' : 'SELL',
  nearLegAmount: null,
  nearLegValueDate: '',
  nearLegSettlementMethod: 'FULL',
  nearLegCostRate: null,
  nearLegCustomerRate: null,
  nearLegCurrency1Account: '',
  nearLegCurrency2Account: '',
  nearLegBranchProfitPoint: null,
  // 远端
  farLegDirection: (route.query.swapType || 'S_B') === 'S_B' ? 'SELL' : 'BUY',
  farLegAmount: null,
  farLegValueDate: '',
  farLegSettlementMethod: 'FULL',
  farLegCostRate: null,
  farLegCustomerRate: null,
  farLegCurrency1Account: '',
  farLegCurrency2Account: '',
  farLegBranchProfitPoint: null,
  // 兼容旧字段（提交时自动填充）
  swapPoint: Number(route.query.swapPoint) || null,
  nearSpotRate: null,
  customerRate: null,
  costRate: null,
  branchProfitPoint: null
})

// 表单校验规则
const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  swapType: [{ required: true, message: '请选择掉期类型', trigger: 'change' }],
  nearLegAmount: [
    { required: true, message: '请输入近端金额', trigger: 'blur' },
    { type: 'number', min: 0.000001, message: '近端金额必须大于0', trigger: 'blur' }
  ],
  nearLegCustomerRate: [
    { required: true, message: '请输入近端客户汇率', trigger: 'blur' },
    { type: 'number', min: 0.00000001, message: '近端客户汇率必须大于0', trigger: 'blur' }
  ],
  nearLegCurrency1Account: [{ required: true, message: '请选择近端币种1账户', trigger: 'change' }],
  nearLegCurrency2Account: [{ required: true, message: '请选择近端币种2账户', trigger: 'change' }],
  nearLegValueDate: [{ required: true, message: '近端起息日不能为空', trigger: 'change' }],
  farLegAmount: [
    { required: true, message: '请输入远端金额', trigger: 'blur' },
    { type: 'number', min: 0.000001, message: '远端金额必须大于0', trigger: 'blur' }
  ],
  farLegCustomerRate: [
    { required: true, message: '请输入远端客户汇率', trigger: 'blur' },
    { type: 'number', min: 0.00000001, message: '远端客户汇率必须大于0', trigger: 'blur' }
  ],
  farLegCurrency1Account: [{ required: true, message: '请选择远端币种1账户', trigger: 'change' }],
  farLegCurrency2Account: [{ required: true, message: '请选择远端币种2账户', trigger: 'change' }],
  farLegValueDate: [{ required: true, message: '远端到期日不能为空', trigger: 'change' }]
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

// 掉期类型变化时自动设置近端/远端方向
// S/B(分行近卖远买)→客户近买远卖→nearLegDirection=BUY, farLegDirection=SELL
// B/S(分行近买远卖)→客户近卖远买→nearLegDirection=SELL, farLegDirection=BUY
function handleSwapTypeChange(val) {
  if (val === 'S_B') {
    form.nearLegDirection = 'BUY'
    form.farLegDirection = 'SELL'
  } else {
    form.nearLegDirection = 'SELL'
    form.farLegDirection = 'BUY'
  }
  // 切换方向时，重新从牌价获取汇率
  fillLegRates()
}

// 根据近端/远端方向自动填充成本汇率和客户汇率
// 客户买基准货币(BUY): 成本汇率=总/分S/B(totalSellRate), 客户汇率=分/客S/B(branchCustomerSellRate)
// 客户卖基准货币(SELL): 成本汇率=总/分B/S(totalBuyRate), 客户汇率=分/客B/S(branchCustomerBuyRate)
function fillLegRates() {
  // 近端
  if (form.nearLegDirection === 'BUY') {
    form.nearLegCostRate = totalSellRate.value
    form.nearLegCustomerRate = branchCustomerSellRate.value
  } else {
    form.nearLegCostRate = totalBuyRate.value
    form.nearLegCustomerRate = branchCustomerBuyRate.value
  }
  // 远端
  if (form.farLegDirection === 'BUY') {
    form.farLegCostRate = totalSellRate.value
    form.farLegCustomerRate = branchCustomerSellRate.value
  } else {
    form.farLegCostRate = totalBuyRate.value
    form.farLegCustomerRate = branchCustomerBuyRate.value
  }
  // 计算分行收益点
  calculateBranchProfitPoint()
}

// 计算近端/远端分行收益点
// 客户买入(BUY): 分行收益点 = (客户汇率 - 成本汇率) * 1000
// 客户卖出(SELL): 分行收益点 = (成本汇率 - 客户汇率) * 1000
function calculateBranchProfitPoint() {
  // 近端
  if (form.nearLegCustomerRate != null && form.nearLegCostRate != null) {
    if (form.nearLegDirection === 'BUY') {
      form.nearLegBranchProfitPoint = (form.nearLegCustomerRate - form.nearLegCostRate) * 1000
    } else {
      form.nearLegBranchProfitPoint = (form.nearLegCostRate - form.nearLegCustomerRate) * 1000
    }
  } else {
    form.nearLegBranchProfitPoint = null
  }
  // 远端
  if (form.farLegCustomerRate != null && form.farLegCostRate != null) {
    if (form.farLegDirection === 'BUY') {
      form.farLegBranchProfitPoint = (form.farLegCustomerRate - form.farLegCostRate) * 1000
    } else {
      form.farLegBranchProfitPoint = (form.farLegCostRate - form.farLegCustomerRate) * 1000
    }
  } else {
    form.farLegBranchProfitPoint = null
  }
}

// 打开客户搜索弹窗
function openCustomerDialog() {
  customerDialogVisible.value = true
}

// 客户选中回调：回填客户号与名称，并加载该客户账户列表与保证金账户列表
function handleCustomerSelect(customer) {
  form.customerId = customer.customerId
  form.customerName = customer.customerName
  form.nearLegCurrency1Account = ''
  form.nearLegCurrency2Account = ''
  form.farLegCurrency1Account = ''
  form.farLegCurrency2Account = ''
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

// 提交掉期交易：校验表单 -> 校验保证金余额 -> 组装数据 -> 调用接口 -> 成功后返回
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
      // 兼容旧字段：nearLegRate/farLegRate 取客户汇率值
      const payload = {
        customerId: form.customerId,
        branchCode: userStore.userInfo.orgCode || '',
        baseCurrency: baseCurrency.value,
        quoteCurrency: quoteCurrency.value,
        swapType: form.swapType,
        term: form.term,
        tradeDate: form.tradeDate,
        settlementMethod: form.settlementMethod,
        purposeCode: form.purposeCode || null,
        marginAccountId: form.marginAccountId || null,
        marginAmount: form.marginAmount,
        // 近端
        nearLegDirection: form.nearLegDirection,
        nearLegAmount: form.nearLegAmount,
        nearLegRate: form.nearLegCustomerRate,
        nearLegCostRate: form.nearLegCostRate,
        nearLegCustomerRate: form.nearLegCustomerRate,
        nearLegBranchProfitPoint: form.nearLegBranchProfitPoint,
        nearLegValueDate: form.nearLegValueDate,
        nearLegAccount: form.nearLegCurrency1Account,
        nearLegCurrency1Account: form.nearLegCurrency1Account,
        nearLegCurrency2Account: form.nearLegCurrency2Account,
        nearLegSettlementMethod: form.nearLegSettlementMethod,
        // 远端
        farLegDirection: form.farLegDirection,
        farLegAmount: form.farLegAmount,
        farLegRate: form.farLegCustomerRate,
        farLegCostRate: form.farLegCostRate,
        farLegCustomerRate: form.farLegCustomerRate,
        farLegBranchProfitPoint: form.farLegBranchProfitPoint,
        farLegValueDate: form.farLegValueDate,
        farLegAccount: form.farLegCurrency1Account,
        farLegCurrency1Account: form.farLegCurrency1Account,
        farLegCurrency2Account: form.farLegCurrency2Account,
        farLegSettlementMethod: form.farLegSettlementMethod,
        // 兼容旧字段
        swapPoint: form.swapPoint,
        nearSpotRate: form.nearSpotRate,
        customerRate: form.nearLegCustomerRate,
        costRate: form.nearLegCostRate,
        branchProfitPoint: form.nearLegBranchProfitPoint
      }

      if (isEditMode.value) {
        await updateAndResubmitSwapTrade(editTradeId.value, payload)
        if (editTaskId.value) {
          await completeModifyTask(editTaskId.value)
        }
        ElMessage.success('掉期交易编辑成功')
      } else {
        await createSwapTrade(payload)
        ElMessage.success('掉期交易录入成功')
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
  // 加载掉期牌价列表（会自动计算日期和汇率）
  await loadSwapQuotes()
  // 如果是编辑模式，加载交易详情
  if (isEditMode.value) {
    await loadTradeDetail()
  }
})

// 监听近端客户汇率变化，重新计算近端分行收益点
watch(() => form.nearLegCustomerRate, () => {
  calculateBranchProfitPoint()
})

// 监听远端客户汇率变化，重新计算远端分行收益点
watch(() => form.farLegCustomerRate, () => {
  calculateBranchProfitPoint()
})

// 监听近端金额变化，自动计算保证金金额 = 近端金额 × 10%（不可人工修改）
// 同时同步远端金额 = 近端金额（远端金额不可单独修改）
watch(() => form.nearLegAmount, (val) => {
  if (val != null && val > 0) {
    form.marginAmount = Math.round(val * MARGIN_RATE * 100) / 100
  } else {
    form.marginAmount = null
  }
  // 远端金额始终与近端保持一致
  form.farLegAmount = val
})

// 监听交易日变化，重新计算近端/远端日期
watch(() => form.tradeDate, () => {
  calculateLegDates()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">{{ isEditMode ? '编辑掉期交易' : '掉期交易录入' }}</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        class="trade-form"
      >
        <!-- 公共信息区 -->
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
                  v-for="quote in uniqueQuoteList"
                  :key="quote.quoteId"
                  :label="quote.currencyPair"
                  :value="getQuoteKey(quote)"
                />
              </el-select>
              <el-input v-else :model-value="currencyPair" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="掉期类型" prop="swapType">
              <el-radio-group v-model="form.swapType" @change="handleSwapTypeChange">
                <el-radio value="S_B">S/B 近卖远买</el-radio>
                <el-radio value="B_S">B/S 近买远卖</el-radio>
              </el-radio-group>
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
                placeholder="自动计算（近端金额×10%）"
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
        </el-row>

        <!-- 近端 Leg 区 -->
        <el-card shadow="never" class="leg-card">
          <template #header>
            <span class="leg-title">近端交易信息（Near Leg）</span>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="近端方向">
                <el-input :model-value="form.nearLegDirection === 'BUY' ? '买入(BUY)' : '卖出(SELL)'" readonly />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="近端金额" prop="nearLegAmount">
                <el-input-number
                  v-model="form.nearLegAmount"
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
              <el-form-item label="交易日">
                <el-date-picker
                  v-model="form.tradeDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="交易日"
                  style="width: 100%"
                  disabled
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="起息日" prop="nearLegValueDate">
                <el-date-picker
                  v-model="form.nearLegValueDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="起息日"
                  style="width: 100%"
                  disabled
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="近端交割方式">
                <el-input :model-value="form.nearLegSettlementMethod === 'FULL' ? '全额交割' : '差额交割'" readonly />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="近端分行收益点">
                <el-input-number
                  v-model="form.nearLegBranchProfitPoint"
                  :precision="2"
                  :step="0.01"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成本汇率">
                <el-input-number
                  v-model="form.nearLegCostRate"
                  :precision="4"
                  :step="0.0001"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客户汇率" prop="nearLegCustomerRate">
                <el-input-number
                  v-model="form.nearLegCustomerRate"
                  :precision="4"
                  :step="0.0001"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="币种1账户" prop="nearLegCurrency1Account">
                <el-select
                  v-model="form.nearLegCurrency1Account"
                  v-loading="accountLoading"
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
              <el-form-item label="币种2账户" prop="nearLegCurrency2Account">
                <el-select
                  v-model="form.nearLegCurrency2Account"
                  v-loading="accountLoading"
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
        </el-card>

        <!-- 远端 Leg 区 -->
        <el-card shadow="never" class="leg-card">
          <template #header>
            <span class="leg-title">远端交易信息（Far Leg）</span>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="远端方向">
                <el-input :model-value="form.farLegDirection === 'BUY' ? '买入(BUY)' : '卖出(SELL)'" readonly />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="远端金额" prop="farLegAmount">
                <el-input-number
                  v-model="form.farLegAmount"
                  :precision="2"
                  :step="100"
                  :min="0"
                  :controls="false"
                  disabled
                  placeholder="与近端金额一致"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="远端到期日" prop="farLegValueDate">
                <el-date-picker
                  v-model="form.farLegValueDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="远端到期日"
                  style="width: 100%"
                  disabled
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="期限">
                <el-input :model-value="form.term" readonly placeholder="选择币种后自动填充" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="远端交割方式">
                <el-input :model-value="form.farLegSettlementMethod === 'FULL' ? '全额交割' : '差额交割'" readonly />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="远端分行收益点">
                <el-input-number
                  v-model="form.farLegBranchProfitPoint"
                  :precision="2"
                  :step="0.01"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成本汇率">
                <el-input-number
                  v-model="form.farLegCostRate"
                  :precision="4"
                  :step="0.0001"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客户汇率" prop="farLegCustomerRate">
                <el-input-number
                  v-model="form.farLegCustomerRate"
                  :precision="4"
                  :step="0.0001"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="币种1账户" prop="farLegCurrency1Account">
                <el-select
                  v-model="form.farLegCurrency1Account"
                  v-loading="accountLoading"
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
              <el-form-item label="币种2账户" prop="farLegCurrency2Account">
                <el-select
                  v-model="form.farLegCurrency2Account"
                  v-loading="accountLoading"
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
        </el-card>

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
.leg-card {
  margin-bottom: 16px;
}
.leg-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a2a6c;
}
</style>
