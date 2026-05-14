<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <!-- <el-input v-model="query.groupId" placeholder="设备组ID" :min="1" controls-position="right" style="width: 160px" />
        <el-input v-model="query.firmwareId" placeholder="固件ID" :min="1" controls-position="right" style="width: 160px" /> -->
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 160px">
          <el-option label="INIT" value="INIT" />
          <el-option label="RUNNING" value="RUNNING" />
          <el-option label="FINISHED" value="FINISHED" />
        </el-select>
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <el-table :data="tableData.records">
      <el-table-column prop="id" label="批量任务ID" width="120" />
      <el-table-column prop="groupName" label="设备组名称" width="120" />
      <el-table-column prop="firmware" label="固件名称" width="220" />
      <el-table-column prop="status" label="状态" width="100"/>
      <el-table-column prop="totalCount" label="总设备数" />
      <el-table-column prop="successCount" label="成功数" />
      <el-table-column prop="failCount" label="失败数" />
      <el-table-column prop="remark" label="任务备注" width="140"/>
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      
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

const query = reactive({ current: 1, pageSize: 10,  status: '' })
const tableData = reactive({ total: 0, records: [] })

async function loadData() {
  const data = await request.get('/api/batch-upgrade-tasks', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

function changePage(page) {
  query.current = page
  loadData()
}

onMounted(loadData)
</script>
