<template>
  <div class="login-shell">
    <div class="login-card">
      <section class="login-hero">
        <h1>FOTA 升级管理平台</h1>
        <p>面向设备、固件与升级任务全链路管理</p>
        <p>当前已接入：登录、仪表盘、设备、分组、固件、升级日志、用户、操作日志。</p>
      </section>
      <section class="login-form">
        <h2>后台登录</h2>
        <p class="muted">先使用账号密码登录，短信注册能力后续无缝补入</p>
        <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" @click="handleLogin">登录系统</el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'

const router = useRouter()
const form = reactive({
  phone: 'admin',
  password: 'admin'
})

async function handleLogin() {
  const data = await request.post('/api/auth/login', form)
  localStorage.setItem('fota_token', data.token)
  localStorage.setItem('fota_user', JSON.stringify({ userId: data.userId, username: data.username }))
  router.push('/dashboard')
}
</script>
