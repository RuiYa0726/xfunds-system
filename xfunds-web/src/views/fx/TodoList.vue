<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyTasks } from '@/api/task'
import { getTradeDetail, approveTrade, rejectTrade, returnTrade } from '@/api/trade'
import { useAppStore } from '@/store/app'
import {
  formatTaskType,
  formatTaskStatus,
  formatTradeType,
  formatTradeDirection,
  formatTradeStatus,
  formatSpecialTradeType,
  formatSettlementMethod,
  formatSwapType,
  formatLifecycleOp,
  lifecycleOpMap,
  getStatusTagType
} from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

// 表格数据与分页
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

// 处理弹窗状态
const processVisible = ref(false)
const currentTask = ref(null)
const tradeDetail = ref(null)
const detailLoading = ref(false)
// 审批意见
const approvalForm = reactive({
  comment: ''
})
// 审批操作进行中状态
const approving = ref(false)

// 解析任务载荷（针对提前违约/提前交割/原价展期/市价展期任务）
const taskPayload = computed(() => {
  const specialTypes = ['EARLY_DEFAULT', 'EARLY_DELIVERY', 'ROLLOVER_ORIGINAL', 'ROLLOVER_MARKET']
  if (currentTask.value?.payload && specialTypes.includes(currentTask.value?.taskType)) {
    try {
      return JSON.parse(currentTask.value.payload)
    } catch (e) {
      return null
    }
  }
  return null
})

// 提前违约相关计算属性（直接返回值，不需要嵌套computed）
const earlyDefaultNearLegDirection = computed(() => taskPayload.value?.originalTradeDirection)
const earlyDefaultFarLegDirection = computed(() => {
  const dir = taskPayload.value?.originalTradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})
const earlyDefaultSpotDirection = computed(() => {
  const dir = taskPayload.value?.originalTradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})
const earlyDefaultToday = computed(() => {
  if (taskPayload.value?.swapNearLegValueDate) {
    return taskPayload.value.swapNearLegValueDate
  }
  return new Date().toISOString().split('T')[0]
})

// 提前交割相关计算属性（直接返回值，不需要嵌套computed）
const earlyDeliveryNearLegDirection = computed(() => taskPayload.value?.originalTradeDirection)
const earlyDeliveryFarLegDirection = computed(() => {
  const dir = taskPayload.value?.originalTradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})
const earlyDeliveryToday = computed(() => {
  if (taskPayload.value?.nearLegValueDate) {
    return taskPayload.value.nearLegValueDate
  }
  return new Date().toISOString().split('T')[0]
})

// 展期相关计算属性：近端方向与原交易相反（平掉旧交易），远端方向与原交易相同（新远期）
const rolloverNearLegDirection = computed(() => {
  const dir = taskPayload.value?.originalTradeDirection
  return dir === 'BUY' ? 'SELL' : dir === 'SELL' ? 'BUY' : dir
})
const rolloverFarLegDirection = computed(() => taskPayload.value?.originalTradeDirection)

// 弹窗标题：根据任务类型动态生成
const dialogTitle = computed(() => {
  const t = currentTask.value?.taskType
  if (t === 'EARLY_DEFAULT') return '提前违约审批'
  if (t === 'EARLY_DELIVERY') return '提前交割审批'
  if (t === 'ROLLOVER_ORIGINAL') return '原价展期审批'
  if (t === 'ROLLOVER_MARKET') return '市价展期审批'
  return '任务处理'
})

// 判断是否为修改任务
function isModifyTask(row) {
  return row && row.taskType === 'MODIFY'
}

// 判断是否为特殊生命周期任务（提前违约/提前交割/原价展期/市价展期）
// 这类任务本质上都是复核任务，类型列应显示"复核"，事件内容列显示具体操作
function isSpecialLifecycleTask(row) {
  const t = row && row.taskType
  return ['EARLY_DEFAULT', 'EARLY_DELIVERY', 'ROLLOVER_ORIGINAL', 'ROLLOVER_MARKET'].includes(t)
}

