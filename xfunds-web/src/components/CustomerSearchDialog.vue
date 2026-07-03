<script setup>
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { searchCustomers } from '@/api/customer'

// 客户搜索弹窗组件
// 通过 v-model:visible 控制显隐，选中客户后 emit select 事件
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'update:visible'])

// 搜索关键字（客户号或名称）
const searchKeyword = ref('')
// 客户搜索结果列表
const customerList = ref([])
// 表格加载状态
const loading = ref(false)

// 弹窗打开时重置搜索条件与结果
watch(
  () => props.visible,
  (val) => {
    if (val) {
      searchKeyword.value = ''
      customerList.value = []
    }
  }
)

// 执行客户搜索：调用后端 searchCustomers 接口
async function handleSearch() {
  loading.value = true
  try {
    const res = await searchCustomers({ keyword: searchKeyword.value })
    customerList.value = res.data || []
  } catch (e) {
    customerList.value = []
  } finally {
    loading.value = false
  }
}

// 选中某行客户：向外抛出选中数据并关闭弹窗
function handleSelect(row) {
  emit('select', {
    customerId: row.customerId,
    customerName: row.customerName
  })
  emit('update:visible', false)
}

// 关闭弹窗
function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="客户搜索"
    width="720px"
    @update:model-value="handleClose"
  >
    <!-- 搜索条件区 -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="请输入客户号或客户名称"
        clearable
        style="width: 300px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
        搜索
      </el-button>
    </div>

    <!-- 搜索结果表格 -->
    <el-table
      v-loading="loading"
      :data="customerList"
      border
      stripe
      size="small"
      max-height="360"
      @row-dblclick="handleSelect"
    >
      <el-table-column prop="customerId" label="客户号" width="140" />
      <el-table-column prop="customerName" label="客户名称" min-width="180" />
      <el-table-column prop="customerType" label="客户类型" width="100" />
      <el-table-column prop="contactPhone" label="联系电话" width="140" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleSelect(row)">
            选择
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
