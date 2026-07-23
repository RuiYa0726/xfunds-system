<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTradeDetail, fullDefault } from '@/api/trade'
import { getSwapQuotes } from '@/api/quote'
import { getCustomerAccounts, getCustomerMarginAccounts } from '@/api/customer'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// 加载状态
const loading = ref(false)
const submitting = ref(false)
// 账户加载中状态
const accountLoading = ref(false)
// 牌价加载中状态
const quoteLoading = ref(false)

// 客户全部账户列表
const allAccounts = ref([])
// 客户保证金账户列表
const marginAccounts = ref([])
// 掉期牌价列表
const swapQuoteList = ref([])

// 基础货币、报价货币（从原交易加载）
const baseCurrency = ref('')
const quoteCurrency = ref('')
// 期限（从原交易加载）
const term = ref('')

// 实时牌价参数（用于计算成本汇率、客户汇率）
const totalBuyRate = ref(null)
const totalSellRate = ref(null)
const branchCustomerBuyRate = ref(null)
const branchCustomerSellRate = ref(null)

// 交易币对展示（只读）
const currencyPair = computed(() => `${baseCurrency.value}/${quoteCurrency.value}`)

// 原交易ID（从路由参数获取）
const originalTradeId = computed(() => route.query.tradeId || '')

// 抵消交易表单数据（所有字段只读展示）
const form = reactive({
  customerId: '',
  customerName: '',
  swapType: '',
  term: '',
  tradeDate: '',
  settlementMethod: 'FULL',
  marginAccountId: '',
  marginAmount: null,
  purposeCode: '',
  fxPurposeCode: '',
  // 近端
  nearLegDirection: '',
  nearLegAmount: null,
  nearLegValueDate: '',
  nearLegSettlementMethod: 'FULL',
  nearLegCostRate: null,
  nearLegCustomerRate: null,
  nearLegCurrency1Account: '',
  nearLegCurrency2Account: '',
  nearLegBranchProfitPoint: null,
  // 远端
  farLegDirection: '',
  farLegAmount: null,
  farLegValueDate: '',
  farLegSettlementMethod: 'FULL',
  farLegCostRate: null,
  farLegCustomerRate: null,
  farLegCurrency1Account: '',
  farLegCurrency2Account: '',
  farLegBranchProfitPoint: null,
  // 兼容旧字段
  swapPoint: null,
  nearSpotRate: null,
  customerRate: null,
  costRate: null,
  branchProfitPoint: null,
  // 违约操作字段（可输入）
  penaltyAmount: null,
  penaltyAccount: '',
  remark: ''
})

// 获取今日日期字符串 YYYY-MM-DD
function getTodayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 加载掉期牌价列表
async function loadSwapQuotes() {
  quoteLoading.value = true
  try {
    const res = await getSwapQuotes()
    swapQuoteList.value = res.data || []
  } catch (e) {
    swapQuoteList.value = []
  } finally {
    quoteLoading.value = false
  }
}

// 加载客户账户列表
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

// 根据新方向和实时牌价计算成本汇率和客户汇率
// BUY方向: costRate=totalSellRate, customerRate=branchCustomerSellRate
// SELL方向: costRate=totalBuyRate, customerRate=branchCustomerBuyRate
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

