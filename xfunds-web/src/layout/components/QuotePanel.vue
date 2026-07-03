<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getSpotQuotes, getForwardQuotes, getSwapQuotes } from '@/api/quote'

const router = useRouter()

// 当前激活的牌价 Tab
const activeTab = ref('spot')

// 各类牌价数据
const spotQuotes = ref([])
const forwardQuotes = ref([])
const swapQuotes = ref([])

// 加载状态
const loading = ref(false)

// 加载即期牌价
async function loadSpotQuotes() {
  loading.value = true
  try {
    const res = await getSpotQuotes()
    spotQuotes.value = res.data || []
  } catch (e) {
    spotQuotes.value = []
  } finally {
    loading.value = false
  }
}

// 加载远期牌价
async function loadForwardQuotes() {
  loading.value = true
  try {
    const res = await getForwardQuotes()
    forwardQuotes.value = res.data || []
  } catch (e) {
    forwardQuotes.value = []
  } finally {
    loading.value = false
  }
}

// 加载掉期牌价
async function loadSwapQuotes() {
  loading.value = true
  try {
    const res = await getSwapQuotes()
    swapQuotes.value = res.data || []
  } catch (e) {
    swapQuotes.value = []
  } finally {
    loading.value = false
  }
}

// 点击分/客买卖价跳转交易录入页，携带牌价参数
// 逻辑：点击分/客买价 = 分行要从客户那买外币 = 客户卖外币 = 交易方向为 SELL
// 点击分/客卖价 = 分行要卖外币给客户 = 客户买外币 = 交易方向为 BUY
function goToTradeEntry(type, row, direction) {
  const pathMap = {
    spot: '/fx/spot-entry',
    forward: '/fx/forward-entry',
    swap: '/fx/swap-entry'
  }
  // 点击分/客买价(direction='buy') = 客户卖出 = 方向设为 SELL
  // 点击分/客卖价(direction='sell') = 客户买入 = 方向设为 BUY
  const tradeDirection = direction === 'buy' ? 'SELL' : 'BUY'
  const customerRate = direction === 'buy' ? row.branchCustomerBuyRate : row.branchCustomerSellRate
  const query = {
    currencyPair: row.currencyPair,
    baseCurrency: row.baseCurrency,
    quoteCurrency: row.quoteCurrency,
    direction: tradeDirection,
    customerRate: customerRate,
    totalBuyRate: row.totalBuyRate,
    totalSellRate: row.totalSellRate
  }
  // 远期牌价额外携带远期点与远期汇率
  if (type === 'forward') {
    query.forwardPoint = row.forwardPoint
    query.forwardRate = customerRate
    query.term = row.term
  }
  // 掉期牌价：点击分/客S/B(=分/客卖价,branchCustomerSellRate)设为 S/B(近卖远买)
  // 点击分/客B/S(=分/客买价,branchCustomerBuyRate)设为 B/S(近买远卖)
  if (type === 'swap') {
    query.swapPoint = row.swapPoint
    query.swapType = direction === 'buy' ? 'B_S' : 'S_B'
    query.term = row.term
    query.maturityDate = row.maturityDate
    delete query.direction // 掉期交易用 swapType 而不是 direction
  }
  router.push({
    path: pathMap[type],
    query: query
  })
}

// Tab 切换时按需加载对应牌价
function handleTabChange(tab) {
  if (tab === 'spot' && spotQuotes.value.length === 0) loadSpotQuotes()
  if (tab === 'forward' && forwardQuotes.value.length === 0) loadForwardQuotes()
  if (tab === 'swap' && swapQuotes.value.length === 0) loadSwapQuotes()
}

onMounted(() => {
  loadSpotQuotes()
})
</script>

<template>
  <div class="quote-panel">
    <el-tabs v-model="activeTab" class="quote-tabs" @tab-change="handleTabChange">
      <!-- 即期牌价 -->
      <el-tab-pane label="即期牌价" name="spot">
        <el-table
          v-loading="loading"
          :data="spotQuotes"
          border
          stripe
          size="small"
          height="100%"
        >
          <el-table-column prop="currencyPair" label="货币对" width="100" />
          <el-table-column prop="marketMidRate" label="市场中间价" width="110" />
          <el-table-column prop="totalBuyRate" label="总/分买价" width="100" />
          <el-table-column prop="totalSellRate" label="总/分卖价" width="100" />
          <el-table-column label="分/客买价" width="110">
            <template #default="{ row }">
              <el-link type="primary" @click="goToTradeEntry('spot', row, 'buy')">
                {{ row.branchCustomerBuyRate }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="分/客卖价" width="110">
            <template #default="{ row }">
              <el-link type="primary" @click="goToTradeEntry('spot', row, 'sell')">
                {{ row.branchCustomerSellRate }}
              </el-link>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 远期牌价 -->
      <el-tab-pane label="远期牌价" name="forward">
        <el-table
          v-loading="loading"
          :data="forwardQuotes"
          border
          stripe
          size="small"
          height="100%"
        >
          <el-table-column prop="currencyPair" label="货币对" width="100" />
          <el-table-column prop="term" label="期限" width="90" />
          <el-table-column prop="maturityDate" label="到期日" width="110" />
          <el-table-column prop="totalBuyRate" label="总/分买价" width="100" />
          <el-table-column prop="totalSellRate" label="总/分卖价" width="100" />
          <el-table-column label="分/客买价" width="110">
            <template #default="{ row }">
              <el-link type="primary" @click="goToTradeEntry('forward', row, 'buy')">
                {{ row.branchCustomerBuyRate }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="分/客卖价" width="110">
            <template #default="{ row }">
              <el-link type="primary" @click="goToTradeEntry('forward', row, 'sell')">
                {{ row.branchCustomerSellRate }}
              </el-link>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 掉期牌价 -->
      <el-tab-pane label="掉期牌价" name="swap">
        <el-table
          v-loading="loading"
          :data="swapQuotes"
          border
          stripe
          size="small"
          height="100%"
        >
          <el-table-column prop="currencyPair" label="货币对" width="100" />
          <el-table-column prop="term" label="期限" width="90" />
          <el-table-column prop="maturityDate" label="到期日" width="110" />
          <el-table-column prop="totalSellRate" label="总/分S/B" width="100" />
          <el-table-column prop="totalBuyRate" label="总/分B/S" width="100" />
          <el-table-column label="分/客S/B" width="110">
            <template #default="{ row }">
              <el-link type="primary" @click="goToTradeEntry('swap', row, 'sell')">
                {{ row.branchCustomerSellRate }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="分/客B/S" width="110">
            <template #default="{ row }">
              <el-link type="primary" @click="goToTradeEntry('swap', row, 'buy')">
                {{ row.branchCustomerBuyRate }}
              </el-link>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.quote-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  padding: 8px 12px;
}

.quote-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.quote-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

.quote-tabs :deep(.el-tab-pane) {
  height: 100%;
}
</style>
