<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listUnmaturedOptions,
  closeOption,
  listEuropeanMaturedOptions,
  abandonOption,
  premiumSettle,
  listAmericanMonitoring,
  listAmericanMaturedOptions,
  executeOption
} from '@/api/option'
import {
  formatTradeStatus,
  formatOptionDirection,
  formatOptionStyle,
  formatOptionType,
  formatOptionDeliveryType,
  getStatusTagType
} from '@/utils/constants'

// 当前激活的 tab
const activeTab = ref('unmatured')

// 各 tab 对应的查询接口
const tabApiMap = {
  unmatured: listUnmaturedOptions,
  europeanMatured: listEuropeanMaturedOptions,
  premium: listUnmaturedOptions,
  americanMonitoring: listAmericanMonitoring,
  americanMatured: listAmericanMaturedOptions
}

// 查询条件表单
const queryForm = reactive({
  businessNo: '',
  customerId: '',
  optionStyle: '',
  optionType: ''
})

// 表格数据与分页
const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

// 期权类别下拉选项
const optionStyleOptions = [
  { value: '', label: '全部' },
  { value: 'AMERICAN', label: '美式' },
  { value: 'EUROPEAN', label: '欧式' }
]

// 期权种类下拉选项
const optionTypeOptions = [
  { value: '', label: '全部' },
  { value: 'CALL', label: '看涨' },
  { value: 'PUT', label: '看跌' }
]

// ===== 操作弹窗公共状态 =====
const dialogVisible = ref(false)
const dialogType = ref('')
const currentRow = ref(null)
const submitting = ref(false)
const opFormRef = ref(null)

// 操作弹窗类型 -> 标题映射
const dialogTitleMap = {
  close: '平仓',
  exercise: '执行行权',
  abandon: '放弃',
  premiumSettle: '期权费交割'
}

// 当前弹窗标题
const dialogTitle = computed(() => dialogTitleMap[dialogType.value] || '')

// 操作表单数据（各操作共用，按需使用字段）
const opForm = reactive({
  tradeId: '',
  closeDate: '',
  closeAmount: null,
  closePremium: null,
  closePnl: null,
  settlementAccount: '',
  exerciseDate: '',
  referenceRate: null,
  abandonDate: '',
  remark: ''
})

// 获取今日日期字符串 YYYY-MM-DD
function getTodayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 根据操作类型返回对应的表单校验规则
function getOpRules(type) {
  const requiredRule = (msg) => [{ required: true, message: msg, trigger: 'blur' }]
  switch (type) {
    case 'close':
      return {
        closeDate: requiredRule('请选择平仓日'),
        closeAmount: requiredRule('请输入平仓金额'),
        settlementAccount: requiredRule('请输入交割账户')
      }
    case 'exercise':
      return {
        exerciseDate: requiredRule('请选择行权日'),
        referenceRate: [
          requiredRule('请输入参考汇率'),
          { type: 'number', min: 0.00000001, message: '参考汇率必须大于0', trigger: 'blur' }
        ],
        settlementAccount: requiredRule('请输入交割账户')
      }
    case 'abandon':
      return {
        abandonDate: requiredRule('请选择放弃日')
      }
    case 'premiumSettle':
      return {
        settlementAccount: requiredRule('请输入交割账户')
      }
    default:
      return {}
  }
}

// 当前操作表单的校验规则
const opRules = computed(() => getOpRules(dialogType.value))

// 组装查询参数：合并过滤条件与分页，空值不传
function buildQueryParams() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  if (queryForm.businessNo) params.businessNo = queryForm.businessNo
  if (queryForm.customerId) params.customerId = queryForm.customerId
  if (queryForm.optionStyle) params.optionStyle = queryForm.optionStyle
  if (queryForm.optionType) params.optionType = queryForm.optionType
  return params
}

