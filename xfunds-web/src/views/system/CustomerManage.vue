<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getCustomerList, saveCustomer, getCustomerAccounts, addCustomerAccount, updateCustomerAccount, getCustomerMarginAccounts, adjustMarginAccount } from '@/api/customer'

// 客户列表数据与分页
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

// 查询条件表单
const queryForm = reactive({
  customerId: '',
  customerName: '',
  customerType: ''
})

// 客户类型下拉选项
const customerTypeOptions = [
  { value: '', label: '全部' },
  { value: 'CORP', label: '对公客户' },
  { value: 'RETAIL', label: '个人客户' }
]

// 证件类型下拉选项
const idTypeOptions = [
  { value: 'USCC', label: '统一社会信用代码' },
  { value: 'ID_CARD', label: '居民身份证' },
  { value: 'PASSPORT', label: '护照' },
  { value: 'BUSINESS_LICENSE', label: '营业执照' },
  { value: 'OTHER', label: '其他' }
]

// 客户类型显示格式化：CORP→对公客户，RETAIL→个人客户
function formatCustomerType(type) {
  if (!type) return '-'
  const map = { CORP: '对公客户', RETAIL: '个人客户' }
  return map[type] || type
}

// 证件类型显示格式化
function formatIdType(type) {
  if (!type) return '-'
  const opt = idTypeOptions.find(o => o.value === type)
  return opt ? opt.label : type
}

// 编辑弹窗状态
const editVisible = ref(false)
const submitting = ref(false)
const editFormRef = ref(null)
const editForm = reactive({
  customerId: '',
  customerName: '',
  customerType: 'CORP',
  contactPhone: '',
  contactAddress: '',
  idType: '',
  idNo: ''
})

// 编辑表单校验规则
const editRules = {
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }]
}

// 账户弹窗状态
const accountVisible = ref(false)
const accountLoading = ref(false)
const accountList = ref([])
const currentCustomerId = ref('')

// 新增账户弹窗状态
const addAccountVisible = ref(false)
const addAccountSubmitting = ref(false)
const addAccountFormRef = ref(null)
const addAccountForm = reactive({
  accountNo: '',
  currency: '',
  accountType: 'CASH',
  balance: null
})
const addAccountRules = {
  accountNo: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  currency: [{ required: true, message: '请输入币种', trigger: 'blur' }],
  accountType: [{ required: true, message: '请选择账户类型', trigger: 'change' }]
}

// 保证金账户管理弹窗状态
const marginVisible = ref(false)
const marginLoading = ref(false)
const marginList = ref([])

// 交易账户余额调整弹窗状态
const tradeBalanceVisible = ref(false)
const tradeBalanceSubmitting = ref(false)
const tradeBalanceForm = reactive({
  accountId: null,
  accountNo: '',
  currency: '',
  accountType: '',
  oldBalance: null,
  newBalance: null,
  frozenAmount: null,
  status: '1'
})

// 保证金账户余额调整弹窗状态
const marginBalanceVisible = ref(false)
const marginBalanceSubmitting = ref(false)
const marginBalanceForm = reactive({
  marginAccountId: '',
  currency: '',
  oldBalance: null,
  newBalance: null,
  remark: ''
})

// 组装查询参数
function buildQueryParams() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  if (queryForm.customerId) params.customerId = queryForm.customerId
  if (queryForm.customerName) params.customerName = queryForm.customerName
  if (queryForm.customerType) params.customerType = queryForm.customerType
  return params
}

