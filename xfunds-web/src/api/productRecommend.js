import request from '@/utils/request'

// 获取客户产品推荐
export function getProductRecommend(customerId) {
  return request({
    url: '/product-recommend/recommend',
    method: 'get',
    params: { customerId }
  })
}
