<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTradeDetail, earlyDefault, getCustomerAccounts, searchQuotes } from '@/api/trade'
import { formatTradeDirection } from '@/utils/constants'

const route = useRoute()
const router = useRouter()

// 加载状态
const loading = ref(false)
const submitting = ref(false)

// 原交易数据
const originalTrade = ref(null)

// 客户CNY账户列表
const cnyAccounts = ref([])

// 即期报价
const spotQuote = ref(null)

const PENALTY_RATE_DIFF = 0.02

// 计算今日日期
const today = computed(() => new Date().toISOString().split('T')[0])

// 表单数据
const form = reactive({
  // 违约金额固定为原交易金额
  defaultAmount: null,
  // 即期客户汇率
  spotCustomerRate: null,
  // 即期成本汇率
  spotCostRate: null,
  // 惩罚汇率（可修改）
  penaltyRate: null,
  // 掉期近端成本汇率
  swapNearLegCostRate: null,
  // 掉期远端成本汇率
  swapFarLegCostRate: null,
  // 轧差账户
  nettingAccount: '',
  // 轧差货币（默认CNY）
  nettingCurrency: 'CNY',
  // 备注
  remark: ''
})

// 计算属性：分/客买价
const branchCustomerBuyRate = computed(() => spotQuote.value?.branchCustomerBuyRate)
// 计算属性：分/客卖价
const branchCustomerSellRate = computed(() => spotQuote.value?.branchCustomerSellRate)
// 计算属性：总/分买价
const headBranchBuyRate = computed(() => spotQuote.value?.headBranchBuyRate)
// 计算属性：总/分卖价
const headBranchSellRate = computed(() => spotQuote.value?.headBranchSellRate)

// 计算属性：原交易金额
const originalAmount = computed(() => originalTrade.value?.notionalAmount || 0)