// 加载原交易详情并计算抵消交易
async function loadOriginalTrade() {
  if (!originalTradeId.value) {
    ElMessage.error('交易ID不能为空')
    router.back()
    return
  }
  loading.value = true
  try {
    const res = await getTradeDetail(originalTradeId.value)
    const detail = res.data
    if (!detail || !detail.master) {
      ElMessage.error('加载交易详情失败')
      return
    }
    const master = detail.master
    const swapDetail = detail.swapDetail

    // 设置基础货币、报价货币、期限
    baseCurrency.value = master.baseCurrency || ''
    quoteCurrency.value = master.quoteCurrency || ''
    term.value = swapDetail?.term || ''

    // 填充公共信息（来自原交易）
    form.customerId = master.customerId || ''
    form.customerName = master.customerName || ''
    form.term = swapDetail?.term || ''
    form.tradeDate = master.tradeDate || getTodayStr()
    form.settlementMethod = master.settlementMethod || 'FULL'
    form.marginAccountId = swapDetail?.marginAccountId || ''
    form.marginAmount = swapDetail?.marginAmount || null
    form.purposeCode = master.purposeCode || ''
    form.fxPurposeCode = master.fxPurposeCode || ''

    // 计算抵消交易方向：原 S_B → 新 B_S，原 B_S → 新 S_B
    // 原 S_B: nearLegDirection=BUY, farLegDirection=SELL → 新 B_S: nearLegDirection=SELL, farLegDirection=BUY
    // 原 B_S: nearLegDirection=SELL, farLegDirection=BUY → 新 S_B: nearLegDirection=BUY, farLegDirection=SELL
    const originalSwapType = swapDetail?.swapType || 'S_B'
    if (originalSwapType === 'S_B') {
      form.swapType = 'B_S'
      form.nearLegDirection = 'SELL'
      form.farLegDirection = 'BUY'
    } else {
      form.swapType = 'S_B'
      form.nearLegDirection = 'BUY'
      form.farLegDirection = 'SELL'
    }

    // 近端/远端金额、日期、账户、交割方式沿用原交易
    form.nearLegAmount = swapDetail?.nearLegAmount || null
    form.nearLegValueDate = swapDetail?.nearLegValueDate || ''
    form.nearLegSettlementMethod = swapDetail?.nearLegSettlementMethod || 'FULL'
    form.nearLegCurrency1Account = swapDetail?.nearLegCurrency1Account || ''
    form.nearLegCurrency2Account = swapDetail?.nearLegCurrency2Account || ''

    form.farLegAmount = swapDetail?.farLegAmount || null
    form.farLegValueDate = swapDetail?.farLegValueDate || ''
    form.farLegSettlementMethod = swapDetail?.farLegSettlementMethod || 'FULL'
    form.farLegCurrency1Account = swapDetail?.farLegCurrency1Account || ''
    form.farLegCurrency2Account = swapDetail?.farLegCurrency2Account || ''

    // 兼容旧字段
    form.swapPoint = swapDetail?.swapPoint || null
    form.nearSpotRate = swapDetail?.nearSpotRate || null

    // 根据原交易的货币对和期限，在牌价列表中找到匹配的实时牌价
    if (swapQuoteList.value.length > 0) {
      const quote = swapQuoteList.value.find(
        q => q.baseCurrency === baseCurrency.value &&
             q.quoteCurrency === quoteCurrency.value &&
             (form.term ? q.term === form.term : true)
      )
      if (quote) {
        totalBuyRate.value = Number(quote.totalBuyRate) || null
        totalSellRate.value = Number(quote.totalSellRate) || null
        branchCustomerBuyRate.value = Number(quote.branchCustomerBuyRate) || null
        branchCustomerSellRate.value = Number(quote.branchCustomerSellRate) || null
      }
    }

    // 根据新方向和实时牌价计算成本汇率、客户汇率、分行收益点
    fillLegRates()

    // 兼容旧字段：customerRate/costRate/branchProfitPoint 取近端值
    form.customerRate = form.nearLegCustomerRate
    form.costRate = form.nearLegCostRate
    form.branchProfitPoint = form.nearLegBranchProfitPoint

    // 加载客户账户列表用于显示账户号
    if (master.customerId) {
      loadAccounts(master.customerId)
      loadMarginAccounts(master.customerId)
    }
  } catch (e) {
    ElMessage.error('加载交易详情失败')
  } finally {
    loading.value = false
  }
}

// 提交全部违约
async function handleSubmit() {
  // 校验违约金账户
  if (!form.penaltyAccount) {
    ElMessage.warning('请输入违约金账户')
    return
  }
  submitting.value = true
  try {
    // 组装提交数据：包含原交易ID、违约操作字段，以及所有抵消交易字段
    const payload = {
      // 全部违约特有字段
      tradeId: originalTradeId.value,
      penaltyAmount: form.penaltyAmount,
      penaltyAccount: form.penaltyAccount,
      remark: form.remark,
      // 抵消交易字段（继承自 SwapTradeEntryRequest）
      customerId: form.customerId,
      branchCode: userStore.userInfo.orgCode || '',
      baseCurrency: baseCurrency.value,
      quoteCurrency: quoteCurrency.value,
      swapType: form.swapType,
      term: form.term,
      tradeDate: form.tradeDate,
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
      branchProfitPoint: form.nearLegBranchProfitPoint,
      // 保证金与用途
      marginAccountId: form.marginAccountId || null,
      marginAmount: form.marginAmount,
      purposeCode: form.purposeCode || null,
      fxPurposeCode: form.fxPurposeCode || null
    }
    await fullDefault(payload)
    ElMessage.success('掉期全部违约操作提交成功')
    // 触发待办面板刷新
    appStore.refreshTodos()
    router.back()
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    submitting.value = false
  }
}

