<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getUsers, getUserDetail, saveUser, deleteUser, resetUserPassword, getRoles, getOrgs } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

// 用户列表数据与分页
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

// 查询条件表单
const queryForm = reactive({
  username: '',
  orgCode: ''
})

// 机构列表与角色列表（下拉选项）
const orgOptions = ref([])
const roleOptions = ref([])

// 角色 ID -> 名称 映射，用于表格展示
const roleNameMap = computed(() => {
  const map = {}
  roleOptions.value.forEach(r => {
    map[r.roleId] = r.roleName
  })
  return map
})

// 编辑弹窗状态
const editVisible = ref(false)
const submitting = ref(false)
const editFormRef = ref(null)
const editForm = reactive({
  userId: null,
  username: '',
  password: '',
  realName: '',
  orgCode: '',
  status: '1',
  roleIds: []
})

// 是否新增模式
const isAdd = computed(() => !editForm.userId)

// 编辑表单校验规则
const editRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  orgCode: [{ required: true, message: '请选择所属机构', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// 新增模式下密码必填，编辑模式下密码非必填（通过动态校验）
const passwordRule = computed(() => {
  return isAdd.value
    ? [{ required: true, message: '请输入密码', trigger: 'blur' }]
    : []
})

// 重置密码弹窗状态
const resetPwdVisible = ref(false)
const resetPwdSubmitting = ref(false)
const resetPwdFormRef = ref(null)
const resetPwdForm = reactive({
  userId: null,
  username: '',
  password: ''
})
const resetPwdRules = {
  password: [{ required: true, message: '请输入新密码', trigger: 'blur' }]
}

// 组装查询参数
function buildQueryParams() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  if (queryForm.username) params.username = queryForm.username
  if (queryForm.orgCode) params.orgCode = queryForm.orgCode
  return params
}

// 加载用户列表
async function loadData() {
  loading.value = true
  try {
    const res = await getUsers(buildQueryParams())
    tableData.value = res.data?.list || res.data?.records || res.data || []
    total.value = res.data?.total || 0
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 加载机构下拉
async function loadOrgOptions() {
  try {
    const res = await getOrgs({ pageSize: 1000 })
    orgOptions.value = res.data?.list || res.data?.records || res.data || []
  } catch (e) {
    orgOptions.value = []
  }
}

// 加载角色下拉
async function loadRoleOptions() {
  try {
    const res = await getRoles()
    roleOptions.value = res.data || []
  } catch (e) {
    roleOptions.value = []
  }
}

// 点击查询按钮：重置页码后查询
function handleQuery() {
  pagination.pageNum = 1
  loadData()
}

// 点击重置按钮：清空查询条件并重新查询
function handleReset() {
  queryForm.username = ''
  queryForm.orgCode = ''
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

// 机构编码 -> 机构名称
function orgName(orgCode) {
  const o = orgOptions.value.find(item => item.orgCode === orgCode)
  return o ? o.orgName : orgCode
}

// 角色ID列表 -> 角色名称拼接
function roleNames(roleIds) {
  if (!Array.isArray(roleIds) || roleIds.length === 0) return ''
  return roleIds
    .map(id => roleNameMap.value[id])
    .filter(Boolean)
    .join('，')
}

// 打开新增弹窗
function openAddDialog() {
  editForm.userId = null
  editForm.username = ''
  editForm.password = ''
  editForm.realName = ''
  editForm.orgCode = ''
  editForm.status = '1'
  editForm.roleIds = []
  editVisible.value = true
}

// 打开编辑弹窗：拉取详情回填
async function openEditDialog(row) {
  try {
    const res = await getUserDetail(row.userId)
    const detail = res.data || {}
    editForm.userId = detail.userId
    editForm.username = detail.username || ''
    editForm.password = ''
    editForm.realName = detail.realName || ''
    editForm.orgCode = detail.orgCode || ''
    editForm.status = detail.status || '1'
    editForm.roleIds = Array.isArray(detail.roleIds) ? [...detail.roleIds] : []
    editVisible.value = true
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  }
}

// 提交编辑：校验表单 -> 调用接口 -> 成功后刷新
async function handleSubmitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      // 编辑模式下不传密码字段（留空表示不修改密码）
      const payload = { ...editForm }
      if (!isAdd.value && !payload.password) {
        delete payload.password
      }
      await saveUser(payload)
      ElMessage.success(isAdd.value ? '新增成功' : '保存成功')
      editVisible.value = false
      loadData()
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      submitting.value = false
    }
  })
}

