<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Fold, Expand, Bell, ArrowDown, ArrowLeft, ArrowRight, Menu } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import SideMenu from './components/SideMenu.vue'
import QuotePanel from './components/QuotePanel.vue'
import TodoPanel from './components/TodoPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// 当前是否处于首页（首页时中间区显示牌价面板，其他页显示路由视图）
const isHome = computed(() => route.path === '/home')

// 侧边栏宽度：隐藏 0px，折叠 64px，展开 240px
const sideWidth = computed(() => {
  if (appStore.hidden) return '0px'
  return appStore.collapsed ? '64px' : '240px'
})

// 待办面板宽度：折叠 40px，展开 320px
const todoWidth = computed(() => (appStore.todoCollapsed ? '40px' : '320px'))

// 退出登录确认
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (e) {
    // 用户取消
  }
}

// 页面加载时获取用户信息（刷新页面时恢复用户信息）
onMounted(() => {
  if (userStore.token && !userStore.userInfo.userId) {
    userStore.fetchUserInfo().catch(() => {
      // 获取用户信息失败，可能是token过期，跳转到登录页
      router.push('/login')
    })
  }
})
</script>

<template>
  <div class="layout-container">
    <!-- 顶部头部 -->
    <header class="layout-header">
      <div class="header-left">
        <el-icon class="collapse-btn" @click="appStore.toggleCollapse">
          <Fold v-if="!appStore.collapsed" />
          <Expand v-else />
        </el-icon>
        <span class="logo-text">代客外汇交易系统</span>
      </div>
      <div class="header-right">
        <el-icon class="header-icon"><Bell /></el-icon>
        <el-dropdown @command="(cmd) => cmd === 'logout' && handleLogout()">
          <span class="user-info">
            {{ userStore.userInfo.realName || userStore.userInfo.username || '用户' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="user-info-panel">
                <div class="user-info-row">
                  <span class="user-info-label">所属机构</span>
                  <span class="user-info-value">{{ userStore.userInfo.orgName || userStore.userInfo.orgCode || '-' }}</span>
                </div>
                <div class="user-info-row">
                  <span class="user-info-label">角色权限</span>
                  <span class="user-info-value">{{ (userStore.userInfo.roleNames && userStore.userInfo.roleNames.length) ? userStore.userInfo.roleNames.join('、') : '-' }}</span>
                </div>
              </div>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 主体三栏布局 -->
    <div class="layout-body">
      <!-- 左侧菜单栏 -->
      <aside class="layout-aside" :style="{ width: sideWidth }">
        <SideMenu />
      </aside>

      <!-- 侧边栏操作按钮区域 -->
      <div class="aside-controls" :style="{ left: sideWidth }">
        <div v-if="!appStore.hidden" class="aside-actions">
          <div class="action-btn" @click="appStore.toggleCollapse" :title="appStore.collapsed ? '展开菜单' : '折叠菜单'">
            <el-icon><Expand v-if="appStore.collapsed" /><Fold v-else /></el-icon>
            <span>{{ appStore.collapsed ? '展开' : '折叠' }}</span>
          </div>
          <div class="action-btn" @click="appStore.toggleHidden" :title="appStore.hidden ? '显示菜单' : '收起菜单'">
            <el-icon><ArrowLeft /></el-icon>
            <span>收起</span>
          </div>
        </div>
        <div v-else class="aside-show-btn" @click="appStore.toggleHidden" title="显示菜单">
          <el-icon size="16"><ArrowRight /></el-icon>
          <span>拉出</span>
        </div>
      </div>

      <!-- 中间内容区：首页显示牌价面板，其他页显示路由视图 -->
      <main class="layout-main">
        <QuotePanel v-if="isHome" />
        <router-view v-else />
      </main>

      <!-- 右侧待办面板 -->
      <aside class="layout-todo" :style="{ width: todoWidth }">
        <TodoPanel />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.layout-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 顶部头部 */
.layout-header {
  height: 60px;
  background: #1a2a6c;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-icon {
  font-size: 18px;
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #fff;
}

/* 用户信息悬浮面板 */
.user-info-panel {
  padding: 8px 16px;
  min-width: 200px;
}
.user-info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.user-info-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}
.user-info-value {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

/* 主体三栏 */
.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.layout-aside {
  flex-shrink: 0;
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.aside-controls {
  position: fixed;
  bottom: 20px;
  transition: left 0.3s;
  z-index: 100;
}

.aside-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e6e6e6;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  cursor: pointer;
  color: #606266;
  padding: 6px 10px;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}

.action-btn:hover {
  background: #e6e6e6;
  color: #1a2a6c;
}

.aside-show-btn {
  width: 50px;
  height: 80px;
  background: #1a2a6c;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  border-radius: 0 8px 8px 0;
  transition: background 0.2s;
  font-size: 13px;
}

.aside-show-btn:hover {
  background: #2d3f8a;
}

.layout-main {
  flex: 1;
  overflow: auto;
  background: #f0f2f5;
}

.layout-todo {
  width: 320px;
  flex-shrink: 0;
  overflow: hidden;
  transition: width 0.3s;
}
</style>
