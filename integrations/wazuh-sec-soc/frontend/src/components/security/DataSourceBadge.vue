<template>
  <el-tag :type="tagType" effect="dark" size="small" round>{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  source?: string
}>()

const normalized = computed(() => (props.source || 'mock').toLowerCase())

const label = computed(() => {
  if (['wazuh', 'wazuh-api', 'wazuh-indexer', 'indexer', 'realtime', 'live'].includes(normalized.value)) return '实时'
  if (['mysql', 'sync', 'synced', 'import', 'imported'].includes(normalized.value)) return '同步'
  if (['suricata', 'zeek', 'misp', 'opencti', 'external'].includes(normalized.value)) return '外部'
  return '演示'
})

const tagType = computed(() => {
  if (label.value === '实时') return 'success'
  if (label.value === '同步') return 'warning'
  if (label.value === '外部') return 'primary'
  return 'info'
})
</script>
