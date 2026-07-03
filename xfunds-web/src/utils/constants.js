// 交易状态码 -> 中文标签映射
export const tradeStatusMap = {
  PENDING_CHECK: '待复核',
  PENDING_AUTHORIZE: '待授权',
  PENDING_LIFECYCLE_CHECK: '待生命周期复核',
  ACTIVE: '生效',
  MATURED: '到期',
  SETTLED: '已交割',
  DEFAULTED: '已违约',
  CANCELLED: '已撤销',
  REJECTED: '已拒绝',
  RETURNED: '已退回',
  CLOSED: '已关闭'
}

// 交易类型码 -> 中文标签映射
export const tradeTypeMap = {
  SPOT: '即期',
  FORWARD: '远期',
  SWAP: '掉期',
  OPTION: '期权'
}

// 特殊交易类型码 -> 中文标签映射
export const specialTradeTypeMap = {
  NORMAL: '正常',
  EARLY_DELIVERY: '提前交割',
  EARLY_DEFAULT: '提前违约',
  MATURITY_DEFAULT: '到期违约',
  ROLLOVER_ORIGINAL: '原价展期',
  ROLLOVER_MARKET: '市价展期',
  FULL_DEFAULT: '全部违约'
}

// 任务类型码 -> 中文标签映射
export const taskTypeMap = {
  CHECK: '复核',
  AUTHORIZE: '授权',
  CHECK_LIFECYCLE: '生命周期复核',
  MARGIN_CALL: '追保',
  MATURITY_REMIND: '到期提醒',
  EXERCISE_REMIND: '行权提醒',
  QUOTE_CHECK: '报价复核',
  MODIFY: '修改交易',
  EARLY_DEFAULT: '提前违约',
  EARLY_DELIVERY: '提前交割'
}

// 任务状态码 -> 中文标签映射
export const taskStatusMap = {
  PENDING: '待处理',
  CLAIMED: '已认领',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

// 买卖方向码 -> 中文标签映射
export const tradeDirectionMap = {
  BUY: '买入',
  SELL: '卖出'
}

// 交割方式码 -> 中文标签映射
export const settlementMethodMap = {
  FULL: '全额交割',
  NET: '差额交割',
  NONE: '无需交割'
}

// 期权买卖方向码 -> 中文标签映射
export const optionDirectionMap = {
  BUY: '买入',
  SELL: '卖出'
}

// 期权种类码 -> 中文标签映射（看涨/看跌）
export const optionTypeMap = {
  CALL: '看涨',
  PUT: '看跌'
}

// 期权类别码 -> 中文标签映射（美式/欧式）
export const optionStyleMap = {
  AMERICAN: '美式',
  EUROPEAN: '欧式'
}

// 期权涨跌方向码 -> 中文标签映射
export const priceDirectionMap = {
  UP: '涨',
  DOWN: '跌'
}

// 期权交割方式码 -> 中文标签映射（全额/差额）
export const optionSettlementMethodMap = {
  FULL: '全额',
  NET: '差额'
}

// 期权交割类型码 -> 中文标签映射
export const optionDeliveryTypeMap = {
  T0: 'T+0',
  T1: 'T+1',
  T2: 'T+2'
}

// 根据状态码返回对应的 el-tag 类型，用于表格中状态列着色
export function getStatusTagType(status) {
  const typeMap = {
    PENDING_CHECK: 'warning',
    PENDING_AUTHORIZE: 'warning',
    PENDING_LIFECYCLE_CHECK: 'warning',
    ACTIVE: 'success',
    MATURED: 'primary',
    SETTLED: 'info',
    DEFAULTED: 'danger',
    CANCELLED: 'info',
    REJECTED: 'danger',
    RETURNED: 'warning',
    CLOSED: 'info'
  }
  return typeMap[status] || 'info'
}

// 将状态码转换为中文标签
export function formatTradeStatus(status) {
  return tradeStatusMap[status] || status || '-'
}

// 将交易类型码转换为中文标签
export function formatTradeType(type) {
  return tradeTypeMap[type] || type || '-'
}

// 将特殊交易类型码转换为中文标签
export function formatSpecialTradeType(type) {
  return specialTradeTypeMap[type] || (type === 'NORMAL' || !type ? '正常' : type)
}

// 将任务类型码转换为中文标签
export function formatTaskType(type) {
  return taskTypeMap[type] || type || '-'
}

// 将任务状态码转换为中文标签
export function formatTaskStatus(status) {
  return taskStatusMap[status] || status || '-'
}

// 将买卖方向码转换为中文标签
export function formatTradeDirection(direction) {
  return tradeDirectionMap[direction] || direction || '-'
}

// 将掉期类型码转换为中文标签
export function formatSwapType(swapType) {
  const swapTypeMap = { S_B: 'S/B 近卖远买', B_S: 'B/S 近买远卖' }
  return swapTypeMap[swapType] || swapType || '-'
}

// 将交割方式码转换为中文标签
export function formatSettlementMethod(method) {
  return settlementMethodMap[method] || method || '-'
}

// 将期权买卖方向码转换为中文标签
export function formatOptionDirection(direction) {
  return optionDirectionMap[direction] || direction || '-'
}

// 将期权种类码转换为中文标签（看涨/看跌）
export function formatOptionType(type) {
  return optionTypeMap[type] || type || '-'
}

// 将期权类别码转换为中文标签（美式/欧式）
export function formatOptionStyle(style) {
  return optionStyleMap[style] || style || '-'
}

// 将期权涨跌方向码转换为中文标签
export function formatPriceDirection(direction) {
  return priceDirectionMap[direction] || direction || '-'
}

// 将期权交割方式码转换为中文标签（全额/差额）
export function formatOptionSettlementMethod(method) {
  return optionSettlementMethodMap[method] || method || '-'
}

// 将期权交割类型码转换为中文标签
export function formatOptionDeliveryType(type) {
  return optionDeliveryTypeMap[type] || type || '-'
}
