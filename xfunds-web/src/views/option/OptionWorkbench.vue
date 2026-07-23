<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getOptionTasks,
  getOptionWorkbench,
  viewOriginalTrade
} from '@/api/option'
import { getTradeDetail, approveTrade, rejectTrade } from '@/api/trade'
import {
  formatTaskType,
  formatTaskStatus,
  formatTradeStatus,
  formatTradeType,
  formatOptionDirection,
  formatPriceDirection,
  formatOptionStyle,
  formatOptionType,
  formatOptionDeliveryType,
  formatOptionSettlementMethod,
  formatLifecycleOp,
  lifecycleOpMap,
  getStatusTagType
} from '@/utils/constants'

const route = useRoute()
const router = useRouter()

// ===== 待办任务列表 =====
const taskData = ref([])
const taskLoading = ref(false)
// 待办任务分页（前端分页）
const taskCurrentPage = ref(1)
const taskPageSize = ref(10)
// 当前页展示的待办任务数据
const pagedTaskData = computed(() => {
  const start = (taskCurrentPage.value - 1) * taskPageSize.value
  return taskData.value.slice(start, start + taskPageSize.value)
})

// ===== 期权价内提醒列表 =====
const reminderData = ref([])
const reminderLoading = ref(false)
// 价内提醒分页（前端分页）
const reminderCurrentPage = ref(1)
const reminderPageSize = ref(10)
// 当前页展示的价内提醒数据
const pagedReminderData = computed(() => {
  const start = (reminderCurrentPage.value - 1) * reminderPageSize.value
  return reminderData.value.slice(start, start + reminderPageSize.value)
})

// ===== 任务处理弹窗 =====
const processVisible = ref(false)
const currentTask = ref(null)
const tradeDetail = ref(null)
const detailLoading = ref(false)
const approvalForm = reactive({ comment: '' })
const approving = ref(false)

// ===== 查看原交易弹窗 =====
const originalVisible = ref(false)
const originalLoading = ref(false)
const originalDetail = ref(null)

// 获取今日日期字符串 YYYY-MM-DD
function getTodayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 加载待办任务列表
async function loadTasks() {
  taskLoading.value = true
  try {
    const res = await getOptionTasks()
    taskData.value = res.data?.records || res.data?.list || res.data || []
  } catch (e) {
    taskData.value = []
  } finally {
    taskLoading.value = false
  }
}

// 加载期权价内提醒列表
async function loadReminders() {
  reminderLoading.value = true
  try {
    const res = await getOptionWorkbench()
    const list = res.data?.records || res.data?.list || res.data || []
    // 按交易日期逆序排序（日期越晚越靠上）
    list.sort((a, b) => (b.tradeDate || '').localeCompare(a.tradeDate || ''))
    reminderData.value = list
  } catch (e) {
    reminderData.value = []
  } finally {
    reminderLoading.value = false
  }
}

// 打开任务处理：跳转到期权交易复核页面（与录入页相同字段但只读，含通过/拒绝/退回/取消）
function openProcessDialog(row) {
  const tradeId = row.tradeId || row.businessId
  const taskId = row.taskId || row.id
  const businessType = row.businessType || ''
  if (!tradeId || !taskId) {
    ElMessage.warning('任务信息不完整')
    return
  }
  router.push({ path: '/option/review', query: { tradeId, taskId, businessType } })
}

// 组装审批提交数据
function buildApprovalPayload(action) {
  const taskId = currentTask.value?.taskId || currentTask.value?.id
  const tradeId = currentTask.value?.tradeId || currentTask.value?.businessId
  return {
    taskId,
    tradeId,
    action,
    comment: approvalForm.comment
  }
}

// 执行审批操作：通过/拒绝/退回
async function handleApproval(action) {
  const taskId = currentTask.value?.taskId || currentTask.value?.id
  if (!taskId) {
    ElMessage.warning('未获取到任务ID')
    return
  }
  approving.value = true
  try {
    const payload = buildApprovalPayload(action)
    const apiMap = {
      approve: approveTrade,
      reject: rejectTrade
    }
    const api = apiMap[action]
    if (!api) throw new Error('未知审批操作')
    await api(payload)
    const actionLabel = { approve: '通过', reject: '拒绝' }[action]
    ElMessage.success(`${actionLabel}成功`)
    processVisible.value = false
    loadTasks()
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    approving.value = false
  }
}