function toNumber(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function roundTo(value, scale) {
  const num = toNumber(value)
  if (num === null) {
    return null
  }
  return Number(num.toFixed(scale))
}

// 自动计算惩罚汇率
function calculateDefaultPenaltyRate() {
  const spotRate = toNumber(form.spotCustomerRate)
  const direction = originalTrade.value?.tradeDirection
  if (spotRate === null || !direction) {
    return null
  }
  if (direction === 'BUY') {
    return roundTo(spotRate + PENALTY_RATE_DIFF, 8)
  }
  if (direction === 'SELL') {
    return roundTo(spotRate - PENALTY_RATE_DIFF, 8)
  }
  return null
}

// 监听即期客户汇率变化，自动更新惩罚汇率
watch(
  () => form.spotCustomerRate,
  () => {
    const defaultRate = calculateDefaultPenaltyRate()
    if (defaultRate !== null) {
      form.penaltyRate = defaultRate
    }
  }
)

// 计算属性：远端金额（与近端相反）
const farLegAmount = computed(() => form.defaultAmount)

// 计算属性：即期金额（与近端相反）
const spotAmount = computed(() => form.defaultAmount)

// 计算属性：近端买卖方向（与原交易相同）
const nearLegDirection = computed(() => originalTrade.value?.tradeDirection)

// 计算属性：远端买卖方向（与原交易相反）
const farLegDirection = computed(() => {
  const dir = originalTrade.value?.tradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})

// 计算属性：即期买卖方向（与原交易相反）
const spotDirection = computed(() => {
  const dir = originalTrade.value?.tradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})

// 计算属性：轧差金额
const nettingAmount = computed(() => {
  const defaultAmount = toNumber(form.defaultAmount)
  const spotRate = toNumber(form.spotCustomerRate)
  const nearRate = toNumber(form.penaltyRate)
  const direction = originalTrade.value?.tradeDirection
  if (defaultAmount === null || spotRate === null || nearRate === null || !direction) {
    return null
  }
  if (direction === 'SELL') {
    return roundTo((spotRate - nearRate) * defaultAmount, 2)
  }
  if (direction === 'BUY') {
    return roundTo((nearRate - spotRate) * defaultAmount, 2)
  }
  return null
})

// 计算属性：轧差账户余额（从已加载的CNY账户列表中查询）
const nettingAccountBalance = computed(() => {
  if (!form.nettingAccount || cnyAccounts.value.length === 0) {
    return null
  }
  const acc = cnyAccounts.value.find(a => a.accountNo === form.nettingAccount)
  return acc ? acc.balance : null
})

// 计算属性：轧差账户冻结金额
const nettingAccountFrozen = computed(() => {
  if (!form.nettingAccount || cnyAccounts.value.length === 0) {
    return null
  }
  const acc = cnyAccounts.value.find(a => a.accountNo === form.nettingAccount)
  return acc ? (acc.frozenAmount || 0) : null
})

// 计算属性：轧差账户可用余额（余额 - 冻结金额）
const nettingAccountAvailable = computed(() => {
  const balance = toNumber(nettingAccountBalance.value)
  const frozen = toNumber(nettingAccountFrozen.value)
  if (balance === null) {
    return null
  }
  return balance - (frozen || 0)
})

// 计算属性：轧差账户可用余额是否不足
const nettingBalanceInsufficient = computed(() => {
  const available = toNumber(nettingAccountAvailable.value)
  const amount = toNumber(nettingAmount.value)
  if (available === null || amount === null) {
    return false
  }
  return available < amount
})

// 加载客户CNY账户列表
async function loadCnyAccounts() {
  if (!originalTrade.value?.customerId) return
  
  try {
    const res = await getCustomerAccounts(originalTrade.value.customerId, 'CNY')
    cnyAccounts.value = res.data || []
    // 如果有账户，默认选择第一个
    if (cnyAccounts.value.length > 0 && !form.nettingAccount) {
      form.nettingAccount = cnyAccounts.value[0].accountNo
    }
  } catch (e) {
    console.error('加载客户账户失败', e)
  }
}

// 加载即期报价
async function loadSpotQuote() {
  if (!originalTrade.value?.currencyPair) return
  
  try {
    const res = await searchQuotes('SPOT', originalTrade.value.currencyPair)
    if (res.data && res.data.length > 0) {
      spotQuote.value = res.data[0]
      
      // 确定即期交易方向是与原交易相反
      const spotDir = spotDirection.value
      
      // 即期客户汇率
      if (spotDir === 'BUY') {
        form.spotCustomerRate = spotQuote.value.branchCustomerSellRate
        // 成本汇率：交易方向BUY（客户买入）取总/分卖价
        form.spotCostRate = spotQuote.value.headBranchSellRate
      } else if (spotDir === 'SELL') {
        form.spotCustomerRate = spotQuote.value.branchCustomerBuyRate
        // 成本汇率：交易方向SELL（客户卖出）取总/分买价
        form.spotCostRate = spotQuote.value.headBranchBuyRate
      }
      
      // 掉期近端交易方向与原交易相同
      const nearLegDir = nearLegDirection.value
      if (nearLegDir === 'BUY') {
        // 掉期近端成本汇率取总/分卖价
        form.swapNearLegCostRate = spotQuote.value.headBranchSellRate
      } else if (nearLegDir === 'SELL') {
        // 掉期近端成本汇率取总/分买价
        form.swapNearLegCostRate = spotQuote.value.headBranchBuyRate
      }
      
      // 掉期远端交易方向与原交易相反
      const farLegDir = farLegDirection.value
      if (farLegDir === 'BUY') {
        // 掉期远端成本汇率取总/分卖价
        form.swapFarLegCostRate = spotQuote.value.headBranchSellRate
      } else if (farLegDir === 'SELL') {
        // 掉期远端成本汇率取总/分买价
        form.swapFarLegCostRate = spotQuote.value.headBranchBuyRate
      }
      
      // 初始化惩罚汇率
      const defaultRate = calculateDefaultPenaltyRate()
      if (defaultRate !== null) {
        form.penaltyRate = defaultRate
      }
    }
  } catch (e) {
    console.error('加载即期报价失败', e)
  }
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
    originalTrade.value = res.data.master // 注意：TradeDetailVO 中主表信息在 master 属性下
    // 默认违约金额为原交易金额
    form.defaultAmount = originalTrade.value.notionalAmount
    
    // 加载客户CNY账户
    await loadCnyAccounts()
    
    // 加载即期报价
    await loadSpotQuote()
  } catch (e) {
    ElMessage.error('加载交易详情失败')
  } finally {
    loading.value = false
  }
}

