<template>
  <el-dialog v-model="visible" :title="title" width="720px">
    <div class="import-actions">
      <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
      <UploadButton label="上传 Excel" accept=".xls,.xlsx" :auto-upload="false" @selected="onSelected" />
    </div>
    <el-alert v-if="result" class="result-alert" :title="`总计 ${result.totalCount} 行，成功 ${result.successCount} 行，失败 ${result.failCount} 行`" :type="result.failCount ? 'warning' : 'success'" show-icon :closable="false" />
    <el-table v-if="result?.errors.length" :data="result.errors" border height="260">
      <el-table-column prop="rowNumber" label="行号" width="90" />
      <el-table-column prop="fieldName" label="字段" width="140" />
      <el-table-column prop="reason" label="错误原因" min-width="220" />
    </el-table>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Download } from '@element-plus/icons-vue'
import UploadButton from '@/components/UploadButton/UploadButton.vue'
import { downloadExcelTemplate, importExcel } from '@/api/excel'
import type { ExcelImportResult } from '@/types/excel'

const props = withDefaults(defineProps<{
  modelValue: boolean
  templateCode: string
  title?: string
}>(), {
  title: 'Excel 导入',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  imported: []
}>()

const result = ref<ExcelImportResult | null>(null)
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

async function downloadTemplate() {
  await downloadExcelTemplate(props.templateCode)
}

async function onSelected(rawFile: File) {
  result.value = await importExcel(props.templateCode, rawFile)
  emit('imported')
}
</script>

<style scoped>
.import-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.result-alert {
  margin-bottom: 12px;
}
</style>
