<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="用户名/手机号" clearable style="width: 240px" />
        <el-button @click="loadData">查询</el-button>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="openDialog()">新增用户</el-button>
      </div>
    </div>

    <el-table :data="tableData.records">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="deviceGroupIds" label="设备组权限" />
      <el-table-column prop="createdAt" label="创建时间" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="560px">
    <el-form :model="form" label-position="top">
      <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
      <el-form-item label="设备组权限">
        <el-tree-select
          v-model="form.deviceGroupIds"
          multiple
          show-checkbox
          check-strictly
          :data="groupTree"
          node-key="id"
          :props="{ label: 'label', children: 'children', value: 'id' }"
        />
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
import request from '../api/request'

const dialogVisible = ref(false)
const groupTree = ref([])
const query = reactive({ current: 1, pageSize: 10, keyword: '' })
const tableData = reactive({ total: 0, records: [] })
const form = reactive({ id: null, username: '', phone: '', password: '', deviceGroupIds: [] })

async function loadData() {
  const data = await request.get('/api/users', { params: query })
  tableData.total = data.total
  tableData.records = data.records
}

async function loadGroups() {
  groupTree.value = await request.get('/api/device-groups/tree')
}

function openDialog(row) {
  Object.assign(form, row || { id: null, username: '', phone: '', password: '', deviceGroupIds: [] })
  if (!row) {
    form.password = ''
  }
  dialogVisible.value = true
}

async function submit() {
  if (form.id) {
    await request.put(`/api/users/${form.id}`, form)
  } else {
    await request.post('/api/users', form)
  }
  dialogVisible.value = false
  loadData()
}

async function remove(id) {
  await request.delete(`/api/users/${id}`)
  loadData()
}

onMounted(async () => {
  await loadGroups()
  await loadData()
})
</script>
