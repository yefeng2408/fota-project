<template>
  <main class="login-shell">
    <div class="login-card">
      <section class="login-hero" aria-labelledby="platform-title">
        <div class="login-brand"><span class="login-mark">FP</span><span>FOTA 管理平台</span></div>
        <div class="hero-copy">
          <span class="hero-eyebrow">FIRMWARE OVER THE AIR</span>
          <h1 id="platform-title">连接每一台设备<br>掌控每一次升级<span>。</span></h1>
          <p>从设备接入到固件发布，让远程升级有序进行。</p>
        </div>
        <div class="device-network" aria-hidden="true">
          <div class="network-grid"></div>
          <svg class="network-lines" viewBox="0 0 400 180" fill="none" preserveAspectRatio="none">
            <path d="M200 55V102M70 139V102H330V139M200 102V139" stroke="#7799c7" stroke-width="1.5" />
            <circle cx="200" cy="102" r="4" fill="#f4c95d" />
          </svg>
          <div class="network-hub"><el-icon><UploadFilled /></el-icon><span>FOTA CLOUD</span><i></i></div>
          <div class="network-devices">
            <span><el-icon><Monitor /></el-icon></span>
            <span><el-icon><Cpu /></el-icon></span>
            <span><el-icon><Connection /></el-icon></span>
          </div>
        </div>
        <div class="hero-features"><span>设备分组</span><i></i><span>批量升级</span><i></i><span>进度追踪</span></div>
      </section>
      <section class="login-form" aria-labelledby="login-title">
        <div class="form-heading">
          <span class="form-eyebrow">管理控制台</span>
          <h2 id="login-title">欢迎回来</h2>
          <p>登录账号，开始管理你的设备</p>
        </div>
        <el-form ref="loginFormRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="handleLogin">
          <el-form-item label="账号" prop="phone">
            <el-input v-model="form.phone" placeholder="请输入账号或手机号" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" autocomplete="current-password" />
          </el-form-item>
          <el-button class="login-submit" type="primary" native-type="submit" :loading="submitting">
            {{ submitting ? '正在登录…' : '登录管理平台' }}<el-icon v-if="!submitting"><Right /></el-icon>
          </el-button>
        </el-form>
        <p class="login-help">账号由管理员分配，如需帮助请联系管理员</p>
        <div class="form-footer"><span>FOTA · 设备升级控制平台</span><a href="https://gitee.com/yf123456/fota-project" target="_blank" rel="noopener noreferrer">项目源码 <el-icon><TopRight /></el-icon></a></div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, Cpu, Lock, Monitor, Right, TopRight, UploadFilled, User } from '@element-plus/icons-vue'
import request from '../api/request'

