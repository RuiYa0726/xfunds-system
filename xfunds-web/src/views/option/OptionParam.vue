<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getOptionParams, saveOptionParam } from '@/api/option'

// 参数列表数据
const tableData = ref([])
const loading = ref(false)

// 编辑弹窗状态
const editVisible = ref(false)
const submitting = ref(false)
const editFormRef = ref(null)
const editForm = reactive({
  paramId: '',
  paramCode: '',
  paramName: '',
  paramValue: '',
  description: ''
})

// 编辑表单校验规则
const editRules = {
  paramValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }]
}

// 加载期权参数列表
async function loadData() {
  loading.value = true
  try {
    const res = await getOptionParams()
    tableData.value = res.data?.records || res.data?.list || res.data || []
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

// 打开编辑弹窗：回填当前行数据
function openEditDialog(row) {
  editForm.paramId = row.paramId || row.id || ''
  editForm.paramCode = row.paramCode || ''
  editForm.paramName = row.paramName || ''
  editForm.paramValue = row.paramValue ?? ''
  editForm.description = row.description ?? ''
  editVisible.value = true
}

// 提交编辑：校验表单 -> 调用接口 -> 成功后刷新
async function handleSubmitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await saveOptionParam({
        paramId: editForm.paramId,
        paramCode: editForm.paramCode,
        paramName: editForm.paramName,
        paramValue: editForm.paramValue,
        description: editForm.description
      })
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

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="page-title">期权参数管理</span>
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadData">
            刷新
          </el-button>
        </div>
      </template>

      <!-- 参数列表表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="paramCode" label="参数编码" width="180" />
        <el-table-column prop="paramName" label="参数名称" min-width="180" />
        <el-table-column prop="paramValue" label="参数值" width="180" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      title="编辑参数"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-form-item label="参数编码">
          <el-input v-model="editForm.paramCode" readonly />
        </el-form-item>
        <el-form-item label="参数名称">
          <el-input v-model="editForm.paramName" readonly />
        </el-form-item>
        <el-form-item label="参数值" prop="paramValue">
          <el-input v-model="editForm.paramValue" placeholder="请输入参数值" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitEdit">保存</el-button>
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
</style>
