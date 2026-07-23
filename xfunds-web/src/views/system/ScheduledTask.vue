<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, VideoPlay, ArrowLeft, View } from '@element-plus/icons-vue'
import {
  getMaturitySettlementInfo,
  runMaturitySettlement,
  getQuoteRefreshInfo,
  runQuoteRefresh,
  getCnyBalanceRefreshInfo,
  runCnyBalanceRefresh,
  getJobLogs,
  getJobLogDetails
} from '@/api/scheduledJob'
import { formatTradeType } from '@/utils/constants'

// 当前查看的任务：null=列表页，'MATURITY_SETTLEMENT'=到期交割详情，'QUOTE_REFRESH'=牌价刷新详情，'CNY_BALANCE_REFRESH'=折人民币余额刷新详情
const currentJob = ref(null)

// 列表页：任务信息
const maturityInfo = ref(null)
const quoteInfo = ref(null)
const cnyBalanceInfo = ref(null)
const listLoading = ref(false)

// 详情页：任务信息
const info = ref(null)
const infoLoading = ref(false)
const running = ref(false)

// 详情页：执行历史
const logs = ref([])
const total = ref(0)
const logLoading = ref(false)
const query = reactive({
  jobName: '',
  pageNum: 1,
  pageSize: 10
})

// 执行明细弹窗
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailList = ref([])
const detailTitle = ref('')

// 状态标签类型映射
function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function statusText(status) {
  if (status === 'SUCCESS') return '全部成功'
  if (status === 'PARTIAL') return '部分失败'
  if (status === 'FAILED') return '异常'
  return status || '-'
}

function triggerTagType(type) {
  return type === 'AUTO' ? 'info' : 'warning'
}

function triggerText(type) {
  return type === 'AUTO' ? '自动' : '手动'
}

function operatorText(row) {
  if (row?.triggerType === 'AUTO') return '自动执行'
  if (row?.operatorName) return row.operatorName
  if (row?.operatorId != null) return String(row.operatorId)
  return '-'
}

function detailResultTagType(result) {
  return result === 'SUCCESS' ? 'success' : 'danger'
}

function detailResultText(result) {
  if (result === 'SUCCESS') return '成功'
  if (result === 'FAIL') return '失败'
  return result || '-'
}

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ')
}

// ===== 列表页 =====
async function loadList() {
  listLoading.value = true
  try {
    const [mRes, qRes, cRes] = await Promise.all([
      getMaturitySettlementInfo(),
      getQuoteRefreshInfo(),
      getCnyBalanceRefreshInfo()
    ])
    maturityInfo.value = mRes.data || null
    quoteInfo.value = qRes.data || null
    cnyBalanceInfo.value = cRes.data || null
  } catch (e) {
    maturityInfo.value = null
    quoteInfo.value = null
    cnyBalanceInfo.value = null
  } finally {
    listLoading.value = false
  }
}

// 点击任务卡片进入详情
function handleViewDetail(jobName) {
  currentJob.value = jobName
  query.jobName = jobName
  query.pageNum = 1
  loadInfo()
  loadLogs()
}

// 返回列表
function handleBack() {
  currentJob.value = null
  // 刷新列表数据
  loadList()
}

// ===== 详情页 =====
const nextRunTimeText = computed(() => fmt(info.value?.nextRunTime))

async function loadInfo() {
  infoLoading.value = true
  try {
    let res
    if (currentJob.value === 'MATURITY_SETTLEMENT') {
      res = await getMaturitySettlementInfo()
    } else if (currentJob.value === 'QUOTE_REFRESH') {
      res = await getQuoteRefreshInfo()
    } else {
      res = await getCnyBalanceRefreshInfo()
    }
    info.value = res.data || null
  } catch (e) {
    info.value = null
  } finally {
    infoLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const res = await getJobLogs(query)
    const data = res.data || {}
    logs.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    logs.value = []
    total.value = 0
  } finally {
    logLoading.value = false
  }
}