const router = useRouter()
const loginFormRef = ref(null)
const submitting = ref(false)
const form = reactive({ phone: 'admin', password: 'admin' })
const rules = {
  phone: [{ required: true, whitespace: true, message: '请输入账号或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (submitting.value) return
  submitting.value = true
  try {
    const valid = await loginFormRef.value.validate().catch(() => false)
    if (!valid) return
    const data = await request.post('/api/auth/login', form)
    localStorage.setItem('fota_token', data.token)
    localStorage.setItem('fota_user', JSON.stringify({ userId: data.userId, username: data.username }))
    await router.push('/dashboard')
  } catch {
    // 请求拦截器统一展示登录失败信息，保留表单以便重试。
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-shell {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 48px 24px;
  background: radial-gradient(ellipse at 10% 15%, #e2eaf5 0, transparent 50%), radial-gradient(ellipse at 95% 85%, #e7edf6 0, transparent 45%), #f4f7fb;
}
.login-card {
  display: grid;
  grid-template-columns: 1.08fr 1fr;
  width: min(1040px, 100%);
  border: 1px solid #e4eaf2;
  border-radius: 22px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 24px 70px -28px #243c6440;
}
.login-hero {
  position: relative;
  overflow: hidden;
  padding: 38px 42px 30px;
  color: #fff;
  background: radial-gradient(ellipse at 100% 70%, #315985 0, transparent 65%), #1c3456;
}
.login-brand { display: flex; align-items: center; gap: 12px; font-size: 17px; font-weight: 600; }
.login-mark { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; background: #f6d878; color: #1c3456; font-size: 15px; font-weight: 800; }
.hero-copy { margin-top: 48px; }
.hero-eyebrow { color: #a7bdd8; letter-spacing: 2.4px; font-size: 10px; }
.hero-copy h1 { margin: 14px 0 16px; font-size: clamp(27px, 2.5vw, 35px); line-height: 1.5; letter-spacing: 1px; font-weight: 600; }
.hero-copy h1 > span { color: #f6d878; }
.hero-copy p { color: #bccde2; font-size: 13px; line-height: 1.8; margin: 0; }
.device-network { position: relative; height: 180px; margin: 24px 0 18px; }
.network-grid { position: absolute; inset: 0; background-image: radial-gradient(#7b9cc038 1px, transparent 1px); background-size: 18px 18px; mask-image: linear-gradient(transparent, #000 30%, #000 70%, transparent); }
.network-lines { position: absolute; width: 100%; height: 100%; }
.network-hub { position: absolute; top: 12px; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: 12px; padding: 14px 18px; background: #355477; border: 1px solid #728dab; border-radius: 12px; box-shadow: 0 8px 30px #0b20363b; white-space: nowrap; }
.network-hub .el-icon { color: #f6d878; font-size: 24px; }
.network-hub span { font-size: 11px; letter-spacing: 1.3px; }
.network-hub > i:not(.el-icon) { width: 5px; height: 5px; border-radius: 50%; background: #9edcc1; }
.network-devices { position: absolute; top: 126px; width: 100%; display: flex; justify-content: space-around; }
.network-devices > span { display: grid; place-items: center; width: 46px; height: 42px; border: 1px solid #6c88a7; border-radius: 9px; background: #2d4c70; font-size: 22px; color: #c9d9ec; }
.hero-features { display: flex; align-items: center; justify-content: center; gap: 16px; color: #b9cbe1; font-size: 12px; }
.hero-features i { width: 3px; height: 3px; background: #7894b5; border-radius: 50%; }
.login-form { display: flex; flex-direction: column; justify-content: center; padding: 56px 48px 30px; min-width: 0; background: #fff; }
.form-eyebrow { color: #73829a; font-size: 12px; letter-spacing: 2px; }
.form-heading h2 { margin: 12px 0 10px; font-size: 30px; font-weight: 650; color: #172e50; }
.form-heading p { color: #8390a3; font-size: 14px; margin: 0 0 34px; }
.login-form :deep(.el-form-item) { margin-bottom: 24px; }
.login-form :deep(.el-form-item__label) { color: #3d4d64; font-weight: 500; }
.login-form :deep(.el-input__wrapper) { min-height: 46px; border-radius: 8px; background: #fbfcfe; }
.login-form :deep(.el-input__prefix) { margin-right: 6px; }
.login-submit { width: 100%; height: 46px; margin-top: 4px; border-radius: 8px; font-size: 14px; font-weight: 600; --el-color-primary: #2d65ae; --el-color-primary-light-3: #487fbf; --el-color-primary-dark-2: #23518f; box-shadow: 0 6px 16px #2d65ae20; }
.login-submit .el-icon { margin-left: 12px; }
.login-help { text-align: center; font-size: 12px; line-height: 1.8; color: #8b96a6; margin: 18px 0 0; }
.form-footer { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 12px; border-top: 1px solid #edf0f5; padding-top: 20px; margin-top: 42px; font-size: 11px; color: #98a2b1; }
.form-footer a { display: inline-flex; gap: 4px; align-items: center; color: #71829b; text-decoration: none; }
.form-footer a:hover { color: #2d65ae; }
.form-footer a:focus-visible { outline: 2px solid #2d65ae; outline-offset: 4px; }
@media (max-width: 820px) {
  .login-card { grid-template-columns: 1fr; max-width: 480px; }
  .login-hero { padding: 24px 30px; }
  .hero-copy { margin-top: 22px; }
  .hero-copy h1 { font-size: 26px; margin-bottom: 8px; }
  .hero-eyebrow, .device-network, .hero-features { display: none; }
  .login-form { padding: 32px 30px 24px; }
  .form-heading p { margin-bottom: 24px; }
  .form-footer { margin-top: 28px; }
}
@media (max-width: 480px) {
  .login-shell { padding: 20px 14px; }
  .login-hero, .login-form { padding-inline: 24px; }
  .hero-copy h1 { font-size: 23px; letter-spacing: 0; }
  .hero-copy p { font-size: 12px; }
  .form-heading h2 { font-size: 26px; }
}
</style>
