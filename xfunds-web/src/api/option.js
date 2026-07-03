import request from '@/utils/request'

// 期权工作台：查询美式期权价内提醒列表
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

// 期权工作台：执行行权
export function executeOption(data) {
  return request.post('/option/workbench/execute', data)
}

// 期权工作台：暂不处理（推迟提醒）
export function postponeReminder(data) {
  return request.post('/option/workbench/postpone', data)
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

// 平仓期权
export function closeOption(data) {
  return request.post('/option/close', data)
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

// 平仓交易查询
export function queryCloseTrades(params) {
  return request.get('/option/close-list', { params })
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

// 获取期权参数列表
export function getOptionParams() {
  return request.get('/option/param/list')
}

// 保存期权参数
export function saveOptionParam(data) {
  return request.post('/option/param/save', data)
}
