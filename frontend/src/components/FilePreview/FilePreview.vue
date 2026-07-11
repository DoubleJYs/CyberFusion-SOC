<template>
  <div class="file-preview">
    <div v-if="file" class="preview-head">
      <div>
        <strong>{{ file.originalName }}</strong>
        <span>{{ file.fileExt || 'file' }} · {{ formatSize(file.fileSize) }}</span>
      </div>
      <el-button :icon="Download" type="primary" plain @click="download">下载</el-button>
    </div>
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-alert v-else-if="error" :title="error" type="warning" show-icon :closable="false" />
    <el-image v-else-if="isImage" class="preview-image" :src="objectUrl" fit="contain" :preview-src-list="objectUrl ? [objectUrl] : []">
      <template #error>
        <el-empty description="图片不可预览" :image-size="80" />
      </template>
    </el-image>
    <iframe v-else-if="isPdf && objectUrl" class="preview-frame" :src="objectUrl" title="PDF 文件预览" />
    <div v-else-if="isExcel && tablePreview" class="table-preview">
      <div class="table-preview-meta">
        <span>{{ tablePreview.totalRows }} 行数据</span>
        <el-tag v-if="tablePreview.truncated" type="warning" effect="plain">仅预览前 200 行</el-tag>
      </div>
      <el-table :data="tableRows" border height="520" empty-text="暂无可预览内容">
        <el-table-column
          v-for="(header, index) in tablePreview.headers"
          :key="`${header}-${index}`"
          :label="header || `列 ${index + 1}`"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.cells[index] || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>
    <el-alert v-else title="该文件类型暂不支持预览，请下载后查看" type="info" :closable="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { downloadFileBlob, previewFileBlob, previewFileTable, saveBlob, type FileTablePreview } from '@/api/file'
import type { SysFileRecord } from '@/types/file'

const props = defineProps<{
  file?: SysFileRecord | null
}>()

const objectUrl = ref('')
const tablePreview = ref<FileTablePreview | null>(null)
const loading = ref(false)
const error = ref('')
const isImage = computed(() => Boolean(props.file?.contentType?.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(props.file?.fileExt || '')))
const isPdf = computed(() => Boolean(props.file?.contentType === 'application/pdf' || normalizedExt.value === 'pdf'))
const isExcel = computed(() => normalizedExt.value === 'xlsx')
const normalizedExt = computed(() => (props.file?.fileExt || '').toLowerCase())
const tableRows = computed(() => (tablePreview.value?.rows || []).map((cells, index) => ({ id: index, cells })))

watch(() => props.file?.id, async () => {
  release()
  tablePreview.value = null
  error.value = ''
  if (!props.file) return
  if (!isImage.value && !isPdf.value && !isExcel.value) return
  loading.value = true
  try {
    if (isImage.value || isPdf.value) {
      const blob = await previewFileBlob(props.file.id)
      objectUrl.value = URL.createObjectURL(blob)
    } else if (isExcel.value) {
      tablePreview.value = await previewFileTable(props.file.id)
    }
  } catch {
    error.value = isExcel.value ? 'Excel 预览加载失败，请下载后查看。' : '文件预览加载失败，请下载后查看。'
  } finally {
    loading.value = false
  }
}, { immediate: true })

onBeforeUnmount(release)

async function download() {
  if (!props.file) return
  const blob = await downloadFileBlob(props.file.id)
  saveBlob(blob, props.file.originalName)
}

function release() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.file-preview {
  min-height: 120px;
  padding: 10px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.46);
}

.preview-image {
  width: 100%;
  max-height: 360px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(245, 241, 234, 0.78)),
    var(--soc-canvas-soft);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--soc-border);
}

.preview-head div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.preview-head strong,
.preview-head span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-head span {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.preview-frame {
  width: 100%;
  height: 620px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: #fff;
}

.table-preview {
  display: grid;
  gap: 10px;
}

.table-preview-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--soc-text-muted);
  font-size: 12px;
}

@media (max-width: 760px) {
  .preview-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .preview-frame {
    height: 520px;
  }
}
</style>
