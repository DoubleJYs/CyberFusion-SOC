<template>
  <el-tag effect="light" :type="type" class="severity-badge" :class="severity">{{ label }}</el-tag>
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
  font-weight: 760;
}
.severity-badge.critical { border-color: rgba(229, 72, 77, 0.25); background: rgba(255, 235, 236, 0.9); color: #c91f2e; }
.severity-badge.high { border-color: rgba(240, 138, 36, 0.26); background: rgba(255, 242, 224, 0.92); color: #c05f15; }
.severity-badge.medium { border-color: rgba(214, 162, 29, 0.28); background: rgba(255, 247, 218, 0.92); color: #946f00; }
.severity-badge.low { border-color: rgba(21, 168, 189, 0.24); background: rgba(225, 249, 252, 0.9); color: #08798b; }
</style>
