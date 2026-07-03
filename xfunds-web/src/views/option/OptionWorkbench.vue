<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getOptionTasks,
  getOptionWorkbench,
  viewOriginalTrade,
  executeOption,
  postponeReminder
} from '@/api/option'
import { getTradeDetail, approveTrade, rejectTrade, returnTrade } from '@/api/trade'
import {
  formatTaskType,
  formatTaskStatus,
  formatTradeStatus,
  formatOptionDirection,
  formatPriceDirection,
  formatOptionStyle,
  formatOptionType,
  formatOptionDeliveryType,
  formatOptionSettlementMethod,
  getStatusTagType
} from '@/utils/constants'

// ===== 待办任务列表 =====
const taskData = ref([])
const taskLoading = ref(false)

// ===== 美式期权价内提醒列表 =====
const reminderData = ref([])
const reminderLoading = ref(false)

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

// ===== 执行行权弹窗 =====
const exerciseVisible = ref(false)
const exerciseSubmitting = ref(false)
const exerciseFormRef = ref(null)
const exerciseForm = reactive({
  tradeId: '',
  exerciseDate: '',
  referenceRate: null,
  settlementAccount: '',
  remark: ''
})
const exerciseRules = {
  exerciseDate: [{ required: true, message: '请选择行权日', trigger: 'change' }],
  referenceRate: [
    { required: true, message: '请输入参考汇率', trigger: 'blur' },
    { type: 'number', min: 0.00000001, message: '参考汇率必须大于0', trigger: 'blur' }
  ],
  settlementAccount: [{ required: true, message: '请输入交割账户', trigger: 'blur' }]
}

// ===== 暂不处理弹窗 =====
const postponeVisible = ref(false)
const postponeSubmitting = ref(false)
const postponeFormRef = ref(null)
const postponeForm = reactive({
  tradeId: '',
  remark: ''
})
const postponeRules = {
  remark: [{ required: true, message: '请输入备注', trigger: 'blur' }]
}

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

// 加载美式期权价内提醒列表
async function loadReminders() {
  reminderLoading.value = true
  try {
    const res = await getOptionWorkbench()
    reminderData.value = res.data?.records || res.data?.list || res.data || []
  } catch (e) {
    reminderData.value = []
  } finally {
    reminderLoading.value = false
  }
}

// 打开任务处理弹窗：加载交易详情
async function openProcessDialog(row) {
  currentTask.value = row
  approvalForm.comment = ''
  processVisible.value = true
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
    originalDetail.value = res.data || null
  } catch (e) {
    originalDetail.value = null
  } finally {
    originalLoading.value = false
  }
}

// 打开执行行权弹窗
function openExerciseDialog(row) {
  exerciseForm.tradeId = row.tradeId || row.id
  exerciseForm.exerciseDate = getTodayStr()
  exerciseForm.referenceRate = row.referenceRate ?? null
  exerciseForm.settlementAccount = ''
  exerciseForm.remark = ''
  exerciseVisible.value = true
}

// 提交执行行权
async function handleSubmitExercise() {
  if (!exerciseFormRef.value) return
  await exerciseFormRef.value.validate(async (valid) => {
    if (!valid) return
    exerciseSubmitting.value = true
    try {
      await executeOption({
        tradeId: exerciseForm.tradeId,
        exerciseDate: exerciseForm.exerciseDate,
        referenceRate: exerciseForm.referenceRate,
        settlementAccount: exerciseForm.settlementAccount,
        remark: exerciseForm.remark
      })
      ElMessage.success('执行成功')
      exerciseVisible.value = false
      loadReminders()
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      exerciseSubmitting.value = false
    }
  })
}

// 打开暂不处理弹窗
function openPostponeDialog(row) {
  postponeForm.tradeId = row.tradeId || row.id
  postponeForm.remark = ''
  postponeVisible.value = true
}

