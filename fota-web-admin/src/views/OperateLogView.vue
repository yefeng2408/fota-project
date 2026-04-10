<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.userId" placeholder="用户ID" clearable />
        <el-input v-model="query.action" placeholder="操作类型" clearable />
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <el-table :data="tableData.records">
      <el-table-column prop="userId" label="用户ID" />
      <el-table-column prop="action" label="操作类型" />
      <el-table-column prop="detail" label="详情" min-width="340" />
      <el-table-column prop="createdAt" label="创建时间" />
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import request from '../api/request'

const query = reactive({ current: 1, pageSize: 10, userId: '', action: '' })
const tableData = reactive({ total: 0, records: [] })

async function loadData() {
  const data = await request.get('/api/operate-logs', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

onMounted(loadData)
</script>