async function handleRun() {
  const isMaturity = currentJob.value === 'MATURITY_SETTLEMENT'
  const isCny = currentJob.value === 'CNY_BALANCE_REFRESH'
  const confirmMsg = isMaturity
    ? '将立即检索当天到期交易并执行交割（扣减账户余额、退还/扣除保证金）。是否继续？'
    : isCny
      ? '将立即依据当日即期汇率重新计算所有客户账户的折人民币余额。是否继续？'
      : '将立即模拟生成新的外汇牌价并更新牌价展示，旧牌价将自动失效。是否继续？'
  try {
    await ElMessageBox.confirm(confirmMsg, '手动执行确认', {
      confirmButtonText: '执行',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  running.value = true
  try {
    let res
    if (isMaturity) {
      res = await runMaturitySettlement()
    } else if (isCny) {
      res = await runCnyBalanceRefresh()
    } else {
      res = await runQuoteRefresh()
    }
    const d = res.data || {}
    ElMessage.success(
      isMaturity
        ? `执行完成：共处理 ${d.totalCount ?? 0} 笔，成功 ${d.successCount ?? 0} 笔，失败 ${d.failCount ?? 0} 笔`
        : isCny
          ? `执行完成：更新账户 ${d.totalCount ?? 0} 个，状态${d.status === 'SUCCESS' ? '成功' : '失败'}，耗时 ${d.durationMs ?? 0} 毫秒`
          : `执行完成：牌价刷新${d.status === 'SUCCESS' ? '成功' : '失败'}，耗时 ${d.durationMs ?? 0} 毫秒`
    )
    await loadInfo()
    query.pageNum = 1
    await loadLogs()
  } catch (e) {
    // 错误信息已由请求拦截器统一提示
  } finally {
    running.value = false
  }
}

async function handleViewDetails(row) {
  const logId = row?.logId
  if (!logId) {
    ElMessage.warning('未获取到执行日志ID')
    return
  }
  detailTitle.value = `执行情况 - ${fmt(row.runTime)}（${triggerText(row.triggerType)}）`
  detailVisible.value = true
  detailLoading.value = true
  detailList.value = []
  try {
    const res = await getJobLogDetails(logId)
    detailList.value = res.data || []
  } catch (e) {
    detailList.value = []
  } finally {
    detailLoading.value = false
  }
}

function handlePageChange(p) {
  query.pageNum = p
  loadLogs()
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="page-container">
    <!-- ===== 列表页：定时任务列表 ===== -->
    <template v-if="!currentJob">
      <el-card shadow="never">
        <template #header>
          <span class="page-title">定时任务</span>
        </template>

        <el-row :gutter="16" v-loading="listLoading">
          <!-- 到期交割定时任务 -->
          <el-col :span="8">
            <el-card shadow="hover" class="task-card" @click="handleViewDetail('MATURITY_SETTLEMENT')">
              <div class="task-card-header">
                <span class="task-name">到期交割定时任务</span>
                <el-tag size="small" type="success">已启用</el-tag>
              </div>
              <el-descriptions :column="1" border size="small" class="task-desc">
                <el-descriptions-item label="执行频率">
                  {{ maturityInfo?.scheduleDesc || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="Cron 表达式">
                  <el-tag size="small" type="info">{{ maturityInfo?.cronExpression || '-' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="下次执行">
                  {{ fmt(maturityInfo?.nextRunTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="最近状态">
                  <el-tag v-if="maturityInfo?.latestRunStatus" size="small" :type="statusTagType(maturityInfo.latestRunStatus)">
                    {{ statusText(maturityInfo.latestRunStatus) }}
                  </el-tag>
                  <span v-else>-</span>
                </el-descriptions-item>
              </el-descriptions>
              <div class="task-card-footer">
                <span class="task-desc-text">检索当天到期的交易并执行交割</span>
                <el-button type="primary" size="small" :icon="View">查看详情</el-button>
              </div>
            </el-card>
          </el-col>

          <!-- 获取牌价定时任务 -->
          <el-col :span="8">
            <el-card shadow="hover" class="task-card" @click="handleViewDetail('QUOTE_REFRESH')">
              <div class="task-card-header">
                <span class="task-name">获取牌价定时任务</span>
                <el-tag size="small" type="success">已启用</el-tag>
              </div>
              <el-descriptions :column="1" border size="small" class="task-desc">
                <el-descriptions-item label="执行频率">
                  {{ quoteInfo?.scheduleDesc || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="Cron 表达式">
                  <el-tag size="small" type="info">{{ quoteInfo?.cronExpression || '-' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="下次执行">
                  {{ fmt(quoteInfo?.nextRunTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="最近状态">
                  <el-tag v-if="quoteInfo?.latestRunStatus" size="small" :type="statusTagType(quoteInfo.latestRunStatus)">
                    {{ statusText(quoteInfo.latestRunStatus) }}
                  </el-tag>
                  <span v-else>-</span>
                </el-descriptions-item>
              </el-descriptions>
              <div class="task-card-footer">
                <span class="task-desc-text">每小时模拟生成新的外汇牌价</span>
                <el-button type="primary" size="small" :icon="View">查看详情</el-button>
              </div>
            </el-card>
          </el-col>

          <!-- 折人民币余额刷新定时任务 -->
          <el-col :span="8">
            <el-card shadow="hover" class="task-card" @click="handleViewDetail('CNY_BALANCE_REFRESH')">
              <div class="task-card-header">
                <span class="task-name">折人民币余额刷新任务</span>
                <el-tag size="small" type="success">已启用</el-tag>
              </div>
              <el-descriptions :column="1" border size="small" class="task-desc">
                <el-descriptions-item label="执行频率">
                  {{ cnyBalanceInfo?.scheduleDesc || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="Cron 表达式">
                  <el-tag size="small" type="info">{{ cnyBalanceInfo?.cronExpression || '-' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="下次执行">
                  {{ fmt(cnyBalanceInfo?.nextRunTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="最近状态">
                  <el-tag v-if="cnyBalanceInfo?.latestRunStatus" size="small" :type="statusTagType(cnyBalanceInfo.latestRunStatus)">
                    {{ statusText(cnyBalanceInfo.latestRunStatus) }}
                  </el-tag>
                  <span v-else>-</span>
                </el-descriptions-item>
              </el-descriptions>
              <div class="task-card-footer">
                <span class="task-desc-text">按当日即期汇率刷新账户折人民币余额</span>
                <el-button type="primary" size="small" :icon="View">查看详情</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <!-- ===== 详情页 ===== -->
    <template v-else>
      <!-- 任务信息卡片 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-header">
            <div class="header-left">
              <el-button :icon="ArrowLeft" size="small" @click="handleBack">返回</el-button>
              <span class="page-title">
                {{ currentJob === 'MATURITY_SETTLEMENT' ? '到期交割定时任务' : currentJob === 'QUOTE_REFRESH' ? '获取牌价定时任务' : '折人民币余额刷新任务' }}
              </span>
            </div>
            <el-button
              type="primary"
              :icon="VideoPlay"
              :loading="running"
              @click="handleRun"
            >
              手动执行
            </el-button>
          </div>
        </template>

        <el-descriptions v-loading="infoLoading" :column="2" border size="small">
          <el-descriptions-item label="任务名称">
            {{ info?.jobName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="执行频率">
            {{ info?.scheduleDesc || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="Cron 表达式">
            <el-tag size="small" type="info">{{ info?.cronExpression || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="是否启用">
            <el-tag size="small" :type="info?.enabled ? 'success' : 'info'">
              {{ info?.enabled ? '已启用' : '未启用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下次执行时间">
            {{ nextRunTimeText }}
          </el-descriptions-item>
          <el-descriptions-item label="最近执行状态">
            <el-tag
              v-if="info?.latestRunStatus"
              size="small"
              :type="statusTagType(info.latestRunStatus)"
            >
              {{ statusText(info.latestRunStatus) }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="最近执行时间">
            {{ fmt(info?.latestRunTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="最近执行摘要">
            {{ info?.latestRunSummary || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="任务说明" :span="2">
            {{ info?.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 执行历史卡片 -->
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="page-title">执行历史</span>
            <el-button :icon="Refresh" :loading="logLoading" @click="loadLogs">
              刷新
            </el-button>
          </div>
        </template>

        <el-table
          v-loading="logLoading"
          :data="logs"
          border
          stripe
          size="small"
          style="width: 100%"
        >
          <el-table-column label="操作" width="120" fixed="left">
            <template #default="{ row }">
              <el-button
                v-if="currentJob === 'MATURITY_SETTLEMENT'"
                type="primary"
                size="small"
                link
                @click="handleViewDetails(row)"
              >
                查看执行情况
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="runTime" label="执行时间" width="170">
            <template #default="{ row }">{{ fmt(row.runTime) }}</template>
          </el-table-column>
          <el-table-column prop="triggerType" label="触发方式" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="triggerTagType(row.triggerType)">
                {{ triggerText(row.triggerType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalCount" label="处理数" width="80" align="right" />
          <el-table-column prop="successCount" label="成功数" width="80" align="right" />
          <el-table-column prop="failCount" label="失败数" width="80" align="right" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
          <el-table-column label="操作人" width="120">
            <template #default="{ row }">{{ operatorText(row) }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            background
            layout="total, prev, pager, next, jumper"
            :total="total"
            :page-size="query.pageSize"
            :current-page="query.pageNum"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

      <!-- 执行情况明细弹窗 -->
      <el-dialog
        v-model="detailVisible"
        :title="detailTitle"
        width="1100px"
        destroy-on-close
      >
        <el-table
          v-loading="detailLoading"
          :data="detailList"
          border
          stripe
          size="small"
          style="width: 100%"
          max-height="480"
        >
          <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
          <el-table-column label="交易类型" width="90">
            <template #default="{ row }">{{ formatTradeType(row.tradeType) }}</template>
          </el-table-column>
          <el-table-column prop="settleAccount" label="交易账户" width="160" />
          <el-table-column prop="settleAmount" label="交易金额" width="140" align="right" />
          <el-table-column prop="marginAccount" label="保证金账户" width="160" />
          <el-table-column prop="marginAmount" label="保证金金额" width="140" align="right" />
          <el-table-column label="执行结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="detailResultTagType(row.result)">
                {{ detailResultText(row.result) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!detailLoading && detailList.length === 0" description="本次执行无明细记录" />
        <template #footer>
          <el-button @click="detailVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </template>
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
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.task-card {
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.task-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
}
.task-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.task-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.task-desc {
  margin-bottom: 12px;
}
.task-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.task-desc-text {
  font-size: 12px;
  color: #909399;
}
</style>
