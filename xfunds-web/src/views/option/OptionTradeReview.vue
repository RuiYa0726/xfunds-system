<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOptionDetail } from '@/api/option'
import { getCustomerAccounts } from '@/api/customer'
import { approveTrade, rejectTrade } from '@/api/trade'
import { getTaskDetail } from '@/api/task'
import {
  formatOptionDirection,
  formatOptionStyle,
  formatOptionDeliveryType,
  formatOptionSettlementMethod
} from '@/utils/constants'

const router = useRouter()
const route = useRoute()

// 加载状态
const loading = ref(false)
// 操作中状态
const approving = ref(false)

// 交易详情数据
const detail = ref(null)
// 客户账户列表（用于账户标签展示）
const accounts = ref([])
// 当前任务ID（从路由参数获取）
const taskId = ref(route.query.taskId || '')
// 任务业务类型（ABANDON/EXERCISE/PREMIUM_SETTLE，用于动态显示标题）
const businessType = ref(route.query.businessType || '')

// 复核页面标题：根据 businessType 动态显示
const pageTitle = computed(() => {
  if (businessType.value === 'ABANDON') return '放弃期权复核'
  if (businessType.value === 'EXERCISE') return '执行期权复核'
  if (businessType.value === 'PREMIUM_SETTLE') return '期权费交割复核'
  return '期权交易复核'
})

// 表单数据（从后端加载后回填，全部只读）
const form = reactive({
  customerId: '',
  customerName: '',
  currencyPair: 'USD/CNY',
  baseCurrency: 'USD',
  quoteCurrency: 'CNY',
  currency1Account: '',
  currency2Account: '',
  premiumAccountId: '',
  buyerSeller: 'BUY',
  optionType: 'CALL',
  priceDirection: 'UP',
  spotRate: null,
  strikePrice: null,
  optionStyle: 'EUROPEAN',
  exerciseTimePoint: '15:00:00',
  tradeDate: '',
  maturityDate: '',
  deliveryType: 'T2',
  deliveryDate: '',
  days: null,
  premiumValueDate: '',
  settlementMethod: 'FULL',
  notionalAmount: null,
  premiumAmount: null,
  premiumCurrency: 'CNY',
  observationStartDate: '',
  observationEndDate: '',
  purposeCode: '',
  fxPurposeCode: '',
  branchName: ''
})

// 审批意见
const approvalComment = ref('')

// 是否美式期权
const isAmerican = computed(() => form.optionStyle === 'AMERICAN')
// 是否欧式期权
const isEuropean = computed(() => form.optionStyle === 'EUROPEAN')

// 基础货币名称（从货币对自动派生）：USD/CNY → USD
const baseCurrencyName = computed(() => {
  const parts = (form.currencyPair || '').split('/')
  return parts.length === 2 ? parts[0].trim().toUpperCase() : ''
})

// 报价货币名称（从货币对自动派生）：USD/CNY → CNY
const quoteCurrencyName = computed(() => {
  const parts = (form.currencyPair || '').split('/')
  return parts.length === 2 ? parts[1].trim().toUpperCase() : ''
})

// 涨跌方向中文
function priceDirectionText(direction) {
  return direction === 'UP' ? '涨' : direction === 'DOWN' ? '跌' : '-'
}

// 根据账户ID查找账户信息
function findAccount(accountIdStr) {
  if (!accountIdStr || !accounts.value.length) return null
  return accounts.value.find((a) => String(a.accountId) === String(accountIdStr))
}

// 账户显示标签：账号 | 币种 | 余额
function accountLabel(accountIdStr) {
  const acc = findAccount(accountIdStr)
  if (!acc) return accountIdStr || '-'
  return `${acc.accountNo} | ${acc.currency} | 余额 ${acc.balance ?? 0}`
}