// 提交暂不处理
async function handleSubmitPostpone() {
  if (!postponeFormRef.value) return
  await postponeFormRef.value.validate(async (valid) => {
    if (!valid) return
    postponeSubmitting.value = true
    try {
      await postponeReminder({
        tradeId: postponeForm.tradeId,
        remark: postponeForm.remark
      })
      ElMessage.success('已暂不处理')
      postponeVisible.value = false
      loadReminders()
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      postponeSubmitting.value = false
    }
  })
}

onMounted(() => {
  loadTasks()
  loadReminders()
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
        :data="taskData"
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
        <el-table-column prop="businessType" label="事件内容" width="140" />
        <el-table-column prop="makerName" label="发起人" width="120" />
        <el-table-column prop="assigneeId" label="受理人" width="120">
          <template #default="{ row }">{{ row.assigneeId || '待认领' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openProcessDialog(row)">
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 底部：美式期权价内提醒 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="page-title">美式期权价内提醒</span>
          <el-button type="primary" :icon="Refresh" :loading="reminderLoading" @click="loadReminders">
            刷新
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="reminderLoading"
        :data="reminderData"
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
        <el-table-column prop="originalAmount" label="原始签约金额" width="130" />
        <el-table-column prop="closedAmount" label="已平仓金额" width="120" />
        <el-table-column prop="remainingAmount" label="剩余未处理金额" width="130" />
        <el-table-column prop="observationStartDate" label="观察期开始日" width="120" />
        <el-table-column prop="observationEndDate" label="观察期结束日" width="120" />
        <el-table-column prop="tradeDate" label="交易日期" width="110" />
        <el-table-column prop="currency1Amount" label="货币1金额" width="120" />
        <el-table-column prop="currency2Amount" label="货币2金额" width="120" />
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="交割类型" width="100">
          <template #default="{ row }">{{ formatOptionDeliveryType(row.deliveryType) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openOriginalDialog(row)">
              查看原交易
            </el-button>
            <el-button type="success" size="small" link @click="openExerciseDialog(row)">
              执行
            </el-button>
            <el-button type="warning" size="small" link @click="openPostponeDialog(row)">
              暂不处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-button type="warning" :loading="approving" @click="handleApproval('return')">退回</el-button>
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
          <el-descriptions-item label="原始签约金额">{{ originalDetail.notionalAmount }}</el-descriptions-item>
          <el-descriptions-item label="货币1金额">{{ originalDetail.currency1Amount }}</el-descriptions-item>
          <el-descriptions-item label="货币2金额">{{ originalDetail.currency2Amount }}</el-descriptions-item>
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

    <!-- 执行行权弹窗 -->
    <el-dialog
      v-model="exerciseVisible"
      title="执行行权"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="exerciseFormRef"
        :model="exerciseForm"
        :rules="exerciseRules"
        label-width="100px"
      >
        <el-form-item label="行权日" prop="exerciseDate">
          <el-date-picker
            v-model="exerciseForm.exerciseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择行权日"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="参考汇率" prop="referenceRate">
          <el-input-number
            v-model="exerciseForm.referenceRate"
            :precision="8"
            :step="0.0001"
            :min="0"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="交割账户" prop="settlementAccount">
          <el-input v-model="exerciseForm.settlementAccount" placeholder="请输入交割账户" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="exerciseForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="exerciseVisible = false">取消</el-button>
        <el-button type="primary" :loading="exerciseSubmitting" @click="handleSubmitExercise">确定</el-button>
      </template>
    </el-dialog>

    <!-- 暂不处理弹窗 -->
    <el-dialog
      v-model="postponeVisible"
      title="暂不处理"
      width="460px"
      destroy-on-close
    >
      <el-form
        ref="postponeFormRef"
        :model="postponeForm"
        :rules="postponeRules"
        label-width="100px"
      >
        <el-form-item label="备注" prop="remark">
          <el-input v-model="postponeForm.remark" type="textarea" :rows="3" placeholder="请输入暂不处理原因" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="postponeVisible = false">取消</el-button>
        <el-button type="primary" :loading="postponeSubmitting" @click="handleSubmitPostpone">确定</el-button>
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
</style>
