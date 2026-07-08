<script setup>
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/app'
import { Money, TrendCharts, Setting } from '@element-plus/icons-vue'
import { markRaw } from 'vue'

const route = useRoute()
const appStore = useAppStore()

// 左侧菜单数据：三大菜单组
const menuGroups = [
  {
    title: '外汇交易管理',
    icon: markRaw(Money),
    items: [
      { name: 'FxWorkbench', path: '/fx/workbench', label: '外汇工作台' },
      { name: 'UnmaturedTrade', path: '/fx/unmatured', label: '未到期交易管理' },
      { name: 'CustomerTradeQuery', path: '/fx/customer-query', label: '客户交易查询' },
      { name: 'TodoList', path: '/fx/todo', label: '待办任务' }
    ]
  },
  {
    title: '期权交易管理',
    icon: markRaw(TrendCharts),
    items: [
      { name: 'OptionWorkbench', path: '/option/workbench', label: '期权工作台' },
      { name: 'OptionTradeEntry', path: '/option/entry', label: '期权交易录入' },
      { name: 'OptionLifecycle', path: '/option/lifecycle', label: '存续期管理' },
      { name: 'OptionQuery', path: '/option/query', label: '期权交易查询' }
    ]
  },
  {
    title: '公共管理',
    icon: markRaw(Setting),
    items: [
      { name: 'SysParam', path: '/system/param', label: '系统参数管理' },
      { name: 'CustomerManage', path: '/system/customer', label: '客户管理' },
      { name: 'UserManage', path: '/system/user', label: '登录用户管理' },
      { name: 'ScheduledTask', path: '/system/task', label: '定时任务' }
    ]
  }
]
</script>

<template>
  <div class="side-menu" :class="{ collapsed: appStore.collapsed }" v-show="!appStore.hidden">
    <el-menu
      :default-active="route.path"
      :collapse="appStore.collapsed"
      :collapse-transition="false"
      router
      class="side-menu-el"
    >
      <template v-for="group in menuGroups" :key="group.title">
        <el-sub-menu :index="group.title">
          <template #title>
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item
            v-for="item in group.items"
            :key="item.name"
            :index="item.path"
          >
            {{ item.label }}
          </el-menu-item>
        </el-sub-menu>
      </template>
    </el-menu>
  </div>
</template>

<style scoped>
.side-menu {
  height: 100%;
  background: #fff;
  border-right: 1px solid #e6e6e6;
  overflow-y: auto;
  overflow-x: hidden;
}

.side-menu-el {
  border-right: none;
}

.side-menu-el:not(.el-menu--collapse) {
  width: 240px;
}
</style>
