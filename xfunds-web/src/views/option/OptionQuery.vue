<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  queryOptions,
  queryCloseTrades,
  queryPremiumTrades,
  queryExerciseTrades,
  queryAbandonTrades,
  getOptionDetail
} from '@/api/option'
import {
  formatTradeStatus,
  formatOptionDirection,
  formatOptionStyle,
  formatOptionType,
  formatPriceDirection,
  formatOptionDeliveryType,
  formatOptionSettlementMethod,
  getStatusTagType,
  tradeStatusMap
} from '@/utils/constants'

// 当前激活的 tab
const activeTab = ref('option')

// 各 tab 对应的查询接口
const tabApiMap = {
  option: queryOptions,
  close: queryCloseTrades,
  premium: queryPremiumTrades,
  exercise: queryExerciseTrades,
  abandon: queryAbandonTrades
}

// 查询条件表单
const queryForm = reactive({
  businessNo: '',
  customerId: '',
  optionStyle: '',
  optionType: '',
  status: ''
})

// 表格数据与分页
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

// 期权类别下拉选项
const optionStyleOptions = [
  { value: '', label: '全部' },
  { value: 'AMERICAN', label: '美式' },
  { value: 'EUROPEAN', label: '欧式' }
]

// 期权种类下拉选项
const optionTypeOptions = [
  { value: '', label: '全部' },
  { value: 'CALL', label: '看涨' },
  { value: 'PUT', label: '看跌' }
]

// 交易状态下拉选项
const statusOptions = Object.keys(tradeStatusMap).map((key) => ({
  value: key,
  label: tradeStatusMap[key]
}))

// 详情弹窗状态
const detailVisible = ref(false)
const detailLoading = ref(false)
const optionDetail = ref(null)
const detailActiveTab = ref('master')

// 组装查询参数：合并过滤条件与分页，空值不传
function buildQueryParams() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  if (queryForm.businessNo) params.businessNo = queryForm.businessNo
  if (queryForm.customerId) params.customerId = queryForm.customerId
  if (queryForm.optionStyle) params.optionStyle = queryForm.optionStyle
  if (queryForm.optionType) params.optionType = queryForm.optionType
  if (queryForm.status) params.status = queryForm.status
  return params
}

