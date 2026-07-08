import { createRouter, createWebHistory } from 'vue-router'

// 路由表定义
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/Home.vue'),
        meta: { title: '首页' }
      },
      // 外汇交易管理 - 牌价
      {
        path: 'fx/workbench',
        name: 'FxWorkbench',
        component: () => import('@/views/fx/FxWorkbench.vue'),
        meta: { title: '外汇工作台' }
      },
      // 外汇交易管理 - 交易录入
      {
        path: 'fx/spot-entry',
        name: 'SpotTradeEntry',
        component: () => import('@/views/fx/SpotTradeEntry.vue'),
        meta: { title: '即期交易录入' }
      },
      {
        path: 'fx/forward-entry',
        name: 'ForwardTradeEntry',
        component: () => import('@/views/fx/ForwardTradeEntry.vue'),
        meta: { title: '远期交易录入' }
      },
      {
        path: 'fx/swap-entry',
        name: 'SwapTradeEntry',
        component: () => import('@/views/fx/SwapTradeEntry.vue'),
        meta: { title: '掉期交易录入' }
      },
      // 外汇交易管理 - 交易管理
      {
        path: 'fx/unmatured',
        name: 'UnmaturedTrade',
        component: () => import('@/views/fx/UnmaturedTrade.vue'),
        meta: { title: '未到期交易管理' }
      },
      {
          path: 'fx/early-default',
          name: 'EarlyDefault',
          component: () => import('@/views/fx/EarlyDefaultPage.vue'),
          meta: { title: '提前违约' }
        },
        {
          path: 'fx/early-delivery',
          name: 'EarlyDelivery',
          component: () => import('@/views/fx/EarlyDeliveryPage.vue'),
          meta: { title: '提前交割' }
        },
        {
          path: 'fx/swap-full-default',
          name: 'SwapFullDefault',
          component: () => import('@/views/fx/SwapFullDefaultPage.vue'),
          meta: { title: '掉期全部违约' }
        },
        {
          path: 'fx/rollover',
          name: 'Rollover',
          component: () => import('@/views/fx/RolloverPage.vue'),
          meta: { title: '远期展期' }
        },
      {
        path: 'fx/customer-query',
        name: 'CustomerTradeQuery',
        component: () => import('@/views/fx/CustomerTradeQuery.vue'),
        meta: { title: '客户交易查询' }
      },
      {
        path: 'fx/todo',
        name: 'TodoList',
        component: () => import('@/views/fx/TodoList.vue'),
        meta: { title: '待办任务' }
      },
      // 期权交易管理
      {
        path: 'option/workbench',
        name: 'OptionWorkbench',
        component: () => import('@/views/option/OptionWorkbench.vue'),
        meta: { title: '期权工作台' }
      },
      {
        path: 'option/entry',
        name: 'OptionTradeEntry',
        component: () => import('@/views/option/OptionTradeEntry.vue'),
        meta: { title: '期权交易录入' }
      },
      {
        path: 'option/review',
        name: 'OptionTradeReview',
        component: () => import('@/views/option/OptionTradeReview.vue'),
        meta: { title: '期权交易复核' }
      },
      {
        path: 'option/lifecycle',
        name: 'OptionLifecycle',
        component: () => import('@/views/option/OptionLifecycle.vue'),
        meta: { title: '期权存续期管理' }
      },
      {
        path: 'option/query',
        name: 'OptionQuery',
        component: () => import('@/views/option/OptionQuery.vue'),
        meta: { title: '期权交易查询' }
      },
      // 公共管理
      {
        path: 'system/param',
        name: 'SysParam',
        component: () => import('@/views/system/SysParam.vue'),
        meta: { title: '系统参数管理' }
      },
      {
        path: 'system/customer',
        name: 'CustomerManage',
        component: () => import('@/views/system/CustomerManage.vue'),
        meta: { title: '客户管理' }
      },
      {
        path: 'system/user',
        name: 'UserManage',
        component: () => import('@/views/system/UserManage.vue'),
        meta: { title: '登录用户管理' }
      },
      {
        path: 'system/task',
        name: 'ScheduledTask',
        component: () => import('@/views/system/ScheduledTask.vue'),
        meta: { title: '定时任务' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：未登录且访问受保护页面时跳转登录
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('xfunds_token')
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    // 已登录访问登录页则跳转首页
    next('/home')
  } else {
    next()
  }
})

export default router
