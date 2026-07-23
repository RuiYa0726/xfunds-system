<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

// 登录表单引用，用于校验
const loginFormRef = ref(null)

// 登录表单数据，默认填充 admin/admin123 便于联调
const loginForm = reactive({
  username: 'admin',
  password: 'admin123'
})

// 表单校验规则
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 登录加载状态
const loading = ref(false)

// 处理登录：校验表单 -> 调用接口 -> 存储 token -> 跳转首页
async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(loginForm)
      ElMessage.success('登录成功')
      router.push('/home')
    } catch (e) {
      // 错误信息已由 request 拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-container">
    <!-- 南京城市天际线背景图 + 深色渐变遮罩 -->
    <div class="bg-layer"></div>
    <div class="bg-overlay"></div>

    <!-- 顶部品牌标识 -->
    <div class="brand-top">
      <div class="brand-logo">
        <span class="brand-icon">XF</span>
      </div>
      <span class="brand-name">XFUNDS</span>
    </div>

    <!-- 左侧标语区 -->
    <div class="slogan-area">
      <h1 class="slogan-main">代客外汇AI智能服务平台</h1>
      <p class="slogan-sub">Bank Foreign Exchange Trading Platform</p>
      <div class="slogan-desc">
        <span class="slogan-line"></span>
        <span>专业 · 安全 · 高效</span>
        <span class="slogan-line"></span>
      </div>
      <p class="slogan-city">南京 · 金融科技</p>
    </div>

    <!-- 右侧登录卡片（毛玻璃效果） -->
    <div class="login-box">
      <div class="login-header">
        <h2>欢迎登录</h2>
        <p>Welcome Back</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 底部版权 -->
    <div class="footer">
      <span>© 2026 XFUNDS · 银行代客外汇交易系统 · All Rights Reserved</span>
    </div>
  </div>
</template>

<style scoped>
/* ===== 全屏容器 ===== */
.login-container {
  width: 100%;
  height: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  overflow: hidden;
}

/* ===== 南京城市天际线背景图 ===== */
.bg-layer {
  position: absolute;
  inset: 0;
  background-image: url('https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Nanjing%20city%20skyline%20modern%20skyscrapers%20financial%20district%20golden%20hour%20sunset%20cinematic%20ultra%20realistic%20high-end%20professional%20architecture%20aerial%20view&image_size=landscape_16_9');
  background-size: cover;
  background-position: center;
  z-index: 0;
  /* 增强对比度和饱和度，让图片更清晰锐利 */
  filter: contrast(1.2) saturate(1.15) brightness(1.05);
}

/* ===== 深蓝渐变遮罩（轻透明，清晰展示城市天际线） ===== */
.bg-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(10, 25, 65, 0.38) 0%, rgba(20, 40, 90, 0.18) 50%, rgba(10, 25, 65, 0.38) 100%);
  z-index: 1;
}

/* ===== 顶部品牌标识 ===== */
.brand-top {
  position: absolute;
  top: 32px;
  left: 48px;
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 2;
}

.brand-logo {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4facfe, #00c6fb);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(79, 172, 254, 0.4);
}

.brand-icon {
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 3px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.5);
}

/* ===== 左侧标语区 ===== */
.slogan-area {
  position: relative;
  z-index: 2;
  padding-left: 80px;
  max-width: 560px;
}

.slogan-main {
  font-size: 42px;
  font-weight: 800;
  color: #fff;
  margin-bottom: 12px;
  letter-spacing: 4px;
  text-shadow: 0 2px 24px rgba(0, 0, 0, 0.6);
  line-height: 1.3;
  white-space: nowrap;
}

.slogan-sub {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.75);
  letter-spacing: 2px;
  margin-bottom: 36px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.5);
}

.slogan-desc {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.slogan-line {
  display: inline-block;
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #4facfe, transparent);
}

.slogan-desc span:nth-child(2) {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.8);
  letter-spacing: 6px;
}

.slogan-city {
  font-size: 14px;
  color: rgba(79, 172, 254, 0.8);
  letter-spacing: 4px;
}

/* ===== 右侧登录卡片（毛玻璃效果） ===== */
.login-box {
  position: relative;
  z-index: 2;
  width: 400px;
  padding: 48px 40px 36px;
  margin-right: 80px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-header h2 {
  font-size: 26px;
  color: #fff;
  margin-bottom: 6px;
  letter-spacing: 2px;
}

.login-header p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 1px;
}

/* ===== 表单输入框样式覆盖（适配深色毛玻璃背景） ===== */
.login-box :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  box-shadow: none !important;
  transition: all 0.3s;
}

.login-box :deep(.el-input__wrapper:hover) {
  border-color: rgba(79, 172, 254, 0.5);
}

.login-box :deep(.el-input__wrapper.is-focus) {
  border-color: #4facfe;
  background: rgba(255, 255, 255, 0.1);
}

.login-box :deep(.el-input__inner) {
  color: #fff;
}

.login-box :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.35);
}

.login-box :deep(.el-input__prefix-inner) {
  color: rgba(255, 255, 255, 0.4);
}

/* ===== 登录按钮 ===== */
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 8px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #4facfe, #00c6fb);
  box-shadow: 0 6px 20px rgba(79, 172, 254, 0.35);
  transition: all 0.3s;
}

.login-btn:hover {
  background: linear-gradient(135deg, #5fbcff, #1ad0ff);
  box-shadow: 0 8px 28px rgba(79, 172, 254, 0.5);
  transform: translateY(-1px);
}

/* ===== 底部提示 ===== */
.login-tip {
  text-align: center;
  margin-top: 16px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.3);
  letter-spacing: 1px;
}

/* ===== 底部版权 ===== */
.footer {
  position: absolute;
  bottom: 24px;
  left: 0;
  width: 100%;
  text-align: center;
  z-index: 2;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.25);
  letter-spacing: 1px;
}
</style>
