<template>
  <el-timeline class="attack-timeline">
    <el-timeline-item v-for="item in items" :key="item.id || item.createdAt" :timestamp="item.createdAt" placement="top">
      <strong>{{ item.action }}</strong>
      <p>{{ item.fromStatus || '开始' }} -> {{ item.toStatus || '-' }}</p>
      <small>{{ item.operatorName || '-' }} · {{ item.remark || '无说明' }}</small>
    </el-timeline-item>
  </el-timeline>
</template>

<script setup lang="ts">
defineProps<{ items: Array<{ id?: number; action: string; fromStatus?: string; toStatus?: string; operatorName?: string; remark?: string; createdAt?: string }> }>()
</script>

<style scoped>
.attack-timeline {
  padding: 6px 0 0 2px;
}
p {
  margin: 4px 0;
  color: var(--soc-text-muted);
}
small {
  color: var(--soc-text-subtle);
}
</style>
