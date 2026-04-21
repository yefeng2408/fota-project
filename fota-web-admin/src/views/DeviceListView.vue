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
          <el-table-column prop="imei" label="IMEI" width="100" />
          <el-table-column label="设备名称" min-width="80" width="120" show-overflow-tooltip>
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
          <el-table-column prop="deviceType" label="设备类型" width="100" />
          <el-table-column prop="deviceGroupName" label="设备分组" min-width="100" width="110" show-overflow-tooltip />
          <el-table-column label="当前/目标版本" width="130" min-width="100">
            <template #default="{ row }">
              <span class="version-flow" :title="`${row.currentFirmwareVersion || '-'} → ${row.targetFirmwareVersion || '无'}`">
                <span>{{ row.currentFirmwareVersion || '-' }}</span>
                <span class="version-arrow"> → </span>
                <span>{{ row.targetFirmwareVersion || '无' }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="固件绑定" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="isFirmwareBound(row) ? 'success' : 'info'">
                {{ isFirmwareBound(row) ? '已绑定' : '未绑定' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="升级状态" width="110">
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="light"
                :type="upgradeStatusTagType(row.deviceUpgradeStatus)">
                    {{ formatUpgradeStatus(row.deviceUpgradeStatus) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="升级进度" width="160">
            <template #default="{ row }">
              <div class="progress-cell">
                <el-progress
                  :percentage="normalizeProgress(row.progress)"
                  :stroke-width="12"
                  :show-text="true"
                  :status="progressBarStatus(row)"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                type="success"
                :disabled="!canStartUpgrade(row)"
                @click="startUpgrade(row)"
              >
                开始升级
              </el-button>
              <br>
              <el-dropdown class="action-dropdown" @command="(command) => handleAction(command, row)">
                <el-button size="small">
                  更多操作
                </el-button>
                <template #dropdown>
                    <el-dropdown-menu class="action-dropdown-menu">
                      <el-dropdown-item command="edit" class="action-dropdown-item">
                        <el-button size="small">编辑</el-button>
                      </el-dropdown-item>

                      <el-dropdown-item command="delete" class="action-dropdown-item">
                        <el-button size="small" type="danger" plain>删除</el-button>
                      </el-dropdown-item>

                      <el-dropdown-item
                        command="cancel"
                        class="action-dropdown-item"
                        :disabled="row.deviceUpgradeStatus !== 'UPGRADING'"
                      >
                        <el-button
                          size="small"
                          type="warning"
                          plain
                          :disabled="row.deviceUpgradeStatus !== 'UPGRADING'"
                        >
                          取消升级
                        </el-button>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                </template>
              </el-dropdown>
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
          :disabled="Boolean(form.id)"
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
      <el-form-item label="目标（绑定）固件">
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
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
  WAIT_RESULT: '等待升级结果',
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

const wsRef = ref(null)
let wsReconnectTimer = null
let wsReconnectAttempts = 0
const WS_RECONNECT_MAX = 5
const WS_RECONNECT_DELAY = 2000

function applyUpgradeEvent(event) {
  if (!event?.imei) {
    return
  }
  const row = tableData.records.find((item) => item.imei === event.imei)
  if (!row) {
    return
  }
  if (event.status !== undefined && event.status !== null && event.status !== '') {
    row.deviceUpgradeStatus = event.status
  }
  if (event.progress !== undefined && event.progress !== null && event.progress !== '') {
    row.progress = normalizeProgress(event.progress)
  }
  if (event.currentFirmwareVersion !== undefined && event.currentFirmwareVersion !== null) {
    row.currentFirmwareVersion = event.currentFirmwareVersion
  }
  if (event.targetFirmwareVersion !== undefined && event.targetFirmwareVersion !== null) {
    row.targetFirmwareVersion = event.targetFirmwareVersion
  }
}

function getDeviceUpgradeWsUrl() {
  return 'ws://localhost:8080/ws/device-upgrade'
}

function clearWsReconnectTimer() {
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
}

function scheduleWsReconnect() {
  if (wsReconnectAttempts >= WS_RECONNECT_MAX) {
    return
  }
  clearWsReconnectTimer()
  wsReconnectTimer = setTimeout(() => {
    wsReconnectAttempts += 1
    connectDeviceUpgradeWs()
  }, WS_RECONNECT_DELAY)
}

function connectDeviceUpgradeWs() {
  clearWsReconnectTimer()
  try {
    const ws = new WebSocket(getDeviceUpgradeWsUrl())
    wsRef.value = ws

    ws.onopen = () => {
      wsReconnectAttempts = 0
    }

    ws.onmessage = (messageEvent) => {
      try {
        const payload = JSON.parse(messageEvent.data)
        applyUpgradeEvent(payload)
      } catch (error) {
        console.error('解析设备升级推送消息失败:', error)
      }
    }

    ws.onclose = () => {
      if (wsRef.value === ws) {
        wsRef.value = null
      }
      scheduleWsReconnect()
    }

    ws.onerror = (error) => {
      console.error('设备升级 WebSocket 连接异常:', error)
      ws.close()
    }
  } catch (error) {
    console.error('创建设备升级 WebSocket 失败:', error)
    scheduleWsReconnect()
  }
}



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

function upgradeStatusTagType(status) {
  if (status === 'SUCCESS' || status === 'DONE') {
    return 'success'
  }
  if (status === 'UPGRADING' || status === 'UPGRADE_REQUESTED' || status === 'WAIT_RESULT') {
    return 'warning'
  }
  if (status === 'FAIL' || status === 'TIMEOUT' || status === 'CANCEL_UPGRADE' || status === 'CANCELLED') {
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

function progressBarStatus(row) {
  const status = row.deviceUpgradeStatus
  if (status === 'SUCCESS' || status === 'DONE') {
    return 'success'
  }
  if (status === 'FAIL' || status === 'TIMEOUT' || status === 'CANCEL_UPGRADE' || status === 'CANCELLED') {
    return 'exception'
  }
  return undefined
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


async function startUpgrade(row) {
  try {
    const taskId = await request.post('/api/upgrade-task/start', { imei: row.imei })
    if (taskId !== undefined && taskId !== null) {
      ElMessage.success(`已发起升级请求，任务ID：${taskId}`)
      await loadDevices()
      return
    }
    console.log('升级接口返回异常:', taskId)
    ElMessage.error('升级失败，请稍后重试')
  } catch (error) {
    console.error('升级接口调用失败:', error)
  }
}

function cancelUpgrade(row) {
  ElMessage.info(`设备 ${row.imei} 正在升级，等待接入取消升级接口`)
}

function handleAction(command, row) {
  if (command === 'edit') {
    openDialog(row)
    return
  }
  if (command === 'delete') {
    remove(row.id)
    return
  }
  if (command === 'cancel') {
    cancelUpgrade(row)
  }
}

async function openDialog(row) {
  if (row?.deviceUpgradeStatus === 'UPGRADING') {
    ElMessage.warning('设备升级中，不可编辑！')
    return
  }
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
  if (form.id && form.deviceUpgradeStatus === 'UPGRADING') {
    ElMessage.warning('设备升级中，不可编辑！')
    return
  }
  if (!/^\d{8}$/.test(form.imei)) {
    ElMessage.warning('IMEI必须是8位纯数字')
    return
  }
  const payload = {
    ...form,
    targetFirmwareId: form.targetFirmwareId || null,
    deviceUpgradeStatus: form.id ? form.deviceUpgradeStatus : 'NO_TASK'
  }
  const isEdit = Boolean(form.id)
  if (isEdit) {
    await request.put(`/api/devices/${form.id}`, payload)
  } else {
    await request.post('/api/devices', payload)
  }
  ElMessage.success(isEdit ? '更新成功' : '新增成功')
  dialogVisible.value = false
  await Promise.all([loadDevices(), loadGroups()])
}

async function remove(id) {
  await request.delete(`/api/devices/${id}`)
  ElMessage.success('删除成功')
  await Promise.all([loadDevices(), loadGroups()])
}

onMounted(async () => {
  await loadGroups()
  await loadFirmwares()
  await loadDevices()
  connectDeviceUpgradeWs()
})


onBeforeUnmount(() => {
  clearWsReconnectTimer()
  if (wsRef.value) {
    wsRef.value.close()
    wsRef.value = null
  }
})


</script>

<style scoped>

.action-dropdown-menu :deep(.el-dropdown-menu__item) {
  padding: 6px 12px;
  justify-content: center;
}

.action-dropdown-item :deep(.el-button) {
  min-width: 96px;
}

.action-dropdown {
  display: inline-block;
  margin-top: 4px;
}

.device-table-scroll {
  overflow-x: hidden;
  width: 100%;
}

.device-list-layout {
  grid-template-columns: 240px minmax(0, 1fr);
}

.device-table {
  width: 100%;
  min-width: 100%;
}

.status-text {
  white-space: nowrap;
}

.progress-cell {
  min-width: 120px;
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

.action-dropdown {
  display: inline-block;
  margin-top: 4px;
}
</style>

.version-flow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.version-arrow {
  color: #8a94a6;
}
