<template>
  <div class="split-layout device-list-layout">
    <div class="block-card">
      <div class="table-toolbar">
        <strong>设备分组树</strong>
      </div>
      <el-tree
        ref="groupTreeRef"
        :data="groupTree"
        node-key="id"
        default-expand-all
        show-checkbox
        check-strictly
        :props="{ label: 'label', children: 'children' }"
        @node-click="handleGroupClick"
        @check-change="handleGroupCheckChange"
      >
        <template #default="{ data }">
          <div class="group-tree-node">
            <span>{{ data.label }} ({{ data.deviceCount || 0 }})</span>
            <el-tag
              v-if="selectedBatchGroup && selectedBatchGroup.id === data.id"
              size="small"
              type="warning"
              effect="plain"
            >
              批量目标
            </el-tag>
            <el-button
              link
              type="danger"
              class="group-delete-btn"
              @click.stop="removeGroup(data)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-tree>
    </div>

    <div class="block-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" placeholder="按 IMEI/设备名搜索" clearable style="width: 180px" />
          <el-button @click="loadDevices">查询</el-button>
          <el-tooltip
            content="自动跳过不满足升级条件的设备，如未绑定固件、或当前正处于升级中的设备。"
            placement="top"
          >
            <span class="toolbar-button-wrapper">
              <el-button type="warning" :disabled="!canBatchUpgradeSelectedGroup" @click="openBatchUpgradeDialog">
                批量升级
              </el-button>
            </span>
          </el-tooltip>
          <el-button type="success" plain :disabled="!selectedBatchGroup" @click="simulateGroupOnline">
            模拟上线
          </el-button>
          <el-button type="info" plain :disabled="!selectedBatchGroup" @click="simulateGroupOffline">
            模拟下线
          </el-button>
          <el-button type="primary" plain @click="openImportDialog">
            批量添加
          </el-button>
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
                :type="upgradeStatusTagType(row.deviceUpgradeStatus)"
              >
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
          :striped="row.deviceUpgradeStatus === 'UPGRADING'"
          :striped-flow="row.deviceUpgradeStatus === 'UPGRADING'"
          :duration="2"
          :class="[
            'custom-progress',
            {
              'is-upgrading': row.deviceUpgradeStatus === 'UPGRADING',
              'is-success': row.deviceUpgradeStatus === 'SUCCESS' || row.deviceUpgradeStatus === 'DONE'
            }
          ]"
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

  <el-dialog v-model="batchDialogVisible" title="批量升级设备组" width="520px">
    <el-form :model="batchUpgradeForm" label-position="top">
      <el-form-item label="已勾选设备组">
        <el-input :model-value="selectedBatchGroup?.label || ''" disabled />
      </el-form-item>
      <el-form-item label="当前分组设备数">
        <el-input :model-value="`${selectedBatchGroup?.deviceCount || 0} 台`" disabled />
      </el-form-item>
      <el-form-item label="统一升级固件">
        <el-select
          v-model="batchUpgradeForm.firmwareId"
          placeholder="请选择要统一下发的固件"
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
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="系统会先把组内可升级设备统一绑定到所选固件，再批量发起升级请求。设备类型与固件不匹配、或当前已有升级任务的设备会自动跳过。"
      />
    </el-form>
    <template #footer>
      <el-button @click="batchDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="batchSubmitting" @click="submitBatchUpgrade">
        确认批量升级
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="importDialogVisible" title="批量添加设备" width="560px">
    <el-form :model="importForm" label-position="top">
      <el-form-item label="设备分组">
        <el-tree-select
          v-model="importForm.deviceGroupId"
          :data="groupTree"
          check-strictly
          node-key="id"
          :props="{ label: 'label', children: 'children', value: 'id' }"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="设备类型">
        <el-select v-model="importForm.deviceType" placeholder="请选择设备类型" style="width: 100%">
          <el-option
            v-for="item in deviceTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标（绑定）固件">
        <el-select
          v-model="importForm.targetFirmwareId"
          placeholder="请选择目标固件"
          clearable
          filterable
          :empty-values="[null, undefined, '']"
          :value-on-clear="null"
          style="width: 100%"
        >
          <el-option
            v-for="item in importFirmwareOptions"
            :key="item.id"
            :label="formatFirmwareLabel(item)"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Excel 文件">
        <div class="import-actions">
          <el-button link type="primary" @click="downloadImportTemplate">
            下载模板
          </el-button>
        </div>
        <el-upload
          ref="importUploadRef"
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          accept=".xls,.xlsx"
          :on-change="handleImportFileChange"
          :on-remove="handleImportFileRemove"
          :before-upload="beforeImportFileUpload"
        >
          <el-button type="primary">上传 Excel</el-button>
          <template #tip>
            <div class="upload-tip">
              仅支持 `.xls/.xlsx`，表头必须包含 `imei` 和 `设备名称`。`imei` 必须是 8 位数字，设备名称可留空。
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="importDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="importSubmitting" @click="submitImportDevices">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

