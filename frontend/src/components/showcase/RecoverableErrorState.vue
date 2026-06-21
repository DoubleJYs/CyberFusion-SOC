<template>
  <section class="recoverable-error-card">
    <div>
      <span>DATA SOURCE</span>
      <h2>演示数据加载失败</h2>
      <p>可能是后端未启动、数据库未初始化或接口暂时不可用。</p>
    </div>
    <div class="recoverable-actions">
      <el-button type="primary" :loading="retrying" @click="$emit('retry')">重试</el-button>
      <el-button @click="$emit('useOffline')">使用离线演示数据</el-button>
      <el-button text @click="$emit('toggleDiagnostics')">查看诊断信息</el-button>
    </div>
    <pre v-if="showDiagnostics">{{ diagnosticsText || '暂无诊断信息。' }}</pre>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  retrying?: boolean
  showDiagnostics?: boolean
  diagnosticsText?: string
}>()

defineEmits<{
  (event: 'retry'): void
  (event: 'useOffline'): void
  (event: 'toggleDiagnostics'): void
}>()
</script>
