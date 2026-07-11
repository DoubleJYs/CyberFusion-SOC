<template>
  <el-tag effect="light" :type="type" class="status-badge">{{ text }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status?: string }>()
const map: Record<string, string> = {
  new: '新告警',
  acknowledged: '已确认',
  false_positive: '误报',
  ignored: '已忽略',
  closed: '已关闭',
  ticketed: '已转工单',
  open: '待修复',
  fixing: '修复中',
  reviewing: '待复核',
  fixed: '已修复',
  accepted: '已接受风险',
  failed: '未通过',
  remediating: '整改中',
  passed: '已通过',
  confirmed: '已确认',
  READY: '就绪',
  DRY_RUN: '演示发送',
  PENDING: '待发送',
  SENT: '已发送',
  FAIL: '发送失败',
  whitelisted: '已降噪',
  enabled: '启用',
  disabled: '停用',
  linked: '已关联',
  online: '在线',
  offline: '离线',
  pending_heartbeat: '待心跳',
  warning: '异常',
  empty: '无数据',
}
const text = computed(() => map[props.status || ''] || props.status || '-')
const type = computed(() => {
  if (['closed', '已关闭', '已归档', 'fixed', 'passed', 'confirmed', 'READY', 'SENT', 'enabled', 'linked', 'online'].includes(props.status || '')) return 'success'
  if (['ignored', 'false_positive', 'accepted', 'DRY_RUN', 'whitelisted', 'disabled', 'offline', 'empty'].includes(props.status || '')) return 'info'
  if (['new', '待分派', 'open', 'failed', 'FAIL'].includes(props.status || '')) return 'danger'
  if (['待复核', 'acknowledged', 'reviewing', 'fixing', 'remediating', 'PENDING', 'warning', 'pending_heartbeat'].includes(props.status || '')) return 'warning'
  return 'primary'
})
</script>

<style scoped>
.status-badge {
  border-color: rgba(255, 255, 255, 0.55);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
  font-weight: 700;
}
</style>
