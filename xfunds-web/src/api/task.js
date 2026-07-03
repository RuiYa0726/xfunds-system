import request from '@/utils/request'

// 查询我的待办任务
export function getMyTasks(params) {
  return request({
    url: '/task/my',
    method: 'get',
    params
  })
}

// 查询角色池待办任务
export function getRoleTasks(params) {
  return request({
    url: '/task/role',
    method: 'get',
    params
  })
}

// 认领任务
export function claimTask(taskId) {
  return request({
    url: `/task/${taskId}/claim`,
    method: 'post'
  })
}

// 完成任务
export function completeTask(taskId, data) {
  return request({
    url: `/task/${taskId}/complete`,
    method: 'post',
    data
  })
}

// 取消任务
export function cancelTask(taskId, data) {
  return request({
    url: `/task/${taskId}/cancel`,
    method: 'post',
    data
  })
}

// 完成修改任务
export function completeModifyTask(taskId) {
  return request({
    url: `/task/${taskId}/complete-modify`,
    method: 'post'
  })
}
