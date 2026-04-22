<template>
  <div class="dashboard-view">
    <div class="stats-grid">
      <div class="stat-card" v-for="item in cards" :key="item.label">
        <div class="stat-title">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
      </div>
    </div>

    <div class="split-layout dashboard-panels">
      <div class="block-card recent-tasks-card">
        <h3>最近升级任务</h3>
        <el-table :data="overview.recentTasks || []" size="small" width="100%">
          <el-table-column prop="taskId" label="任务ID" min-width="150" />
          <el-table-column prop="imei" label="IMEI" min-width="110" />
          <el-table-column prop="taskStatus" label="状态" min-width="110" />
          <el-table-column prop="progress" label="进度" min-width="90" />
        </el-table>
      </div>
      <div class="block-card trends-card">
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
  offlineDevices: 0,
  successTasks: 0,
  failedTasks: 0,
  recentTasks: [],
  logTrends: []
})

const cards = computed(() => [
  { label: '设备总数', value: overview.totalDevices },
  { label: '在线设备', value: overview.onlineDevices },
  { label: '离线设备', value: overview.offlineDevices },
  { label: '升级成功', value: overview.successTasks },
  { label: '升级失败', value: overview.failedTasks }
])

onMounted(async () => {
  Object.assign(overview, await request.get('/api/dashboard/overview'))
})
</script>

<style scoped>
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  min-height: 108px;
  padding: 22px 24px;
  border-radius: 26px;
}

.stat-title {
  font-size: 17px;
  font-weight: 700;
  color: #7a89aa;
  margin-bottom: 14px;
}

.stat-value {
  font-size: 48px;
  line-height: 1;
  font-weight: 800;
  color: #17315d;
}

.dashboard-panels {
  grid-template-columns: minmax(0, 1.35fr) minmax(360px, 1fr);
  align-items: start;
}

.recent-tasks-card :deep(.el-table),
.trends-card :deep(.el-table) {
  width: 100%;
}

@media (max-width: 1440px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-panels {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .stat-card {
    min-height: 96px;
    padding: 18px 20px;
  }

  .stat-value {
    font-size: 40px;
  }
}
</style>
