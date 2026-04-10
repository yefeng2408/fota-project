<template>
  <div class="split-layout">
    <div class="block-card">
      <div class="table-toolbar">
        <strong>设备分组树</strong>
      </div>
      <el-tree
        :data="groupTree"
        node-key="id"
        default-expand-all
        :props="{ label: 'label', children: 'children' }"
        @node-click="handleGroupClick"
      >
        <template #default="{ data }">
          <span>{{ data.label }} ({{ data.deviceCount || 0 }})</span>
        </template>
      </el-tree>
    </div>

    <div class="block-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" placeholder="按 IMEI/设备名搜索" clearable style="width: 240px" />
          <el-button @click="loadDevices">查询</el-button>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="openDialog()">新增设备</el-button>
        </div>
      </div>

      <el-table :data="tableData.records">
        <el-table-column prop="imei" label="IMEI" />
        <el-table-column prop="deviceName" label="设备名称" />
        <el-table-column prop="deviceGroupName" label="设备分组" />
        <el-table-column prop="firmwareVersion" label="固件版本" />
        <el-table-column prop="onlineStatus" label="在线状态" />
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
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
  </div>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备' : '新增设备'" width="520px">
    <el-form :model="form" label-position="top">
      <el-form-item label="IMEI"><el-input v-model="form.imei" /></el-form-item>
      <el-form-item label="设备名称"><el-input v-model="form.deviceName" /></el-form-item>
      <el-form-item label="设备分组">
        <el-tree-select
          v-model="form.deviceGroupId"
          :data="groupTree"
          check-strictly
          node-key="id"
          :props="{ label: 'label', children: 'children', value: 'id' }"
        />
      </el-form-item>
      <el-form-item label="固件版本"><el-input v-model="form.firmwareVersion" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import request from '../api/request'

const groupTree = ref([])
const dialogVisible = ref(false)
const tableData = reactive({ total: 0, records: [] })
const query = reactive({ current: 1, pageSize: 10, keyword: '', deviceGroupId: null })
const form = reactive({ id: null, imei: '', deviceName: '', firmwareVersion: '', deviceGroupId: null })

async function loadGroups() {
  groupTree.value = await request.get('/api/device-groups/tree')
}

async function loadDevices() {
  const data = await request.get('/api/devices', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

function handleGroupClick(node) {
  query.deviceGroupId = node.id
  query.current = 1
  loadDevices()
}

function changePage(page) {
  query.current = page
  loadDevices()
}

function openDialog(row) {
  Object.assign(form, row || { id: null, imei: '', deviceName: '', firmwareVersion: '', deviceGroupId: null })
  dialogVisible.value = true
}

async function submit() {
  if (form.id) {
    await request.put(`/api/devices/${form.id}`, form)
  } else {
    await request.post('/api/devices', form)
  }
  dialogVisible.value = false
  loadDevices()
  loadGroups()
}

async function remove(id) {
  await request.delete(`/api/devices/${id}`)
  loadDevices()
  loadGroups()
}

onMounted(async () => {
  await loadGroups()
  await loadDevices()
})
</script>
