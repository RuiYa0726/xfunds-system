<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ChatLineRound, Message, Close, ArrowRight, Menu } from '@element-plus/icons-vue';
import { searchMenuPath } from '@/api/menuNav';

const router = useRouter();
const inputText = ref('');
const messages = ref([]);
const isLoading = ref(false);
const isDraggingBall = ref(false);
const isDraggingWindow = ref(false);
const dragOffset = ref({ x: 0, y: 0 });
const ballPosition = ref({ right: '30px', bottom: '30px' });
const windowPosition = ref({ left: '50%', top: '20%' });
const showWindow = ref(false);
// 会话ID：用于维持多轮澄清上下文，组件存活期间保持不变
const sessionId = ref(`nav-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`);

onMounted(() => {
  messages.value = [{
    type: 'system',
    content: '您好！我是菜单导航助手，请问您想办理什么业务？',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }];
});

function currentTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

async function handleSend(forcedQuery) {
  const query = (forcedQuery !== undefined ? forcedQuery : inputText.value).trim();
  if (!query || isLoading.value) return;
  messages.value.push({ type: 'user', content: query, time: currentTime() });
  inputText.value = '';
  isLoading.value = true;
  try {
    const response = await searchMenuPath(query, sessionId.value);
    if (response.code === 200 && response.data) {
      const data = response.data;
      if (data.needClarification) {
        // 需要澄清：展示反问问题及快捷可选项
        messages.value.push({
          type: 'system',
          content: data.clarificationQuestion,
          clarificationOptions: data.clarificationOptions || [],
          matchedTradeType: data.matchedTradeType,
          matchedSubType: data.matchedSubType,
          time: currentTime()
        });
      } else {
        const menuPathStr = data.menuPath ? data.menuPath.join(' > ') : '';
        let content = '';
        if (data.description) content += `【业务类型】${data.description}\n`;
        if (data.tradeType) content += `【交易类型】${data.tradeType}\n`;
        if (data.subType) content += `【子类型】${data.subType}\n`;
        if (menuPathStr) content += `\n【推荐菜单路径】\n${menuPathStr}\n`;
        messages.value.push({
          type: 'system',
          content: content || '已为您找到对应菜单。',
          routePath: data.routePath,
          menuPath: data.menuPath,
          time: currentTime()
        });
      }
    } else {
      messages.value.push({
        type: 'system',
        content: '抱歉，我暂时无法识别您的需求。请尝试使用更具体的描述，例如：\n- "我要做美元即期结售汇"\n- "远期交易提前交割怎么操作"\n- "发起期权交易"',
        time: currentTime()
      });
    }
  } catch (error) {
    messages.value.push({
      type: 'system',
      content: '抱歉，服务暂时不可用，请稍后再试。',
      time: currentTime()
    });
  } finally {
    isLoading.value = false;
    scrollToBottom();
  }
}

