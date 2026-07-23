import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例，统一配置 baseURL 与超时时间
const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器：自动携带 token
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('xfunds_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一处理业务码与错误
service.interceptors.response.use(
  (response) => {
    // blob 响应（文件下载）直接放行，由调用方处理
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const res = response.data
    // 后端统一返回结构 { code, message, data }
    if (res.code === 200) {
      return res
    }
    // 401 未授权：清除登录态并跳转登录页
    if (res.code === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('xfunds_token')
      localStorage.removeItem('xfunds_user')
      window.location.href = '/login'
      return Promise.reject(new Error(res.message || '未授权'))
    }
    // 其他业务错误：提示并拒绝
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 层错误处理
    if (error.response && error.response.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('xfunds_token')
      localStorage.removeItem('xfunds_user')
      window.location.href = '/login'
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default service
