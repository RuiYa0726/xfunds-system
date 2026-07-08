import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getUserInfo, logout as logoutApi } from '@/api/auth'

// 用户状态管理：token、用户信息、登录登出
export const useUserStore = defineStore('user', () => {
  // 从 localStorage 恢复 token，实现持久化
  const token = ref(localStorage.getItem('xfunds_token') || '')
  // 用户信息：userId、username、realName、orgCode、roles
  const userInfo = ref(
    JSON.parse(localStorage.getItem('xfunds_user') || 'null') || {
      userId: '',
      username: '',
      realName: '',
      orgCode: '',
      orgName: '',
      roles: [],
      roleNames: []
    }
  )

  // 设置 token 并持久化
  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('xfunds_token', newToken)
  }

  // 设置用户信息并持久化
  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('xfunds_user', JSON.stringify(info))
  }

  // 登录：调用接口获取 token，然后获取用户信息，存储登录态
  async function login(loginForm) {
    const res = await loginApi(loginForm)
    setToken(res.data.token)
    // 登录成功后立即获取用户信息
    await fetchUserInfo()
    return res
  }

  // 拉取并存储当前用户信息
  async function fetchUserInfo() {
    const res = await getUserInfo()
    setUserInfo(res.data)
    return res.data
  }

  // 退出登录：调用接口并清除本地登录态
  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // 即使接口失败也清除本地态
    }
    token.value = ''
    userInfo.value = { userId: '', username: '', realName: '', orgCode: '', orgName: '', roles: [], roleNames: [] }
    localStorage.removeItem('xfunds_token')
    localStorage.removeItem('xfunds_user')
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    login,
    fetchUserInfo,
    logout
  }
})
