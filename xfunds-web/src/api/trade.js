import request from '@/utils/request'

// 创建即期交易
export function createSpotTrade(data) {
  return request({
    url: '/trade/spot',
    method: 'post',
    data
  })
}

// 创建远期交易
export function createForwardTrade(data) {
  return request({
    url: '/trade/forward',
    method: 'post',
    data
  })
}

// 创建掉期交易
export function createSwapTrade(data) {
  return request({
    url: '/trade/swap',
    method: 'post',
    data
  })
}

// 查询未到期交易
export function queryUnmaturedTrades(params) {
  return request({
    url: '/trade/unmatured',
    method: 'get',
    params
  })
}

// 客户交易查询
export function queryCustomerTrades(params) {
  return request({
    url: '/trade/customer',
    method: 'get',
    params
  })
}

// 提前交割
export function earlyDelivery(data) {
  return request({
    url: '/trade/early-delivery',
    method: 'post',
    data
  })
}

// 提前违约
export function earlyDefault(data) {
  return request({
    url: '/trade/early-default',
    method: 'post',
    data
  })
}

// 原价展期
export function rolloverOriginal(data) {
  return request({
    url: '/trade/rollover-original',
    method: 'post',
    data
  })
}

// 市价展期
export function rolloverMarket(data) {
  return request({
    url: '/trade/rollover-market',
    method: 'post',
    data
  })
}

// 保证金增补
export function marginSupplement(data) {
  return request({
    url: '/trade/margin-supplement',
    method: 'post',
    data
  })
}

// 全部违约（掉期近端/远端整体违约）
export function fullDefault(data) {
  return request({
    url: '/trade/full-default',
    method: 'post',
    data
  })
}

// 审批通过
export function approveTrade(data) {
  return request({
    url: '/trade/approve',
    method: 'post',
    data
  })
}

// 审批拒绝
export function rejectTrade(data) {
  return request({
    url: '/trade/reject',
    method: 'post',
    data
  })
}

// 审批退回
export function returnTrade(data) {
  return request({
    url: '/trade/return',
    method: 'post',
    data
  })
}

// 查询交易详情
export function getTradeDetail(tradeId) {
  return request({
    url: `/trade/detail/${tradeId}`,
    method: 'get'
  })
}

// 查询交易列表（通用）
export function queryTradeList(params) {
  return request({
    url: '/trade/list',
    method: 'get',
    params
  })
}

// 更新即期交易并重新提交
export function updateAndResubmitSpotTrade(tradeId, data) {
  return request({
    url: `/trade/spot/${tradeId}/resubmit`,
    method: 'post',
    data
  })
}

// 更新远期交易并重新提交
export function updateAndResubmitForwardTrade(tradeId, data) {
  return request({
    url: `/trade/forward/${tradeId}/resubmit`,
    method: 'post',
    data
  })
}

// 更新掉期交易并重新提交
export function updateAndResubmitSwapTrade(tradeId, data) {
  return request({
    url: `/trade/swap/${tradeId}/resubmit`,
    method: 'post',
    data
  })
}

// 获取客户账户列表
export function getCustomerAccounts(customerId, currency) {
  return request({
    url: `/customer/${customerId}/accounts`,
    method: 'get',
    params: { currency }
  })
}

// 获取即期报价列表
export function getSpotQuotes() {
  return request({
    url: '/quote/spot',
    method: 'get'
  })
}

// 搜索报价
export function searchQuotes(quoteType, currencyPair) {
  return request({
    url: '/quote/search',
    method: 'get',
    params: { quoteType, currencyPair }
  })
}

// 重新执行交割失败的交易
export function retrySettle(tradeId) {
  return request({
    url: `/scheduled-job/retry-settle/${tradeId}`,
    method: 'post'
  })
}
