<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { queryCustomerTrades, getTradeDetail, retrySettle } from '@/api/trade'
import {
  formatTradeType,
  formatTradeDirection,
  formatTradeStatus,
  formatSpecialTradeType,
  formatSettlementMethod,
  formatSwapType,
  getStatusTagType,
  tradeStatusMap,
  specialTradeTypeMap,
  formatEventType
} from '@/utils/constants'

// 查询条件表单
const queryForm = reactive({
  businessNo: '',
  customerId: '',
  tradeType: '',
  status: '',
  specialTradeType: ''
})

// 表格数据与分页
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

// 交易类型下拉选项（外汇工作台仅含即期/远期/掉期，期权在期权交易管理模块查询）
const tradeTypeOptions = [
  { value: '', label: '全部' },
  { value: 'SPOT', label: '即期' },
  { value: 'FORWARD', label: '远期' },
  { value: 'SWAP', label: '掉期' }
]

// 交易状态下拉选项
const statusOptions = Object.keys(tradeStatusMap).map((key) => ({
  value: key,
  label: tradeStatusMap[key]
}))

// 特殊交易类型下拉选项
const specialTradeTypeOptions = Object.keys(specialTradeTypeMap).map((key) => ({
  value: key,
  label: specialTradeTypeMap[key]
}))

// 交割类型格式化：T0->T+0, T1->T+1, T2->T+2
function formatDeliveryType(type) {
  const map = { T0: 'T+0', T1: 'T+1', T2: 'T+2' }
  return map[type] || type || '-'
}

// 表格中方向/掉期类型展示：SWAP 展示掉期类型，其他展示买卖方向
function formatDirectionOrSwap(row) {
  if (row.tradeType === 'SWAP') {
    // SWAP: master.tradeDirection = nearLegDirection; S_B->BUY, B_S->SELL
    return row.tradeDirection === 'BUY' ? 'S/B 近卖远买' : 'B/S 近买远卖'
  }
  return formatTradeDirection(row.tradeDirection)
}

// 原交易类型格式化：优先取 originalTradeType，为空时回退到当前 tradeType
function formatOriginalTradeType(row) {
  return formatTradeType(row.originalTradeType || row.tradeType)
}

// 交易时间格式化（24小时制）
function fmtTradeTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ')
}

// 详情弹窗状态
const detailVisible = ref(false)
const detailLoading = ref(false)
const tradeDetail = ref(null)
// 详情弹窗内部子 tab
const detailActiveTab = ref('detail')

// 组装查询参数：合并过滤条件与分页，空值不传
function buildQueryParams() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  if (queryForm.businessNo) params.businessNo = queryForm.businessNo
  if (queryForm.customerId) params.customerId = queryForm.customerId
  if (queryForm.tradeType) params.tradeType = queryForm.tradeType
  if (queryForm.status) params.status = queryForm.status
  if (queryForm.specialTradeType) params.specialTradeType = queryForm.specialTradeType
  return params
}

