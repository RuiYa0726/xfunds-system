import request from '@/utils/request'

// 客户搜索（供交易录入选择客户号）
export function searchCustomers(params) {
  return request({
    url: '/customer/search',
    method: 'get',
    params
  })
}

// 获取客户账户列表（按币种、现汇/现钞）
export function getCustomerAccounts(customerNo) {
  return request({
    url: `/customer/${customerNo}/accounts`,
    method: 'get'
  })
}

// 获取客户保证金账户列表（用于交易录入选择保证金账户）
export function getCustomerMarginAccounts(customerId) {
  return request({
    url: `/customer/${customerId}/margin-accounts`,
    method: 'get'
  })
}

// 获取客户余额
export function getCustomerBalance(customerNo) {
  return request({
    url: `/customer/${customerNo}/balance`,
    method: 'get'
  })
}

// 获取客户详情
export function getCustomerDetail(customerId) {
  return request({
    url: `/customer/${customerId}`,
    method: 'get'
  })
}

// 获取客户列表（系统管理用）
export function getCustomerList(params) {
  return request({
    url: '/customer/list',
    method: 'get',
    params
  })
}

// 保存客户（新增/编辑）
export function saveCustomer(data) {
  return request({
    url: '/customer/save',
    method: 'post',
    data
  })
}

// 新增客户账户
export function addCustomerAccount(customerId, data) {
  return request({
    url: `/customer/${customerId}/account`,
    method: 'post',
    data
  })
}

// 更新客户交易账户（含余额人工调整）
export function updateCustomerAccount(customerId, accountId, data) {
  return request({
    url: `/customer/${customerId}/account/${accountId}`,
    method: 'post',
    data
  })
}

// 人工调整保证金账户余额（与定时交割任务联动同一张表）
export function adjustMarginAccount(data) {
  return request({
    url: '/customer/margin-account/adjust',
    method: 'post',
    data
  })
}

// 新增保证金账户（手工为客户创建保证金账户）
export function addMarginAccount(customerId, data) {
  return request({
    url: `/customer/${customerId}/margin-account`,
    method: 'post',
    data
  })
}