// 特殊生命周期任务类型集合（用于 MODIFY 任务的事件内容判断）
const specialTypeSet = ['EARLY_DEFAULT', 'EARLY_DELIVERY', 'ROLLOVER_ORIGINAL', 'ROLLOVER_MARKET']

// 格式化事件内容：特殊任务显示具体操作名称，MODIFY+特殊businessType也显示操作名称，普通任务显示交易类型
// 期权生命周期任务（CHECK_LIFECYCLE）根据businessType显示"放弃期权/执行期权/期权费交割"
// 期权交易根据optionStyle显示"欧式期权"或"美式期权"
function formatEventContent(row) {
  const bt = row && row.businessType
  if (isSpecialLifecycleTask(row)) {
    return formatTaskType(row.taskType)
  }
  if (row && row.taskType === 'MODIFY' && specialTypeSet.includes(bt)) {
    return formatTaskType(bt)
  }
  // 期权生命周期任务：根据businessType显示具体操作（放弃期权/执行期权/期权费交割）
  if (row && row.taskType === 'CHECK_LIFECYCLE' && bt && lifecycleOpMap[bt]) {
    return formatLifecycleOp(bt)
  }
  // 期权交易：根据optionStyle显示欧式期权/美式期权
  if (bt === 'OPTION' || (row && row.tradeType === 'OPTION')) {
    const style = row && row.optionStyle
    if (style === 'EUROPEAN') return '欧式期权'
    if (style === 'AMERICAN') return '美式期权'
    return '期权交易'
  }
  return formatTradeType(bt)
}

// 判断是否为提前违约任务
function isEarlyDefaultTask(row) {
    return row && row.taskType === "EARLY_DEFAULT";
}

// 判断是否为提前交割任务
function isEarlyDeliveryTask(row) {
    return row && row.taskType === "EARLY_DELIVERY";
}

// 判断是否为原价展期任务
function isRolloverOriginalTask(row) {
    return row && row.taskType === "ROLLOVER_ORIGINAL";
}

// 判断是否为市价展期任务
function isRolloverMarketTask(row) {
    return row && row.taskType === "ROLLOVER_MARKET";
}

