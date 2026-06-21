<template>
  <el-tag :type="tagType">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface StatusOption {
  value: string | number
  label: string
  type?: 'success' | 'warning' | 'info' | 'primary' | 'danger'
}

const props = defineProps<{
  value?: string | number | null
  options?: StatusOption[]
}>()

const defaultOptions: StatusOption[] = [
  { value: 1, label: '启用', type: 'success' },
  { value: 0, label: '停用', type: 'info' },
  { value: 'SUCCESS', label: '成功', type: 'success' },
  { value: 'FAIL', label: '失败', type: 'danger' },
  { value: 'PARTIAL_FAIL', label: '部分失败', type: 'warning' },
  { value: 'IMPORT', label: '导入', type: 'primary' },
  { value: 'EXPORT', label: '导出', type: 'success' },
]

const matched = computed(() => (props.options || defaultOptions).find((item) => String(item.value) === String(props.value)))
const label = computed(() => matched.value?.label || String(props.value ?? '-'))
const tagType = computed(() => matched.value?.type || 'info')
</script>
