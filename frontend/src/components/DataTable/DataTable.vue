<template>
  <section class="table-panel">
    <div v-if="title || subtitle || badge" class="table-panel-head">
      <div>
        <strong>{{ title }}</strong>
        <span>{{ subtitle }}</span>
      </div>
      <el-tag v-if="badge" effect="plain">{{ badge }}</el-tag>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-table
      v-loading="loading"
      :data="data"
      :height="height"
      :row-key="rowKey"
      :empty-text="emptyText"
    >
      <el-table-column
        v-for="column in columns"
        :key="column.prop || column.label"
        :prop="column.prop"
        :label="column.label"
        :width="column.width"
        :min-width="column.minWidth"
        :fixed="column.fixed"
        :show-overflow-tooltip="column.showOverflowTooltip"
        :align="column.align"
      >
        <template v-if="column.slot" #default="{ row }">
          <slot :name="column.slot" :row="row" />
        </template>
      </el-table-column>
      <el-table-column v-if="$slots.operation" label="操作" fixed="right" :width="operationWidth">
        <template #default="{ row }">
          <slot name="operation" :row="row" />
        </template>
      </el-table-column>
      <template #empty>
        <EmptyState :description="emptyText" />
      </template>
    </el-table>
    <div v-if="pagination" class="pagination-row">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :current-page="pagination.pageNum"
        :page-size="pagination.pageSize"
        :page-sizes="pageSizes"
        :total="pagination.total"
        @current-change="emit('page-change', $event)"
        @size-change="emit('size-change', $event)"
      />
    </div>
  </section>
</template>

<script setup lang="ts" generic="T extends Record<string, unknown>">
import EmptyState from '@/components/EmptyState/EmptyState.vue'

export interface DataTableColumn {
  prop?: string
  label: string
  width?: string | number
  minWidth?: string | number
  fixed?: true | 'left' | 'right'
  showOverflowTooltip?: boolean
  align?: 'left' | 'center' | 'right'
  slot?: string
}

export interface DataTablePagination {
  pageNum: number
  pageSize: number
  total: number
}

withDefaults(defineProps<{
  data: T[]
  columns: DataTableColumn[]
  title?: string
  subtitle?: string
  badge?: string
  loading?: boolean
  error?: string
  rowKey?: string
  height?: string | number
  emptyText?: string
  pagination?: DataTablePagination
  pageSizes?: number[]
  operationWidth?: string | number
}>(), {
  title: '',
  subtitle: '',
  badge: '',
  loading: false,
  error: '',
  rowKey: 'id',
  height: undefined,
  emptyText: '暂无数据',
  pagination: undefined,
  pageSizes: () => [10, 20, 50, 100],
  operationWidth: 160,
})

const emit = defineEmits<{
  'page-change': [page: number]
  'size-change': [size: number]
}>()
</script>

<style scoped>
.table-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--soc-border);
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.76), rgba(255, 248, 238, 0.58)),
    rgba(255, 255, 255, 0.46);
}

.table-panel-head div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.table-panel-head strong {
  color: var(--soc-text);
  font-size: 14px;
  font-weight: 750;
}

.table-panel-head span {
  overflow: hidden;
  color: var(--soc-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .table-panel-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .table-panel-head span {
    white-space: normal;
  }
}
</style>
