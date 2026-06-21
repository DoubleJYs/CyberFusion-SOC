<template>
  <div class="file-preview">
    <el-image v-if="isImage" class="preview-image" :src="objectUrl" fit="cover" :preview-src-list="objectUrl ? [objectUrl] : []">
      <template #error>
        <el-empty description="图片不可预览" :image-size="80" />
      </template>
    </el-image>
    <el-alert v-else title="非图片文件，请下载后查看" type="info" :closable="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { previewFileBlob } from '@/api/file'
import type { SysFileRecord } from '@/types/file'

const props = defineProps<{
  file?: SysFileRecord | null
}>()

const objectUrl = ref('')
const isImage = computed(() => Boolean(props.file?.contentType?.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(props.file?.fileExt || '')))

watch(() => props.file?.id, async () => {
  release()
  if (!props.file || !isImage.value) return
  const blob = await previewFileBlob(props.file.id)
  objectUrl.value = URL.createObjectURL(blob)
}, { immediate: true })

onBeforeUnmount(release)

function release() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
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
</style>
