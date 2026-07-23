import request from '@/utils/request'

// 期权工作台：查询期权价内提醒列表
export function getOptionWorkbench() {
  return request.get('/option/workbench/reminders')
}

// 期权工作台：查询待办任务列表
export function getOptionTasks() {
  return request.get('/option/workbench/tasks')
}

// 期权工作台：查看原交易详情
export function viewOriginalTrade(tradeId) {
  return request.get(`/option/workbench/original/${tradeId}`)
}

// 发起期权交易
export function createOption(data) {
  return request.post('/option/create', data)
}

// 查询期权交易详情
export function getOptionDetail(tradeId) {
  return request.get(`/option/detail/${tradeId}`)
}

// 查询未到期期权列表
export function listUnmaturedOptions(params) {
  return request.get('/option/unmatured', { params })
}

// 查询欧式到期期权列表
export function listEuropeanMaturedOptions(params) {
  return request.get('/option/european-matured', { params })
}

// 放弃期权
export function abandonOption(data) {
  return request.post('/option/abandon', data)
}

// 期权费交割
export function premiumSettle(data) {
  return request.post('/option/premium-settle', data)
}

// 查询美式期权监控列表
export function listAmericanMonitoring(params) {
  return request.get('/option/american-monitoring', { params })
}

// 查询美式到期期权列表
export function listAmericanMaturedOptions(params) {
  return request.get('/option/american-matured', { params })
}

// 期权交易查询
export function queryOptions(params) {
  return request.get('/option/list', { params })
}

// 期权费交割查询
export function queryPremiumTrades(params) {
  return request.get('/option/premium-list', { params })
}

// 行权交易查询
export function queryExerciseTrades(params) {
  return request.get('/option/exercise-list', { params })
}

// 放弃交易查询
export function queryAbandonTrades(params) {
  return request.get('/option/abandon-list', { params })
}

// 行权期权
export function executeOption(data) {
  return request.post('/option/exercise', data)
}
