<template>
  <div class="block-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <strong>设备分组管理</strong>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="openDialog()">新建设备组</el-button>
      </div>
    </div>

    <el-tree :data="treeData" node-key="id" default-expand-all :props="{ label: 'label', children: 'children' }">
      <template #default="{ data }">
        <div class="group-tree-node">
          <OverflowTooltipText
            class="group-tree-node__label"
            :text="formatGroupTreeLabel(data)"
          />
          <span class="group-tree-node__actions">
            <el-button link type="primary" @click.stop="openDialog(data)">编辑</el-button>
            <el-button link type="danger" @click.stop="remove(data.id)">删除</el-button>
          </span>
        </div>
      </template>
    </el-tree>
  </div>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备组' : '新建设备组'" width="520px">
    <el-form :model="form" label-position="top">
      <el-form-item label="设备组名称"><el-input v-model="form.deviceGroupName" /></el-form-item>
      <el-form-item label="上级分组">
        <el-tree-select
          v-model="form.parentId"
          :data="treeData"
          check-strictly
          clearable
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
import OverflowTooltipText from '../components/OverflowTooltipText.vue'
import request from '../api/request'

const treeData = ref([])
const dialogVisible = ref(false)
const form = reactive({ id: null, deviceGroupName: '', parentId: null })

async function loadData() {
  treeData.value = await request.get('/api/device-groups/tree')
}

function formatGroupTreeLabel(group) {
  return `${group?.label || ''} (${Number(group?.deviceCount || 0)}台)`
}

function openDialog(row) {
  Object.assign(form, row ? { id: row.id, deviceGroupName: row.label, parentId: row.parentId || null } : { id: null, deviceGroupName: '', parentId: null })
  dialogVisible.value = true
}

async function submit() {
  if (form.id) {
    await request.put(`/api/device-groups/${form.id}`, form)
  } else {
    await request.post('/api/device-groups', form)
  }
  dialogVisible.value = false
  loadData()
}

async function remove(id) {
  await request.delete(`/api/device-groups/${id}`)
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.group-tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.group-tree-node__label {
  flex: 1;
  min-width: 0;
}

.group-tree-node__actions {
  flex: 0 0 auto;
}
</style>