// 加载任务列表
async function loadData() {
  loading.value = true
  try {
    const res = await getMyTasks()
    console.log('待办任务API返回:', res)
    // 后端返回 { code: 200, data: [...] }，data 是数组
    const list = res.data?.records || res.data?.list || res.data || []
    console.log('解析后的任务列表:', list)
    tableData.value = Array.isArray(list) ? list : []
    total.value = tableData.value.length
  } catch (e) {
    console.error('加载待办任务失败:', e)
    ElMessage.error('加载待办任务失败: ' + (e.message || '未知错误'))
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
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

// 处理修改任务：跳转到编辑页面
// 退回经办重新编辑时，MODIFY 任务的 businessType 被设为原任务类型
//   （EARLY_DELIVERY / ROLLOVER_ORIGINAL / ROLLOVER_MARKET 或 SPOT/FORWARD/SWAP）
function handleModifyTask(row) {
  if (!row) {
    ElMessage.warning('任务信息不完整')
    return
  }
  const tradeId = row.tradeId || row.businessId
  const taskId = row.taskId || row.id
  const businessType = row.businessType || row.tradeType

  // 1. 特殊生命周期任务退回经办：跳转到对应的发起页面，传入 tradeId 和 taskId
  if (businessType === 'EARLY_DELIVERY') {
    router.push({ path: '/fx/early-delivery', query: { tradeId, taskId } })
    return
  }
  if (businessType === 'ROLLOVER_ORIGINAL') {
    router.push({ path: '/fx/rollover', query: { tradeId, taskId, mode: 'ORIGINAL' } })
    return
  }
  if (businessType === 'ROLLOVER_MARKET') {
    router.push({ path: '/fx/rollover', query: { tradeId, taskId, mode: 'MARKET' } })
    return
  }
  if (businessType === 'EARLY_DEFAULT') {
    router.push({ path: '/fx/early-default', query: { tradeId, taskId } })
    return
  }

  // 2. 普通交易修改：根据交易类型跳转对应的交易录入页面
  let routePath = ''
  if (businessType === 'SPOT' || (row.tradeType && row.tradeType.includes('SPOT'))) {
    routePath = '/fx/spot-entry'
  } else if (businessType === 'FORWARD' || (row.tradeType && row.tradeType.includes('FORWARD'))) {
    routePath = '/fx/forward-entry'
  } else if (businessType === 'SWAP' || (row.tradeType && row.tradeType.includes('SWAP'))) {
    routePath = '/fx/swap-entry'
  } else if (businessType === 'OPTION' || (row.tradeType && row.tradeType.includes('OPTION'))) {
    routePath = '/option/entry'
  } else {
    // 默认尝试判断
    if (tradeDetail.value?.master?.tradeType) {
      const type = tradeDetail.value.master.tradeType
      if (type === 'SPOT') routePath = '/fx/spot-entry'
      else if (type === 'FORWARD') routePath = '/fx/forward-entry'
      else if (type === 'SWAP') routePath = '/fx/swap-entry'
    }
  }

  if (!routePath) {
    ElMessage.warning('无法确定交易类型')
    return
  }

  // 跳转到交易录入页面，并传入tradeId和taskId
  router.push({
    path: routePath,
    query: {
      tradeId,
      taskId
    }
  })
}

// 打开处理弹窗：加载交易详情
async function openProcessDialog(row) {
  currentTask.value = row
  approvalForm.comment = ''

  // 如果是修改任务，直接跳转编辑页面，不打开弹窗
  if (isModifyTask(row)) {
    // 先加载交易详情，然后跳转
    detailLoading.value = true
    const tradeId = row.tradeId || row.businessId
    if (tradeId) {
      try {
        const res = await getTradeDetail(tradeId)
        tradeDetail.value = res.data || null
      } catch (e) {
        tradeDetail.value = null
      } finally {
        detailLoading.value = false
      }
    }
    handleModifyTask(row)
    return
  }

  // 期权交易复核任务：跳转到期权复核页面（与录入页相同但只读，含通过/拒绝/退回/取消）
  if (row.tradeType === 'OPTION' || row.businessType === 'OPTION') {
    const tradeId = row.tradeId || row.businessId
    const taskId = row.taskId || row.id
    router.push({ path: '/option/review', query: { tradeId, taskId } })
    return
  }

  processVisible.value = true

  // 特殊生命周期任务（提前违约/提前交割/原价展期/市价展期）不需要加载交易详情，直接显示任务载荷
  if (isEarlyDefaultTask(row) || isEarlyDeliveryTask(row)
      || isRolloverOriginalTask(row) || isRolloverMarketTask(row)) {
    tradeDetail.value = null
    detailLoading.value = false
    return
  }

  // 普通任务加载交易详情
  detailLoading.value = true
  tradeDetail.value = null
  const tradeId = row.tradeId || row.businessId
  if (tradeId) {
    try {
      const res = await getTradeDetail(tradeId)
      tradeDetail.value = res.data || null
    } catch (e) {
      tradeDetail.value = null
    } finally {
      detailLoading.value = false
    }
  } else {
    detailLoading.value = false
  }
}

// 表格行双击：打开处理弹窗
function handleRowDblClick(row) {
  openProcessDialog(row)
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
      reject: rejectTrade,
      return: returnTrade
    }
    const api = apiMap[action]
    if (!api) throw new Error('未知审批操作')
    await api(payload)
    const actionLabel = { approve: '通过', reject: '拒绝', return: '退回' }[action]
    ElMessage.success(`${actionLabel}成功`)
    processVisible.value = false
    loadData()
    // 触发待办面板刷新
    appStore.refreshTodos()
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    approving.value = false
  }
}