// 打开查看原交易弹窗
async function openOriginalDialog(row) {
  const tradeId = row.tradeId || row.id
  if (!tradeId) {
    ElMessage.warning('未获取到交易ID')
    return
  }
  originalVisible.value = true
  originalLoading.value = true
  originalDetail.value = null
  try {
    const res = await viewOriginalTrade(tradeId)
    // 后端返回嵌套结构 { master, optionDetail, ... }，展平为单层对象供模板使用
    const master = res.data?.master
    const option = res.data?.optionDetail
    if (master && option) {
      originalDetail.value = {
        businessNo: master.businessNo,
        customerId: master.customerId,
        customerName: master.customerName,
        currencyPair: master.currencyPair,
        baseCurrency: master.baseCurrency,
        quoteCurrency: master.quoteCurrency,
        spotRate: master.spotRate,
        tradeDate: master.tradeDate,
        maturityDate: master.maturityDate,
        deliveryType: master.deliveryType,
        deliveryDate: master.valueDate,
        optionStatus: master.status,
        buyerSeller: option.buyerSeller,
        optionType: option.optionType,
        // 涨跌方向由期权类型推导：CALL→涨，PUT→跌
        priceDirection: option.optionType === 'CALL' ? 'UP' : 'DOWN',
        optionStyle: option.optionStyle,
        strikePrice: option.strikePrice,
        // 行权时点：后端格式 "yyyy-MM-ddTHH:mm:ss"，界面只显示 HH:mm:ss
        exerciseTimePoint: option.exerciseTimePoint
          ? option.exerciseTimePoint.split('T')[1] || option.exerciseTimePoint
          : '',
        days: option.days,
        premiumValueDate: option.premiumValueDate,
        settlementMethod: option.settlementMethod,
        notionalAmount: option.notionalAmount,
        premiumAmount: option.premiumAmount,
        premiumCurrency: option.premiumCurrency,
        observationStartDate: option.observationStartDate,
        observationEndDate: option.observationEndDate
      }
    } else {
      originalDetail.value = null
    }
  } catch (e) {
    originalDetail.value = null
  } finally {
    originalLoading.value = false
  }
}

onMounted(async () => {
  await loadTasks()
  loadReminders()
  // 检测路由参数，若有 taskId/tradeId 则自动打开任务处理弹窗（来自右侧待办双击）
  const qTaskId = route.query.taskId
  const qTradeId = route.query.tradeId
  if (qTaskId || qTradeId) {
    // 在已加载的待办列表中查找匹配任务
    const matched = taskData.value.find(
      (t) => String(t.taskId || t.id) === String(qTaskId)
        || String(t.tradeId || t.businessId) === String(qTradeId)
    )
    if (matched) {
      openProcessDialog(matched)
    } else if (qTradeId) {
      // 列表中未找到，直接用 tradeId 打开处理弹窗
      openProcessDialog({ tradeId: qTradeId, taskId: qTaskId })
    }
  }
})
</script>

