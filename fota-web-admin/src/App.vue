<template>
  <router-view v-if="isLoginPage" />
  <div v-else :class="['shell', { 'sidebar-collapsed': isSidebarCollapsed }]">
    <aside class="sidebar">
      <button
        :aria-label="isSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        class="sidebar-toggle"
        type="button"
        @click="toggleSidebar"
      >
        {{ isSidebarCollapsed ? '›' : '‹' }}
      </button>

      <div class="brand">
        <div class="brand-mark">FP</div>
        <div class="brand-copy">
          <div class="brand-title">FOTA 管理平台</div>
          <div class="brand-subtitle">设备升级控制平台</div>
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
          <!-- <el-menu-item index="/device-firmware-bindings">历史升级固件</el-menu-item> -->
        </el-sub-menu>
        <el-sub-menu index="firmware">
          <template #title>固件管理</template>
          <el-menu-item index="/firmwares">固件列表</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="upgrade">
          <template #title>升级管理</template>
          <el-menu-item index="/batch-upgrade-tasks">批量任务</el-menu-item>
          <el-menu-item index="/upgrade-tasks">升级任务</el-menu-item>
          <!-- <el-menu-item index="/upgrade-task-processes">任务进度</el-menu-item> -->
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
          <h5 style="color:brown">当前版本已经接入断点续传功能。 
            流程如下：设备处于升级中，点击“模拟下线” 则推送“升级掉线”状态。此时设备暂停升级。若再次点击“模拟上线”，则设备恢复正常升级流程。
            注意：如果设备断开连接时间超过5分钟，再次上线则无法进入断点续传状态。批量升级调度器最大同时可升级设备数为500，等候区为5000。
          </h5>
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
  <SiteFooter />
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SiteFooter from './components/SiteFooter.vue'

const route = useRoute()
const router = useRouter()
const SIDEBAR_STORAGE_KEY = 'fota_sidebar_collapsed'

const titleMap = {
  '/dashboard': '首页概览',
  '/devices': '设备列表',
  '/device-groups': '设备分组',
  '/device-firmware-bindings': '设备绑定固件',
  '/firmwares': '固件管理',
  '/upgrade-tasks': '升级任务',
  '/batch-upgrade-tasks': '批量任务',
  '/upgrade-task-processes': '任务进度',
  '/upgrade-logs': '升级日志',
  '/users': '用户权限',
  '/operate-logs': '操作日志'
}

const isLoginPage = computed(() => route.path === '/login')
const pageTitle = computed(() => titleMap[route.path] || 'FOTA 管理后台')
const isSidebarCollapsed = ref(localStorage.getItem(SIDEBAR_STORAGE_KEY) === '1')

const currentUser = computed(() => {
  const raw = localStorage.getItem('fota_user')
  return raw ? JSON.parse(raw) : null
})

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
  localStorage.setItem(SIDEBAR_STORAGE_KEY, isSidebarCollapsed.value ? '1' : '0')
}

function logout() {
  localStorage.removeItem('fota_token')
  localStorage.removeItem('fota_user')
  router.push('/login')
}
</script>
