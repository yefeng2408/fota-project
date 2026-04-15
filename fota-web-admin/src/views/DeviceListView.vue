<template>
  <div class="split-layout device-list-layout">
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
          <el-table-column label="设备名称" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="device-name-cell">
                <span
                  class="online-dot"
                  :class="{ 'is-online': isDeviceOnline(row) }"
                  :title="isDeviceOnline(row) ? '在线' : '离线'"
                />
                <span>{{ row.deviceName || '-' }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="deviceType" label="设备类型" width="120" />
          <el-table-column prop="deviceGroupName" label="设备分组" min-width="140" show-overflow-tooltip />
          <el-table-column prop="currentFirmwareVersion" label="当前固件版本" width="120" />
          <el-table-column prop="targetFirmwareVersion" label="目标固件版本" width="120" />
          <el-table-column prop="targetFirmwareName" label="目标固件名" min-width="150" show-overflow-tooltip />
          <el-table-column label="固件绑定" width="110">
            <template #default="{ row }">
              {{ isFirmwareBound(row) ? '已绑定' : '未绑定' }}
            </template>
          </el-table-column>
          <el-table-column label="升级状态" width="120">
            <template #default="{ row }">
              <span class="status-text">{{ formatUpgradeStatus(row.deviceUpgradeStatus) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可升级" width="100">
            <template #default="{ row }">
              <span :class="['upgrade-flag', { 'is-enabled': isTruthy(row.isUpgradable) }]">
                {{ isTruthy(row.isUpgradable) ? '可升级' : '不可升级' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="360">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="openDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="remove(row.id)">删除</el-button>
              <el-button
                size="small"
                type="success"
                :disabled="!canStartUpgrade(row)"
                @click="startUpgrade(row)"
              >
                开始升级
              </el-button>
              <el-button
                size="small"
                type="warning"
                :disabled="row.deviceUpgradeStatus !== 'UPGRADING'"
                @click="cancelUpgrade(row)"
              >
                取消升级
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
          :empty-values="[null, undefined, '']"
          :value-on-clear="null"
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

const upgradeStatusTextMap = {
  NO_TASK: '未升级',
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
  deviceUpgradeStatus: 'NO_TASK',
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

function formatUpgradeStatus(status) {
  return upgradeStatusTextMap[status] || status || '-'
}

function isTruthy(value) {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    return value === 1
  }
  return ['1', 'true', 'yes', 'y', 'online', 'connected', '在线'].includes(String(value || '').toLowerCase())
}

function isDeviceOnline(row) {
  return isTruthy(row.isOnline)
}

function isFirmwareBound(row) {
  return isTruthy(row.isBind)
}

/**
 * 三个条件同时满足才能是可升级
 * is_bind = 1
 * is_online = 1
 * upgrade_status = NO_TASK 
 */
function canStartUpgrade(row) {
  return isFirmwareBound(row) && isDeviceOnline(row) && row.deviceUpgradeStatus === 'NO_TASK'
}

function startUpgrade(row) {
  ElMessage.info(`设备 ${row.imei} 已满足开始升级条件，等待接入升级任务接口`)
}

function cancelUpgrade(row) {
  ElMessage.info(`设备 ${row.imei} 正在升级，等待接入取消升级接口`)
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
    deviceUpgradeStatus: 'NO_TASK',
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
  const payload = {
    ...form,
    targetFirmwareId: form.targetFirmwareId || null,
    deviceUpgradeStatus: form.id ? form.deviceUpgradeStatus : 'NO_TASK'
  }
  if (form.id) {
    await request.put(`/api/devices/${form.id}`, payload)
  } else {
    await request.post('/api/devices', payload)
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

<style scoped>
.device-table-scroll {
  overflow-x: auto;
  width: 100%;
}

.device-list-layout {
  grid-template-columns: 240px minmax(0, 1fr);
}

.device-table {
  min-width: 1580px;
}

.status-text {
  white-space: nowrap;
}

.device-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a8b0bd;
  box-shadow: 0 0 0 3px rgba(168, 176, 189, 0.16);
  flex: 0 0 auto;
}

.online-dot.is-online {
  background: #2fb344;
  box-shadow: 0 0 0 3px rgba(47, 179, 68, 0.16);
}

.upgrade-flag {
  color: #8a94a6;
  white-space: nowrap;
}

.upgrade-flag.is-enabled {
  color: #2fb344;
  font-weight: 700;
}
</style>
