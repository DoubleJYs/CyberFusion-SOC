<template>
  <div class="empty-state">
    <el-empty :description="description" :image-size="88" />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  description?: string
}>()
</script>

<style scoped>
.empty-state {
  display: grid;
  place-items: center;
  min-height: 180px;
  margin: 10px;
  border: 1px dashed rgba(179, 173, 163, 0.52);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.58), rgba(250, 246, 238, 0.5));
}

.empty-state :deep(.el-empty__description p) {
  color: var(--soc-text-muted);
  font-size: 13px;
}
</style>
