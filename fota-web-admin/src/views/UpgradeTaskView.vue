<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <!-- <el-input-number v-model="query.taskId" placeholder="任务ID" :min="1" controls-position="right" /> -->
        <el-input v-model="query.imei" placeholder="设备IMEI" clearable style="width: 180px" />
        <el-select v-model="query.taskStatus" clearable placeholder="任务状态" style="width: 180px">
          <el-option
            v-for="item in taskStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button @click="handleSearch">查询</el-button>
      </div>
    </div>

    <el-table :data="tableData.records">
      <el-table-column prop="batchId" label="批次id" min-width="100" />
      <el-table-column prop="imei" label="设备IMEI" width="120" />
      <el-table-column prop="firmwareVersion" label="目标固件版本号" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.firmwareVersion || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="任务状态" width="150">
        <template #default="{ row }">
          <el-tag size="small" effect="light" :type="taskStatusTagType(row.taskStatus)">
            {{ formatTaskStatus(row.taskStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="normalizeProgress(row.progress)"
            :stroke-width="12"
            :show-text="true"
            :status="progressBarStatus(row.taskStatus)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.failReason || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="120">
        <template #default="{ row }">
          <span>{{ row.operator ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" min-width="180">
        <template #default="{ row }">
          <span>{{ row.startTime || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="endTime" label="完成时间" min-width="180">
        <template #default="{ row }">
          <span>{{ row.endTime || '-' }}</span>
        </template>
      </el-table-column>
      <!-- <el-table-column prop="createdAt" label="创建时间" min-width="180">
        <template #default="{ row }">
          <span>{{ row.createdAt || '-' }}</span>
        </template>
      </el-table-column> -->
    </el-table>

    <el-pagination
      style="margin-top: 16px"
      layout="total, prev, pager, next"
      :current-page="query.current"
      :page-size="query.pageSize"
      :total="tableData.total"
      @current-change="changePage"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import request from '../api/request'

const upgradeStatusTextMap = {
  NO_TASK: '未升级',
  UPGRADE_REQUESTED: '已下发升级请求',
  UPGRADING: '升级中',
  WAIT_RESULT: '等待升级结果',
  SUCCESS: '升级成功',
  FAIL: '升级失败',
  TIMEOUT: '升级超时',
  PAUSED: '升级暂停',
  CANCELING: '取消中',
  CANCEL_UPGRADE: '已取消'
}

const taskStatusOptions = Object.entries(upgradeStatusTextMap).map(([value, label]) => ({
  label,
  value
}))

const query = reactive({
  current: 1,
  pageSize: 10,
  taskId: null,
  imei: '',
  taskStatus: ''
})

const tableData = reactive({
  total: 0,
  records: []
})

async function loadData() {
  const data = await request.get('/api/upgrade-tasks', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

function handleSearch() {
  query.current = 1
  loadData()
}

function changePage(page) {
  query.current = page
  loadData()
}

function formatTaskStatus(status) {
  return upgradeStatusTextMap[status] || status || '-'
}

function taskStatusTagType(status) {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'UPGRADING' || status === 'UPGRADE_REQUESTED' || status === 'WAIT_RESULT' || status === 'CANCELING') {
    return 'warning'
  }
  if (status === 'FAIL' || status === 'TIMEOUT' || status === 'CANCEL_UPGRADE') {
    return 'danger'
  }
  return 'info'
}

function normalizeProgress(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0) {
    return 0
  }
  if (num > 100) {
    return 100
  }
  return Math.round(num)
}

function progressBarStatus(status) {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAIL' || status === 'TIMEOUT' || status === 'CANCEL_UPGRADE') {
    return 'exception'
  }
  return undefined
}

onMounted(loadData)
</script>