<template>
  <div class="page-container">
    <!-- 顶部：待办任务列表 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="page-title">待办任务列表</span>
          <el-button type="primary" :icon="Refresh" :loading="taskLoading" @click="loadTasks">
            刷新
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="taskLoading"
        :data="pagedTaskData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ formatTaskType(row.taskType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'warning' : 'info'" size="small">
              {{ formatTaskStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="事件内容" width="140">
          <template #default="{ row }">
            {{ row.taskType === 'CHECK_LIFECYCLE' && row.businessType && lifecycleOpMap[row.businessType]
              ? formatLifecycleOp(row.businessType)
              : (row.tradeType === 'OPTION' || row.businessType === 'OPTION'
                ? (row.optionStyle === 'EUROPEAN' ? '欧式期权' : row.optionStyle === 'AMERICAN' ? '美式期权' : '期权交易')
                : formatTradeType(row.businessType)) }}
          </template>
        </el-table-column>
        <el-table-column prop="makerName" label="发起人" width="120" />
        <el-table-column label="受理人" width="120">
          <template #default="{ row }">{{ row.assigneeName || '待认领' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openProcessDialog(row)">
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 待办任务分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="taskCurrentPage"
          v-model:page-size="taskPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="taskData.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          small
        />
      </div>
    </el-card>

    <!-- 底部：期权价内提醒 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="page-title">期权价内提醒</span>
          <el-button type="primary" :icon="Refresh" :loading="reminderLoading" @click="loadReminders">
            刷新
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="reminderLoading"
        :data="pagedReminderData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="150" fixed />
        <el-table-column label="买卖方向" width="90">
          <template #default="{ row }">{{ formatOptionDirection(row.buyerSeller) }}</template>
        </el-table-column>
        <el-table-column label="第一币种涨/跌" width="110">
          <template #default="{ row }">{{ formatPriceDirection(row.priceDirection) }}</template>
        </el-table-column>
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column prop="referenceRate" label="参考汇率" width="110" />
        <el-table-column prop="strikePrice" label="执行汇率" width="110" />
        <el-table-column label="期权状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.optionStatus)" size="small">
              {{ formatTradeStatus(row.optionStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="originalAmount" label="面值（币种1）" width="130" />
        <el-table-column prop="remainingAmount" label="剩余未处理金额" width="130" />
        <el-table-column prop="maturityDate" label="到期日" width="110" />
        <el-table-column label="观察期开始日" width="120">
          <template #default="{ row }">{{ row.observationStartDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="观察期结束日" width="120">
          <template #default="{ row }">{{ row.observationEndDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="tradeDate" label="交易日期" width="110" />
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="交割类型" width="100">
          <template #default="{ row }">{{ formatOptionDeliveryType(row.deliveryType) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openOriginalDialog(row)">
              查看原交易
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 价内提醒分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="reminderCurrentPage"
          v-model:page-size="reminderPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="reminderData.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          small
        />
      </div>
    </el-card>

    <!-- 任务处理弹窗 -->
    <el-dialog
      v-model="processVisible"
      title="任务处理"
      width="800px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-descriptions v-if="tradeDetail" :column="2" border size="small" title="交易详情">
          <el-descriptions-item label="业务编号">{{ tradeDetail.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="货币对">{{ tradeDetail.currencyPair }}</el-descriptions-item>
          <el-descriptions-item label="买卖方向">{{ formatOptionDirection(tradeDetail.buyerSeller) }}</el-descriptions-item>
          <el-descriptions-item label="期权类别">{{ formatOptionStyle(tradeDetail.optionStyle) }}</el-descriptions-item>
          <el-descriptions-item label="执行价格">{{ tradeDetail.strikePrice }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ tradeDetail.notionalAmount }}</el-descriptions-item>
          <el-descriptions-item label="交易日">{{ tradeDetail.tradeDate }}</el-descriptions-item>
          <el-descriptions-item label="到期日">{{ tradeDetail.maturityDate }}</el-descriptions-item>
          <el-descriptions-item label="交易状态">
            <el-tag :type="getStatusTagType(tradeDetail.status)" size="small">
              {{ formatTradeStatus(tradeDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户号">{{ tradeDetail.customerId }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ tradeDetail.customerName }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!detailLoading" description="暂无交易详情" />

        <el-divider content-position="left">审批处理</el-divider>
        <el-form label-width="100px">
          <el-form-item label="审批意见">
            <el-input
              v-model="approvalForm.comment"
              type="textarea"
              :rows="3"
              placeholder="请输入审批意见"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="danger" :loading="approving" @click="handleApproval('reject')">拒绝</el-button>
        <el-button type="primary" :loading="approving" @click="handleApproval('approve')">通过</el-button>
      </template>
    </el-dialog>

    <!-- 查看原交易弹窗 -->
    <el-dialog
      v-model="originalVisible"
      title="原交易详情"
      width="820px"
      destroy-on-close
    >
      <div v-loading="originalLoading">
        <el-descriptions v-if="originalDetail" :column="2" border size="small">
          <el-descriptions-item label="业务编号">{{ originalDetail.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="客户号">{{ originalDetail.customerId }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ originalDetail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="货币对">{{ originalDetail.currencyPair }}</el-descriptions-item>
          <el-descriptions-item label="基础货币">{{ originalDetail.baseCurrency }}</el-descriptions-item>
          <el-descriptions-item label="报价货币">{{ originalDetail.quoteCurrency }}</el-descriptions-item>
          <el-descriptions-item label="买卖方向">{{ formatOptionDirection(originalDetail.buyerSeller) }}</el-descriptions-item>
          <el-descriptions-item label="期权种类">{{ formatOptionType(originalDetail.optionType) }}</el-descriptions-item>
          <el-descriptions-item label="涨跌方向">{{ formatPriceDirection(originalDetail.priceDirection) }}</el-descriptions-item>
          <el-descriptions-item label="期权类别">{{ formatOptionStyle(originalDetail.optionStyle) }}</el-descriptions-item>
          <el-descriptions-item label="即期汇率">{{ originalDetail.spotRate }}</el-descriptions-item>
          <el-descriptions-item label="执行价格">{{ originalDetail.strikePrice }}</el-descriptions-item>
          <el-descriptions-item label="行权时点">{{ originalDetail.exerciseTimePoint }}</el-descriptions-item>
          <el-descriptions-item label="交易日">{{ originalDetail.tradeDate }}</el-descriptions-item>
          <el-descriptions-item label="到期日">{{ originalDetail.maturityDate }}</el-descriptions-item>
          <el-descriptions-item label="交割类型">{{ formatOptionDeliveryType(originalDetail.deliveryType) }}</el-descriptions-item>
          <el-descriptions-item label="交割日">{{ originalDetail.deliveryDate }}</el-descriptions-item>
          <el-descriptions-item label="天数">{{ originalDetail.days }}</el-descriptions-item>
          <el-descriptions-item label="期权费交割日">{{ originalDetail.premiumValueDate }}</el-descriptions-item>
          <el-descriptions-item label="交割方式">{{ formatOptionSettlementMethod(originalDetail.settlementMethod) }}</el-descriptions-item>
          <el-descriptions-item label="面值（币种1）">{{ originalDetail.notionalAmount }}</el-descriptions-item>
          <el-descriptions-item label="期权费金额">{{ originalDetail.premiumAmount }}</el-descriptions-item>
          <el-descriptions-item label="期权费币种">{{ originalDetail.premiumCurrency }}</el-descriptions-item>
          <el-descriptions-item label="观察期开始日">{{ originalDetail.observationStartDate }}</el-descriptions-item>
          <el-descriptions-item label="观察期结束日">{{ originalDetail.observationEndDate }}</el-descriptions-item>
          <el-descriptions-item label="期权状态">
            <el-tag :type="getStatusTagType(originalDetail.optionStatus)" size="small">
              {{ formatTradeStatus(originalDetail.optionStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!originalLoading" description="暂无数据" />
      </div>

      <template #footer>
        <el-button @click="originalVisible = false">关闭</el-button>
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
.section-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