// 点击快捷可选项，作为对反问的回答发送
function handleOptionClick(option) {
  handleSend(option);
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

function handleNavigate(routePath) {
  if (routePath) {
    router.push(routePath);
    showWindow.value = false;
  }
}

function scrollToBottom() {
  setTimeout(() => {
    const container = document.querySelector('.navigator-messages');
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }, 100);
}

function toggleWindow() {
  showWindow.value = !showWindow.value;
}

function handleBallMouseDown(e) {
  isDraggingBall.value = true;
  dragOffset.value = {
    x: e.clientX - (window.innerWidth - parseFloat(ballPosition.value.right)),
    y: e.clientY - (window.innerHeight - parseFloat(ballPosition.value.bottom))
  };
  document.addEventListener('mousemove', handleBallMouseMove);
  document.addEventListener('mouseup', handleBallMouseUp);
}

function handleBallMouseMove(e) {
  if (!isDraggingBall.value) return;
  const right = Math.max(10, Math.min(window.innerWidth - 60, window.innerWidth - e.clientX + dragOffset.value.x));
  const bottom = Math.max(10, Math.min(window.innerHeight - 60, window.innerHeight - e.clientY + dragOffset.value.y));
  ballPosition.value = {
    right: `${right}px`,
    bottom: `${bottom}px`
  };
}

function handleBallMouseUp() {
  isDraggingBall.value = false;
  document.removeEventListener('mousemove', handleBallMouseMove);
  document.removeEventListener('mouseup', handleBallMouseUp);
}

function handleWindowMouseDown(e) {
  if (e.target.closest('.navigator-header')) {
    isDraggingWindow.value = true;
    dragOffset.value = {
      x: e.clientX - parseFloat(windowPosition.value.left),
      y: e.clientY - parseFloat(windowPosition.value.top)
    };
    document.addEventListener('mousemove', handleWindowMouseMove);
    document.addEventListener('mouseup', handleWindowMouseUp);
  }
}

function handleWindowMouseMove(e) {
  if (!isDraggingWindow.value) return;
  windowPosition.value = {
    left: `${Math.max(10, Math.min(window.innerWidth - 490, e.clientX - dragOffset.value.x))}px`,
    top: `${Math.max(10, Math.min(window.innerHeight - 500, e.clientY - dragOffset.value.y))}px`
  };
}

function handleWindowMouseUp() {
  isDraggingWindow.value = false;
  document.removeEventListener('mousemove', handleWindowMouseMove);
  document.removeEventListener('mouseup', handleWindowMouseUp);
}

onUnmounted(() => {
  document.removeEventListener('mousemove', handleBallMouseMove);
  document.removeEventListener('mouseup', handleBallMouseUp);
  document.removeEventListener('mousemove', handleWindowMouseMove);
  document.removeEventListener('mouseup', handleWindowMouseUp);
});
</script>

<template>
  <Teleport to="body">
    <div
      class="navigator-float-ball"
      :style="{ right: ballPosition.right, bottom: ballPosition.bottom }"
      @mousedown="handleBallMouseDown"
      @click="toggleWindow"
      :class="{ 'is-dragging': isDraggingBall }"
    >
      <el-icon class="ball-icon"><ChatLineRound /></el-icon>
      <span class="ball-badge">导航</span>
    </div>

    <div v-if="showWindow" class="navigator-mask" @click="showWindow = false">
      <div
        class="navigator-container"
        :style="{ left: windowPosition.left, top: windowPosition.top }"
        @mousedown="handleWindowMouseDown"
        @click.stop
      >
        <div class="navigator-header">
          <div class="navigator-title">
            <el-icon class="navigator-icon"><ChatLineRound /></el-icon>
            <span>菜单导航助手</span>
          </div>
          <el-icon class="navigator-close" @click="showWindow = false">
            <Close />
          </el-icon>
        </div>

        <div class="navigator-messages">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-item', `message-${msg.type}`]"
          >
            <div class="message-avatar">
              <el-icon v-if="msg.type === 'system'" size="20"><Menu /></el-icon>
              <span v-else class="user-avatar">用</span>
            </div>
            <div class="message-content">
              <div class="message-text">{{ msg.content }}</div>
              <div v-if="msg.clarificationOptions && msg.clarificationOptions.length" class="message-options">
                <el-button
                  v-for="opt in msg.clarificationOptions"
                  :key="opt"
                  size="small"
                  round
                  @click="handleOptionClick(opt)"
                  class="option-btn"
                >{{ opt }}</el-button>
              </div>
              <div v-if="msg.routePath" class="message-action">
                <el-button
                  type="primary"
                  size="small"
                  @click="handleNavigate(msg.routePath)"
                  class="navigate-btn"
                >
                  <el-icon><ArrowRight /></el-icon>
                  跳转至 {{ msg.menuPath ? msg.menuPath[msg.menuPath.length - 1] : '对应页面' }}
                </el-button>
              </div>
              <div class="message-time">{{ msg.time }}</div>
            </div>
          </div>
          <div v-if="isLoading" class="loading-indicator">
            <el-icon class="loading-icon"><svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle class="path" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-dasharray="60" stroke-dashoffset="0" style="animation: dash 1.2s ease-in-out infinite"></circle></svg></el-icon>
            <span>正在思考中...</span>
          </div>
        </div>

        <div class="navigator-input">
          <el-input
            v-model="inputText"
            placeholder="请输入您想办理的业务，例如：我要做美元即期结售汇"
            @keydown="handleKeydown"
            :disabled="isLoading"
            class="input-field"
          />
          <el-button
            type="primary"
            @click="handleSend"
            :disabled="!inputText.trim() || isLoading"
            class="send-btn"
          >
            <el-icon><Message /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.navigator-float-ball {
  position: fixed;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #1a2a6c 0%, #2d3f8a 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(26, 42, 108, 0.4);
  cursor: pointer;
  z-index: 9998;
  transition: transform 0.2s, box-shadow 0.2s;
}

.navigator-float-ball:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 24px rgba(26, 42, 108, 0.5);
}

.navigator-float-ball.is-dragging {
  cursor: grabbing;
  transform: scale(1.15);
  box-shadow: 0 8px 32px rgba(26, 42, 108, 0.6);
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

.navigator-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.navigator-container {
  width: 480px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: fixed;
}

.navigator-header {
  height: 56px;
  background: linear-gradient(135deg, #1a2a6c 0%, #2d3f8a 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  cursor: move;
  flex-shrink: 0;
}

.navigator-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
}

.navigator-icon {
  font-size: 20px;
}

.navigator-close {
  font-size: 20px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: background 0.2s;
}

.navigator-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.navigator-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  max-height: 400px;
  background: #f8f9fa;
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
  background: linear-gradient(135deg, #1a2a6c 0%, #2d3f8a 100%);
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
  max-width: 75%;
}

.message-text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.message-system .message-text {
  background: #fff;
  color: #303133;
  border: 1px solid #e6e6e6;
  border-radius: 0 12px 12px 12px;
}

.message-user .message-text {
  background: linear-gradient(135deg, #1a2a6c 0%, #2d3f8a 100%);
  color: #fff;
  border-radius: 12px 0 12px 12px;
}

.message-action {
  margin-top: 8px;
}

.message-options {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.option-btn {
  font-size: 12px;
  padding: 4px 14px;
  border: 1px solid #1a2a6c;
  color: #1a2a6c;
  background: #fff;
}

.option-btn:hover {
  background: #1a2a6c;
  color: #fff;
}

.navigate-btn {
  font-size: 12px;
  padding: 4px 12px;
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
  border: 1px solid #e6e6e6;
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

.navigator-input {
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
  padding: 0 20px;
}
</style>
