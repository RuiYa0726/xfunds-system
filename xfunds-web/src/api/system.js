import request from '@/utils/request'

// 获取机构树
export function getOrgTree() {
  return request({
    url: '/system/org/tree',
    method: 'get'
  })
}

// 获取机构列表
export function getOrgs(params) {
  return request({
    url: '/system/org/list',
    method: 'get',
    params
  })
}

// 获取当前登录用户所属机构及其所有下级机构（扁平列表）
export function getMyOrgsWithChildren() {
  return request({
    url: '/system/org/my-with-children',
    method: 'get'
  })
}

// 保存机构（新增/编辑）
export function saveOrg(data) {
  return request({
    url: '/system/org/save',
    method: 'post',
    data
  })
}

// 获取用户列表
export function getUsers(params) {
  return request({
    url: '/system/user/list',
    method: 'get',
    params
  })
}

// 获取用户详情
export function getUserDetail(userId) {
  return request({
    url: `/system/user/${userId}`,
    method: 'get'
  })
}

// 保存用户（新增/编辑）
export function saveUser(data) {
  return request({
    url: '/system/user/save',
    method: 'post',
    data
  })
}

// 删除用户
export function deleteUser(userId) {
  return request({
    url: `/system/user/${userId}`,
    method: 'delete'
  })
}

// 重置用户密码
export function resetUserPassword(userId, data) {
  return request({
    url: `/system/user/${userId}/reset-password`,
    method: 'post',
    data
  })
}

// 获取角色列表
export function getRoles() {
  return request({
    url: '/system/role/list',
    method: 'get'
  })
}

// 获取客户列表
export function getCustomers(params) {
  return request({
    url: '/system/customer/list',
    method: 'get',
    params
  })
}

// 保存客户（新增/编辑）
export function saveCustomer(data) {
  return request({
    url: '/system/customer/save',
    method: 'post',
    data
  })
}



// 获取系统参数列表
export function getSysParams(params) {
  return request({
    url: '/system/param/list',
    method: 'get',
    params
  })
}

// 保存系统参数
export function saveSysParam(data) {
  return request({
    url: '/system/param/save',
    method: 'post',
    data
  })
}

// 获取系统业务日期
export function getSysParamDate() {
  return request({
    url: '/system/param/date',
    method: 'get'
  })
}

// 设置系统业务日期
export function saveSysParamDate(data) {
  return request({
    url: '/system/param/date',
    method: 'post',
    data
  })
}
