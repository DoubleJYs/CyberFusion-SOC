<template>
  <el-tag effect="dark" :type="type" class="severity-badge" :class="severity">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ severity?: string }>()
const label = computed(() => ({ critical: '严重', high: '高危', medium: '中危', low: '低危' }[props.severity || ''] || props.severity || '-'))
const type = computed(() => (props.severity === 'low' ? 'info' : props.severity === 'medium' ? 'warning' : 'danger'))
</script>

<style scoped>
.severity-badge {
  min-width: 48px;
  justify-content: center;
  border: 0;
}
.severity-badge.critical { background: var(--soc-critical); }
.severity-badge.high { background: var(--soc-high); }
.severity-badge.medium { background: var(--soc-medium); }
.severity-badge.low { background: var(--soc-low); }
</style>
