<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-input-number v-model="query.taskId" placeholder="任务ID" :min="1" controls-position="right" />
        <el-input-number v-model="query.deviceId" placeholder="设备ID" :min="1" controls-position="right" />
        <el-input v-model="query.eventType" placeholder="事件类型" clearable style="width: 240px" />
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <el-table :data="tableData.records">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="taskId" label="任务ID" min-width="140" />
      <el-table-column prop="deviceId" label="设备ID" />
      <el-table-column prop="eventType" label="事件类型" min-width="220" />
      <el-table-column prop="packetNo" label="分包号" />
      <el-table-column prop="retryNo" label="重试次数" />
      <el-table-column prop="message" label="说明" min-width="220" />
      <el-table-column prop="extraJson" label="扩展信息" min-width="240" show-overflow-tooltip />
      <el-table-column prop="eventTime" label="事件时间" min-width="180" />
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

const query = reactive({ current: 1, pageSize: 10, taskId: null, deviceId: null, eventType: '' })
const tableData = reactive({ total: 0, records: [] })

async function loadData() {
  const data = await request.get('/api/upgrade-task-processes', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

function changePage(page) {
  query.current = page
  loadData()
}

onMounted(loadData)
</script>
