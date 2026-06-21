<template>
  <el-drawer v-model="visibleModel" :title="title" size="560px" class="showcase-evidence-drawer">
    <div class="evidence-drawer-stack">
      <el-alert
        v-if="sourceLabel"
        :title="sourceLabel"
        :type="sourceLabel.includes('离线') ? 'warning' : 'success'"
        show-icon
        :closable="false"
      />
      <section class="evidence-field-grid">
        <template v-for="row in rows" :key="row.label">
          <span>{{ row.label }}</span>
          <strong>{{ row.value || '-' }}</strong>
        </template>
      </section>
      <section v-if="normalizedEvent" class="evidence-json-block">
        <div>
          <strong>normalized_event</strong>
          <span>归一化字段，仅用于技术复核</span>
        </div>
        <pre>{{ formatJson(normalizedEvent) }}</pre>
      </section>
      <section v-if="rawJson" class="evidence-json-block">
        <div>
          <strong>raw JSON</strong>
          <span>原始样例内容，不展示攻击 payload</span>
        </div>
        <pre>{{ formatJson(rawJson) }}</pre>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: boolean
  title: string
  rows: Array<{ label: string; value?: string | number }>
  sourceLabel?: string
  normalizedEvent?: Record<string, unknown>
  rawJson?: Record<string, unknown>
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const visibleModel = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

function formatJson(value: Record<string, unknown>) {
  return JSON.stringify(value, null, 2)
}
</script>
