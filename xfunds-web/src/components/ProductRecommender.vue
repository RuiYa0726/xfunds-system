<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { TrendCharts, Message, Close, ArrowRight, Promotion, Search } from '@element-plus/icons-vue'
import { getProductRecommend } from '@/api/productRecommend'

const router = useRouter()
const inputText = ref('')
const messages = ref([])
const isLoading = ref(false)
const isDraggingBall = ref(false)
const isDraggingWindow = ref(false)
const dragOffset = ref({ x: 0, y: 0 })
const ballPosition = ref({ right: '30px', bottom: '170px' })
const windowPosition = ref({ left: '50%', top: '15%' })
const showWindow = ref(false)

onMounted(() => {
  messages.value = [{
    type: 'system',
    content: '您好！我是产品推荐助手，依据客户交易量、资产规模、汇率走势等维度为您智能推荐外汇交易产品。\n请输入客户号（如 C20240001），我将为您生成专属产品推荐方案。',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }]
})

function currentTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function handleSend(forcedQuery) {
  // 当作为 @click="handleSend" 绑定时，forcedQuery 会是 MouseEvent 对象，需排除
  const query = (typeof forcedQuery === 'string' ? forcedQuery : inputText.value).trim()
  if (!query || isLoading.value) return
  messages.value.push({ type: 'user', content: query, time: currentTime() })
  inputText.value = ''
  isLoading.value = true
  try {
    const response = await getProductRecommend(query)
    if (response.code === 200 && response.data) {
      const data = response.data
      const portrait = data.portrait || {}

      // 构建画像摘要
      let content = `【客户】${data.customerName || data.customerId}\n`
      if (portrait.customerType) content += `【客户类型】${portrait.customerType === 'CORP' ? '企业客户' : '个人客户'}\n`
      if (portrait.riskLevel) content += `【风险等级】${riskText(portrait.riskLevel)}\n`
      if (portrait.rfmSegment) content += `【RFM分群】${portrait.rfmSegment}\n`
      if (portrait.frequency12m != null) content += `【近12月交易】${portrait.frequency12m} 笔，总额 ${formatMoney(portrait.monetary12mCny)} 元\n`
      if (portrait.aumLabel) content += `【资产规模】${portrait.aumLabel}（${formatMoney(portrait.aumTotalCny)} 元）\n`
      if (portrait.hasDerivativeLicense != null) content += `【衍生品资质】${portrait.hasDerivativeLicense ? '已认证' : '未认证'}\n`
      if (portrait.customerType === 'CORP' && portrait.corpType) content += `【企业类型】${corpTypeText(portrait.corpType)}\n`
      if (portrait.exposureType && portrait.exposureType !== 'NONE') content += `【敞口类型】${exposureText(portrait.exposureType)}\n`

      content += `\n========== 推荐产品 ==========\n`
      const recs = data.recommendations || []
      if (recs.length === 0) {
        content += '暂无匹配的推荐产品。'
      } else {
        recs.forEach((rec, idx) => {
          content += `\n推荐${idx + 1}：${rec.productName}（${rec.productCode}）\n`
          content += `优先级：${'★'.repeat(Math.min(rec.priority, 5))}\n`
          content += `风险层级：${riskTierText(rec.riskTier)}\n`
        })
      }
      if (data.fallback) {
        content += `\n（注：未命中精准规则，以上为兜底推荐）\n`
      }

      messages.value.push({
        type: 'system',
        content: content,
        recommendations: recs,
        time: currentTime()
      })
    } else {
      messages.value.push({
        type: 'system',
        content: response.msg || '未找到该客户或计算失败，请确认客户号后重试。',
        time: currentTime()
      })
    }
  } catch (error) {
    // 后端返回业务错误（如客户不存在）时，error.message 即后端 message
    const errMsg = error?.response?.data?.message || error?.message || '服务暂时不可用'
    messages.value.push({
      type: 'system',
      content: errMsg,
      time: currentTime()
    })
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

function riskText(level) {
  const map = { AGGRESSIVE: '激进型', BALANCED: '平衡型', CONSERVATIVE: '保守型' }
  return map[level] || level || '-'
}

function industryText(ind) {
  const map = { Manufacturing: '制造业', Trading: '贸易业', Technology: '科技业', Other: '其他' }
  return map[ind] || ind
}

function corpTypeText(type) {
  const map = {
    MANUFACTURING: '制造业',
    TRADING: '贸易业',
    SERVICE: '服务业',
    INVESTMENT_HOLDING: '投资控股',
    FINANCIAL_INSTITUTION: '金融机构',
    SME: '中小民企'
  }
  return map[type] || type
}

function exposureText(exp) {
  const map = { PAYABLE: '应付（购汇）', RECEIVABLE: '应收（结汇）', DUAL: '双向收付汇', NONE: '无' }
  return map[exp] || exp
}

function riskTierText(tier) {
  const map = { BASIC: '基础类', STANDARD: '标准套保类', COMPLEX: '复杂结构类' }
  return map[tier] || tier
}

function formatMoney(val) {
  if (val == null) return '0'
  const num = Number(val)
  if (isNaN(num)) return val
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 跳转到对应交易发起页面
function handleNavigate(productCode) {
  const routeMap = {
    SPOT: '/fx/spot-entry',
    FORWARD: '/fx/forward-entry',
    SWAP: '/fx/swap-entry',
    AMERICAN_OPTION: '/option/entry',
    EUROPEAN_OPTION: '/option/entry'
  }
  const path = routeMap[productCode]
  if (path) {
    router.push(path).catch(() => {})
    showWindow.value = false
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function scrollToBottom() {
  setTimeout(() => {
    const container = document.querySelector('.recommender-messages')
    if (container) {
      container.scrollTop = container.scrollHeight
    }
  }, 100)
}

function toggleWindow() {
  showWindow.value = !showWindow.value
}

function handleBallMouseDown(e) {
  isDraggingBall.value = true
  dragOffset.value = {
    x: e.clientX - (window.innerWidth - parseFloat(ballPosition.value.right)),
    y: e.clientY - (window.innerHeight - parseFloat(ballPosition.value.bottom))
  }
  document.addEventListener('mousemove', handleBallMouseMove)
  document.addEventListener('mouseup', handleBallMouseUp)
}

function handleBallMouseMove(e) {
  if (!isDraggingBall.value) return
  const right = Math.max(10, Math.min(window.innerWidth - 60, window.innerWidth - e.clientX + dragOffset.value.x))
  const bottom = Math.max(10, Math.min(window.innerHeight - 60, window.innerHeight - e.clientY + dragOffset.value.y))
  ballPosition.value = { right: `${right}px`, bottom: `${bottom}px` }
}

function handleBallMouseUp() {
  isDraggingBall.value = false
  document.removeEventListener('mousemove', handleBallMouseMove)
  document.removeEventListener('mouseup', handleBallMouseUp)
}

function handleWindowMouseDown(e) {
  if (e.target.closest('.recommender-header')) {
    isDraggingWindow.value = true
    dragOffset.value = {
      x: e.clientX - parseFloat(windowPosition.value.left),
      y: e.clientY - parseFloat(windowPosition.value.top)
    }
    document.addEventListener('mousemove', handleWindowMouseMove)
    document.addEventListener('mouseup', handleWindowMouseUp)
  }
}

function handleWindowMouseMove(e) {
  if (!isDraggingWindow.value) return
  windowPosition.value = {
    left: `${Math.max(10, Math.min(window.innerWidth - 540, e.clientX - dragOffset.value.x))}px`,
    top: `${Math.max(10, Math.min(window.innerHeight - 500, e.clientY - dragOffset.value.y))}px`
  }
}

function handleWindowMouseUp() {
  isDraggingWindow.value = false
  document.removeEventListener('mousemove', handleWindowMouseMove)
  document.removeEventListener('mouseup', handleWindowMouseUp)
}

onUnmounted(() => {
  document.removeEventListener('mousemove', handleBallMouseMove)
  document.removeEventListener('mouseup', handleBallMouseUp)
  document.removeEventListener('mousemove', handleWindowMouseMove)
  document.removeEventListener('mouseup', handleWindowMouseUp)
})
</script>

<template>
  <Teleport to="body">
    <div
      class="recommender-float-ball"
      :style="{ right: ballPosition.right, bottom: ballPosition.bottom }"
      @mousedown="handleBallMouseDown"
      @click="toggleWindow"
      :class="{ 'is-dragging': isDraggingBall }"
    >
      <el-icon class="ball-icon"><TrendCharts /></el-icon>
      <span class="ball-badge">推荐</span>
    </div>

    <div v-if="showWindow" class="recommender-mask" @click="showWindow = false">
      <div
        class="recommender-container"
        :style="{ left: windowPosition.left, top: windowPosition.top }"
        @mousedown="handleWindowMouseDown"
        @click.stop
      >
        <div class="recommender-header">
          <div class="recommender-title">
            <el-icon class="recommender-icon"><TrendCharts /></el-icon>
            <span>智能产品推荐助手</span>
          </div>
          <el-icon class="recommender-close" @click="showWindow = false">
            <Close />
          </el-icon>
        </div>

        <div class="recommender-messages">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-item', `message-${msg.type}`]"
          >
            <div class="message-avatar">
              <el-icon v-if="msg.type === 'system'" size="20"><Promotion /></el-icon>
              <span v-else class="user-avatar">用</span>
            </div>
            <div class="message-content">
              <div class="message-text">{{ msg.content }}</div>
              <div v-if="msg.recommendations && msg.recommendations.length" class="message-recs">
                <div
                  v-for="rec in msg.recommendations"
                  :key="rec.productCode"
                  class="rec-card"
                >
                  <div class="rec-header">
                    <span class="rec-name">{{ rec.productName }}</span>
                    <el-tag size="small" :type="rec.riskTier === 'BASIC' ? 'success' : rec.riskTier === 'STANDARD' ? 'warning' : 'danger'">
                      {{ riskTierText(rec.riskTier) }}
                    </el-tag>
                  </div>
                  <div class="rec-scenario">场景：{{ rec.scenario }}</div>
                  <div class="rec-reason">{{ rec.reason }}</div>
                  <el-button
                    type="primary"
                    size="small"
                    :icon="ArrowRight"
                    @click="handleNavigate(rec.productCode)"
                    class="rec-btn"
                  >前往办理</el-button>
                </div>
              </div>
              <div class="message-time">{{ msg.time }}</div>
            </div>
          </div>
          <div v-if="isLoading" class="loading-indicator">
            <el-icon class="loading-icon"><svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle class="path" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-dasharray="60" stroke-dashoffset="0" style="animation: dash 1.2s ease-in-out infinite"></circle></svg></el-icon>
            <span>正在分析客户画像，生成推荐方案...</span>
          </div>
        </div>

        <div class="recommender-input">
          <el-input
            v-model="inputText"
            placeholder="请输入客户号，如 C20240001"
            @keydown="handleKeydown"
            :disabled="isLoading"
            class="input-field"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button
            type="primary"
            @click="handleSend"
            :disabled="!inputText.trim() || isLoading"
            class="send-btn"
          >
            <el-icon><Message /></el-icon>
            <span>推荐</span>
          </el-button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.recommender-float-ball {
  position: fixed;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #0d7c66 0%, #16a085 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(13, 124, 102, 0.4);
  cursor: pointer;
  z-index: 10000;
  transition: transform 0.2s, box-shadow 0.2s;
}

.recommender-float-ball:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 24px rgba(13, 124, 102, 0.5);
}

.recommender-float-ball.is-dragging {
  cursor: grabbing;
  transform: scale(1.15);
  box-shadow: 0 8px 32px rgba(13, 124, 102, 0.6);
}

.ball-icon {
  font-size: 24px;
  color: #fff;
}

.ball-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 10px;
  white-space: nowrap;
}

.recommender-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 10001;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recommender-container {
  width: 530px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: fixed;
}

.recommender-header {
  height: 56px;
  background: linear-gradient(135deg, #0d7c66 0%, #16a085 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  cursor: move;
  flex-shrink: 0;
}

.recommender-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
}

.recommender-icon {
  font-size: 20px;
}

.recommender-close {
  font-size: 20px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: background 0.2s;
}

.recommender-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.recommender-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  max-height: 420px;
  background: #f0f9f6;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message-system {
  flex-direction: row;
}

.message-user {
  flex-direction: row-reverse;
}

.message-system .message-content {
  align-items: flex-start;
}

.message-user .message-content {
  align-items: flex-end;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-system .message-avatar {
  background: linear-gradient(135deg, #0d7c66 0%, #16a085 100%);
  color: #fff;
}

.message-user .message-avatar {
  background: #e6e6e6;
}

.user-avatar {
  font-size: 14px;
  color: #606266;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 78%;
}

.message-text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.message-system .message-text {
  background: #fff;
  color: #303133;
  border: 1px solid #d1e7e0;
  border-radius: 0 12px 12px 12px;
}

.message-user .message-text {
  background: linear-gradient(135deg, #0d7c66 0%, #16a085 100%);
  color: #fff;
  border-radius: 12px 0 12px 12px;
}

.message-recs {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.rec-card {
  background: #fff;
  border: 1px solid #d1e7e0;
  border-left: 4px solid #16a085;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rec-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rec-name {
  font-size: 15px;
  font-weight: 600;
  color: #0d7c66;
}

.rec-scenario {
  font-size: 12px;
  color: #909399;
}

.rec-reason {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-line !important;
  word-break: break-all;
}

.rec-btn {
  align-self: flex-start;
  font-size: 12px;
  margin-top: 4px;
}

.message-time {
  font-size: 11px;
  color: #909399;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #d1e7e0;
  font-size: 14px;
  color: #606266;
}

.loading-icon {
  font-size: 16px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes dash {
  to { stroke-dashoffset: -120; }
}

.recommender-input {
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #e6e6e6;
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.input-field {
  flex: 1;
}

.send-btn {
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
