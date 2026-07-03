<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTradeDetail, earlyDelivery, getCustomerAccounts, getSpotQuotes } from '@/api/trade'
import { formatTradeType, formatTradeDirection } from '@/utils/constants'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const originalTrade = ref(null)
const quotes = ref([])

// 表单数据
const form = reactive({
  // 近端
  nearLegCustomerRate: null,
  nearLegCostRate: null,
  nearLegAccount1: '',
  nearLegAccount2: '',
  // 远端
  farLegCustomerRate: null,
  farLegCostRate: null,
  // 备注
  remark: ''
})

// 今日日期
const today = computed(() => new Date().toISOString().split('T')[0])

// 近端交易方向（与原交易相同）
const nearLegDirection = computed(() => originalTrade.value?.tradeDirection)

// 远端交易方向（与原交易相反）
const farLegDirection = computed(() => {
  const dir = originalTrade.value?.tradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})

// 获取货币对的两个币种
function splitCurrencyPair(pair) {
  if (!pair) return { base: '', quote: '' }
  const [base, quote] = pair.split('/')
  return { base, quote }
}

// 加载原交易详情
async function loadOriginalTrade(tradeId) {
  loading.value = true
  try {
    const res = await getTradeDetail(tradeId)
    originalTrade.value = res.data?.master || res.data
    // 远端客户汇率与成本汇率取原交易的客户汇率和成本汇率
    form.farLegCustomerRate = originalTrade.value.customerRate
    form.farLegCostRate = originalTrade.value.costRate
    // 自动加载报价和客户账户（报价加载后自动填充近端汇率）
    await Promise.all([
      loadQuotes(),
      loadCustomerAccounts()
    ])
  } catch (e) {
    ElMessage.error('加载原交易失败')
  } finally {
    loading.value = false
  }
}

// 加载报价
async function loadQuotes() {
  try {
    const res = await getSpotQuotes()
    quotes.value = res.data || []
    // 自动计算成本汇率
    updateCostRates()
  } catch (e) {
    ElMessage.error('加载报价失败')
  }
}

// 根据交易方向使用实时牌价自动填充近端成本汇率和客户汇率
function updateCostRates() {
  if (!originalTrade.value || !quotes.value.length) return
  const quote = quotes.value.find(q => q.currencyPair === originalTrade.value.currencyPair)
  if (!quote) return
  // 近端成本汇率与客户汇率取实时牌价
  // 客户买(BUY)：成本汇率取总/分S/B(totalSellRate)，客户汇率取分/客S/B(branchCustomerSellRate)
  // 客户卖(SELL)：成本汇率取总/分B/S(totalBuyRate)，客户汇率取分/客B/S(branchCustomerBuyRate)
  if (nearLegDirection.value === 'BUY') {
    form.nearLegCostRate = quote.totalSellRate
    form.nearLegCustomerRate = quote.branchCustomerSellRate
  } else {
    form.nearLegCostRate = quote.totalBuyRate
    form.nearLegCustomerRate = quote.branchCustomerBuyRate
  }
  // 远端成本汇率与客户汇率取原交易相同的汇率（已在 loadOriginalTrade 中设置）
}

// 客户账户列表
const customerAccounts = ref([])
async function loadCustomerAccounts() {
  if (!originalTrade.value?.customerId) return
  try {
    const { base, quote } = splitCurrencyPair(originalTrade.value.currencyPair)
    const [acc1, acc2] = await Promise.all([
      getCustomerAccounts(originalTrade.value.customerId, base),
      getCustomerAccounts(originalTrade.value.customerId, quote)
    ])
    customerAccounts.value = [...(acc1.data || []), ...(acc2.data || [])]
  } catch (e) {
    // 加载失败不影响
  }
}

// 过滤对应币种的账户
function getAccountsForCurrency(currency) {
  return customerAccounts.value.filter(a => a.currency === currency)
}