// 加载客户列表
async function loadData() {
  loading.value = true
  try {
    const res = await getCustomerList(buildQueryParams())
    tableData.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 点击查询按钮：重置页码后查询
function handleQuery() {
  pagination.pageNum = 1
  loadData()
}

// 点击重置按钮：清空查询条件并重新查询
function handleReset() {
  queryForm.customerId = ''
  queryForm.customerName = ''
  queryForm.customerType = ''
  pagination.pageNum = 1
  loadData()
}

// 分页页码变化
function handlePageChange(page) {
  pagination.pageNum = page
  loadData()
}

// 分页每页条数变化
function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.pageNum = 1
  loadData()
}

// 打开新增弹窗
function openAddDialog() {
  editForm.customerId = ''
  editForm.customerName = ''
  editForm.customerType = 'CORP'
  editForm.contactPhone = ''
  editForm.contactAddress = ''
  editForm.idType = ''
  editForm.idNo = ''
  editVisible.value = true
}

// 打开编辑弹窗：回填当前行数据
function openEditDialog(row) {
  editForm.customerId = row.customerId || ''
  editForm.customerName = row.customerName || ''
  editForm.customerType = row.customerType || 'CORP'
  editForm.contactPhone = row.contactPhone || ''
  editForm.contactAddress = row.contactAddress || ''
  editForm.idType = row.idType || ''
  editForm.idNo = row.idNo || ''
  editVisible.value = true
}

// 提交编辑：校验表单 -> 调用接口 -> 成功后刷新
async function handleSubmitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await saveCustomer({ ...editForm })
      ElMessage.success('保存成功')
      editVisible.value = false
      loadData()
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      submitting.value = false
    }
  })
}

// 打开账户管理弹窗：加载该客户账户列表
async function openAccountDialog(row) {
  currentCustomerId.value = row.customerId
  accountVisible.value = true
  accountLoading.value = true
  accountList.value = []
  try {
    const res = await getCustomerAccounts(row.customerId)
    accountList.value = res.data || []
  } catch (e) {
    accountList.value = []
  } finally {
    accountLoading.value = false
  }
}

// 打开新增账户弹窗
function openAddAccountDialog() {
  addAccountForm.accountNo = ''
  addAccountForm.currency = ''
  addAccountForm.accountType = 'CASH'
  addAccountForm.balance = null
  addAccountVisible.value = true
}

// 提交新增账户
async function handleSubmitAddAccount() {
  if (!addAccountFormRef.value) return
  await addAccountFormRef.value.validate(async (valid) => {
    if (!valid) return
    addAccountSubmitting.value = true
    try {
      await addCustomerAccount(currentCustomerId.value, { ...addAccountForm })
      ElMessage.success('账户新增成功')
      addAccountVisible.value = false
      // 刷新账户列表
      openAccountDialog({ customerId: currentCustomerId.value })
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      addAccountSubmitting.value = false
    }
  })
}

// 打开保证金账户管理弹窗：加载该客户保证金账户列表
async function openMarginDialog(row) {
  currentCustomerId.value = row.customerId
  marginVisible.value = true
  marginLoading.value = true
  marginList.value = []
  try {
    const res = await getCustomerMarginAccounts(row.customerId)
    marginList.value = res.data || []
  } catch (e) {
    marginList.value = []
  } finally {
    marginLoading.value = false
  }
}

// 打开交易账户余额调整弹窗：回填当前行数据
function openTradeBalanceEdit(row) {
  tradeBalanceForm.accountId = row.accountId
  tradeBalanceForm.accountNo = row.accountNo
  tradeBalanceForm.currency = row.currency
  tradeBalanceForm.accountType = row.accountType
  tradeBalanceForm.oldBalance = row.balance
  tradeBalanceForm.newBalance = row.balance
  tradeBalanceForm.frozenAmount = row.frozenAmount
  tradeBalanceForm.status = row.status
  tradeBalanceVisible.value = true
}

// 提交交易账户余额调整：调用更新接口，提交完整账户对象
async function handleSubmitTradeBalance() {
  tradeBalanceSubmitting.value = true
  try {
    await updateCustomerAccount(currentCustomerId.value, tradeBalanceForm.accountId, {
      accountNo: tradeBalanceForm.accountNo,
      currency: tradeBalanceForm.currency,
      accountType: tradeBalanceForm.accountType,
      balance: tradeBalanceForm.newBalance,
      frozenAmount: tradeBalanceForm.frozenAmount,
      status: tradeBalanceForm.status
    })
    ElMessage.success('交易账户余额调整成功')
    tradeBalanceVisible.value = false
    // 刷新交易账户列表
    openAccountDialog({ customerId: currentCustomerId.value })
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    tradeBalanceSubmitting.value = false
  }
}

