<template>
  <el-button :type="type" :icon="Download" :loading="loading" @click="runExport">{{ label }}</el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Download } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  label?: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | ''
  action: () => Promise<void>
}>(), {
  label: '导出',
  type: '',
})

const loading = ref(false)

async function runExport() {
  loading.value = true
  try {
    await props.action()
  } finally {
    loading.value = false
  }
}
</script>
