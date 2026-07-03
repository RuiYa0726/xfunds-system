import { defineStore } from 'pinia'
import { ref } from 'vue'

// 应用状态管理：侧边栏折叠状态、待办面板折叠状态
export const useAppStore = defineStore('app', () => {
  // 侧边栏是否折叠（半折叠，显示图标）
  const collapsed = ref(false)
  // 侧边栏是否完全隐藏
  const hidden = ref(false)
  // 待办面板是否折叠
  const todoCollapsed = ref(false)
  // 待办任务刷新计数（用于触发 TodoPanel 刷新）
  const todoRefreshCount = ref(0)

  // 切换侧边栏折叠状态
  function toggleCollapse() {
    collapsed.value = !collapsed.value
  }

  // 切换侧边栏完全隐藏状态
  function toggleHidden() {
    hidden.value = !hidden.value
  }

  // 切换待办面板折叠状态
  function toggleTodoCollapse() {
    todoCollapsed.value = !todoCollapsed.value
  }

  // 刷新待办任务
  function refreshTodos() {
    todoRefreshCount.value += 1
  }

  return {
    collapsed,
    hidden,
    todoCollapsed,
    todoRefreshCount,
    toggleCollapse,
    toggleHidden,
    toggleTodoCollapse,
    refreshTodos
  }
})
