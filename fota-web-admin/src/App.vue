<template>
  <router-view v-if="isLoginPage" />
  <div v-else class="shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">FP</div>
        <div>
          <div class="brand-title">FOTA 管理平台</div>
          <div class="brand-subtitle">设备升级控制台</div>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        class="nav-menu"
        background-color="transparent"
        text-color="#c7d2e3"
        active-text-color="#fff8d2"
        router
      >
        <el-menu-item index="/dashboard">仪表盘</el-menu-item>
        <el-sub-menu index="devices">
          <template #title>设备管理</template>
          <el-menu-item index="/devices">设备列表</el-menu-item>
          <el-menu-item index="/device-groups">设备分组</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="firmware">
          <template #title>固件管理</template>
          <el-menu-item index="/firmwares">固件列表</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="upgrade">
          <template #title>升级管理</template>
          <el-menu-item index="/upgrade-tasks">升级任务</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/upgrade-logs">升级日志</el-menu-item>
        <el-menu-item index="/users">用户权限</el-menu-item>
        <el-menu-item index="/operate-logs">操作日志</el-menu-item>
      </el-menu>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <div class="page-title">{{ pageTitle }}</div>
          <div class="page-subtitle">聚焦设备、固件、升级与审计链路</div>
        </div>
        <div class="topbar-right">
          <span class="user-chip">{{ currentUser?.username || '未登录' }}</span>
          <el-button type="danger" plain @click="logout">退出登录</el-button>
        </div>
      </header>
      <section class="content-card">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const titleMap = {
  '/dashboard': '首页概览',
  '/devices': '设备列表',
  '/device-groups': '设备分组',
  '/firmwares': '固件管理',
  '/upgrade-tasks': '升级管理',
  '/upgrade-logs': '升级日志',
  '/users': '用户权限',
  '/operate-logs': '操作日志'
}

const isLoginPage = computed(() => route.path === '/login')
const pageTitle = computed(() => titleMap[route.path] || 'FOTA 管理后台')
const currentUser = computed(() => {
  const raw = localStorage.getItem('fota_user')
  return raw ? JSON.parse(raw) : null
})

function logout() {
  localStorage.removeItem('fota_token')
  localStorage.removeItem('fota_user')
  router.push('/login')
}
</script>