// 取消：返回上一页
function handleCancel() {
  router.back()
}

onMounted(async () => {
  // 先加载掉期牌价列表
  await loadSwapQuotes()
  // 再加载原交易详情并计算抵消交易
  await loadOriginalTrade()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span class="page-title">掉期全部违约</span>
          <el-button size="small" @click="handleCancel">返回</el-button>
        </div>
      </template>

      <el-skeleton :loading="loading" animated>
        <el-form
          :model="form"
          label-width="120px"
          class="trade-form"
        >
          <!-- 公共信息区 -->
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="客户号">
                <el-input v-model="form.customerId" readonly />
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
                <el-input :model-value="currencyPair" readonly />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="掉期类型">
                <el-input
                  :model-value="form.swapType === 'S_B' ? 'S/B 近卖远买' : 'B/S 近买远卖'"
                  readonly
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="保证金账户">
                <el-select
                  v-model="form.marginAccountId"
                  v-loading="accountLoading"
                  placeholder="保证金账户"
                  disabled
                  style="width: 100%"
                >
                  <el-option
                    v-for="acc in marginAccounts"
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
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="用途编码">
                <el-input v-model="form.purposeCode" readonly />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结售汇用途编码">
                <el-input v-model="form.fxPurposeCode" readonly />
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
                <el-form-item label="近端金额">
                  <el-input-number
                    v-model="form.nearLegAmount"
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
                <el-form-item label="起息日">
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
                <el-form-item label="客户汇率">
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
                <el-form-item label="币种1账户">
                  <el-select
                    v-model="form.nearLegCurrency1Account"
                    v-loading="accountLoading"
                    placeholder="币种1账户"
                    disabled
                    style="width: 100%"
                  >
                    <el-option
                      v-for="acc in allAccounts.filter(a => a.currency === baseCurrency)"
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
                    v-model="form.nearLegCurrency2Account"
                    v-loading="accountLoading"
                    placeholder="币种2账户"
                    disabled
                    style="width: 100%"
                  >
                    <el-option
                      v-for="acc in allAccounts.filter(a => a.currency === quoteCurrency)"
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
                <el-form-item label="远端金额">
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
                <el-form-item label="远端到期日">
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
                  <el-input :model-value="form.term" readonly />
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
                <el-form-item label="客户汇率">
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
                <el-form-item label="币种1账户">
                  <el-select
                    v-model="form.farLegCurrency1Account"
                    v-loading="accountLoading"
                    placeholder="币种1账户"
                    disabled
                    style="width: 100%"
                  >
                    <el-option
                      v-for="acc in allAccounts.filter(a => a.currency === baseCurrency)"
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
                    v-loading="accountLoading"
                    placeholder="币种2账户"
                    disabled
                    style="width: 100%"
                  >
                    <el-option
                      v-for="acc in allAccounts.filter(a => a.currency === quoteCurrency)"
                      :key="acc.accountNo"
                      :label="accountLabel(acc)"
                      :value="acc.accountNo"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-card>

          <!-- 违约操作区 -->
          <el-divider content-position="left">违约操作信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="违约金">
                <el-input-number
                  v-model="form.penaltyAmount"
                  :precision="2"
                  :step="100"
                  :min="0"
                  :controls="false"
                  placeholder="请输入违约金"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="违约金账户">
                <el-input
                  v-model="form.penaltyAccount"
                  placeholder="请输入违约金账户"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="备注">
                <el-input
                  v-model="form.remark"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入备注"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 操作按钮 -->
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
            <el-button @click="handleCancel">取消</el-button>
          </el-form-item>
        </el-form>
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
