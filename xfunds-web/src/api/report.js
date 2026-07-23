import request from '@/utils/request'

/**
 * 报表助手 API
 */

// 获取字段元数据（按分组返回）
export function getReportFields() {
  return request({
    url: '/report/fields',
    method: 'get'
  })
}

// 获取字段码值字典
export function getReportDict() {
  return request({
    url: '/report/dict',
    method: 'get'
  })
}

// 自然语言解析
export function parseNaturalLanguage(query) {
  return request({
    url: '/report/parse',
    method: 'post',
    data: { query }
  })
}

// 执行报表查询
export function queryReport(data) {
  return request({
    url: '/report/query',
    method: 'post',
    data
  })
}

// 导出 Excel（返回 blob）
export function exportReport(data) {
  return request({
    url: '/report/export',
    method: 'post',
    data,
    responseType: 'blob'
  })
}
