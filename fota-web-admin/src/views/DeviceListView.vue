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

      <div class="device-table-scroll">
        <el-table :data="tableData.records" class="device-table">
          <el-table-column prop="imei" label="IMEI" width="120" />
          <el-table-column prop="deviceName" label="设备名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="deviceType" label="设备类型" width="120" />
          <el-table-column prop="deviceGroupName" label="设备分组" min-width="140" show-overflow-tooltip />
          <el-table-column prop="currentFirmwareVersion" label="当前固件版本" width="140" />
          <el-table-column label="升级状态" width="150">
            <template #default="{ row }">
              <span class="status-text">{{ formatUpgradeStatus(row.deviceUpgradeStatus) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="onlineStatus" label="在线状态" width="110" />
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="openDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="remove(row.id)">删除</el-button>
              <el-button
                size="small"
                type="success"
                :disabled="row.deviceUpgradeStatus !== 'READY'"
                @click="startUpgrade(row)"
              >
                开始升级
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

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
      <el-form-item label="设备类型"><el-input v-model="form.deviceType" placeholder="例如 D056 / D057 / MOTOR_V1" /></el-form-item>
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
      <el-form-item label="目标固件ID"><el-input-number v-model="form.targetFirmwareId" :min="1" controls-position="right" /></el-form-item>
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
const dialogVisible = ref(false)
const tableData = reactive({ total: 0, records: [] })
const query = reactive({ current: 1, pageSize: 10, keyword: '', deviceGroupId: null })

const upgradeStatusTextMap = {
  IDLE: '未绑定固件',
  READY: '可升级',
  UPGRADE_REQUESTED: '已下发升级请求',
  UPGRADING: '升级中',
  SUCCESS: '升级成功',
  FAIL: '升级失败',
  TIMEOUT: '升级超时',
  PAUSED: '升级暂停',
  CANCEL_UPGRADE: '用户取消升级'
}
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

function handleGroupClick(node) {
  query.deviceGroupId = node.id
  query.current = 1
  loadDevices()
}

function changePage(page) {
  query.current = page
  loadDevices()
}

function formatUpgradeStatus(status) {
  return upgradeStatusTextMap[status] || status || '-'
}

function startUpgrade(row) {
  ElMessage.info(`设备 ${row.imei} 已满足开始升级条件，等待接入升级任务接口`)
}

function openDialog(row) {
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

<style scoped>
.device-table-scroll {
  overflow-x: auto;
  width: 100%;
}

.device-table {
  min-width: 1360px;
}

.status-text {
  white-space: nowrap;
}
</style>