// 加载客户交易列表
async function loadData() {
  loading.value = true
  try {
    const res = await queryCustomerTrades(buildQueryParams())
    tableData.value = res.data?.records || res.data?.list || []
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
  queryForm.tradeType = ''
  queryForm.status = ''
  queryForm.specialTradeType = ''
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

// 打开详情弹窗并加载交易详情
async function openDetail(row) {
  const tradeId = row.tradeId || row.id
  if (!tradeId) {
    ElMessage.warning('未获取到交易ID')
    return
  }
  detailVisible.value = true
  detailLoading.value = true
  detailActiveTab.value = 'detail'
  tradeDetail.value = null
  try {
    const res = await getTradeDetail(tradeId)
    tradeDetail.value = res.data || null
  } catch (e) {
    tradeDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

// 重新执行交割失败的交易
async function handleRetrySettle(row) {
  const tradeId = row.tradeId || row.id
  if (!tradeId) return
  try {
    await retrySettle(tradeId)
    ElMessage.success('重新交割成功')
    loadData()
  } catch (e) {
    // 错误由拦截器处理
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
        <span class="page-title">客户交易查询</span>
      </template>

      <!-- 查询条件表单 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="业务编号">
          <el-input v-model="queryForm.businessNo" placeholder="请输入业务编号" clearable />
        </el-form-item>
        <el-form-item label="客户号">
          <el-input v-model="queryForm.customerId" placeholder="请输入客户号" clearable />
        </el-form-item>
        <el-form-item label="交易类型">
          <el-select v-model="queryForm.tradeType" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in tradeTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="交易状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="特殊交易类型">
          <el-select v-model="queryForm.specialTradeType" placeholder="全部" clearable style="width: 160px">
            <el-option
              v-for="opt in specialTradeTypeOptions"
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

      <!-- 交易列表表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column label="交易类型" width="90">
          <template #default="{ row }">{{ formatTradeType(row.tradeType) }}</template>
        </el-table-column>
        <el-table-column label="原交易类型" width="100">
          <template #default="{ row }">{{ formatOriginalTradeType(row) }}</template>
        </el-table-column>
        <el-table-column label="特殊交易类型" width="120">
          <template #default="{ row }">{{ formatSpecialTradeType(row.specialTradeType) }}</template>
        </el-table-column>
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column label="买卖方向" width="140">
          <template #default="{ row }">{{ formatDirectionOrSwap(row) }}</template>
        </el-table-column>
        <el-table-column prop="notionalAmount" label="金额" width="140" />
        <el-table-column prop="customerRate" label="客户汇率" width="110" />
        <el-table-column prop="tradeDate" label="交易日" width="110" />
        <el-table-column label="交易时间" width="160">
          <template #default="{ row }">{{ fmtTradeTime(row.tradeTime) }}</template>
        </el-table-column>
        <el-table-column prop="maturityDate" label="到期日" width="110" />
        <el-table-column label="交割方式" width="110">
          <template #default="{ row }">{{ formatSettlementMethod(row.settlementMethod) }}</template>
        </el-table-column>
        <el-table-column label="交易状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ formatTradeStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="makerName" label="经办人" width="100" />
        <el-table-column prop="checkerName" label="复核人" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDetail(row)">查看详情</el-button>
            <el-button
              v-if="row.status === 'SETTLE_FAILED'"
              type="warning"
              size="small"
              link
              @click="handleRetrySettle(row)"
            >重新交割</el-button>
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

    <!-- 交易详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="交易详情"
      width="1000px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-tabs v-model="detailActiveTab">
          <!-- 交易详情 tab（按交易类型展示不同字段，与录入页面一致） -->
          <el-tab-pane label="交易详情" name="detail">
            <template v-if="tradeDetail?.master">
              <!-- 基本信息（所有交易类型通用） -->
              <el-divider content-position="left">基本信息</el-divider>
              <el-descriptions :column="3" border size="small" class="detail-desc">
                <el-descriptions-item label="业务编号">{{ tradeDetail.master.businessNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="交易类型">{{ formatTradeType(tradeDetail.master.tradeType) }}</el-descriptions-item>
                <el-descriptions-item label="特殊交易类型">{{ formatSpecialTradeType(tradeDetail.master.specialTradeType) }}</el-descriptions-item>
                <el-descriptions-item label="交易状态">{{ formatTradeStatus(tradeDetail.master.status) }}</el-descriptions-item>
                <el-descriptions-item label="交易机构">{{ tradeDetail.master.branchName || tradeDetail.master.branchCode || '-' }}</el-descriptions-item>
                <el-descriptions-item label="经办人">{{ tradeDetail.makerName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="复核人">{{ tradeDetail.checkerName || '-' }}</el-descriptions-item>
              </el-descriptions>

              <!-- ===== SPOT 即期交易详情 ===== -->
              <template v-if="tradeDetail.master.tradeType === 'SPOT'">
                <el-divider content-position="left">交易信息</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="客户号">{{ tradeDetail.master.customerId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户名称">{{ tradeDetail.master.customerName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交易币种">{{ tradeDetail.master.currencyPair || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="即期汇率">{{ tradeDetail.spotDetail?.spotRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户汇率">{{ tradeDetail.master.customerRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="成本汇率">{{ tradeDetail.master.costRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="分行收益点">{{ tradeDetail.master.branchProfitPoint ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="买卖方向">{{ formatTradeDirection(tradeDetail.master.tradeDirection) }}</el-descriptions-item>
                  <el-descriptions-item label="金额">{{ tradeDetail.master.notionalAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交易日">{{ tradeDetail.master.tradeDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="起息日">{{ tradeDetail.master.valueDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交割类型">{{ formatDeliveryType(tradeDetail.master.deliveryType) }}</el-descriptions-item>
                  <el-descriptions-item label="交割方式">{{ formatSettlementMethod(tradeDetail.master.settlementMethod) }}</el-descriptions-item>
                  <el-descriptions-item label="币种1账户">{{ tradeDetail.spotDetail?.currency1Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="币种2账户">{{ tradeDetail.spotDetail?.currency2Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="保证金账户">{{ tradeDetail.spotDetail?.marginAccountId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="保证金金额">{{ tradeDetail.spotDetail?.marginAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="用途编码">{{ tradeDetail.master.purposeCode || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="结售汇用途编码">{{ tradeDetail.master.fxPurposeCode || '-' }}</el-descriptions-item>
                </el-descriptions>
                <!-- 提前违约生成的即期交易显示轧差信息 -->
                <template v-if="tradeDetail.master.specialTradeType === 'EARLY_DEFAULT'">
                  <el-divider content-position="left">轧差信息</el-divider>
                  <el-descriptions :column="3" border size="small" class="detail-desc">
                    <el-descriptions-item label="轧差货币">{{ tradeDetail.master.nettingCurrency || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="轧差账户">{{ tradeDetail.master.nettingAccount || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="轧差金额">{{ tradeDetail.master.nettingAmount ?? '-' }}</el-descriptions-item>
                  </el-descriptions>
                </template>
              </template>

              <!-- ===== FORWARD 远期交易详情 ===== -->
              <template v-else-if="tradeDetail.master.tradeType === 'FORWARD'">
                <el-divider content-position="left">交易信息</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="客户号">{{ tradeDetail.master.customerId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户名称">{{ tradeDetail.master.customerName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交易币种">{{ tradeDetail.master.currencyPair || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="即期汇率">{{ tradeDetail.master.spotRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户汇率">{{ tradeDetail.master.customerRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="成本汇率">{{ tradeDetail.master.costRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="分行收益点">{{ tradeDetail.master.branchProfitPoint ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="买卖方向">{{ formatTradeDirection(tradeDetail.master.tradeDirection) }}</el-descriptions-item>
                  <el-descriptions-item label="金额">{{ tradeDetail.master.notionalAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交易日">{{ tradeDetail.master.tradeDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="起息日">{{ tradeDetail.master.valueDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="到期日">{{ tradeDetail.master.maturityDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交割类型">{{ formatDeliveryType(tradeDetail.master.deliveryType) }}</el-descriptions-item>
                  <el-descriptions-item label="交割方式">{{ formatSettlementMethod(tradeDetail.master.settlementMethod) }}</el-descriptions-item>
                  <el-descriptions-item label="币种1账户">{{ tradeDetail.forwardDetail?.currency1Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="币种2账户">{{ tradeDetail.forwardDetail?.currency2Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="保证金账户">{{ tradeDetail.forwardDetail?.marginAccountId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="保证金金额">{{ tradeDetail.forwardDetail?.marginAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="用途编码">{{ tradeDetail.master.purposeCode || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="结售汇用途编码">{{ tradeDetail.master.fxPurposeCode || '-' }}</el-descriptions-item>
                </el-descriptions>
              </template>

              <!-- ===== SWAP 掉期交易详情 ===== -->
              <template v-else-if="tradeDetail.master.tradeType === 'SWAP'">
                <el-divider content-position="left">公共信息</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="客户号">{{ tradeDetail.master.customerId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户名称">{{ tradeDetail.master.customerName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交易币种">{{ tradeDetail.master.currencyPair || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="掉期类型">{{ formatSwapType(tradeDetail.swapDetail?.swapType) }}</el-descriptions-item>
                  <el-descriptions-item label="保证金账户">{{ tradeDetail.swapDetail?.marginAccountId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="保证金金额">{{ tradeDetail.swapDetail?.marginAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="用途编码">{{ tradeDetail.master.purposeCode || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="结售汇用途编码">{{ tradeDetail.master.fxPurposeCode || '-' }}</el-descriptions-item>
                </el-descriptions>

                <!-- 近端交易信息 -->
                <el-divider content-position="left">近端交易信息（Near Leg）</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="近端方向">{{ formatTradeDirection(tradeDetail.swapDetail?.nearLegDirection) }}</el-descriptions-item>
                  <el-descriptions-item label="近端金额">{{ tradeDetail.swapDetail?.nearLegAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交易日">{{ tradeDetail.master.tradeDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="起息日">{{ tradeDetail.swapDetail?.nearLegValueDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="近端交割方式">{{ formatSettlementMethod(tradeDetail.swapDetail?.nearLegSettlementMethod) }}</el-descriptions-item>
                  <el-descriptions-item label="近端分行收益点">{{ tradeDetail.swapDetail?.nearLegBranchProfitPoint ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="成本汇率">{{ tradeDetail.swapDetail?.nearLegCostRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户汇率">{{ tradeDetail.swapDetail?.nearLegCustomerRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="币种1账户">{{ tradeDetail.swapDetail?.nearLegCurrency1Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="币种2账户">{{ tradeDetail.swapDetail?.nearLegCurrency2Account || '-' }}</el-descriptions-item>
                </el-descriptions>

                <!-- 远端交易信息 -->
                <el-divider content-position="left">远端交易信息（Far Leg）</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="远端方向">{{ formatTradeDirection(tradeDetail.swapDetail?.farLegDirection) }}</el-descriptions-item>
                  <el-descriptions-item label="远端金额">{{ tradeDetail.swapDetail?.farLegAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="远端到期日">{{ tradeDetail.swapDetail?.farLegValueDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期限">{{ tradeDetail.swapDetail?.term || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="远端交割方式">{{ formatSettlementMethod(tradeDetail.swapDetail?.farLegSettlementMethod) }}</el-descriptions-item>
                  <el-descriptions-item label="远端分行收益点">{{ tradeDetail.swapDetail?.farLegBranchProfitPoint ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="成本汇率">{{ tradeDetail.swapDetail?.farLegCostRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户汇率">{{ tradeDetail.swapDetail?.farLegCustomerRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item v-if="tradeDetail.master.specialTradeType !== 'EARLY_DELIVERY'" label="币种1账户">{{ tradeDetail.swapDetail?.farLegCurrency1Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item v-if="tradeDetail.master.specialTradeType !== 'EARLY_DELIVERY'" label="币种2账户">{{ tradeDetail.swapDetail?.farLegCurrency2Account || '-' }}</el-descriptions-item>
                </el-descriptions>

                <!-- 提前违约/市价展期生成的掉期交易展示轧差信息 -->
                <template v-if="['EARLY_DEFAULT', 'ROLLOVER_MARKET'].includes(tradeDetail.master.specialTradeType)">
                  <el-divider content-position="left">轧差信息</el-divider>
                  <el-descriptions :column="3" border size="small" class="detail-desc">
                    <el-descriptions-item label="轧差货币">{{ tradeDetail.master.nettingCurrency || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="轧差账户">{{ tradeDetail.master.nettingAccount || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="轧差金额">{{ tradeDetail.master.nettingAmount ?? '-' }}</el-descriptions-item>
                  </el-descriptions>
                </template>
              </template>

              <!-- ===== OPTION 期权交易详情 ===== -->
              <template v-else-if="tradeDetail.master.tradeType === 'OPTION'">
                <el-divider content-position="left">公共信息</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="客户号">{{ tradeDetail.master.customerId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="客户名称">{{ tradeDetail.master.customerName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交易币种">{{ tradeDetail.master.currencyPair || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="买卖方向">{{ formatTradeDirection(tradeDetail.optionDetail?.buyerSeller) }}</el-descriptions-item>
                  <el-descriptions-item label="期权类别">{{ tradeDetail.optionDetail?.optionStyle === 'AMERICAN' ? '美式' : '欧式' }}</el-descriptions-item>
                  <el-descriptions-item label="涨跌方向">{{ tradeDetail.optionDetail?.optionType === 'CALL' ? '涨' : '跌' }}</el-descriptions-item>
                  <el-descriptions-item label="交易日">{{ tradeDetail.master.tradeDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="到期日">{{ tradeDetail.optionDetail?.maturityDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交割类型">{{ tradeDetail.master.deliveryType || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交割日">{{ tradeDetail.master.valueDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="天数">{{ tradeDetail.optionDetail?.days ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="行权时点">{{ tradeDetail.optionDetail?.exerciseTimePoint || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="交割方式">{{ formatSettlementMethod(tradeDetail.optionDetail?.settlementMethod) }}</el-descriptions-item>
                  <el-descriptions-item label="用途编码">{{ tradeDetail.master.purposeCode || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="结售汇用途编码">{{ tradeDetail.master.fxPurposeCode || '-' }}</el-descriptions-item>
                </el-descriptions>

                <el-divider content-position="left">交易要素</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="即期汇率">{{ tradeDetail.master.spotRate ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="执行价格">{{ tradeDetail.optionDetail?.strikePrice ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="面值（币种1）">{{ tradeDetail.optionDetail?.notionalAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="币种1账户">{{ tradeDetail.optionDetail?.currency1Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="币种2账户">{{ tradeDetail.optionDetail?.currency2Account || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="观察期开始日">{{ tradeDetail.optionDetail?.observationStartDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="观察期结束日">{{ tradeDetail.optionDetail?.observationEndDate || '-' }}</el-descriptions-item>
                </el-descriptions>

                <el-divider content-position="left">费用/报价</el-divider>
                <el-descriptions :column="3" border size="small" class="detail-desc">
                  <el-descriptions-item label="期权费金额">{{ tradeDetail.optionDetail?.premiumAmount ?? '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期权费币种">{{ tradeDetail.optionDetail?.premiumCurrency || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期权费账户">{{ tradeDetail.optionDetail?.premiumAccountId || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期权费交割日">{{ tradeDetail.optionDetail?.premiumValueDate || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期权费已付">{{ tradeDetail.optionDetail?.premiumPaidFlag === '1' ? '是' : '否' }}</el-descriptions-item>
                </el-descriptions>
              </template>
            </template>
            <el-empty v-else description="暂无数据" />
          </el-tab-pane>

          <!-- 生命周期事件 tab -->
          <el-tab-pane label="生命周期事件" name="lifecycleEvents">
            <el-table
              :data="tradeDetail?.lifecycleList || []"
              border
              stripe
              size="small"
              max-height="360"
            >
              <el-table-column label="事件类型" width="140">
                <template #default="{ row }">{{ formatEventType(row.eventType) }}</template>
              </el-table-column>
              <el-table-column prop="eventTime" label="事件时间" width="160" />
              <el-table-column label="操作人" width="120">
                <template #default="{ row }">{{ row.operatorName || row.operatorId || '-' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="160" />
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
.detail-desc {
  margin-bottom: 8px;
}
</style>
