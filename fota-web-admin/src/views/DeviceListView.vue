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
        <el-table-column prop="deviceType" label="设备类型" />
        <el-table-column prop="deviceGroupName" label="设备分组" />
        <el-table-column prop="currentFirmwareVersion" label="当前固件版本" />
        <el-table-column prop="deviceUpgradeStatus" label="升级状态" />
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
      <el-form-item label="IMEI">
        <el-input
          v-model="form.imei"
          maxlength="8"
          placeholder="请输入8位数字"
          inputmode="numeric"
          @input="handleImeiInput"
        />
      </el-form-item>
      <el-form-item label="设备名称"><el-input v-model="form.deviceName" /></el-form-item>
      <el-form-item label="设备类型">
        <el-select v-model="form.deviceType" placeholder="请选择设备类型" style="width: 100%">
          <el-option
            v-for="item in deviceTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="设备分组">
        <el-tree-select
          v-model="form.deviceGroupId"
          :data="groupTree"
          check-strictly
          node-key="id"
          :props="{ label: 'label', children: 'children', value: 'id' }"
        />
      </el-form-item>
      <el-form-item label="当前固件版本"><el-input v-model="form.currentFirmwareVersion" /></el-form-item>
      <el-form-item label="目标固件">
        <el-select
          v-model="form.targetFirmwareId"
          placeholder="请选择目标固件"
          clearable
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="item in firmwareOptions"
            :key="item.id"
            :label="formatFirmwareLabel(item)"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const groupTree = ref([])
const firmwareOptions = ref([])
const dialogVisible = ref(false)
const tableData = reactive({ total: 0, records: [] })
const query = reactive({ current: 1, pageSize: 10, keyword: '', deviceGroupId: null })
const deviceTypeOptions = [
  { label: 'D056', value: 'D056' },
  { label: 'D057', value: 'D057' },
  { label: 'MOTOR_V1', value: 'MOTOR_V1' }
]
const form = reactive({
  id: null,
  imei: '',
  deviceName: '',
  deviceType: '',
  currentFirmwareVersion: '',
  deviceUpgradeStatus: 'IDLE',
  targetFirmwareId: null,
  deviceGroupId: null
})

async function loadGroups() {
  groupTree.value = await request.get('/api/device-groups/tree')
}

async function loadDevices() {
  const data = await request.get('/api/devices', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

async function loadFirmwares() {
  const data = await request.get('/api/firmwares', { params: { current: 1, pageSize: 1000 } })
  firmwareOptions.value = data.records || []
}

function formatFirmwareLabel(item) {
  return `${item.fileName || '未命名固件'} / ${item.version || '-'}`
}

function handleImeiInput(value) {
  form.imei = String(value || '').replace(/\D/g, '').slice(0, 8)
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

async function openDialog(row) {
  if (!firmwareOptions.value.length) {
    await loadFirmwares()
  }
  Object.assign(form, row || {
    id: null,
    imei: '',
    deviceName: '',
    deviceType: '',
    currentFirmwareVersion: '',
    deviceUpgradeStatus: 'IDLE',
    targetFirmwareId: null,
    deviceGroupId: null
  })
  dialogVisible.value = true
}

async function submit() {
  if (!/^\d{8}$/.test(form.imei)) {
    ElMessage.warning('IMEI必须是8位纯数字')
    return
  }
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
  await loadFirmwares()
  await loadDevices()
})
</script>
