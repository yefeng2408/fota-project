<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-input-number v-model="query.deviceId" placeholder="设备ID" :min="1" controls-position="right" />
        <el-input-number v-model="query.firmwareId" placeholder="固件ID" :min="1" controls-position="right" />
        <el-select v-model="query.bindStatus" placeholder="绑定状态" clearable style="width: 180px">
          <el-option label="BOUND" value="BOUND" />
          <el-option label="TRIGGERED" value="TRIGGERED" />
          <el-option label="CANCELED" value="CANCELED" />
          <el-option label="FINISHED" value="FINISHED" />
        </el-select>
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <el-table :data="tableData.records">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="deviceId" label="设备ID" />
      <el-table-column prop="firmwareId" label="固件ID" />
      <el-table-column prop="bindStatus" label="绑定状态" />
      <el-table-column prop="triggered" label="已触发" />
      <el-table-column prop="operatorId" label="操作人" />
      <el-table-column prop="boundAt" label="绑定时间" min-width="180" />
      <el-table-column prop="triggeredAt" label="触发时间" min-width="180" />
      <el-table-column prop="unboundAt" label="解绑时间" min-width="180" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
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

const query = reactive({ current: 1, pageSize: 10, deviceId: null, firmwareId: null, bindStatus: '' })
const tableData = reactive({ total: 0, records: [] })

async function loadData() {
  const data = await request.get('/api/device-firmware-bindings', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

function changePage(page) {
  query.current = page
  loadData()
}

onMounted(loadData)
</script>