// 提交操作
async function handleSubmit() {
  // 校验
  if (!form.defaultAmount) {
    ElMessage.warning('违约金额不能为空')
    return
  }
  if (!form.penaltyRate) {
    ElMessage.warning('惩罚汇率不能为空')
    return
  }
  if (!form.spotCustomerRate) {
    ElMessage.warning('请输入即期客户汇率')
    return
  }
  if (!form.nettingAccount) {
    ElMessage.warning('请选择轧差账户')
    return
  }
  if (nettingBalanceInsufficient.value) {
    ElMessage.warning('轧差账户余额不足，无法提交')
    return
  }

  submitting.value = true
  try {
    const payload = {
      tradeId: originalTrade.value.tradeId,
      defaultAmount: form.defaultAmount,
      spotMarketRate: form.spotCustomerRate,
      spotCustomerRate: form.spotCustomerRate,
      swapNearLegRate: form.penaltyRate,
      swapNearLegValueDate: today.value,
      swapFarLegRate: originalTrade.value.customerRate,
      nearLegAccount: form.nettingAccount,
      farLegAccount: form.nettingAccount,
      penaltyRate: form.penaltyRate,
      nettingAmount: nettingAmount.value,
      nettingCurrency: form.nettingCurrency,
      nettingAccount: form.nettingAccount,
      remark: form.remark,
      // 新增成本汇率字段
      swapNearLegCostRate: form.swapNearLegCostRate,
      swapFarLegCostRate: form.swapFarLegCostRate
    }
    await earlyDefault(payload)
    ElMessage.success('提前违约操作提交成功')
    router.push('/fx/unmatured')
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

onMounted(() => {
  loadOriginalTrade()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span class="page-title">提前违约</span>
          <el-button size="small" @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-skeleton :loading="loading" animated>
        <!-- 原交易信息 -->
        <el-divider content-position="left">原交易信息</el-divider>
        <el-descriptions :column="2" border size="default">
          <el-descriptions-item label="业务编号" :label-style="{ width: '120px' }">{{ originalTrade?.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="客户号" :label-style="{ width: '120px' }">{{ originalTrade?.customerId }}</el-descriptions-item>
          <el-descriptions-item label="客户名称" :label-style="{ width: '120px' }">{{ originalTrade?.customerName }}</el-descriptions-item>
          <el-descriptions-item label="货币对" :label-style="{ width: '120px' }">{{ originalTrade?.currencyPair }}</el-descriptions-item>
          <el-descriptions-item label="交易机构" :label-style="{ width: '120px' }">{{ originalTrade?.branchName }}</el-descriptions-item>
          <el-descriptions-item label="原交易金额" :label-style="{ width: '120px' }">{{ originalTrade?.notionalAmount }}</el-descriptions-item>
          <el-descriptions-item label="原交易汇率" :label-style="{ width: '120px' }">{{ originalTrade?.customerRate }}</el-descriptions-item>
          <el-descriptions-item label="原到期日" :label-style="{ width: '120px' }">{{ originalTrade?.maturityDate }}</el-descriptions-item>
        </el-descriptions>

        <!-- 近端交易信息 -->
        <el-divider content-position="left">近端交易信息</el-divider>
        <el-form :model="form" label-width="120px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="违约金额">
                <el-input-number
                  :model-value="form.defaultAmount"
                  :precision="2"
                  :step="100"
                  :min="0"
                  :max="originalAmount"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="惩罚汇率">
                <el-input-number
                  v-model="form.penaltyRate"
                  :precision="4"
                  :step="0.0001"
                  :min="0"
                  :controls="false"
                  style="width: 100%"
                  placeholder="请输入惩罚汇率"
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
              <el-form-item label="起息日">
                <el-input :model-value="today" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 远端交易信息 -->
        <el-divider content-position="left">远端交易信息</el-divider>
        <el-form label-width="120px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="客户汇率">
                <el-input-number
                  :model-value="originalTrade?.customerRate"
                  :precision="4"
                  :controls="false"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="金额">
                <el-input-number :model-value="farLegAmount" :precision="2" disabled :controls="false" style="width: 100%" />
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
              <el-form-item label="到期日">
                <el-input :model-value="originalTrade?.maturityDate" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 即期交易信息 -->
        <el-divider content-position="left">即期交易信息</el-divider>
        <el-form label-width="120px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="客户汇率">
                <el-input-number
                  v-model="form.spotCustomerRate"
                  :precision="4"
                  :step="0.0001"
                  :min="0"
                  :controls="false"
                  style="width: 100%"
                  placeholder="请输入即期客户汇率"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="金额">
                <el-input-number :model-value="spotAmount" :precision="2" disabled :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="买卖方向">
                <el-input :model-value="formatTradeDirection(spotDirection)" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="起息日">
                <el-input :model-value="today" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 轧差信息 -->
        <el-divider content-position="left">轧差信息</el-divider>
        <el-form label-width="120px">
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
              <el-form-item label="轧差账户">
                <el-select v-model="form.nettingAccount" placeholder="请选择轧差账户" style="width: 100%" clearable>
                  <el-option
                    v-for="account in cnyAccounts"
                    :key="account.accountId"
                    :label="`${account.accountNo} (${account.accountType})`"
                    :value="account.accountNo"
                  />
                </el-select>
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
            <el-col :span="12">
              <el-form-item label="冻结金额">
                <el-input
                  :model-value="nettingAccountFrozen !== null ? nettingAccountFrozen : '-'"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="可用余额">
                <el-input
                  :model-value="nettingAccountAvailable !== null ? nettingAccountAvailable : '-'"
                  disabled
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert
            v-if="nettingAmount && !nettingBalanceInsufficient"
            title="提交后轧差金额将被冻结，直到定时交割任务执行时扣减"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 8px"
          />
          <el-alert
            v-if="nettingBalanceInsufficient"
            title="轧差账户可用余额不足，无法提交"
            type="error"
            :closable="false"
            show-icon
            style="margin-top: 8px"
          />
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
          <el-button type="primary" :loading="submitting" :disabled="nettingBalanceInsufficient" @click="handleSubmit">提交</el-button>
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