// 打开保证金账户余额调整弹窗：回填当前行数据
function openMarginBalanceEdit(row) {
  marginBalanceForm.marginAccountId = row.marginAccountId
  marginBalanceForm.currency = row.currency
  marginBalanceForm.oldBalance = row.balance
  marginBalanceForm.newBalance = row.balance
  marginBalanceForm.remark = ''
  marginBalanceVisible.value = true
}

// 提交保证金账户余额调整：调用保证金调整接口，后端记录 ADJUST 流水
async function handleSubmitMarginBalance() {
  marginBalanceSubmitting.value = true
  try {
    await adjustMarginAccount({
      marginAccountId: marginBalanceForm.marginAccountId,
      newBalance: marginBalanceForm.newBalance,
      remark: marginBalanceForm.remark
    })
    ElMessage.success('保证金账户余额调整成功')
    marginBalanceVisible.value = false
    // 刷新保证金账户列表
    openMarginDialog({ customerId: currentCustomerId.value })
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    marginBalanceSubmitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="page-title">客户管理</span>
          <el-button type="primary" :icon="Plus" @click="openAddDialog">新增客户</el-button>
        </div>
      </template>

      <!-- 查询条件表单 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="客户号">
          <el-input v-model="queryForm.customerId" placeholder="请输入客户号" clearable />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="queryForm.customerName" placeholder="请输入客户名称" clearable />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="queryForm.customerType" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in customerTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 客户列表表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="customerId" label="客户号" width="140" fixed />
        <el-table-column prop="customerName" label="客户名称" min-width="180" />
        <el-table-column label="客户类型" width="120">
          <template #default="{ row }">{{ formatCustomerType(row.customerType) }}</template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column prop="contactAddress" label="联系地址" min-width="200" />
        <el-table-column label="证件类型" width="160">
          <template #default="{ row }">{{ formatIdType(row.idType) }}</template>
        </el-table-column>
        <el-table-column prop="idNo" label="证件号码" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="success" size="small" link @click="openAccountDialog(row)">交易账户管理</el-button>
            <el-button type="warning" size="small" link @click="openMarginDialog(row)">保证金账户管理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 客户编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="editForm.customerId ? '编辑客户' : '新增客户'"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户号">
              <el-input v-model="editForm.customerId" placeholder="新增时留空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称" prop="customerName">
              <el-input v-model="editForm.customerName" placeholder="请输入客户名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户类型" prop="customerType">
              <el-select v-model="editForm.customerType" style="width: 100%">
                <el-option label="对公客户" value="CORP" />
                <el-option label="个人客户" value="RETAIL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="editForm.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="证件类型">
              <el-select v-model="editForm.idType" placeholder="请选择证件类型" style="width: 100%">
                <el-option
                  v-for="opt in idTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证件号码">
              <el-input v-model="editForm.idNo" placeholder="请输入证件号码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="联系地址">
          <el-input v-model="editForm.contactAddress" placeholder="请输入联系地址" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 交易账户管理弹窗 -->
    <el-dialog
      v-model="accountVisible"
      title="交易账户管理"
      width="820px"
      destroy-on-close
    >
      <div v-loading="accountLoading">
        <div class="account-toolbar">
          <el-button type="primary" :icon="Plus" size="small" @click="openAddAccountDialog">
            新增账户
          </el-button>
          <span class="account-hint">账户余额与定时交割任务联动，交割时自动双向更新</span>
        </div>
        <el-table :data="accountList" border stripe size="small" style="width: 100%">
          <el-table-column prop="accountNo" label="账号" width="200" />
          <el-table-column prop="currency" label="币种" width="100" />
          <el-table-column label="账户类型" width="100">
            <template #default="{ row }">
              {{ row.accountType === 'CASH' ? '现钞' : row.accountType === 'SPOT' ? '现汇' : row.accountType }}
            </template>
          </el-table-column>
          <el-table-column prop="balance" label="余额" width="160" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="openTradeBalanceEdit(row)">调整余额</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="accountVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增账户弹窗 -->
    <el-dialog
      v-model="addAccountVisible"
      title="新增账户"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="addAccountFormRef"
        :model="addAccountForm"
        :rules="addAccountRules"
        label-width="100px"
      >
        <el-form-item label="账号" prop="accountNo">
          <el-input v-model="addAccountForm.accountNo" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="币种" prop="currency">
          <el-input v-model="addAccountForm.currency" placeholder="如 USD" />
        </el-form-item>
        <el-form-item label="账户类型" prop="accountType">
          <el-select v-model="addAccountForm.accountType" style="width: 100%">
            <el-option label="现钞" value="CASH" />
            <el-option label="现汇" value="SPOT" />
          </el-select>
        </el-form-item>
        <el-form-item label="余额">
          <el-input-number
            v-model="addAccountForm.balance"
            :precision="2"
            :step="100"
            :min="0"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addAccountVisible = false">取消</el-button>
        <el-button type="primary" :loading="addAccountSubmitting" @click="handleSubmitAddAccount">保存</el-button>
      </template>
    </el-dialog>

    <!-- 保证金账户管理弹窗 -->
    <el-dialog
      v-model="marginVisible"
      title="保证金账户管理"
      width="720px"
      destroy-on-close
    >
      <div v-loading="marginLoading">
        <div class="account-toolbar">
          <span class="account-hint">保证金余额与定时交割任务联动：审批通过扣减、交割成功退还、交割失败没收</span>
        </div>
        <el-table :data="marginList" border stripe size="small" style="width: 100%">
          <el-table-column prop="marginAccountId" label="保证金账户ID" width="200" />
          <el-table-column prop="currency" label="币种" width="100" />
          <el-table-column prop="balance" label="余额" width="160" />
          <el-table-column prop="frozenAmount" label="冻结金额" width="140" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="openMarginBalanceEdit(row)">调整余额</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="marginVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 交易账户余额调整弹窗 -->
    <el-dialog
      v-model="tradeBalanceVisible"
      title="调整交易账户余额"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="120px">
        <el-form-item label="账号">
          <el-input :model-value="tradeBalanceForm.accountNo" readonly />
        </el-form-item>
        <el-form-item label="币种">
          <el-input :model-value="tradeBalanceForm.currency" readonly />
        </el-form-item>
        <el-form-item label="当前余额">
          <el-input :model-value="tradeBalanceForm.oldBalance" readonly />
        </el-form-item>
        <el-form-item label="新余额">
          <el-input-number
            v-model="tradeBalanceForm.newBalance"
            :precision="2"
            :step="100"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="tradeBalanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="tradeBalanceSubmitting" @click="handleSubmitTradeBalance">保存</el-button>
      </template>
    </el-dialog>

    <!-- 保证金账户余额调整弹窗 -->
    <el-dialog
      v-model="marginBalanceVisible"
      title="调整保证金账户余额"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="120px">
        <el-form-item label="保证金账户ID">
          <el-input :model-value="marginBalanceForm.marginAccountId" readonly />
        </el-form-item>
        <el-form-item label="币种">
          <el-input :model-value="marginBalanceForm.currency" readonly />
        </el-form-item>
        <el-form-item label="当前余额">
          <el-input :model-value="marginBalanceForm.oldBalance" readonly />
        </el-form-item>
        <el-form-item label="新余额">
          <el-input-number
            v-model="marginBalanceForm.newBalance"
            :precision="2"
            :step="100"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="marginBalanceForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入调整原因（记录到保证金流水）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="marginBalanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="marginBalanceSubmitting" @click="handleSubmitMarginBalance">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
  height: 100%;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.query-form {
  margin-bottom: 12px;
}
.pagination-wrapper {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.account-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.account-hint {
  font-size: 12px;
  color: #909399;
}
</style>
