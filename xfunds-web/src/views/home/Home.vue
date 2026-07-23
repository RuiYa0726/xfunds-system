<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

// 欢迎语
const welcomeText = computed(() => {
  const name = userStore.userInfo.realName || userStore.userInfo.username || '用户'
  return `欢迎，${name}`
})
</script>

<template>
  <div class="home-container">
    <el-card class="welcome-card" shadow="hover">
      <div class="welcome-content">
        <h2>{{ welcomeText }}</h2>
        <p>欢迎使用代客外汇交易系统</p>
        <p class="welcome-tip">请通过左侧菜单导航至各功能模块，中间区域展示实时牌价，右侧展示您的待办任务。</p>
      </div>
    </el-card>
    <el-row :gutter="16" class="dashboard-row">
      <el-col :span="8">
        <el-card shadow="hover" class="dashboard-card">
          <div class="dashboard-item">
            <div class="dashboard-label">即期交易</div>
            <div class="dashboard-value">--</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="dashboard-card">
          <div class="dashboard-item">
            <div class="dashboard-label">远期交易</div>
            <div class="dashboard-value">--</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="dashboard-card">
          <div class="dashboard-item">
            <div class="dashboard-label">期权交易</div>
            <div class="dashboard-value">--</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.home-container {
  padding: 16px;
  height: 100%;
  overflow-y: auto;
}

.welcome-card {
  margin-bottom: 16px;
}

.welcome-content h2 {
  color: #303133;
  margin-bottom: 8px;
}

.welcome-content p {
  color: #606266;
  margin-bottom: 4px;
}

.welcome-tip {
  color: #909399;
  font-size: 13px;
}

.dashboard-row {
  margin-bottom: 16px;
}

.dashboard-card {
  text-align: center;
}

.dashboard-item {
  padding: 20px 0;
}

.dashboard-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.dashboard-value {
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
}
</style>
