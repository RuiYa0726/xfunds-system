// 交易状态码 -> 中文标签映射
export const tradeStatusMap = {
  DRAFT: '草稿',
  PENDING_CHECK: '待复核',
  PENDING_AUTHORIZE: '待授权',
  PENDING_LIFECYCLE_CHECK: '待生命周期复核',
  ACTIVE: '生效',
  MATURED: '到期',
  SETTLED: '已交割',
  SETTLE_FAILED: '交割失败',
  DEFAULTED: '已违约',
  CLOSED: '已平仓',
  REJECTED: '已拒绝',
  ROLLED_OVER: '已展期',
  EARLY_SETTLED: '提前交割',
  EARLY_DEFAULTED: '提前违约',
  EXERCISED: '已行权',
  ABANDONED: '已放弃',
  PREMIUM_SETTLED: '期权费已结清',
  CANCELLED: '已撤销',
  RETURNED: '已退回'
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
  EARLY_DELIVERY: '提前交割',
  ROLLOVER_ORIGINAL: '原价展期',
  ROLLOVER_MARKET: '市价展期'
}

// 任务状态码 -> 中文标签映射
export const taskStatusMap = {
  PENDING: '待处理',
  CLAIMED: '已认领',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

// 期权生命周期操作类型 -> 中文标签映射（用于 CHECK_LIFECYCLE 任务的 businessType）
export const lifecycleOpMap = {
  ABANDON: '放弃期权',
  EXERCISE: '执行期权',
  PREMIUM_SETTLE: '期权费交割'
}

// 生命周期事件类型码 -> 中文标签映射
export const eventTypeMap = {
  MAKE: '录入',
  CHECK: '复核',
  AUTHORIZE: '授权',
  SUBMIT: '提交',
  EARLY_DELIVERY: '提前交割',
  EARLY_DEFAULT: '提前违约',
  EARLY_DEFAULT_GEN: '提前违约生成',
  ROLLOVER_ORIGINAL: '原价展期',
  ROLLOVER_MARKET: '市价展期',
  MARGIN_SUPPLEMENT: '追保',
  FULL_DEFAULT: '全部违约',
  SCHEDULED_SETTLE: '定时交割',
  SCHEDULED_SETTLE_FAIL: '定时交割失败',
  EXERCISE: '行权',
  EXERCISE_NET: '行权差额交割',
  POSTPONE: '推迟',
  CLOSE: '平仓',
  ABANDON: '放弃',
  PREMIUM_SETTLE: '期权费交割'
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
  SELL: '卖出',
  BUYER: '买入',
  SELLER: '卖出'
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
    DRAFT: 'info',
    PENDING_CHECK: 'warning',
    PENDING_AUTHORIZE: 'warning',
    PENDING_LIFECYCLE_CHECK: 'warning',
    ACTIVE: 'success',
    MATURED: 'primary',
    SETTLED: 'info',
    SETTLE_FAILED: 'danger',
    DEFAULTED: 'danger',
    CLOSED: 'info',
    REJECTED: 'danger',
    ROLLED_OVER: 'primary',
    EARLY_SETTLED: 'success',
    EARLY_DEFAULTED: 'danger',
    EXERCISED: 'success',
    ABANDONED: 'info',
    PREMIUM_SETTLED: 'info',
    CANCELLED: 'info',
    RETURNED: 'warning'
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

// 将生命周期事件类型码转换为中文标签
export function formatEventType(type) {
  return eventTypeMap[type] || type || '-'
}

// 将期权生命周期操作类型码转换为中文标签
export function formatLifecycleOp(type) {
  return lifecycleOpMap[type] || ''
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

/**
 * 计算掉期期限：远端到期日 - 近端起息日的天数
 * 1天→ON, 2天→TN, 3天→SN, 7天→SW, 28-31天→1M, 其他→非标准
 */
export function calcSwapTerm(nearLegValueDate, farLegValueDate) {
  if (!nearLegValueDate || !farLegValueDate) return '-'
  const near = new Date(nearLegValueDate)
  const far = new Date(farLegValueDate)
  const days = Math.round((far - near) / (1000 * 60 * 60 * 24))
  if (days === 1) return 'ON'
  if (days === 2) return 'TN'
  if (days === 3) return 'SN'
  if (days === 7) return 'SW'
  if (days >= 28 && days <= 31) return '1M'
  return '非标准'
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
