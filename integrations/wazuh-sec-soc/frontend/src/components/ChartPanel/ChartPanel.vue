<template>
  <section class="chart-panel">
    <div class="chart-header">
      <h2>{{ title }}</h2>
      <slot name="actions" />
    </div>
    <div v-loading="loading" class="chart-body">
      <el-empty v-if="empty && !loading" description="暂无图表数据" :image-size="88" />
      <div v-else ref="containerRef" class="chart-container">
        <slot />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{
  title: string
  loading?: boolean
  empty?: boolean
}>(), {
  loading: false,
  empty: false,
})

const containerRef = ref<HTMLElement>()
defineExpose({ containerRef })
</script>

<style scoped>
.chart-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #eef2f7;
}

.chart-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}

.chart-body {
  min-height: 260px;
  padding: 16px;
}

.chart-container {
  min-height: 228px;
}
</style>
