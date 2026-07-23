import request from '@/utils/request'

export function searchMenuPath(query, sessionId) {
  return request({
    url: '/menu-nav/search',
    method: 'get',
    params: { query, sessionId }
  })
}