const groupTreeRef = ref(null)
const groupTree = ref([])
const firmwareOptions = ref([])
const dialogVisible = ref(false)
const batchDialogVisible = ref(false)
const importDialogVisible = ref(false)
const batchSubmitting = ref(false)
const importSubmitting = ref(false)
const selectedBatchGroup = ref(null)
const importUploadRef = ref(null)
const importFile = ref(null)
const tableData = reactive({ total: 0, records: [] })
const query = reactive({ current: 1, pageSize: 6, keyword: '', deviceGroupId: null })
const batchUpgradeForm = reactive({ firmwareId: null })
const importForm = reactive({
  deviceGroupId: null,
  deviceType: '',
  targetFirmwareId: null
})
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
  const envBaseUrl = (import.meta.env.VITE_WS_BASE_URL || '').trim()
  if (envBaseUrl) {
    return `${envBaseUrl.replace(/\/$/, '')}/ws/device-upgrade`
  }
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${window.location.host}/ws/device-upgrade`
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

function findGroupNodeById(nodes, targetId) {
  for (const node of nodes || []) {
    if (node.id === targetId) {
      return node
    }
    const found = findGroupNodeById(node.children || [], targetId)
    if (found) {
      return found
    }
  }
  return null
}

function collectGroupIds(node) {
  const ids = [node.id]
  for (const child of node.children || []) {
    ids.push(...collectGroupIds(child))
  }
  return ids
}

async function loadGroups() {
  const data = await request.get('/api/device-groups/tree')
  groupTree.value = data
  if (!selectedBatchGroup.value?.id) {
    return
  }
  const matched = findGroupNodeById(data, selectedBatchGroup.value.id)
  selectedBatchGroup.value = matched || null
  await nextTick()
  groupTreeRef.value?.setCheckedKeys(matched ? [matched.id] : [])
}

async function removeGroup(group) {
  if (!group?.id) {
    return
  }
  const relatedGroupIds = collectGroupIds(group)
  await ElMessageBox.confirm(
    '确定删除该分组吗？该分组以及其下级分组和设备都会被删除！',
    '删除设备分组',
    {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  )

  await request.delete(`/api/device-groups/${group.id}`)

  if (selectedBatchGroup.value && relatedGroupIds.includes(selectedBatchGroup.value.id)) {
    selectedBatchGroup.value = null
    groupTreeRef.value?.setCheckedKeys([])
  }
  if (query.deviceGroupId && relatedGroupIds.includes(query.deviceGroupId)) {
    query.deviceGroupId = null
    query.current = 1
  }

  ElMessage.success('删除成功')
  await Promise.all([loadGroups(), loadDevices()])
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
  return `${item.fileName || '未命名固件'} / ${item.version || '-'} / ${item.deviceType || '通用'}`
}

const importFirmwareOptions = computed(() => {
  if (!importForm.deviceType) {
    return firmwareOptions.value
  }
  return firmwareOptions.value.filter((item) => !item.deviceType || item.deviceType === importForm.deviceType)
})

const canBatchUpgradeSelectedGroup = computed(() => {
  return Boolean(selectedBatchGroup.value && Number(selectedBatchGroup.value.deviceCount || 0) > 0)
})

function handleImeiInput(value) {
  form.imei = String(value || '').replace(/\D/g, '').slice(0, 8)
}

function handleGroupClick(node) {
  query.deviceGroupId = node.id
  query.current = 1
  loadDevices()
}

function handleGroupCheckChange(data, checked) {
  if (checked) {
    selectedBatchGroup.value = data
    nextTick(() => {
      groupTreeRef.value?.setCheckedKeys([data.id])
    })
    return
  }
  if (selectedBatchGroup.value?.id === data.id) {
    selectedBatchGroup.value = null
    nextTick(() => {
      groupTreeRef.value?.setCheckedKeys([])
    })
  }
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

function openBatchUpgradeDialog() {
  if (!selectedBatchGroup.value) {
    ElMessage.warning('请先勾选一个设备组')
    return
  }
  batchUpgradeForm.firmwareId = null
  batchDialogVisible.value = true
}

function openImportDialog() {
  importForm.deviceGroupId = selectedBatchGroup.value?.id || null
  importForm.deviceType = ''
  importForm.targetFirmwareId = null
  importFile.value = null
  importUploadRef.value?.clearFiles()
  importDialogVisible.value = true
}

function downloadImportTemplate() {
  window.open('/批量导入设备模板.xlsx', '_blank')
}

function beforeImportFileUpload(rawFile) {
  const lowerName = String(rawFile?.name || '').toLowerCase()
  const isExcel = lowerName.endsWith('.xls') || lowerName.endsWith('.xlsx')
  if (!isExcel) {
    ElMessage.warning('只能上传 Excel 文件')
  }
  return isExcel ? false : false
}

function handleImportFileChange(uploadFile) {
  const rawFile = uploadFile?.raw || null
  const lowerName = String(rawFile?.name || '').toLowerCase()
  if (!rawFile || (!lowerName.endsWith('.xls') && !lowerName.endsWith('.xlsx'))) {
    importFile.value = null
    importUploadRef.value?.clearFiles()
    ElMessage.warning('只能上传 Excel 文件')
    return
  }
  importFile.value = rawFile
}

function handleImportFileRemove() {
  importFile.value = null
}

async function submitBatchUpgrade() {
  if (!selectedBatchGroup.value?.id) {
    ElMessage.warning('请先勾选一个设备组')
    return
  }
  if (!batchUpgradeForm.firmwareId) {
    ElMessage.warning('请选择要统一下发的固件')
    return
  }
  batchSubmitting.value = true
  try {
    const result = await request.post('/api/batch-upgrade-tasks/start', {
      groupId: selectedBatchGroup.value.id,
      firmwareId: batchUpgradeForm.firmwareId
    })
    const message = result?.summary || '批量升级已提交'
    if ((result?.skippedCount || 0) > 0) {
      ElMessage.warning(message)
    } else {
      ElMessage.success(message)
    }
    batchDialogVisible.value = false
    await Promise.all([loadDevices(), loadGroups()])
  } finally {
    batchSubmitting.value = false
  }
}

async function simulateGroupOnline() {
  await controlMockDevices('online')
}

async function simulateGroupOffline() {
  await controlMockDevices('offline')
}

async function controlMockDevices(action) {
  if (!selectedBatchGroup.value?.id) {
    ElMessage.warning('请先勾选一个设备组')
    return
  }

  const endpoint = action === 'online' ? '/api/mock-devices/online' : '/api/mock-devices/offline'
  const fallbackMessage = action === 'online' ? '模拟上线已完成' : '模拟下线已完成'

  const result = await request.post(endpoint, { groupId: selectedBatchGroup.value.id })
  const message = result?.summary || fallbackMessage
  if ((result?.skippedCount || 0) > 0) {
    ElMessage.warning(message)
  } else {
    ElMessage.success(message)
  }
  await loadDevices()
}

async function submitImportDevices() {
  if (!importForm.deviceGroupId) {
    ElMessage.warning('请选择设备分组')
    return
  }
  if (!importForm.deviceType) {
    ElMessage.warning('请选择设备类型')
    return
  }
  if (!importFile.value) {
    ElMessage.warning('请上传 Excel 文件')
    return
  }

  const formData = new FormData()
  formData.append('file', importFile.value)
  formData.append('deviceGroupId', String(importForm.deviceGroupId))
  formData.append('deviceType', importForm.deviceType)
  if (importForm.targetFirmwareId) {
    formData.append('targetFirmwareId', String(importForm.targetFirmwareId))
  }

  importSubmitting.value = true
  try {
    const result = await request.post('/api/devices/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success(result?.summary || '批量导入完成')
    importDialogVisible.value = false
    await Promise.all([loadDevices(), loadGroups()])
  } finally {
    importSubmitting.value = false
  }
}

async function remove(id) {
  await request.delete(`/api/devices/${id}`)
  ElMessage.success('删除成功')
  await Promise.all([loadDevices(), loadGroups()])
}

watch(() => importForm.deviceType, () => {
  if (!importForm.targetFirmwareId) {
    return
  }
  const matched = importFirmwareOptions.value.some((item) => item.id === importForm.targetFirmwareId)
  if (!matched) {
    importForm.targetFirmwareId = null
  }
})

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

.group-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.group-tree-node > span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-delete-btn {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.group-tree-node:hover .group-delete-btn {
  opacity: 1;
}

.toolbar-button-wrapper {
  display: inline-flex;
}

.upload-tip {
  line-height: 1.6;
  color: #8a94a6;
}

.import-actions {
  margin-bottom: 8px;
}

.version-flow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.version-arrow {
  color: #8a94a6;
}


/* 自定义进度条样式 */
:deep(.custom-progress .el-progress-bar__outer) {
  background-color: #ebeef5;
  border-radius: 999px;
}

:deep(.custom-progress .el-progress-bar__inner) {
  border-radius: 999px;
  transition: all 0.3s ease;
}

/* 升级中：橙色条纹流动 */
:deep(.custom-progress.is-upgrading .el-progress-bar__inner) {
  --el-fill-color-light: #f5a623;
  --el-color-warning: #f5a623;
  background: repeating-linear-gradient(
    45deg,
    #f5a623,
    #f5a623 10px,
    #f8b84e 10px,
    #f8b84e 20px
  ) !important;
  background-size: 40px 40px;
}

/* 升级成功：绿色纯色 */
:deep(.custom-progress.is-success .el-progress-bar__inner) {
  background: #67c23a !important;
}

:deep(.custom-progress .el-progress__text) {
  font-size: 12px;
  color: #606266;
  min-width: 40px;
}

</style>