// 删除用户
async function handleDelete(row) {
  // 禁止删除当前登录用户
  if (String(row.userId) === String(userStore.userInfo.userId)) {
    ElMessage.warning('不能删除当前登录用户')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.username}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteUser(row.userId)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  }
}

// 打开重置密码弹窗
function openResetPwdDialog(row) {
  resetPwdForm.userId = row.userId
  resetPwdForm.username = row.username
  resetPwdForm.password = ''
  resetPwdVisible.value = true
}

// 提交重置密码
async function handleSubmitResetPwd() {
  if (!resetPwdFormRef.value) return
  await resetPwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    resetPwdSubmitting.value = true
    try {
      await resetUserPassword(resetPwdForm.userId, { password: resetPwdForm.password })
      ElMessage.success('密码重置成功')
      resetPwdVisible.value = false
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      resetPwdSubmitting.value = false
    }
  })
}

onMounted(() => {
  loadData()
  loadOrgOptions()
  loadRoleOptions()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="page-title">登录用户管理</span>
          <el-button type="primary" :icon="Plus" @click="openAddDialog">新增用户</el-button>
        </div>
      </template>

      <!-- 查询条件表单 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="用户名">
          <el-input v-model="queryForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="所属机构">
          <el-select v-model="queryForm.orgCode" placeholder="全部" clearable style="width: 180px">
            <el-option
              v-for="o in orgOptions"
              :key="o.orgCode"
              :label="o.orgName"
              :value="o.orgCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 用户列表表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="userId" label="用户ID" width="80" fixed />
        <el-table-column prop="username" label="用户名" width="140" fixed />
        <el-table-column prop="realName" label="真实姓名" width="140" />
        <el-table-column label="所属机构" width="160">
          <template #default="{ row }">
            {{ orgName(row.orgCode) }}
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            {{ roleNames(row.roleIds) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'success' : 'danger'" size="small">
              {{ row.status === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="warning" size="small" link @click="openResetPwdDialog(row)">重置密码</el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
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

    <!-- 用户编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="isAdd ? '新增用户' : '编辑用户'"
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
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="editForm.username"
                placeholder="请输入用户名"
                :disabled="!isAdd"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名">
              <el-input v-model="editForm.realName" placeholder="请输入真实姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="密码" prop="password" :rules="passwordRule">
              <el-input
                v-model="editForm.password"
                type="password"
                show-password
                :placeholder="isAdd ? '请输入密码' : '留空表示不修改密码'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="editForm.status" style="width: 100%">
                <el-option label="启用" value="1" />
                <el-option label="停用" value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属机构" prop="orgCode">
              <el-select v-model="editForm.orgCode" placeholder="请选择机构" filterable style="width: 100%">
                <el-option
                  v-for="o in orgOptions"
                  :key="o.orgCode"
                  :label="o.orgName"
                  :value="o.orgCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select
                v-model="editForm.roleIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择角色（可多选）"
                style="width: 100%"
              >
                <el-option
                  v-for="r in roleOptions"
                  :key="r.roleId"
                  :label="r.roleName"
                  :value="r.roleId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog
      v-model="resetPwdVisible"
      title="重置密码"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="100px"
      >
        <el-form-item label="用户名">
          <el-input v-model="resetPwdForm.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="resetPwdForm.password"
            type="password"
            show-password
            placeholder="请输入新密码"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetPwdSubmitting" @click="handleSubmitResetPwd">确定</el-button>
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
</style>
