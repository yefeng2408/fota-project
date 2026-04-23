<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="openUploadDialog">新增</el-button>
        <el-input v-model="query.keyword" placeholder="版本/设备类型/文件名" clearable style="width: 240px" />
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <div class="firmware-table-scroll">
      <el-table :data="tableData.records" class="firmware-table">
        <el-table-column prop="version" label="版本号" width="100" />
        <el-table-column prop="deviceType" label="设备类型" width="100" />
        <el-table-column prop="fileName" label="文件名" min-width="60" show-overflow-tooltip />
        <el-table-column label="文件下载地址" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip v-if="row.downloadUrl" :content="row.fileUrl || row.downloadUrl" placement="top" effect="light">
              <el-link type="primary" :href="row.downloadUrl" target="_blank">下载</el-link>
            </el-tooltip>
            <span v-else>{{ row.fileUrl || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小（M）" width="110" />
        <el-table-column prop="md5" label="MD5" min-width="100" show-overflow-tooltip />
        <el-table-column prop="chunkSize" label="分包大小（byte）" width="120" />
        <el-table-column prop="totalPacket" label="分包数量" width="100" />
        <!-- <el-table-column prop="status" label="状态" width="90" /> -->
        <el-table-column prop="remark" label="备注" min-width="80" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>

  <el-dialog v-model="uploadDialogVisible" title="新增固件" width="560px">
    <el-form :model="uploadForm" label-position="top">
      <el-form-item label="固件版本号">
        <el-input v-model="uploadForm.version" placeholder="例如 v1.0.1" />
      </el-form-item>
      <el-form-item label="设备类型">
        <el-select v-model="uploadForm.deviceType" placeholder="请选择设备类型" style="width: 100%" @change="handleDeviceTypeChange">
          <el-option
            v-for="item in deviceTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="分包大小">
        <el-input-number v-model="uploadForm.chunkSize" :disabled="true" style="width: 100%" />
      </el-form-item>
      <el-form-item label="固件文件">
        <div class="firmware-file-row">
          <el-button @click="triggerFileSelect">选择固件</el-button>
          <span class="firmware-file-name">{{ selectedFileName || '未选择文件' }}</span>
          <input ref="fileInputRef" class="firmware-file-input" type="file" @change="handleFileChange" />
        </div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="uploadForm.remark" type="textarea" :rows="3" placeholder="可填写固件说明、适配范围或发布备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="uploadDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="upload">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const fileRef = ref(null)
const fileInputRef = ref(null)
const selectedFileName = ref('')
const uploadDialogVisible = ref(false)
const query = reactive({ current: 1, pageSize: 10, keyword: '' })
const tableData = reactive({ total: 0, records: [] })
const chunkSizeMap = {
  D056: 512,
  D057: 1024,
  MOTOR_V1: 2048
}
const deviceTypeOptions = [
  { label: 'D056', value: 'D056' },
  { label: 'D057', value: 'D057' },
  { label: 'MOTOR_V1', value: 'MOTOR_V1' }
]
const uploadForm = reactive({
  version: '',
  deviceType: 'D056',
  chunkSize: chunkSizeMap.D056,
  forceUpgrade: 0,
  status: 1,
  remark: ''
})

async function loadData() {
  const data = await request.get('/api/firmwares', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

function openUploadDialog() {
  resetUploadForm()
  uploadDialogVisible.value = true
}

function resetUploadForm() {
  Object.assign(uploadForm, {
    version: '',
    deviceType: 'D056',
    chunkSize: chunkSizeMap.D056,
    forceUpgrade: 0,
    status: 1,
    remark: ''
  })
  fileRef.value = null
  selectedFileName.value = ''
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function handleDeviceTypeChange(deviceType) {
  uploadForm.chunkSize = chunkSizeMap[deviceType]
}

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function handleFileChange(event) {
  fileRef.value = event.target.files[0]
  selectedFileName.value = fileRef.value?.name || ''
}

async function upload() {
  if (!uploadForm.version) {
    ElMessage.warning('请输入固件版本号')
    return
  }
  if (!uploadForm.deviceType) {
    ElMessage.warning('请选择设备类型')
    return
  }
  if (!fileRef.value) {
    ElMessage.warning('请选择固件文件')
    return
  }
  const formData = new FormData()
  formData.append('file', fileRef.value)
  Object.keys(uploadForm).forEach((key) => formData.append(key, uploadForm[key]))
  await request.post('/api/firmwares/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  uploadDialogVisible.value = false
  await loadData()
}

async function remove(id) {
  await request.delete(`/api/firmwares/${id}`)
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.firmware-file-row {
  align-items: center;
  display: flex;
  gap: 12px;
}

.firmware-file-name {
  color: #64748b;
  font-size: 14px;
}

.firmware-file-input {
  display: none;
}

.firmware-table-scroll {
  overflow-x: auto;
  width: 100%;
}

.firmware-table {
  min-width: 1500px;
}
</style>
