<template>
  <section class="panel">
    <div class="search-panel-header">
      <span>筛选条件</span>
      <small>快速定位治理对象与安全运营数据</small>
    </div>
    <el-form :model="modelValue" inline :label-width="labelWidth" class="search-form" @submit.prevent="emit('search')">
      <slot name="default" :expanded="expanded" />
      <template v-if="expanded">
        <slot name="advanced" />
      </template>
      <el-form-item class="search-actions">
        <el-button type="primary" :icon="Search" :loading="loading" @click="emit('search')">查询</el-button>
        <el-button :icon="Refresh" :disabled="loading" @click="emit('reset')">重置</el-button>
        <el-button v-if="$slots.advanced" link type="primary" :icon="expanded ? ArrowUp : ArrowDown" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开' }}
        </el-button>
        <slot name="actions" />
      </el-form-item>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDown, ArrowUp, Refresh, Search } from '@element-plus/icons-vue'

withDefaults(defineProps<{
  modelValue: Record<string, unknown>
  loading?: boolean
  labelWidth?: string
  defaultExpanded?: boolean
}>(), {
  loading: false,
  labelWidth: '72px',
  defaultExpanded: false,
})

const emit = defineEmits<{
  search: []
  reset: []
}>()

const expanded = ref(false)
</script>

<style scoped>
.panel {
  position: relative;
  overflow: hidden;
  padding: 14px 16px 16px 22px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(249, 245, 238, 0.6)),
    var(--soc-glass);
}

.panel::before {
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 12px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--soc-warm), rgba(16, 179, 199, 0.52));
  content: "";
}

.search-panel-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.search-panel-header span {
  color: var(--soc-text);
  font-size: 13px;
  font-weight: 750;
}

.search-panel-header small {
  color: var(--soc-text-subtle);
  font-size: 12px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: flex-start;
}

.search-form :deep(.el-form-item) {
  margin: 0;
}

.search-actions {
  margin-left: auto;
  padding-left: 8px;
}

.search-form :deep(.el-input),
.search-form :deep(.el-select) {
  width: 220px;
  max-width: 100%;
}

@media (max-width: 760px) {
  .search-panel-header {
    display: grid;
    gap: 4px;
  }

  .search-form {
    display: grid;
    grid-template-columns: 1fr;
  }

  .search-form :deep(.el-form-item),
  .search-form :deep(.el-form-item__content),
  .search-form :deep(.el-input),
  .search-form :deep(.el-select),
  .search-form :deep(.el-button) {
    width: 100%;
  }
}
</style>