// 加载当前 tab 数据
async function loadData() {
  loading.value = true
  try {
    const api = tabApiMap[activeTab.value]
    const res = await api(buildQueryParams())
    tableData.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 点击查询按钮：重置页码后查询
function handleQuery() {
  pagination.pageNum = 1
  loadData()
}

// 点击重置按钮：清空查询条件并重新查询
function handleReset() {
  queryForm.businessNo = ''
  queryForm.customerId = ''
  queryForm.optionStyle = ''
  queryForm.optionType = ''
  queryForm.status = ''
  pagination.pageNum = 1
  loadData()
}

// 分页页码变化
function handlePageChange(page) {
  pagination.pageNum = page
  loadData()
}

// 分页每页条数变化
function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.pageNum = 1
  loadData()
}

// 切换 tab 时重置查询并重新加载
function handleTabChange() {
  pagination.pageNum = 1
  loadData()
}

// 打开详情弹窗并加载期权交易详情
async function openDetail(row) {
  const tradeId = row.tradeId || row.id
  if (!tradeId) {
    ElMessage.warning('未获取到交易ID')
    return
  }
  detailVisible.value = true
  detailLoading.value = true
  detailActiveTab.value = 'master'
  optionDetail.value = null
  try {
    const res = await getOptionDetail(tradeId)
    optionDetail.value = res.data || null
  } catch (e) {
    optionDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">期权交易查询</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="期权交易查询" name="option" />
        <el-tab-pane label="平仓交易查询" name="close" />
        <el-tab-pane label="期权费交割查询" name="premium" />
        <el-tab-pane label="行权交易查询" name="exercise" />
        <el-tab-pane label="放弃交易查询" name="abandon" />
      </el-tabs>

      <!-- 查询条件表单 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="业务编号">
          <el-input v-model="queryForm.businessNo" placeholder="请输入业务编号" clearable />
        </el-form-item>
        <el-form-item label="客户号">
          <el-input v-model="queryForm.customerId" placeholder="请输入客户号" clearable />
        </el-form-item>
        <el-form-item label="期权类别">
          <el-select v-model="queryForm.optionStyle" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in optionStyleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期权种类">
          <el-select v-model="queryForm.optionType" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in optionTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="activeTab === 'option'" label="交易状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 期权交易查询表格 -->
      <el-table
        v-if="activeTab === 'option'"
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column label="买卖方向" width="90">
          <template #default="{ row }">{{ formatOptionDirection(row.buyerSeller) }}</template>
        </el-table-column>
        <el-table-column label="期权类别" width="90">
          <template #default="{ row }">{{ formatOptionStyle(row.optionStyle) }}</template>
        </el-table-column>
        <el-table-column label="期权种类" width="90">
          <template #default="{ row }">{{ formatOptionType(row.optionType) }}</template>
        </el-table-column>
        <el-table-column label="涨跌方向" width="90">
          <template #default="{ row }">{{ formatPriceDirection(row.priceDirection) }}</template>
        </el-table-column>
        <el-table-column prop="strikePrice" label="执行价格" width="110" />
        <el-table-column prop="notionalAmount" label="原始签约金额" width="130" />
        <el-table-column prop="premiumAmount" label="期权费金额" width="120" />
        <el-table-column prop="tradeDate" label="交易日" width="110" />
        <el-table-column prop="maturityDate" label="到期日" width="110" />
        <el-table-column label="交割类型" width="90">
          <template #default="{ row }">{{ formatOptionDeliveryType(row.deliveryType) }}</template>
        </el-table-column>
        <el-table-column label="交割方式" width="90">
          <template #default="{ row }">{{ formatOptionSettlementMethod(row.settlementMethod) }}</template>
        </el-table-column>
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.optionStatus || row.status)" size="small">
              {{ formatTradeStatus(row.optionStatus || row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 平仓交易查询表格 -->
      <el-table
        v-else-if="activeTab === 'close'"
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column label="买卖方向" width="90">
          <template #default="{ row }">{{ formatOptionDirection(row.buyerSeller) }}</template>
        </el-table-column>
        <el-table-column prop="closeDate" label="平仓日" width="110" />
        <el-table-column prop="closeAmount" label="平仓金额" width="130" />
        <el-table-column prop="closePremium" label="平仓权利金" width="120" />
        <el-table-column prop="closePnl" label="平仓损益" width="120" />
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 期权费交割查询表格 -->
      <el-table
        v-else-if="activeTab === 'premium'"
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column prop="premiumAmount" label="期权费金额" width="120" />
        <el-table-column prop="premiumCurrency" label="期权费币种" width="100" />
        <el-table-column prop="premiumValueDate" label="期权费交割日" width="120" />
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 行权交易查询表格 -->
      <el-table
        v-else-if="activeTab === 'exercise'"
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column label="买卖方向" width="90">
          <template #default="{ row }">{{ formatOptionDirection(row.buyerSeller) }}</template>
        </el-table-column>
        <el-table-column prop="exerciseDate" label="行权日" width="110" />
        <el-table-column prop="referenceRate" label="参考汇率" width="110" />
        <el-table-column prop="strikePrice" label="执行价格" width="110" />
        <el-table-column prop="notionalAmount" label="原始签约金额" width="130" />
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 放弃交易查询表格 -->
      <el-table
        v-else-if="activeTab === 'abandon'"
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column label="买卖方向" width="90">
          <template #default="{ row }">{{ formatOptionDirection(row.buyerSeller) }}</template>
        </el-table-column>
        <el-table-column prop="abandonDate" label="放弃日" width="110" />
        <el-table-column prop="notionalAmount" label="原始签约金额" width="130" />
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 期权交易详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="期权交易详情"
      width="860px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-tabs v-model="detailActiveTab">
          <!-- 主信息 tab -->
          <el-tab-pane label="主信息" name="master">
            <el-descriptions v-if="optionDetail" :column="2" border size="small">
              <el-descriptions-item label="业务编号">{{ optionDetail.businessNo }}</el-descriptions-item>
              <el-descriptions-item label="客户号">{{ optionDetail.customerId }}</el-descriptions-item>
              <el-descriptions-item label="客户名称">{{ optionDetail.customerName }}</el-descriptions-item>
              <el-descriptions-item label="货币对">{{ optionDetail.currencyPair }}</el-descriptions-item>
              <el-descriptions-item label="基础货币">{{ optionDetail.baseCurrency }}</el-descriptions-item>
              <el-descriptions-item label="报价货币">{{ optionDetail.quoteCurrency }}</el-descriptions-item>
              <el-descriptions-item label="买卖方向">{{ formatOptionDirection(optionDetail.buyerSeller) }}</el-descriptions-item>
              <el-descriptions-item label="期权种类">{{ formatOptionType(optionDetail.optionType) }}</el-descriptions-item>
              <el-descriptions-item label="涨跌方向">{{ formatPriceDirection(optionDetail.priceDirection) }}</el-descriptions-item>
              <el-descriptions-item label="期权类别">{{ formatOptionStyle(optionDetail.optionStyle) }}</el-descriptions-item>
              <el-descriptions-item label="即期汇率">{{ optionDetail.spotRate }}</el-descriptions-item>
              <el-descriptions-item label="执行价格">{{ optionDetail.strikePrice }}</el-descriptions-item>
              <el-descriptions-item label="行权时点">{{ optionDetail.exerciseTimePoint }}</el-descriptions-item>
              <el-descriptions-item label="交易日">{{ optionDetail.tradeDate }}</el-descriptions-item>
              <el-descriptions-item label="到期日">{{ optionDetail.maturityDate }}</el-descriptions-item>
              <el-descriptions-item label="交割类型">{{ formatOptionDeliveryType(optionDetail.deliveryType) }}</el-descriptions-item>
              <el-descriptions-item label="交割日">{{ optionDetail.deliveryDate }}</el-descriptions-item>
              <el-descriptions-item label="天数">{{ optionDetail.days }}</el-descriptions-item>
              <el-descriptions-item label="期权费交割日">{{ optionDetail.premiumValueDate }}</el-descriptions-item>
              <el-descriptions-item label="交割方式">{{ formatOptionSettlementMethod(optionDetail.settlementMethod) }}</el-descriptions-item>
              <el-descriptions-item label="原始签约金额">{{ optionDetail.notionalAmount }}</el-descriptions-item>
              <el-descriptions-item label="货币1金额">{{ optionDetail.currency1Amount }}</el-descriptions-item>
              <el-descriptions-item label="货币2金额">{{ optionDetail.currency2Amount }}</el-descriptions-item>
              <el-descriptions-item label="期权费金额">{{ optionDetail.premiumAmount }}</el-descriptions-item>
              <el-descriptions-item label="期权费币种">{{ optionDetail.premiumCurrency }}</el-descriptions-item>
              <el-descriptions-item label="观察期开始日">{{ optionDetail.observationStartDate }}</el-descriptions-item>
              <el-descriptions-item label="观察期结束日">{{ optionDetail.observationEndDate }}</el-descriptions-item>
              <el-descriptions-item label="交易状态">
                <el-tag :type="getStatusTagType(optionDetail.optionStatus || optionDetail.status)" size="small">
                  {{ formatTradeStatus(optionDetail.optionStatus || optionDetail.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="备注">{{ optionDetail.remark }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="暂无数据" />
          </el-tab-pane>

          <!-- 生命周期事件 tab -->
          <el-tab-pane label="生命周期事件" name="lifecycleEvents">
            <el-table
              :data="optionDetail?.lifecycleEvents || []"
              border
              stripe
              size="small"
              max-height="360"
            >
              <el-table-column prop="eventType" label="事件类型" width="140" />
              <el-table-column prop="eventTime" label="事件时间" width="160" />
              <el-table-column prop="operatorId" label="操作人" width="120" />
              <el-table-column prop="remark" label="备注" min-width="160" />
            </el-table>
          </el-tab-pane>

          <!-- 审批日志 tab -->
          <el-tab-pane label="审批日志" name="approvalLogs">
            <el-table
              :data="optionDetail?.approvalLogs || []"
              border
              stripe
              size="small"
              max-height="360"
            >
              <el-table-column prop="approvalType" label="审批类型" width="120" />
              <el-table-column prop="operatorId" label="操作人" width="120" />
              <el-table-column prop="operateTime" label="操作时间" width="160" />
              <el-table-column label="审批结果" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.result === 'APPROVED' ? 'success' : row.result === 'REJECTED' ? 'danger' : 'warning'" size="small">
                    {{ row.result === 'APPROVED' ? '通过' : row.result === 'REJECTED' ? '拒绝' : row.result === 'RETURNED' ? '退回' : row.result }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="审批意见" min-width="180" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
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
.query-form {
  margin-bottom: 12px;
}
.pagination-wrapper {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
