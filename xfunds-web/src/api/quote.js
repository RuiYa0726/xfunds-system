import request from '@/utils/request'

// 查询即期牌价列表
export function getSpotQuotes(params) {
  return request({
    url: '/quote/spot',
    method: 'get',
    params
  })
}

// 查询远期牌价列表
export function getForwardQuotes(params) {
  return request({
    url: '/quote/forward',
    method: 'get',
    params
  })
}

// 查询掉期牌价列表
export function getSwapQuotes(params) {
  return request({
    url: '/quote/swap',
    method: 'get',
    params
  })
}