// 加载交易详情并回填表单
async function loadDetail() {
  const tradeId = route.query.tradeId
  if (!tradeId) {
    ElMessage.error('未获取到交易ID')
    return
  }
  loading.value = true
  try {
    // 若未通过路由参数传递 businessType，则通过任务详情接口获取
    if (!businessType.value && taskId.value) {
      try {
        const taskRes = await getTaskDetail(taskId.value)
        businessType.value = taskRes.data?.businessType || ''
      } catch (e) {
        // 忽略错误，使用默认标题
      }
    }
    const res = await getOptionDetail(tradeId)
    detail.value = res.data
    const master = res.data?.master
    const option = res.data?.optionDetail
    if (!master || !option) {
      ElMessage.error('交易详情数据不完整')
      return
    }
    // 回填主表字段
    form.customerId = master.customerId || ''
    form.customerName = master.customerName || ''
    form.currencyPair = master.currencyPair || 'USD/CNY'
    form.baseCurrency = master.baseCurrency || form.currencyPair.split('/')[0]
    form.quoteCurrency = master.quoteCurrency || form.currencyPair.split('/')[1]
    form.buyerSeller = master.tradeDirection || 'BUY'
    form.spotRate = master.spotRate ?? null
    form.tradeDate = master.tradeDate || ''
    form.maturityDate = master.maturityDate || ''
    form.deliveryType = master.deliveryType || 'T2'
    form.deliveryDate = master.valueDate || ''
    form.branchName = master.branchName || master.branchCode || ''
    form.purposeCode = master.purposeCode || ''
    form.fxPurposeCode = master.fxPurposeCode || ''
    // 回填期权子表字段
    form.currency1Account = option.currency1Account || ''
    form.currency2Account = option.currency2Account || ''
    form.premiumAccountId = option.premiumAccountId || ''
    form.optionType = option.optionType || 'CALL'
    form.priceDirection = option.optionType === 'CALL' ? 'UP' : 'DOWN'
    form.strikePrice = option.strikePrice ?? null
    form.optionStyle = option.optionStyle || 'EUROPEAN'
    form.notionalAmount = option.notionalAmount ?? null
    form.premiumAmount = option.premiumAmount ?? null
    form.premiumCurrency = option.premiumCurrency || 'CNY'
    form.premiumValueDate = option.premiumValueDate || ''
    form.observationStartDate = option.observationStartDate || ''
    form.observationEndDate = option.observationEndDate || ''
    form.days = option.days ?? null
    form.settlementMethod = option.settlementMethod || 'FULL'
    // 行权时点：后端存储格式为 "yyyy-MM-ddTHH:mm:ss"，界面只显示 HH:mm:ss 部分
    if (option.exerciseTimePoint) {
      const parts = option.exerciseTimePoint.split('T')
      form.exerciseTimePoint = parts.length === 2 ? parts[1] : option.exerciseTimePoint
    }

    // 加载客户账户列表（用于账户标签展示）
    if (master.customerId) {
      try {
        const accRes = await getCustomerAccounts(master.customerId)
        accounts.value = accRes.data || []
      } catch (e) {
        accounts.value = []
      }
    }
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

// 组装审批提交数据
function buildApprovalPayload(action) {
  return {
    taskId: taskId.value,
    tradeId: route.query.tradeId,
    action,
    comment: approvalComment.value
  }
}

// 执行审批操作：通过/拒绝（期权复核不允许退回经办修改）
async function handleApproval(action) {
  if (!taskId.value) {
    ElMessage.warning('未获取到任务ID')
    return
  }
  approving.value = true
  try {
    const payload = buildApprovalPayload(action)
    const apiMap = {
      approve: approveTrade,
      reject: rejectTrade
    }
    const api = apiMap[action]
    if (!api) throw new Error('未知审批操作')
    await api(payload)
    const actionLabel = { approve: '通过', reject: '拒绝' }[action]
    ElMessage.success(`${actionLabel}成功`)
    router.push('/fx/todo')
  } catch (e) {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    approving.value = false
  }
}

// 取消：返回待办列表
function handleCancel() {
  router.push('/fx/todo')
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span class="page-title">{{ pageTitle }}</span>
      </template>

      <el-form
        :model="form"
        label-width="130px"
        class="trade-form"
      >
        <!-- 客户信息 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户号">
              <el-input v-model="form.customerId" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="form.customerName" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 货币对 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交易货币对">
              <el-input v-model="form.currencyPair" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="买卖方向">
              <el-radio-group v-model="form.buyerSeller" disabled>
                <el-radio value="BUY">买入</el-radio>
                <el-radio value="SELL">卖出</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 账户信息 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="`${baseCurrencyName}账户`">
              <el-input :model-value="accountLabel(form.currency1Account)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="`${quoteCurrencyName}账户`">
              <el-input :model-value="accountLabel(form.currency2Account)" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权费账户">
              <el-input :model-value="accountLabel(form.premiumAccountId)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="涨跌方向">
              <el-radio-group v-model="form.priceDirection" disabled>
                <el-radio value="UP">涨</el-radio>
                <el-radio value="DOWN">跌</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 汇率信息 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="即期汇率">
              <el-input-number
                v-model="form.spotRate"
                :precision="4"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行价格">
              <el-input-number
                v-model="form.strikePrice"
                :precision="4"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 期权类别与行权时点 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权类别">
              <el-radio-group v-model="form.optionStyle" disabled>
                <el-radio value="AMERICAN">美式</el-radio>
                <el-radio value="EUROPEAN">欧式</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行权时点">
              <el-input :model-value="form.exerciseTimePoint" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 交易日 + 交割类型 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交易日">
              <el-date-picker
                v-model="form.tradeDate"
                type="date"
                value-format="YYYY-MM-DD"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交割类型">
              <el-radio-group v-model="form.deliveryType" disabled>
                <el-radio value="T0">T+0</el-radio>
                <el-radio value="T1">T+1</el-radio>
                <el-radio value="T2">T+2</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 美式期权：观察期开始日 / 观察期结束日 -->
        <el-row v-if="isAmerican" :gutter="20">
          <el-col :span="12">
            <el-form-item label="观察期开始日">
              <el-date-picker
                v-model="form.observationStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="观察期结束日">
              <el-date-picker
                v-model="form.observationEndDate"
                type="date"
                value-format="YYYY-MM-DD"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 欧式期权：到期日 / 交割日 -->
        <el-row v-if="isEuropean" :gutter="20">
          <el-col :span="12">
            <el-form-item label="到期日">
              <el-date-picker
                v-model="form.maturityDate"
                type="date"
                value-format="YYYY-MM-DD"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交割日">
              <el-date-picker
                v-model="form.deliveryDate"
                type="date"
                value-format="YYYY-MM-DD"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 欧式期权：天数 + 期权费交割日 -->
        <el-row v-if="isEuropean" :gutter="20">
          <el-col :span="12">
            <el-form-item label="天数">
              <el-input-number
                v-model="form.days"
                :min="0"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期权费交割日">
              <el-date-picker
                v-model="form.premiumValueDate"
                type="date"
                value-format="YYYY-MM-DD"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 美式期权：期权费交割日 + 交割方式 -->
        <el-row v-if="isAmerican" :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权费交割日">
              <el-date-picker
                v-model="form.premiumValueDate"
                type="date"
                value-format="YYYY-MM-DD"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交割方式">
              <el-input model-value="全额交割" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 欧式期权：交割方式 + 面值 -->
        <el-row v-if="isEuropean" :gutter="20">
          <el-col :span="12">
            <el-form-item label="交割方式">
              <el-input model-value="全额交割" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="`面值（${baseCurrencyName}）`">
              <el-input-number
                v-model="form.notionalAmount"
                :precision="2"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 美式期权：面值（单独一行） -->
        <el-row v-if="isAmerican" :gutter="20">
          <el-col :span="12">
            <el-form-item :label="`面值（${baseCurrencyName}）`">
              <el-input-number
                v-model="form.notionalAmount"
                :precision="2"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 期权费金额 + 期权费币种 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="期权费金额">
              <el-input-number
                v-model="form.premiumAmount"
                :precision="2"
                :controls="false"
                disabled
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期权费币种">
              <el-input v-model="form.premiumCurrency" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 用途编码 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用途编码">
              <el-input v-model="form.purposeCode" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结售汇用途编码">
              <el-input v-model="form.fxPurposeCode" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 交易机构 -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交易机构">
              <el-input v-model="form.branchName" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 审批意见 -->
        <el-divider content-position="left">审批处理</el-divider>
        <el-form-item label="审批意见">
          <el-input
            v-model="approvalComment"
            type="textarea"
            :rows="3"
            placeholder="请输入审批意见"
          />
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <el-button type="primary" :loading="approving" @click="handleApproval('approve')">通过</el-button>
          <el-button type="danger" :loading="approving" @click="handleApproval('reject')">拒绝</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
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
</style>
