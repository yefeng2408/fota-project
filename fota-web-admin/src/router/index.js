import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/devices', component: () => import('../views/DeviceListView.vue') },
  { path: '/device-groups', component: () => import('../views/DeviceGroupView.vue') },
  { path: '/device-firmware-bindings', component: () => import('../views/DeviceFirmwareBindingView.vue') },
  { path: '/firmwares', component: () => import('../views/FirmwareView.vue') },
  { path: '/upgrade-tasks', component: () => import('../views/UpgradeTaskView.vue') },
  { path: '/batch-upgrade-tasks', component: () => import('../views/BatchUpgradeTaskView.vue') },
  { path: '/upgrade-task-processes', component: () => import('../views/UpgradeTaskProcessView.vue') },
  { path: '/upgrade-logs', component: () => import('../views/UpgradeLogView.vue') },
  { path: '/users', component: () => import('../views/UserView.vue') },
  { path: '/operate-logs', component: () => import('../views/OperateLogView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('fota_token')
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }
  next()
})

export default router
