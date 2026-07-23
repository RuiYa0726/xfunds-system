<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, ArrowRight, ArrowLeft } from '@element-plus/icons-vue'
import { getMyTasks } from '@/api/task'
import { formatTaskType, formatTaskStatus, formatTradeType, formatLifecycleOp, lifecycleOpMap } from '@/utils/constants'
import { useAppStore } from '@/store/app'

const router = useRouter()
const appStore = useAppStore()

const isCollapsed = computed(() => appStore.todoCollapsed)

function handleToggle() {
  appStore.toggleTodoCollapse()
}

// 当前用户待办任务列表
const todoList = ref([])
const loading = ref(false)

// 定时器引用
let pollTimer = null

// 加载当前用户待办任务
async function loadTodos() {
  loading.value = true
  try {
    const res = await getMyTasks()
    todoList.value = res.data?.records || res.data?.list || res.data || []
  } catch (e) {
    todoList.value = []
  } finally {
    loading.value = false
  }
}

// 单击待办项跳转待办处理页（期权任务跳转期权复核页，其他跳转外汇待办）
function handleTodoClick(task) {
  if (task.tradeType === 'OPTION') {
    const tradeId = task.tradeId || task.businessId
    const taskId = task.taskId || task.id
    const businessType = task.businessType || ''
    router.push({ path: '/option/review', query: { tradeId, taskId, businessType } })
  } else {
    router.push('/fx/todo')
  }
}

// 双击待办项：携带任务信息跳转到期权复核页或待办处理页
function handleTodoDblClick(task) {
  if (task.tradeType === 'OPTION') {
    const tradeId = task.tradeId || task.businessId
    const taskId = task.taskId || task.id
    const businessType = task.businessType || ''
    router.push({ path: '/option/review', query: { tradeId, taskId, businessType } })
  } else {
    router.push({
      path: '/fx/todo',
      query: {
        taskId: task.taskId || task.id,
        tradeId: task.tradeId || ''
      }
    })
  }
}

onMounted(() => {
  loadTodos()
  // 启动定时轮询（每 5 秒刷新一次）
  pollTimer = setInterval(() => {
    loadTodos()
  }, 5000)
})

// 格式化事件内容：期权生命周期任务显示具体操作（放弃期权/执行期权），期权交易显示欧式/美式
function formatEventContent(task) {
  const bt = task && task.businessType
  // 期权生命周期任务：根据businessType显示具体操作
  if (task && task.taskType === 'CHECK_LIFECYCLE' && bt && lifecycleOpMap[bt]) {
    return formatLifecycleOp(bt)
  }
  // 期权交易：显示欧式期权/美式期权
  if (bt === 'OPTION' || (task && task.tradeType === 'OPTION')) {
    const style = task && task.optionStyle
    if (style === 'EUROPEAN') return '欧式期权'
    if (style === 'AMERICAN') return '美式期权'
    return '期权交易'
  }
  return formatTradeType(bt)
}

// 监听待办任务刷新信号
watch(
  () => appStore.todoRefreshCount,
  () => {
    loadTodos()
  }
)

onUnmounted(() => {
  // 清理定时器
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})

// 暴露刷新方法供父组件调用
defineExpose({ loadTodos })
</script>

<template>
  <div class="todo-panel" :class="{ collapsed: isCollapsed }">
    <!-- 折叠状态下的垂直标签 -->
    <div v-if="isCollapsed" class="todo-collapsed-bar" @click="handleToggle">
      <el-badge :value="todoList.length" :hidden="todoList.length === 0" type="danger" class="collapsed-badge">
        <el-icon size="20"><Bell /></el-icon>
      </el-badge>
      <span class="collapsed-text">待办</span>
      <el-icon size="16"><ArrowLeft /></el-icon>
    </div>

    <!-- 展开状态下的正常面板 -->
    <template v-else>
      <div class="todo-header">
        <span class="todo-title">我的待办</span>
        <div class="header-actions">
          <el-badge :value="todoList.length" :hidden="todoList.length === 0" type="danger">
            <el-icon size="16"><Bell /></el-icon>
          </el-badge>
          <el-icon class="collapse-icon" @click="handleToggle" title="收起">
            <ArrowRight />
          </el-icon>
        </div>
      </div>
      <div v-loading="loading" class="todo-list">
        <div
          v-for="task in todoList"
          :key="task.taskId || task.id"
          class="todo-item"
          @click="handleTodoClick(task)"
          @dblclick="handleTodoDblClick(task)"
        >
          <div class="todo-item-row">
            <span class="todo-business-no">{{ task.businessNo }}</span>
            <el-tag size="small" :type="task.status === 'PENDING' ? 'warning' : 'info'">
              {{ formatTaskStatus(task.status) }}
            </el-tag>
          </div>
          <div class="todo-item-row todo-item-info">
            <span>类型：{{ formatTaskType(task.taskType) }}</span>
            <span>事件：{{ formatEventContent(task) }}</span>
          </div>
          <div class="todo-item-row todo-item-info">
            <span>发起人：{{ task.makerName }}</span>
            <span>受理人：{{ task.assigneeName || '待认领' }}</span>
          </div>
        </div>
        <el-empty v-if="todoList.length === 0 && !loading" description="暂无待办" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.todo-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #e6e6e6;
  transition: width 0.3s;
  overflow: hidden;
}

.todo-panel.collapsed {
  width: 100%;
}

.todo-collapsed-bar {
  width: 40px;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding-top: 20px;
  gap: 12px;
  cursor: pointer;
  background: #f8f9fa;
  border-left: 1px solid #e6e6e6;
  transition: background 0.2s;
}

.todo-collapsed-bar:hover {
  background: #ecf5ff;
}

.collapsed-badge {
  margin-top: 4px;
}

.collapsed-text {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  writing-mode: vertical-rl;
  text-orientation: mixed;
  letter-spacing: 4px;
}

.todo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-icon {
  font-size: 16px;
  cursor: pointer;
  color: #909399;
  transition: color 0.2s;
}

.collapse-icon:hover {
  color: #409eff;
}

.todo-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.todo-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.todo-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fafafa;
  border-radius: 4px;
  border-left: 3px solid #409eff;
  cursor: pointer;
  transition: background 0.2s;
}

.todo-item:hover {
  background: #ecf5ff;
}

.todo-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.todo-business-no {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

.todo-item-info {
  font-size: 12px;
  color: #909399;
}

.todo-item-info span {
  flex: 1;
}
</style>
