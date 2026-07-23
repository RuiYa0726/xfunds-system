<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
import {
  queryUnmaturedTrades,
  earlyDelivery,
  earlyDefault,
  rolloverOriginal,
  rolloverMarket,
  fullDefault
} from '@/api/trade'
import {
  formatTradeType,
  formatTradeDirection,
  formatTradeStatus,
  formatSettlementMethod,
  formatSpecialTradeType,
  getStatusTagType
} from '@/utils/constants'
import { getMyOrgsWithChildren } from '@/api/system'

// 交易机构下拉选项（当前用户所属机构及以下层级机构）
const orgOptions = ref([])

// 加载交易机构下拉选项
async function loadOrgOptions() {
  try {
    const res = await getMyOrgsWithChildren()
    orgOptions.value = res.data || []
  } catch (e) {
    orgOptions.value = []
  }
}

// 当前激活的 tab：forward=远期未到期，swapFar=掉期远端未到期
const activeTab = ref('forward')

// 查询条件表单
const queryForm = reactive({
  businessNo: '',
  currencyPair: '',
  branchCode: '',
  customerId: ''
})

// 表格数据与分页
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

// 各 tab 对应的交易类型参数
const tabTradeTypeMap = {
  forward: 'FORWARD',
  swapFar: 'SWAP'
}

// 各 tab 对应的操作按钮配置
const tabOperationsMap = {
  forward: ['earlyDelivery', 'earlyDefault', 'rolloverOriginal', 'rolloverMarket'],
  swapFar: ['earlyDelivery', 'earlyDefault', 'rolloverOriginal', 'rolloverMarket']
}

// 操作按钮元信息：key -> { label, type }
const operationMeta = {
  earlyDelivery: { label: '提前交割', type: 'primary' },
  earlyDefault: { label: '提前违约', type: 'danger' },
  rolloverOriginal: { label: '原价展期', type: 'warning' },
  rolloverMarket: { label: '市价展期', type: 'warning' },
  fullDefault: { label: '全部违约', type: 'danger' }
}

// 当前 tab 可用操作按钮列表
const currentOperations = computed(() => tabOperationsMap[activeTab.value] || [])

// 弹窗相关状态
const dialogVisible = ref(false)
const dialogOperation = ref('')
const dialogTitle = computed(() => operationMeta[dialogOperation.value]?.label || '')
const currentRow = ref(null)
const submitting = ref(false)

// 表格选中行（用于在查询区下方触发操作）
const tableRef = ref(null)
const selectedRow = ref(null)

// 操作表单数据（根据不同操作动态使用不同字段）
const opForm = reactive({
  deliveryDate: '',
  deliveryAmount: null,
  defaultAmount: null,
  defaultAccount: '',
  penaltyAmount: null,
  penaltyAccount: '',
  newMaturityDate: '',
  marketRate: null,
  pnlAmount: null,
  remark: ''
})

// 操作表单引用
const opFormRef = ref(null)

// 根据操作类型返回对应的表单校验规则
function getOpRules(operation) {
  const amountRule = { type: 'number', min: 0.000001, message: '金额必须大于0', trigger: 'blur' }
  const requiredRule = (msg) => [{ required: true, message: msg, trigger: 'blur' }]
  switch (operation) {
    case 'earlyDelivery':
      return {
        deliveryDate: requiredRule('请选择交割日期'),
        deliveryAmount: [requiredRule('请输入交割金额'), amountRule],
        penaltyAccount: requiredRule('请输入违约金账户')
      }
    case 'earlyDefault':
      return {
        defaultAmount: [requiredRule('请输入违约金额'), amountRule],
        defaultAccount: requiredRule('请输入违约账户'),
        penaltyAccount: requiredRule('请输入违约金账户')
      }
    case 'rolloverOriginal':
      return {
        newMaturityDate: requiredRule('请选择展期日期'),
        penaltyAccount: requiredRule('请输入违约金账户')
      }
    case 'rolloverMarket':
      return {
        newMaturityDate: requiredRule('请选择展期日期'),
        marketRate: [requiredRule('请输入市场汇率'), { type: 'number', min: 0.00000001, message: '汇率必须大于0', trigger: 'blur' }],
        penaltyAccount: requiredRule('请输入违约金账户')
      }
    case 'fullDefault':
      return {
        penaltyAccount: requiredRule('请输入违约金账户')
      }
    default:
      return {}
  }
}

