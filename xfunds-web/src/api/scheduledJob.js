import request from '@/utils/request'

// 获取到期交割定时任务信息
export function getMaturitySettlementInfo() {
  return request({
    url: '/scheduled-job/maturity-settlement/info',
    method: 'get'
  })
}

// 手动触发到期交割任务（一键交割当天到期交易）
export function runMaturitySettlement() {
  return request({
    url: '/scheduled-job/maturity-settlement/run',
    method: 'post'
  })
}

// 获取牌价定时任务信息
export function getQuoteRefreshInfo() {
  return request({
    url: '/scheduled-job/quote-refresh/info',
    method: 'get'
  })
}

// 手动触发获取牌价任务
export function runQuoteRefresh() {
  return request({
    url: '/scheduled-job/quote-refresh/run',
    method: 'post'
  })
}

// 分页查询定时任务执行日志
export function getJobLogs(params) {
  return request({
    url: '/scheduled-job/logs',
    method: 'get',
    params
  })
}

// 根据执行日志ID查询本次执行的逐笔明细
export function getJobLogDetails(logId) {
  return request({
    url: `/scheduled-job/logs/${logId}/details`,
    method: 'get'
  })
}