// 提交
async function handleSubmit() {
  if (!originalTrade.value) {
    ElMessage.error('没有原交易信息')
    return
  }
  submitting.value = true
  try {
    const { base, quote } = splitCurrencyPair(originalTrade.value.currencyPair)
    const payload = {
      tradeId: originalTrade.value.tradeId || originalTrade.value.id,
      nearLegCustomerRate: form.nearLegCustomerRate,
      nearLegCostRate: form.nearLegCostRate,
      farLegCustomerRate: form.farLegCustomerRate,
      farLegCostRate: form.farLegCostRate,
      nearLegAccount1: form.nearLegAccount1,
      nearLegAccount2: form.nearLegAccount2,
      nearLegValueDate: today.value,
      farLegValueDate: originalTrade.value.maturityDate,
      remark: form.remark
    }
    await earlyDelivery(payload)
    ElMessage.success('提前交割操作成功')
    router.push('/fx/unmatured')
  } catch (e) {
    // 错误已在拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  const tradeId = route.query.tradeId
  if (tradeId) {
    loadOriginalTrade(tradeId)
  } else {
    ElMessage.error('缺少交易ID')
    router.push('/fx/unmatured')
  }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span class="page-title">提前交割</span>
      </template>

      <div v-if="!originalTrade" class="empty-state">
        <el-empty description="加载中..." />
      </div>

      <div v-else>
        <!-- 原交易信息 -->
        <el-divider content-position="left">原交易信息</el-divider>
        <el-descriptions :column="2" border size="default">
          <el-descriptions-item label="业务编号">{{ originalTrade.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="客户号">{{ originalTrade.customerId }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ originalTrade.customerName }}</el-descriptions-item>
          <el-descriptions-item label="货币对">{{ originalTrade.currencyPair }}</el-descriptions-item>
          <el-descriptions-item label="交易机构">{{ originalTrade.branchName }}</el-descriptions-item>
        </el-descriptions>

        <!-- 近端交易信息 -->
        <el-divider content-position="left">近端交易信息</el-divider>
        <el-form label-width="120px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成本汇率">
                <el-input-number
                  :model-value="form.nearLegCostRate"
                  :precision="4"
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
                  :model-value="form.nearLegCustomerRate"
                  :precision="4"
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
                <el-input :model-value="formatTradeDirection(nearLegDirection)" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="金额">
                <el-input-number
                  :model-value="originalTrade.notionalAmount"
                  :precision="2"
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
                <el-input :model-value="today" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="起息日">
                <el-input :model-value="today" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="近端交割方式">
                <el-input model-value="全额交割" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="`币种1账户 (${splitCurrencyPair(originalTrade.currencyPair).base})`">
                <el-select v-model="form.nearLegAccount1" placeholder="请选择" style="width: 100%" clearable filterable>
                  <el-option
                    v-for="acc in getAccountsForCurrency(splitCurrencyPair(originalTrade.currencyPair).base)"
                    :key="acc.accountNo"
                    :label="`${acc.accountNo} - ${acc.accountType}`"
                    :value="acc.accountNo"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="`币种2账户 (${splitCurrencyPair(originalTrade.currencyPair).quote})`">
                <el-select v-model="form.nearLegAccount2" placeholder="请选择" style="width: 100%" clearable filterable>
                  <el-option
                    v-for="acc in getAccountsForCurrency(splitCurrencyPair(originalTrade.currencyPair).quote)"
                    :key="acc.accountNo"
                    :label="`${acc.accountNo} - ${acc.accountType}`"
                    :value="acc.accountNo"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 远端交易信息 -->
        <el-divider content-position="left">远端交易信息</el-divider>
        <el-form label-width="120px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成本汇率">
                <el-input-number
                  :model-value="form.farLegCostRate"
                  :precision="4"
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
                  :model-value="form.farLegCustomerRate"
                  :precision="4"
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
                  :model-value="originalTrade.notionalAmount"
                  :precision="2"
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
                <el-input :model-value="originalTrade.maturityDate" disabled style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="远端交割方式">
                <el-input model-value="无需交割" disabled style="width: 100%" />
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
          <el-button @click="router.push('/fx/unmatured')">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.empty-state {
  padding: 40px 0;
}
.action-bar {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
