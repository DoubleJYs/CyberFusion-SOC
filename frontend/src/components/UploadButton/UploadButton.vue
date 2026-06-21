<template>
  <span class="upload-button">
    <input ref="inputRef" class="native-file" type="file" :accept="accept" @change="onFileChange" />
    <el-button :type="type" :icon="Upload" :loading="uploading" @click="inputRef?.click()">
      {{ uploading ? `${progress}%` : label }}
    </el-button>
  </span>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { uploadFile } from '@/api/file'
import type { SysFileRecord } from '@/types/file'

const props = withDefaults(defineProps<{
  label?: string
  bizType?: string
  accept?: string
  maxSizeMb?: number
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | ''
  autoUpload?: boolean
}>(), {
  label: '上传文件',
  accept: '.jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip',
  maxSizeMb: 20,
  type: 'primary',
  autoUpload: true,
})

const emit = defineEmits<{
  success: [file: SysFileRecord, rawFile: File]
  selected: [rawFile: File]
  error: [message: string]
}>()

const inputRef = ref<HTMLInputElement>()
const uploading = ref(false)
const progress = ref(0)

async function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    const message = `文件大小不能超过 ${props.maxSizeMb}MB`
    ElMessage.warning(message)
    emit('error', message)
    target.value = ''
    return
  }
  if (!props.autoUpload) {
    emit('selected', file)
    target.value = ''
    return
  }
  uploading.value = true
  progress.value = 0
  try {
    const uploaded = await uploadFile(file, props.bizType, (percent) => {
      progress.value = percent
    })
    ElMessage.success('上传成功')
    emit('success', uploaded, file)
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}
</script>

<style scoped>
.upload-button {
  display: inline-flex;
}

.upload-button :deep(.el-button) {
  box-shadow: var(--soc-glass-highlight), 0 8px 18px rgba(91, 77, 53, 0.08);
}

.native-file {
  display: none;
}
</style>