// 加载当前 tab 数据
async function loadData() {
  loading.value = true
  try {
    const api = tabApiMap[activeTab.value]
    const res = await api(buildQueryParams())
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
  queryForm.businessNo = ''
  queryForm.customerId = ''
  queryForm.optionStyle = ''
  queryForm.optionType = ''
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
  opForm.tradeId = ''
  opForm.closeDate = ''
  opForm.closeAmount = null
  opForm.closePremium = null
  opForm.closePnl = null
  opForm.settlementAccount = ''
  opForm.exerciseDate = ''
  opForm.referenceRate = null
  opForm.abandonDate = ''
  opForm.remark = ''
}

// 打开操作弹窗：记录当前行与操作类型，重置表单并预填默认值
function openOperationDialog(row, type) {
  currentRow.value = row
  dialogType.value = type
  resetOpForm()
  opForm.tradeId = row.tradeId || row.id
  if (type === 'exercise') {
    opForm.exerciseDate = getTodayStr()
    opForm.referenceRate = row.referenceRate ?? null
  }
  if (type === 'close') {
    opForm.closeDate = getTodayStr()
  }
  if (type === 'abandon') {
    opForm.abandonDate = getTodayStr()
  }
  dialogVisible.value = true
}

// 根据操作类型组装提交数据
function buildOpPayload() {
  const tradeId = currentRow.value?.tradeId || currentRow.value?.id
  const base = { tradeId, remark: opForm.remark }
  switch (dialogType.value) {
    case 'close':
      return {
        ...base,
        closeDate: opForm.closeDate,
        closeAmount: opForm.closeAmount,
        closePremium: opForm.closePremium,
        closePnl: opForm.closePnl,
        settlementAccount: opForm.settlementAccount
      }
    case 'exercise':
      return {
        ...base,
        exerciseDate: opForm.exerciseDate,
        referenceRate: opForm.referenceRate,
        settlementAccount: opForm.settlementAccount
      }
    case 'abandon':
      return {
        ...base,
        abandonDate: opForm.abandonDate
      }
    case 'premiumSettle':
      return {
        ...base,
        settlementAccount: opForm.settlementAccount
      }
    default:
      return base
  }
}

// 根据操作类型调用对应接口
async function callOperationApi(payload) {
  const apiMap = {
    close: closeOption,
    exercise: executeOption,
    abandon: abandonOption,
    premiumSettle
  }
  const api = apiMap[dialogType.value]
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
  loadData()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">期权存续期管理</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="未到期交易管理" name="unmatured" />
        <el-tab-pane label="欧式到期交易管理" name="europeanMatured" />
        <el-tab-pane label="期权费交割" name="premium" />
        <el-tab-pane label="美式期权监控" name="americanMonitoring" />
        <el-tab-pane label="美式到期交易管理" name="americanMatured" />
      </el-tabs>

      <!-- 查询条件表单 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="业务编号">
          <el-input v-model="queryForm.businessNo" placeholder="请输入业务编号" clearable />
        </el-form-item>
        <el-form-item label="客户号">
          <el-input v-model="queryForm.customerId" placeholder="请输入客户号" clearable />
        </el-form-item>
        <el-form-item label="期权类别">
          <el-select v-model="queryForm.optionStyle" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in optionStyleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期权种类">
          <el-select v-model="queryForm.optionType" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in optionTypeOptions"
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

      <!-- 交易列表表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="businessNo" label="业务编号" width="160" fixed />
        <el-table-column prop="currencyPair" label="货币对" width="100" />
        <el-table-column label="买卖方向" width="90">
          <template #default="{ row }">{{ formatOptionDirection(row.buyerSeller) }}</template>
        </el-table-column>
        <el-table-column label="期权类别" width="90">
          <template #default="{ row }">{{ formatOptionStyle(row.optionStyle) }}</template>
        </el-table-column>
        <el-table-column label="期权种类" width="90">
          <template #default="{ row }">{{ formatOptionType(row.optionType) }}</template>
        </el-table-column>
        <el-table-column prop="strikePrice" label="执行价格" width="110" />
        <el-table-column prop="notionalAmount" label="原始签约金额" width="130" />
        <el-table-column prop="tradeDate" label="交易日" width="110" />
        <el-table-column prop="maturityDate" label="到期日" width="110" />
        <el-table-column label="交割类型" width="90">
          <template #default="{ row }">{{ formatOptionDeliveryType(row.deliveryType) }}</template>
        </el-table-column>
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.optionStatus || row.status)" size="small">
              {{ formatTradeStatus(row.optionStatus || row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <!-- 未到期：平仓 -->
            <el-button
              v-if="activeTab === 'unmatured'"
              type="warning"
              size="small"
              link
              @click="openOperationDialog(row, 'close')"
            >
              平仓
            </el-button>
            <!-- 欧式到期/美式到期：执行、放弃、平仓 -->
            <template v-if="activeTab === 'europeanMatured' || activeTab === 'americanMatured'">
              <el-button type="success" size="small" link @click="openOperationDialog(row, 'exercise')">
                执行
              </el-button>
              <el-button type="danger" size="small" link @click="openOperationDialog(row, 'abandon')">
                放弃
              </el-button>
              <el-button type="warning" size="small" link @click="openOperationDialog(row, 'close')">
                平仓
              </el-button>
            </template>
            <!-- 期权费交割 -->
            <el-button
              v-if="activeTab === 'premium'"
              type="primary"
              size="small"
              link
              @click="openOperationDialog(row, 'premiumSettle')"
            >
              期权费交割
            </el-button>
            <!-- 美式期权监控：执行 -->
            <el-button
              v-if="activeTab === 'americanMonitoring'"
              type="success"
              size="small"
              link
              @click="openOperationDialog(row, 'exercise')"
            >
              执行
            </el-button>
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
        <!-- 平仓字段 -->
        <template v-if="dialogType === 'close'">
          <el-form-item label="平仓日" prop="closeDate">
            <el-date-picker
              v-model="opForm.closeDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择平仓日"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="平仓金额" prop="closeAmount">
            <el-input-number
              v-model="opForm.closeAmount"
              :precision="2"
              :step="100"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="平仓权利金" prop="closePremium">
            <el-input-number
              v-model="opForm.closePremium"
              :precision="2"
              :step="100"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="平仓损益" prop="closePnl">
            <el-input-number
              v-model="opForm.closePnl"
              :precision="2"
              :step="100"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="交割账户" prop="settlementAccount">
            <el-input v-model="opForm.settlementAccount" placeholder="请输入交割账户" />
          </el-form-item>
        </template>

        <!-- 执行行权字段 -->
        <template v-if="dialogType === 'exercise'">
          <el-form-item label="行权日" prop="exerciseDate">
            <el-date-picker
              v-model="opForm.exerciseDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择行权日"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="参考汇率" prop="referenceRate">
            <el-input-number
              v-model="opForm.referenceRate"
              :precision="8"
              :step="0.0001"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="交割账户" prop="settlementAccount">
            <el-input v-model="opForm.settlementAccount" placeholder="请输入交割账户" />
          </el-form-item>
        </template>

        <!-- 放弃字段 -->
        <template v-if="dialogType === 'abandon'">
          <el-form-item label="放弃日" prop="abandonDate">
            <el-date-picker
              v-model="opForm.abandonDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择放弃日"
              style="width: 100%"
            />
          </el-form-item>
        </template>

        <!-- 期权费交割字段 -->
        <template v-if="dialogType === 'premiumSettle'">
          <el-form-item label="交割账户" prop="settlementAccount">
            <el-input v-model="opForm.settlementAccount" placeholder="请输入交割账户" />
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
.pagination-wrapper {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
