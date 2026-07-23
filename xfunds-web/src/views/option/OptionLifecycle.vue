<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listEuropeanMaturedOptions,
  listAmericanMaturedOptions,
  executeOption,
  abandonOption,
  getOptionDetail
} from '@/api/option'
import { getCustomerAccounts } from '@/api/customer'
import {
  formatTradeStatus,
  formatOptionDirection,
  formatOptionStyle,
  formatOptionType,
  formatOptionDeliveryType,
  formatOptionSettlementMethod,
  getStatusTagType
} from '@/utils/constants'

// 当前激活的 tab：europeanMatured=欧式交易管理，americanMatured=美式交易管理
const activeTab = ref('europeanMatured')

// 各 tab 对应的查询接口
const tabApiMap = {
  europeanMatured: listEuropeanMaturedOptions,
  americanMatured: listAmericanMaturedOptions
}

// 查询条件表单
const queryForm = reactive({
  businessNo: '',
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

// ===== 执行弹窗状态 =====
const executeVisible = ref(false)
const executeData = ref(null)
const executeLoading = ref(false)
const executing = ref(false)

// ===== 放弃弹窗状态 =====
const abandonVisible = ref(false)
const abandonData = ref(null)
const abandonLoading = ref(false)
const abandoning = ref(false)

// 客户账户列表（用于展示账户号、币种、余额）
const executeAccounts = ref([])
const abandonAccounts = ref([])

// 当前操作的行
const currentRow = ref(null)

// 是否欧式期权（根据执行弹窗数据判断）
const isEuropeanExecute = computed(() => {
  return executeData.value?.optionDetail?.optionStyle === 'EUROPEAN'
})

// 是否美式期权（根据执行弹窗数据判断）
const isAmericanExecute = computed(() => {
  return executeData.value?.optionDetail?.optionStyle === 'AMERICAN'
})

// 是否欧式期权（根据放弃弹窗数据判断）
const isEuropeanAbandon = computed(() => {
  return abandonData.value?.optionDetail?.optionStyle === 'EUROPEAN'
})

// 是否美式期权（根据放弃弹窗数据判断）
const isAmericanAbandon = computed(() => {
  return abandonData.value?.optionDetail?.optionStyle === 'AMERICAN'
})

// 执行弹窗：基础货币名称（从货币对派生）：USD/CNY → USD
const executeBaseCurrencyName = computed(() => {
  const pair = executeData.value?.master?.currencyPair || ''
  const parts = pair.split('/')
  return parts.length === 2 ? parts[0].trim().toUpperCase() : ''
})

// 执行弹窗：报价货币名称（从货币对派生）：USD/CNY → CNY
const executeQuoteCurrencyName = computed(() => {
  const pair = executeData.value?.master?.currencyPair || ''
  const parts = pair.split('/')
  return parts.length === 2 ? parts[1].trim().toUpperCase() : ''
})

// 执行弹窗：面值金额
const executeNotionalAmount = computed(() => executeData.value?.master?.notionalAmount ?? '-')

// 放弃弹窗：基础货币名称（从货币对派生）：USD/CNY → USD
const abandonBaseCurrencyName = computed(() => {
  const pair = abandonData.value?.master?.currencyPair || ''
  const parts = pair.split('/')
  return parts.length === 2 ? parts[0].trim().toUpperCase() : ''
})

// 放弃弹窗：报价货币名称（从货币对派生）：USD/CNY → CNY
const abandonQuoteCurrencyName = computed(() => {
  const pair = abandonData.value?.master?.currencyPair || ''
  const parts = pair.split('/')
  return parts.length === 2 ? parts[1].trim().toUpperCase() : ''
})

// 放弃弹窗：面值金额
const abandonNotionalAmount = computed(() => abandonData.value?.master?.notionalAmount ?? '-')

// 执行弹窗标题
const executeTitle = computed(() => {
  const style = executeData.value?.optionDetail?.optionStyle
  return style === 'AMERICAN' ? '美式期权 - 执行行权' : style === 'EUROPEAN' ? '欧式期权 - 执行行权' : '期权 - 执行行权'
})

// 放弃弹窗标题
const abandonTitle = computed(() => {
  const style = abandonData.value?.optionDetail?.optionStyle
  return style === 'AMERICAN' ? '美式期权 - 放弃期权' : style === 'EUROPEAN' ? '欧式期权 - 放弃期权' : '期权 - 放弃期权'
})

// 获取今日日期字符串 YYYY-MM-DD
function getTodayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 根据账户ID查找账户信息（从指定账户列表中匹配）
function findAccount(accountIdStr, accountsList) {
  if (!accountIdStr || !accountsList.length) return null
  return accountsList.find((a) => String(a.accountId) === String(accountIdStr))
}

// 执行弹窗的账户显示标签：账号 | 币种 | 余额
function executeAccountLabel(accountIdStr) {
  const acc = findAccount(accountIdStr, executeAccounts.value)
  if (!acc) return accountIdStr || '-'
  return `${acc.accountNo} | ${acc.currency} | 余额 ${acc.balance ?? 0}`
}

// 放弃弹窗的账户显示标签：账号 | 币种 | 余额
function abandonAccountLabel(accountIdStr) {
  const acc = findAccount(accountIdStr, abandonAccounts.value)
  if (!acc) return accountIdStr || '-'
  return `${acc.accountNo} | ${acc.currency} | 余额 ${acc.balance ?? 0}`
}

// 涨跌方向中文
function priceDirectionText(optionType) {
  // CALL→涨，PUT→跌
  if (optionType === 'CALL') return '涨'
  if (optionType === 'PUT') return '跌'
  return '-'
}

// 组装查询参数
function buildQueryParams() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  if (queryForm.businessNo) params.businessNo = queryForm.businessNo
  if (queryForm.customerId) params.customerId = queryForm.customerId
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

// 点击查询按钮
function handleQuery() {
  pagination.pageNum = 1
  loadData()
}

// 点击重置按钮
function handleReset() {
  queryForm.businessNo = ''
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

// 切换 tab 时重新加载
function handleTabChange() {
  pagination.pageNum = 1
  loadData()
}

// 打开执行弹窗：加载交易详情和客户账户
async function openExecuteDialog(row) {
  currentRow.value = row
  executeVisible.value = true
  executeLoading.value = true
  executeData.value = null
  executeAccounts.value = []
  try {
    // 加载期权交易详情（主表 + 期权子表 + 生命周期 + 审批日志）
    const res = await getOptionDetail(row.tradeId)
    executeData.value = res.data
    // 加载客户账户列表（用于显示账户号、币种、余额）
    const master = res.data?.master
    if (master?.customerId) {
      try {
        const accRes = await getCustomerAccounts(master.customerId)
        executeAccounts.value = accRes.data || []
      } catch (e) {
        executeAccounts.value = []
      }
    }
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    executeLoading.value = false
  }
}

// 关闭执行弹窗
function closeExecuteDialog() {
  executeVisible.value = false
  executeData.value = null
  executeAccounts.value = []
  currentRow.value = null
}

// 打开放弃弹窗：加载交易详情和客户账户
async function openAbandonDialog(row) {
  currentRow.value = row
  abandonVisible.value = true
  abandonLoading.value = true
  abandonData.value = null
  abandonAccounts.value = []
  try {
    // 加载期权交易详情（主表 + 期权子表 + 生命周期 + 审批日志）
    const res = await getOptionDetail(row.tradeId)
    abandonData.value = res.data
    // 加载客户账户列表（用于显示账户号、币种、余额）
    const master = res.data?.master
    if (master?.customerId) {
      try {
        const accRes = await getCustomerAccounts(master.customerId)
        abandonAccounts.value = accRes.data || []
      } catch (e) {
        abandonAccounts.value = []
      }
    }
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    abandonLoading.value = false
  }
}

// 关闭放弃弹窗
function closeAbandonDialog() {
  abandonVisible.value = false
  abandonData.value = null
  abandonAccounts.value = []
  currentRow.value = null
}

// 点击执行：确认后调用 executeOption API（审批制：提交后等待复核通过才扣款）
async function handleExecute() {
  const master = executeData.value?.master
  const option = executeData.value?.optionDetail
  if (!master || !option) return

  try {
    await ElMessageBox.confirm(
      `确认提交执行该期权交易？提交后将等待复核通过后扣除 ${executeBaseCurrencyName.value} 账户冻结的 ${executeNotionalAmount.value} 面值。`,
      '确认提交执行',
      {
        confirmButtonText: '确认提交',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (e) {
    // 用户取消
    return
  }

  executing.value = true
  try {
    // 行权日=今天，参考汇率=交易录入时的即期汇率
    const payload = {
      tradeId: master.tradeId,
      exerciseDate: getTodayStr(),
      referenceRate: option.referenceRate || master.spotRate,
      remark: '存续期管理执行行权'
    }
    await executeOption(payload)
    ElMessage.success('行权申请已提交，等待复核通过后扣除账户冻结金额')
    executeVisible.value = false
    loadData()
  } catch (e) {
    // 错误已由拦截器提示（如"欧式期权未到执行日"等）
  } finally {
    executing.value = false
  }
}

// 点击放弃：确认后调用 abandonOption API（审批制：提交后等待复核通过才解冻）
async function handleAbandon() {
  const master = abandonData.value?.master
  if (!master) return

  try {
    await ElMessageBox.confirm(
      `确认提交放弃该期权交易？提交后将等待复核通过后解冻 ${abandonBaseCurrencyName.value} 账户冻结的 ${abandonNotionalAmount.value} 面值。`,
      '确认提交放弃',
      {
        confirmButtonText: '确认提交',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (e) {
    // 用户取消
    return
  }

  abandoning.value = true
  try {
    const payload = {
      tradeId: master.tradeId,
      remark: '存续期管理放弃期权'
    }
    await abandonOption(payload)
    ElMessage.success('放弃申请已提交，等待复核通过后解冻账户余额')
    abandonVisible.value = false
    loadData()
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    abandoning.value = false
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
        <span class="page-title">期权存续期管理</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="欧式交易管理" name="europeanMatured" />
        <el-tab-pane label="美式交易管理" name="americanMatured" />
      </el-tabs>

      <!-- 查询条件表单 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="业务编号">
          <el-input v-model="queryForm.businessNo" placeholder="请输入业务编号" clearable />
        </el-form-item>
        <el-form-item label="客户号">
          <el-input v-model="queryForm.customerId" placeholder="请输入客户号" clearable />
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
          <template #default="{ row }">{{ formatOptionDirection(row.tradeDirection) }}</template>
        </el-table-column>
        <el-table-column label="面值（币种1）" width="130" prop="notionalAmount" />
        <el-table-column prop="tradeDate" label="交易日" width="110" />
        <el-table-column prop="maturityDate" label="到期日" width="110" />
        <el-table-column label="交割类型" width="90">
          <template #default="{ row }">{{ formatOptionDeliveryType(row.deliveryType) }}</template>
        </el-table-column>
        <el-table-column prop="customerId" label="客户号" width="110" />
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ formatTradeStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" link @click="openExecuteDialog(row)">
              执行
            </el-button>
            <el-button type="danger" size="small" link @click="openAbandonDialog(row)">
              放弃
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

    <!-- 执行弹窗：展示4个模块的只读信息 + 执行按钮 -->
    <el-dialog
      v-model="executeVisible"
      :title="executeTitle"
      width="900px"
      destroy-on-close
      @close="closeExecuteDialog"
    >
      <div v-loading="executeLoading">
        <template v-if="executeData">
          <!-- 模块一：客户信息 -->
          <el-divider content-position="left">客户信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="客户号" label-width="100px">
                <el-input :model-value="executeData.master?.customerId || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="客户名称" label-width="100px">
                <el-input :model-value="executeData.master?.customerName || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="`${executeBaseCurrencyName}账户`" label-width="100px">
                <el-input :model-value="executeAccountLabel(executeData.optionDetail?.currency1Account)" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item :label="`${executeQuoteCurrencyName}账户`" label-width="100px">
                <el-input :model-value="executeAccountLabel(executeData.optionDetail?.currency2Account)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权费账户" label-width="100px">
                <el-input :model-value="executeAccountLabel(executeData.optionDetail?.premiumAccountId)" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 模块二：基础信息 -->
          <el-divider content-position="left">基础信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="业务编号" label-width="100px">
                <el-input :model-value="executeData.master?.businessNo || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="买卖方向" label-width="100px">
                <el-input :model-value="formatOptionDirection(executeData.master?.tradeDirection)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="货币对" label-width="100px">
                <el-input :model-value="executeData.master?.currencyPair || '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item :label="`面值（${executeBaseCurrencyName}）`" label-width="100px">
                <el-input :model-value="executeData.optionDetail?.notionalAmount ?? '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="涨跌方向" label-width="100px">
                <el-input :model-value="priceDirectionText(executeData.optionDetail?.optionType)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="即期汇率" label-width="100px">
                <el-input :model-value="executeData.master?.spotRate ?? '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="执行价格" label-width="100px">
                <el-input :model-value="executeData.optionDetail?.strikePrice ?? '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 模块三：交易要素（美式和欧式不同） -->
          <el-divider content-position="left">交易要素</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="交易机构" label-width="100px">
                <el-input :model-value="executeData.master?.branchName || executeData.master?.branchCode || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权类别" label-width="100px">
                <el-input :model-value="formatOptionStyle(executeData.optionDetail?.optionStyle)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="行权时点" label-width="100px">
                <el-input model-value="15:00:00" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="交易日" label-width="100px">
                <el-input :model-value="executeData.master?.tradeDate || '-'" disabled />
              </el-form-item>
            </el-col>
            <!-- 美式期权：观察期开始日、观察期结束日、交割类型、期权费交割日、交割方式 -->
            <template v-if="isAmericanExecute">
              <el-col :span="8">
                <el-form-item label="观察期开始日" label-width="100px">
                  <el-input :model-value="executeData.optionDetail?.observationStartDate || '-'" disabled />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="观察期结束日" label-width="100px">
                  <el-input :model-value="executeData.optionDetail?.observationEndDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
            <!-- 欧式期权：到期日、交割类型、交割日、天数、期权费交割日、交割方式 -->
            <template v-if="isEuropeanExecute">
              <el-col :span="8">
                <el-form-item label="到期日" label-width="100px">
                  <el-input :model-value="executeData.master?.maturityDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="交割类型" label-width="100px">
                <el-input :model-value="formatOptionDeliveryType(executeData.master?.deliveryType)" disabled />
              </el-form-item>
            </el-col>
            <!-- 欧式期权额外显示交割日和天数 -->
            <template v-if="isEuropeanExecute">
              <el-col :span="8">
                <el-form-item label="交割日" label-width="100px">
                  <el-input :model-value="executeData.master?.valueDate || '-'" disabled />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="天数" label-width="100px">
                  <el-input :model-value="executeData.optionDetail?.days ?? '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
            <!-- 美式期权额外显示期权费交割日 -->
            <template v-if="isAmericanExecute">
              <el-col :span="8">
                <el-form-item label="期权费交割日" label-width="100px">
                  <el-input :model-value="executeData.optionDetail?.premiumValueDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
          </el-row>
          <el-row :gutter="20">
            <!-- 欧式期权：期权费交割日和交割方式 -->
            <template v-if="isEuropeanExecute">
              <el-col :span="8">
                <el-form-item label="期权费交割日" label-width="100px">
                  <el-input :model-value="executeData.optionDetail?.premiumValueDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
            <el-col :span="8">
              <el-form-item label="交割方式" label-width="100px">
                <el-input :model-value="formatOptionSettlementMethod(executeData.optionDetail?.settlementMethod)" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 模块四：费用/报价 -->
          <el-divider content-position="left">费用/报价</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="期权费" label-width="100px">
                <el-input :model-value="executeData.optionDetail?.premiumAmount ?? '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权费账户" label-width="100px">
                <el-input :model-value="executeAccountLabel(executeData.optionDetail?.premiumAccountId)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权费币种" label-width="100px">
                <el-input :model-value="executeData.optionDetail?.premiumCurrency || '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 执行说明：使用具体币种和金额 -->
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            style="margin-top: 16px"
          >
            <template #default>
              点击"执行"按钮后，将提交行权申请。复核通过后将扣除 {{ executeBaseCurrencyName }} 账户对应冻结的 {{ executeNotionalAmount }} 面值。
            </template>
          </el-alert>
        </template>
      </div>

      <!-- 弹窗底部：执行按钮 -->
      <template #footer>
        <el-button @click="closeExecuteDialog">关闭</el-button>
        <el-button type="success" :loading="executing" @click="handleExecute">执行</el-button>
      </template>
    </el-dialog>

    <!-- 放弃弹窗：展示4个模块的只读信息 + 放弃按钮 -->
    <el-dialog
      v-model="abandonVisible"
      :title="abandonTitle"
      width="900px"
      destroy-on-close
      @close="closeAbandonDialog"
    >
      <div v-loading="abandonLoading">
        <template v-if="abandonData">
          <!-- 模块一：客户信息 -->
          <el-divider content-position="left">客户信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="客户号" label-width="100px">
                <el-input :model-value="abandonData.master?.customerId || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="客户名称" label-width="100px">
                <el-input :model-value="abandonData.master?.customerName || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="`${abandonBaseCurrencyName}账户`" label-width="100px">
                <el-input :model-value="abandonAccountLabel(abandonData.optionDetail?.currency1Account)" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item :label="`${abandonQuoteCurrencyName}账户`" label-width="100px">
                <el-input :model-value="abandonAccountLabel(abandonData.optionDetail?.currency2Account)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权费账户" label-width="100px">
                <el-input :model-value="abandonAccountLabel(abandonData.optionDetail?.premiumAccountId)" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 模块二：基础信息 -->
          <el-divider content-position="left">基础信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="业务编号" label-width="100px">
                <el-input :model-value="abandonData.master?.businessNo || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="买卖方向" label-width="100px">
                <el-input :model-value="formatOptionDirection(abandonData.master?.tradeDirection)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="货币对" label-width="100px">
                <el-input :model-value="abandonData.master?.currencyPair || '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item :label="`面值（${abandonBaseCurrencyName}）`" label-width="100px">
                <el-input :model-value="abandonData.optionDetail?.notionalAmount ?? '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="涨跌方向" label-width="100px">
                <el-input :model-value="priceDirectionText(abandonData.optionDetail?.optionType)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="即期汇率" label-width="100px">
                <el-input :model-value="abandonData.master?.spotRate ?? '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="执行价格" label-width="100px">
                <el-input :model-value="abandonData.optionDetail?.strikePrice ?? '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 模块三：交易要素（美式和欧式不同） -->
          <el-divider content-position="left">交易要素</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="交易机构" label-width="100px">
                <el-input :model-value="abandonData.master?.branchName || abandonData.master?.branchCode || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权类别" label-width="100px">
                <el-input :model-value="formatOptionStyle(abandonData.optionDetail?.optionStyle)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="行权时点" label-width="100px">
                <el-input model-value="15:00:00" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="交易日" label-width="100px">
                <el-input :model-value="abandonData.master?.tradeDate || '-'" disabled />
              </el-form-item>
            </el-col>
            <!-- 美式期权：观察期开始日、观察期结束日、交割类型、期权费交割日、交割方式 -->
            <template v-if="isAmericanAbandon">
              <el-col :span="8">
                <el-form-item label="观察期开始日" label-width="100px">
                  <el-input :model-value="abandonData.optionDetail?.observationStartDate || '-'" disabled />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="观察期结束日" label-width="100px">
                  <el-input :model-value="abandonData.optionDetail?.observationEndDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
            <!-- 欧式期权：到期日、交割类型、交割日、天数、期权费交割日、交割方式 -->
            <template v-if="isEuropeanAbandon">
              <el-col :span="8">
                <el-form-item label="到期日" label-width="100px">
                  <el-input :model-value="abandonData.master?.maturityDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="交割类型" label-width="100px">
                <el-input :model-value="formatOptionDeliveryType(abandonData.master?.deliveryType)" disabled />
              </el-form-item>
            </el-col>
            <!-- 欧式期权额外显示交割日和天数 -->
            <template v-if="isEuropeanAbandon">
              <el-col :span="8">
                <el-form-item label="交割日" label-width="100px">
                  <el-input :model-value="abandonData.master?.valueDate || '-'" disabled />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="天数" label-width="100px">
                  <el-input :model-value="abandonData.optionDetail?.days ?? '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
            <!-- 美式期权额外显示期权费交割日 -->
            <template v-if="isAmericanAbandon">
              <el-col :span="8">
                <el-form-item label="期权费交割日" label-width="100px">
                  <el-input :model-value="abandonData.optionDetail?.premiumValueDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
          </el-row>
          <el-row :gutter="20">
            <!-- 欧式期权：期权费交割日和交割方式 -->
            <template v-if="isEuropeanAbandon">
              <el-col :span="8">
                <el-form-item label="期权费交割日" label-width="100px">
                  <el-input :model-value="abandonData.optionDetail?.premiumValueDate || '-'" disabled />
                </el-form-item>
              </el-col>
            </template>
            <el-col :span="8">
              <el-form-item label="交割方式" label-width="100px">
                <el-input :model-value="formatOptionSettlementMethod(abandonData.optionDetail?.settlementMethod)" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 模块四：费用/报价 -->
          <el-divider content-position="left">费用/报价</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="期权费" label-width="100px">
                <el-input :model-value="abandonData.optionDetail?.premiumAmount ?? '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权费账户" label-width="100px">
                <el-input :model-value="abandonAccountLabel(abandonData.optionDetail?.premiumAccountId)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期权费币种" label-width="100px">
                <el-input :model-value="abandonData.optionDetail?.premiumCurrency || '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 放弃说明：使用具体币种和金额 -->
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            style="margin-top: 16px"
          >
            <template #default>
              点击"确认提交"按钮后，将提交放弃申请。复核通过后将解冻 {{ abandonBaseCurrencyName }} 账户对应冻结的 {{ abandonNotionalAmount }} 面值，余额不变。
            </template>
          </el-alert>
        </template>
      </div>

      <!-- 弹窗底部：放弃按钮 -->
      <template #footer>
        <el-button @click="closeAbandonDialog">关闭</el-button>
        <el-button type="danger" :loading="abandoning" @click="handleAbandon">放弃</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
}

.page-title {
  font-size: 16px;
  font-weight: bold;
}

.query-form {
  margin-bottom: 16px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