onMounted(async () => {
  await loadData()
  // 从右侧待办面板双击跳转时，自动打开处理弹窗
  const taskId = route.query.taskId
  if (taskId) {
    const task = tableData.value.find(t => String(t.taskId) === String(taskId))
    if (task) {
      openProcessDialog(task)
    } else {
      // 列表中未找到，用query参数构造任务对象
      openProcessDialog({
        taskId: Number(taskId),
        tradeId: route.query.tradeId || ''
      })
    }
    // 清除query参数，避免刷新重复弹窗
    router.replace({ path: '/fx/todo' })
  }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">待办任务</span>
      </template>

      <!-- 任务列表表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
        @row-dblclick="handleRowDblClick"
      >
        <el-table-column label="业务编号" width="160" fixed>
          <template #default="{ row }">{{ row?.businessNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="isModifyTask(row)" type="warning" size="small">{{ formatTaskType(row?.taskType) }}</el-tag>
            <span v-else-if="isSpecialLifecycleTask(row)">复核</span>
            <span v-else>{{ formatTaskType(row?.taskType) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="事件内容" width="140">
          <template #default="{ row }">{{ formatEventContent(row) }}</template>
        </el-table-column>
        <el-table-column label="发起人" width="120">
          <template #default="{ row }">{{ row?.makerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="受理人" width="120">
          <template #default="{ row }">{{ row?.assigneeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ row?.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isModifyTask(row)" type="warning" size="small" link @click="openProcessDialog(row)">
              编辑
            </el-button>
            <el-button v-else type="primary" size="small" link @click="openProcessDialog(row)">
              处理
            </el-button>
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

    <!-- 任务处理弹窗（仅用于审批类任务） -->
    <el-dialog
      v-model="processVisible"
      :title="dialogTitle"
      width="900px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <!-- 提前违约任务详情展示 -->
        <template v-if="isEarlyDefaultTask(currentTask) && taskPayload">
          <!-- 原交易信息 -->
          <el-divider content-position="left">原交易信息</el-divider>
          <el-descriptions :column="2" border size="default">
            <el-descriptions-item label="业务编号" :label-style="{ width: '120px' }">{{ taskPayload.originalBusinessNo }}</el-descriptions-item>
            <el-descriptions-item label="客户号" :label-style="{ width: '120px' }">{{ taskPayload.customerId }}</el-descriptions-item>
            <el-descriptions-item label="客户名称" :label-style="{ width: '120px' }">{{ taskPayload.customerName }}</el-descriptions-item>
            <el-descriptions-item label="货币对" :label-style="{ width: '120px' }">{{ taskPayload.currencyPair }}</el-descriptions-item>
            <el-descriptions-item label="原交易金额" :label-style="{ width: '120px' }">{{ taskPayload.originalAmount }}</el-descriptions-item>
            <el-descriptions-item label="原交易汇率" :label-style="{ width: '120px' }">{{ taskPayload.originalCustomerRate }}</el-descriptions-item>
            <el-descriptions-item label="原到期日" :label-style="{ width: '120px' }">{{ taskPayload.originalMaturityDate }}</el-descriptions-item>
          </el-descriptions>

          <!-- 近端交易信息 -->
          <el-divider content-position="left">近端交易信息</el-divider>
          <el-form label-width="120px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="违约金额">
                  <el-input-number
                    :model-value="taskPayload.defaultAmount"
                    :precision="2"
                    :min="0"
                    :controls="false"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="惩罚汇率">
                  <el-input-number
                    :model-value="taskPayload.penaltyRate"
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
                  <el-input :model-value="formatTradeDirection(earlyDefaultNearLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="起息日">
                  <el-input :model-value="earlyDefaultToday" disabled style="width: 100%" />
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
                    :model-value="taskPayload.swapFarLegRate"
                    :precision="4"
                    :controls="false"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.defaultAmount" :precision="2" disabled :controls="false" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="买卖方向">
                  <el-input :model-value="formatTradeDirection(earlyDefaultFarLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="到期日">
                  <el-input :model-value="taskPayload.originalMaturityDate" disabled style="width: 100%" />
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
                    :model-value="taskPayload.spotCustomerRate"
                    :precision="4"
                    :min="0"
                    :controls="false"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.defaultAmount" :precision="2" disabled :controls="false" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="买卖方向">
                  <el-input :model-value="formatTradeDirection(earlyDefaultSpotDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="起息日">
                  <el-input :model-value="earlyDefaultToday" disabled style="width: 100%" />
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
                  <el-input :model-value="taskPayload.nettingCurrency" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="轧差金额">
                  <el-input-number
                    :model-value="taskPayload.nettingAmount"
                    :precision="2"
                    :controls="false"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="轧差账户">
                  <el-input :model-value="taskPayload.nettingAccount" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- 备注信息 -->
          <el-divider content-position="left">备注信息</el-divider>
          <el-form label-width="120px">
            <el-form-item label="备注">
              <el-input :model-value="taskPayload.remark || ''" type="textarea" :rows="3" disabled />
            </el-form-item>
          </el-form>
        </template>

        <!-- 提前交割任务详情展示 -->
        <template v-else-if="isEarlyDeliveryTask(currentTask) && taskPayload">
          <!-- 原交易信息 -->
          <el-divider content-position="left">原交易信息</el-divider>
          <el-descriptions :column="2" border size="default">
            <el-descriptions-item label="业务编号" :label-style="{ width: '120px' }">{{ taskPayload.originalBusinessNo }}</el-descriptions-item>
            <el-descriptions-item label="客户号" :label-style="{ width: '120px' }">{{ taskPayload.customerId }}</el-descriptions-item>
            <el-descriptions-item label="客户名称" :label-style="{ width: '120px' }">{{ taskPayload.customerName }}</el-descriptions-item>
            <el-descriptions-item label="货币对" :label-style="{ width: '120px' }">{{ taskPayload.currencyPair }}</el-descriptions-item>
            <el-descriptions-item label="交易机构" :label-style="{ width: '120px' }">{{ taskPayload.branchName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="原到期日" :label-style="{ width: '120px' }">{{ taskPayload.originalMaturityDate }}</el-descriptions-item>
            <el-descriptions-item label="原交易金额" :label-style="{ width: '120px' }">{{ taskPayload.originalAmount }}</el-descriptions-item>
            <el-descriptions-item label="原交易汇率" :label-style="{ width: '120px' }">{{ taskPayload.originalCustomerRate }}</el-descriptions-item>
          </el-descriptions>

          <!-- 近端交易信息 -->
          <el-divider content-position="left">近端交易信息</el-divider>
          <el-form label-width="120px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="成本汇率">
                  <el-input-number
                    :model-value="taskPayload.nearLegCostRate"
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
                    :model-value="taskPayload.nearLegCustomerRate"
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
                  <el-input :model-value="formatTradeDirection(earlyDeliveryNearLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.originalAmount" :precision="2" disabled :controls="false" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="交易日">
                  <el-input :model-value="earlyDeliveryToday" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="起息日">
                  <el-input :model-value="earlyDeliveryToday" disabled style="width: 100%" />
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
                <el-form-item label="币种1账户">
                  <el-input :model-value="taskPayload.nearLegAccount1" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="币种2账户">
                  <el-input :model-value="taskPayload.nearLegAccount2" disabled style="width: 100%" />
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
                    :model-value="taskPayload.farLegCostRate"
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
                    :model-value="taskPayload.farLegCustomerRate"
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
                  <el-input :model-value="formatTradeDirection(earlyDeliveryFarLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.originalAmount" :precision="2" disabled :controls="false" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="远端到期日">
                  <el-input :model-value="taskPayload.farLegValueDate || taskPayload.originalMaturityDate" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="远端交割方式">
                  <el-input model-value="无需交割" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- 备注信息 -->
          <el-divider content-position="left">备注信息</el-divider>
          <el-form label-width="120px">
            <el-form-item label="备注">
              <el-input :model-value="taskPayload.remark || ''" type="textarea" :rows="3" disabled />
            </el-form-item>
          </el-form>
        </template>

        <!-- 原价展期任务详情展示（全部只读，字段与发起页一致） -->
        <template v-else-if="isRolloverOriginalTask(currentTask) && taskPayload">
          <!-- 原交易信息 -->
          <el-divider content-position="left">原交易信息</el-divider>
          <el-descriptions :column="2" border size="default">
            <el-descriptions-item label="业务编号" :label-style="{ width: '120px' }">{{ taskPayload.originalBusinessNo }}</el-descriptions-item>
            <el-descriptions-item label="客户号" :label-style="{ width: '120px' }">{{ taskPayload.customerId }}</el-descriptions-item>
            <el-descriptions-item label="客户名称" :label-style="{ width: '120px' }">{{ taskPayload.customerName }}</el-descriptions-item>
            <el-descriptions-item label="货币对" :label-style="{ width: '120px' }">{{ taskPayload.currencyPair }}</el-descriptions-item>
            <el-descriptions-item label="交易机构" :label-style="{ width: '120px' }">{{ taskPayload.branchName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="原到期日" :label-style="{ width: '120px' }">{{ taskPayload.originalMaturityDate }}</el-descriptions-item>
          </el-descriptions>

          <!-- 近端交易信息 -->
          <el-divider content-position="left">近端交易信息</el-divider>
          <el-form label-width="120px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="成本汇率">
                  <el-input-number :model-value="taskPayload.nearLegCostRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户汇率">
                  <el-input-number :model-value="taskPayload.nearLegCustomerRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="买卖方向">
                  <el-input :model-value="formatTradeDirection(rolloverNearLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.originalAmount" :precision="2" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="交易日">
                  <el-input :model-value="earlyDeliveryToday" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="起息日">
                  <el-input :model-value="taskPayload.originalMaturityDate" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="近端交割方式">
                  <el-input model-value="无需交割" disabled style="width: 100%" />
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
                  <el-input-number :model-value="taskPayload.farLegCostRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户汇率">
                  <el-input-number :model-value="taskPayload.farLegCustomerRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="买卖方向">
                  <el-input :model-value="formatTradeDirection(rolloverFarLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.farLegAmount" :precision="2" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="分行收益点">
                  <el-input-number :model-value="taskPayload.farLegBranchProfitPoint" :precision="2" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="远端交割方式">
                  <el-input model-value="全额交割" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="远端到期日">
                  <el-input :model-value="taskPayload.newMaturityDate" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="币种1账户">
                  <el-input :model-value="taskPayload.farLegCurrency1Account" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="币种2账户">
                  <el-input :model-value="taskPayload.farLegCurrency2Account" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- 备注信息 -->
          <el-divider content-position="left">备注信息</el-divider>
          <el-form label-width="120px">
            <el-form-item label="备注">
              <el-input :model-value="taskPayload.remark || ''" type="textarea" :rows="3" disabled />
            </el-form-item>
          </el-form>
        </template>

        <!-- 市价展期任务详情展示（全部只读，字段与发起页一致） -->
        <template v-else-if="isRolloverMarketTask(currentTask) && taskPayload">
          <!-- 原交易信息 -->
          <el-divider content-position="left">原交易信息</el-divider>
          <el-descriptions :column="2" border size="default">
            <el-descriptions-item label="业务编号" :label-style="{ width: '120px' }">{{ taskPayload.originalBusinessNo }}</el-descriptions-item>
            <el-descriptions-item label="客户号" :label-style="{ width: '120px' }">{{ taskPayload.customerId }}</el-descriptions-item>
            <el-descriptions-item label="客户名称" :label-style="{ width: '120px' }">{{ taskPayload.customerName }}</el-descriptions-item>
            <el-descriptions-item label="货币对" :label-style="{ width: '120px' }">{{ taskPayload.currencyPair }}</el-descriptions-item>
            <el-descriptions-item label="交易机构" :label-style="{ width: '120px' }">{{ taskPayload.branchName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="原到期日" :label-style="{ width: '120px' }">{{ taskPayload.originalMaturityDate }}</el-descriptions-item>
          </el-descriptions>

          <!-- 近端交易信息（含轧差信息） -->
          <el-divider content-position="left">近端交易信息</el-divider>
          <el-form label-width="120px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="成本汇率">
                  <el-input-number :model-value="taskPayload.nearLegCostRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户汇率">
                  <el-input-number :model-value="taskPayload.nearLegCustomerRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="买卖方向">
                  <el-input :model-value="formatTradeDirection(rolloverNearLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.originalAmount" :precision="2" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="交易日">
                  <el-input :model-value="earlyDeliveryToday" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="起息日">
                  <el-input :model-value="taskPayload.originalMaturityDate" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="近端交割方式">
                  <el-input model-value="差额交割" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户损益">
                  <el-input-number :model-value="taskPayload.customerPnl" :precision="2" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="轧差货币">
                  <el-input :model-value="taskPayload.nettingCurrency" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="轧差账户">
                  <el-input :model-value="taskPayload.nettingAccount" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="轧差金额">
                  <el-input-number :model-value="taskPayload.nettingAmount" :precision="2" :controls="false" disabled style="width: 100%" />
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
                  <el-input-number :model-value="taskPayload.farLegCostRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户汇率">
                  <el-input-number :model-value="taskPayload.farLegCustomerRate" :precision="4" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="买卖方向">
                  <el-input :model-value="formatTradeDirection(rolloverFarLegDirection)" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="金额">
                  <el-input-number :model-value="taskPayload.farLegAmount" :precision="2" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="分行收益点">
                  <el-input-number :model-value="taskPayload.farLegBranchProfitPoint" :precision="2" :controls="false" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="远端交割方式">
                  <el-input model-value="全额交割" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="远端到期日">
                  <el-input :model-value="taskPayload.newMaturityDate" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="币种1账户">
                  <el-input :model-value="taskPayload.farLegCurrency1Account" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="币种2账户">
                  <el-input :model-value="taskPayload.farLegCurrency2Account" disabled style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- 备注信息 -->
          <el-divider content-position="left">备注信息</el-divider>
          <el-form label-width="120px">
            <el-form-item label="备注">
              <el-input :model-value="taskPayload.remark || ''" type="textarea" :rows="3" disabled />
            </el-form-item>
          </el-form>
        </template>

        <!-- 普通交易详情展示 -->
        <template v-else-if="tradeDetail?.master">
          <el-descriptions :column="2" border size="small" title="交易详情">
            <el-descriptions-item label="业务编号">{{ tradeDetail.master.businessNo }}</el-descriptions-item>
            <el-descriptions-item label="交易类型">{{ formatTradeType(tradeDetail.master.tradeType) }}</el-descriptions-item>
            <el-descriptions-item label="特殊交易类型">{{ formatSpecialTradeType(tradeDetail.master.specialTradeType) }}</el-descriptions-item>
            <el-descriptions-item label="货币对">{{ tradeDetail.master.currencyPair }}</el-descriptions-item>
            <el-descriptions-item v-if="tradeDetail.master.tradeType === 'SWAP'" label="掉期类型">{{ formatSwapType(tradeDetail.swapDetail?.swapType) }}</el-descriptions-item>
            <el-descriptions-item v-else label="买卖方向">{{ formatTradeDirection(tradeDetail.master.tradeDirection) }}</el-descriptions-item>
            <el-descriptions-item label="金额">{{ tradeDetail.master.notionalAmount }}</el-descriptions-item>
            <el-descriptions-item label="客户汇率">{{ tradeDetail.master.customerRate }}</el-descriptions-item>
            <el-descriptions-item label="交易日">{{ tradeDetail.master.tradeDate }}</el-descriptions-item>
            <el-descriptions-item label="到期日">{{ tradeDetail.master.maturityDate }}</el-descriptions-item>
            <el-descriptions-item label="交割方式">{{ formatSettlementMethod(tradeDetail.master.settlementMethod) }}</el-descriptions-item>
            <el-descriptions-item label="交易状态">
              <el-tag :type="getStatusTagType(tradeDetail.master.status)" size="small">
                {{ formatTradeStatus(tradeDetail.master.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="客户号">{{ tradeDetail.master.customerId }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ tradeDetail.master.customerName }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <el-empty v-else-if="!detailLoading" description="暂无详情" />

        <!-- 审批表单 -->
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
        <el-button type="warning" :loading="approving" @click="handleApproval('return')">退回</el-button>
        <el-button type="danger" :loading="approving" @click="handleApproval('reject')">拒绝</el-button>
        <el-button type="primary" :loading="approving" @click="handleApproval('approve')">通过</el-button>
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
.pagination-wrapper {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}
</style>