// 当前操作表单的校验规则
const opRules = computed(() => getOpRules(dialogOperation.value))

// 组装查询参数：合并交易类型、过滤条件与分页
function buildQueryParams() {
  return {
    tradeType: tabTradeTypeMap[activeTab.value],
    tab: activeTab.value,
    ...queryForm,
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
}

// 加载未到期交易列表
async function loadData() {
  loading.value = true
  selectedRow.value = null
  tableRef.value?.setCurrentRow(null)
  try {
    const res = await queryUnmaturedTrades(buildQueryParams())
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 表格单选行变化：记录选中行以启用操作按钮
function handleCurrentChange(val) {
  selectedRow.value = val
}

// 点击查询按钮：重置页码后查询
function handleQuery() {
  pagination.pageNum = 1
  loadData()
}

// 点击重置按钮：清空查询条件并重新查询
function handleReset() {
  queryForm.businessNo = ''
  queryForm.currencyPair = ''
  queryForm.branchCode = ''
  queryForm.customerId = ''
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

// 切换 tab 时重置查询并重新加载
function handleTabChange() {
  pagination.pageNum = 1
  loadData()
}

// 重置操作表单所有字段
function resetOpForm() {
  opForm.deliveryDate = ''
  opForm.deliveryAmount = null
  opForm.defaultAmount = null
  opForm.defaultAccount = ''
  opForm.penaltyAmount = null
  opForm.penaltyAccount = ''
  opForm.newMaturityDate = ''
  opForm.marketRate = null
  opForm.pnlAmount = null
  opForm.remark = ''
}

// 打开操作弹窗：记录当前行与操作类型，重置表单
function openOperationDialog(row, operation) {
  if (operation === 'earlyDefault') {
    // 提前违约跳转到新页面
    router.push({
      path: '/fx/early-default',
      query: { tradeId: row.tradeId || row.id }
    })
    return
  }
  if (operation === 'earlyDelivery') {
    // 提前交割跳转到新页面
    router.push({
      path: '/fx/early-delivery',
      query: { tradeId: row.tradeId || row.id }
    })
    return
  }
  if (operation === 'fullDefault') {
    // 掉期全部违约跳转到新页面
    router.push({
      path: '/fx/swap-full-default',
      query: { tradeId: row.tradeId || row.id }
    })
    return
  }
  if (operation === 'rolloverOriginal') {
    // 原价展期跳转到新页面
    router.push({
      path: '/fx/rollover',
      query: { tradeId: row.tradeId || row.id, mode: 'ORIGINAL' }
    })
    return
  }
  if (operation === 'rolloverMarket') {
    // 市价展期跳转到新页面
    router.push({
      path: '/fx/rollover',
      query: { tradeId: row.tradeId || row.id, mode: 'MARKET' }
    })
    return
  }
  currentRow.value = row
  dialogOperation.value = operation
  resetOpForm()
  dialogVisible.value = true
}

// 根据操作类型组装提交数据
function buildOpPayload() {
  const tradeId = currentRow.value?.tradeId || currentRow.value?.id
  const base = { tradeId, remark: opForm.remark }
  switch (dialogOperation.value) {
    case 'earlyDelivery':
      return {
        ...base,
        deliveryDate: opForm.deliveryDate,
        deliveryAmount: opForm.deliveryAmount,
        penaltyAmount: opForm.penaltyAmount,
        penaltyAccount: opForm.penaltyAccount
      }
    case 'earlyDefault':
      return {
        ...base,
        defaultAmount: opForm.defaultAmount,
        defaultAccount: opForm.defaultAccount,
        penaltyAmount: opForm.penaltyAmount,
        penaltyAccount: opForm.penaltyAccount
      }
    case 'rolloverOriginal':
      return {
        ...base,
        newMaturityDate: opForm.newMaturityDate,
        penaltyAmount: opForm.penaltyAmount,
        penaltyAccount: opForm.penaltyAccount
      }
    case 'rolloverMarket':
      return {
        ...base,
        newMaturityDate: opForm.newMaturityDate,
        marketRate: opForm.marketRate,
        pnlAmount: opForm.pnlAmount,
        penaltyAccount: opForm.penaltyAccount
      }
    case 'fullDefault':
      return {
        ...base,
        penaltyAmount: opForm.penaltyAmount,
        penaltyAccount: opForm.penaltyAccount
      }
    default:
      return base
  }
}

// 根据操作类型调用对应接口
async function callOperationApi(payload) {
  const apiMap = {
    earlyDelivery,
    earlyDefault,
    rolloverOriginal,
    rolloverMarket,
    fullDefault
  }
  const api = apiMap[dialogOperation.value]
  if (!api) throw new Error('未知操作类型')
  return api(payload)
}

// 提交操作：校验表单 -> 调用接口 -> 成功后刷新
async function handleSubmitOperation() {
  if (!opFormRef.value) return
  await opFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = buildOpPayload()
      await callOperationApi(payload)
      ElMessage.success(`${dialogTitle.value}操作成功`)
      dialogVisible.value = false
      loadData()
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  loadOrgOptions()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">未到期交易管理</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="远期未到期" name="forward" />
        <el-tab-pane label="掉期远端未到期" name="swapFar" />
      </el-tabs>

      <!-- 查询条件表单 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="业务编号">
          <el-input v-model="queryForm.businessNo" placeholder="请输入业务编号" clearable />
        </el-form-item>
        <el-form-item label="货币对">
          <el-input v-model="queryForm.currencyPair" placeholder="如 USD/CNY" clearable />
        </el-form-item>
        <el-form-item label="交易机构">
          <el-select v-model="queryForm.branchCode" placeholder="请选择交易机构" clearable filterable style="width: 200px">
            <el-option
              v-for="o in orgOptions"
              :key="o.orgCode"
              :label="o.orgName"
              :value="o.orgCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="客户号">
          <el-input v-model="queryForm.customerId" placeholder="请输入客户号" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作按钮区：先选中交易行，再点击操作 -->
      <div class="action-bar">
        <span v-if="!selectedRow" class="action-tip">请先选择一条交易，再进行操作</span>
        <el-button
          v-for="op in currentOperations"
          :key="op"
          :type="operationMeta[op].type"
          size="small"
          :disabled="!selectedRow"
          @click="openOperationDialog(selectedRow, op)"
        >
          {{ operationMeta[op].label }}
        </el-button>
      </div>

      <!-- 交易列表表格 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        highlight-current-row
        style="width: 100%"
        @current-change="handleCurrentChange"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column label="交易类型" width="90">
          <template #default="{ row }">{{ formatTradeType(row.tradeType) }}</template>
        </el-table-column>
        <el-table-column label="特殊交易类型" width="120">
          <template #default="{ row }">{{ formatSpecialTradeType(row.specialTradeType) }}</template>
        </el-table-column>
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column label="买卖方向" width="90">
          <template #default="{ row }">{{ formatTradeDirection(row.tradeDirection) }}</template>
        </el-table-column>
        <el-table-column prop="notionalAmount" label="金额" width="140" />
        <el-table-column prop="customerRate" label="客户汇率" width="110" />
        <el-table-column prop="tradeDate" label="交易日" width="110" />
        <el-table-column prop="valueDate" label="起息日" width="110" />
        <el-table-column prop="maturityDate" label="到期日" width="110" />
        <el-table-column label="交割方式" width="110">
          <template #default="{ row }">{{ formatSettlementMethod(row.settlementMethod) }}</template>
        </el-table-column>
        <el-table-column prop="branchName" label="交易机构" width="120" />
        <el-table-column prop="customerId" label="客户号" width="120" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ formatTradeStatus(row.status) }}
            </el-tag>
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

    <!-- 操作弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="opFormRef"
        :model="opForm"
        :rules="opRules"
        label-width="110px"
      >
        <!-- 提前交割字段 -->
        <template v-if="dialogOperation === 'earlyDelivery'">
          <el-form-item label="交割日期" prop="deliveryDate">
            <el-date-picker
              v-model="opForm.deliveryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择交割日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="交割金额" prop="deliveryAmount">
            <el-input-number
              v-model="opForm.deliveryAmount"
              :precision="2"
              :step="100"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约金" prop="penaltyAmount">
            <el-input-number
              v-model="opForm.penaltyAmount"
              :precision="2"
              :step="100"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约金账户" prop="penaltyAccount">
            <el-input v-model="opForm.penaltyAccount" placeholder="请输入违约金账户" />
          </el-form-item>
        </template>

        <!-- 提前违约字段 -->
        <template v-if="dialogOperation === 'earlyDefault'">
          <el-form-item label="违约金额" prop="defaultAmount">
            <el-input-number
              v-model="opForm.defaultAmount"
              :precision="2"
              :step="100"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约账户" prop="defaultAccount">
            <el-input v-model="opForm.defaultAccount" placeholder="请输入违约账户" />
          </el-form-item>
          <el-form-item label="违约金" prop="penaltyAmount">
            <el-input-number
              v-model="opForm.penaltyAmount"
              :precision="2"
              :step="100"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约金账户" prop="penaltyAccount">
            <el-input v-model="opForm.penaltyAccount" placeholder="请输入违约金账户" />
          </el-form-item>
        </template>

        <!-- 原价展期字段 -->
        <template v-if="dialogOperation === 'rolloverOriginal'">
          <el-form-item label="展期日期" prop="newMaturityDate">
            <el-date-picker
              v-model="opForm.newMaturityDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择展期日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约金" prop="penaltyAmount">
            <el-input-number
              v-model="opForm.penaltyAmount"
              :precision="2"
              :step="100"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约金账户" prop="penaltyAccount">
            <el-input v-model="opForm.penaltyAccount" placeholder="请输入违约金账户" />
          </el-form-item>
        </template>

        <!-- 市价展期字段 -->
        <template v-if="dialogOperation === 'rolloverMarket'">
          <el-form-item label="展期日期" prop="newMaturityDate">
            <el-date-picker
              v-model="opForm.newMaturityDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择展期日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="市场汇率" prop="marketRate">
            <el-input-number
              v-model="opForm.marketRate"
              :precision="4"
              :step="0.0001"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="损益金额" prop="pnlAmount">
            <el-input-number
              v-model="opForm.pnlAmount"
              :precision="2"
              :step="100"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约金账户" prop="penaltyAccount">
            <el-input v-model="opForm.penaltyAccount" placeholder="请输入违约金账户" />
          </el-form-item>
        </template>

        <!-- 全部违约字段 -->
        <template v-if="dialogOperation === 'fullDefault'">
          <el-form-item label="违约金" prop="penaltyAmount">
            <el-input-number
              v-model="opForm.penaltyAmount"
              :precision="2"
              :step="100"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="违约金账户" prop="penaltyAccount">
            <el-input v-model="opForm.penaltyAccount" placeholder="请输入违约金账户" />
          </el-form-item>
        </template>

        <!-- 公共备注字段 -->
        <el-form-item label="备注" prop="remark">
          <el-input v-model="opForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitOperation">确定</el-button>
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
.query-form {
  margin-bottom: 12px;
}
.action-bar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.action-tip {
  color: #909399;
  font-size: 13px;
}
.pagination-wrapper {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
