<template>
  <el-table :data="attachments" border size="small" empty-text="暂无附件">
    <el-table-column prop="fileName" label="文件名" min-width="180" />
    <el-table-column label="大小" width="110">
      <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
    </el-table-column>
    <el-table-column prop="uploaderName" label="上传人" width="120" />
    <el-table-column prop="remark" label="备注" min-width="140" />
    <el-table-column label="操作" width="140">
      <template #default="{ row }">
        <el-button link type="primary" @click="download(row)">下载</el-button>
        <el-button link type="danger" @click="$emit('remove', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { downloadFileBlob, saveBlob } from '@/api/file'
import type { SysAttachmentRecord } from '@/types/file'

defineProps<{
  attachments: SysAttachmentRecord[]
}>()

defineEmits<{
  remove: [row: SysAttachmentRecord]
}>()

function formatSize(size?: number) {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function download(row: SysAttachmentRecord) {
  const blob = await downloadFileBlob(row.fileId)
  saveBlob(blob, row.fileName || `file-${row.fileId}`)
}
</script>
