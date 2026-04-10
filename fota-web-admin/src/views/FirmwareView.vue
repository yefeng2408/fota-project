<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="版本/设备类型/文件名" clearable style="width: 240px" />
        <el-button @click="loadData">查询</el-button>
      </div>
    </div>

    <el-form :model="uploadForm" inline style="margin-bottom: 16px">
      <el-form-item label="版本"><el-input v-model="uploadForm.version" /></el-form-item>
      <el-form-item label="设备类型"><el-input v-model="uploadForm.deviceType" /></el-form-item>
      <el-form-item label="分包大小"><el-input-number v-model="uploadForm.chunkSize" :min="128" /></el-form-item>
      <el-form-item label="固件文件">
        <input type="file" @change="handleFileChange" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="upload">上传固件</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData.records">
      <el-table-column prop="version" label="版本号" />
      <el-table-column prop="deviceType" label="设备类型" />
      <el-table-column prop="fileName" label="文件名" />
      <el-table-column prop="fileSize" label="大小" />
      <el-table-column prop="md5" label="MD5" min-width="240" />
      <el-table-column prop="chunkSize" label="分包大小" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import request from '../api/request'

const fileRef = ref(null)
const query = reactive({ current: 1, pageSize: 10, keyword: '' })
const tableData = reactive({ total: 0, records: [] })
const uploadForm = reactive({
  version: '',
  deviceType: '',
  chunkSize: 1024,
  forceUpgrade: 0,
  status: 1,
  remark: ''
})

async function loadData() {
  const data = await request.get('/api/firmwares', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

function handleFileChange(event) {
  fileRef.value = event.target.files[0]
}

async function upload() {
  const formData = new FormData()
  formData.append('file', fileRef.value)
  Object.keys(uploadForm).forEach((key) => formData.append(key, uploadForm[key]))
  await request.post('/api/firmwares/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  await loadData()
}

async function remove(id) {
  await request.delete(`/api/firmwares/${id}`)
  loadData()
}

onMounted(loadData)
</script>
