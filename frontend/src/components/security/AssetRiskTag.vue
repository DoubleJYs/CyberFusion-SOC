<template>
  <el-tag :type="type" effect="dark" class="asset-risk" :class="riskLevel">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ riskLevel?: string }>()
const label = computed(() => ({ critical: '严重风险', high: '高风险', medium: '中风险', low: '低风险' }[props.riskLevel || ''] || props.riskLevel || '-'))
const type = computed(() => (props.riskLevel === 'low' ? 'success' : props.riskLevel === 'medium' ? 'warning' : 'danger'))
</script>

<style scoped>
.asset-risk {
  min-width: 64px;
  justify-content: center;
  border: 0;
}
.asset-risk.critical { background: var(--soc-critical); }
.asset-risk.high { background: var(--soc-high); }
.asset-risk.medium { background: var(--soc-medium); }
.asset-risk.low { background: var(--soc-success); }
</style>
