<template>
  <el-tag :type="tagType" effect="light" size="small" round class="source-badge">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  source?: string
}>()

const normalized = computed(() => (props.source || 'mock').toLowerCase())

const label = computed(() => {
  if (['macos-agent', 'windows-agent', 'host-agent'].includes(normalized.value)) return '主机'
  if (['wazuh', 'wazuh-api', 'wazuh-indexer', 'indexer', 'realtime', 'live'].includes(normalized.value)) return '实时'
  if (['mysql', 'sync', 'synced', 'import', 'imported'].includes(normalized.value)) return '同步'
  if (['waf', 'suricata', 'zeek', 'misp', 'trivy', 'zap', 'sigma', 'shuffle', 'cyberchef', 'opencti', 'falco', 'osquery', 'velociraptor', 'cowrie', 'external'].includes(normalized.value)) return '外部'
  return '演示'
})

const tagType = computed(() => {
  if (label.value === '主机') return 'success'
  if (label.value === '实时') return 'success'
  if (label.value === '同步') return 'warning'
  if (label.value === '外部') return 'primary'
  return 'info'
})
</script>

<style scoped>
.source-badge {
  border-color: rgba(255, 255, 255, 0.5);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
  font-weight: 700;
}
</style>
