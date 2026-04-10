<template>
  <div>
    <div class="stats-grid">
      <div class="stat-card" v-for="item in cards" :key="item.label">
        <div class="stat-title">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
      </div>
    </div>

    <div class="split-layout">
      <div class="block-card">
        <h3>最近升级任务</h3>
        <el-table :data="overview.recentTasks || []" size="small">
          <el-table-column prop="taskId" label="任务ID" />
          <el-table-column prop="imei" label="IMEI" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="progress" label="进度" />
        </el-table>
      </div>
      <div class="block-card">
        <h3>近 7 天升级日志趋势</h3>
        <el-table :data="overview.logTrends || []" size="small">
          <el-table-column prop="date" label="日期" />
          <el-table-column prop="count" label="日志数" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import request from '../api/request'

const overview = reactive({
  totalDevices: 0,
  onlineDevices: 0,
  successTasks: 0,
  failedTasks: 0,
  recentTasks: [],
  logTrends: []
})

const cards = computed(() => [
  { label: '设备总数', value: overview.totalDevices },
  { label: '在线设备', value: overview.onlineDevices },
  { label: '升级成功', value: overview.successTasks },
  { label: '升级失败', value: overview.failedTasks }
])

onMounted(async () => {
  Object.assign(overview, await request.get('/api/dashboard/overview'))
})
</script>
