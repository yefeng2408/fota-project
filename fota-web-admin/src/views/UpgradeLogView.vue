<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.taskId" placeholder="任务ID" clearable />
        <el-input v-model="query.deviceId" placeholder="设备ID" clearable />
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="INIT" value="INIT" />
          <el-option label="PENDING" value="PENDING" />
          <el-option label="UPGRADING" value="UPGRADING" />
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAIL" value="FAIL" />
          <el-option label="TIMEOUT" value="TIMEOUT" />
        </el-select>
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <el-table :data="tableData.records">
      <el-table-column prop="taskId" label="任务ID" />
      <el-table-column prop="deviceId" label="设备ID" />
      <el-table-column prop="packetNo" label="分包序号" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="retryCount" label="重试次数" />
      <el-table-column prop="message" label="日志内容" min-width="260" />
      <el-table-column prop="createdAt" label="创建时间" />
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import request from '../api/request'

const query = reactive({ current: 1, pageSize: 10, taskId: '', deviceId: '', status: '' })
const tableData = reactive({ total: 0, records: [] })

async function loadData() {
  const data = await request.get('/api/upgrade-logs', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

onMounted(loadData)
</script>
