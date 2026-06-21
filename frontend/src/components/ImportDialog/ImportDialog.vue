<template>
  <el-dialog v-model="visible" :title="title" width="720px">
    <section class="import-brief">
      <span>数据导入任务</span>
      <strong>{{ templateCode }}</strong>
      <p>下载模板后上传经过授权的数据文件，导入结果仅记录结构化摘要与错误行原因。</p>
    </section>
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
.import-brief {
  display: grid;
  gap: 6px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius-card);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.78), rgba(255, 248, 238, 0.56)),
    var(--soc-glass);
  box-shadow: var(--soc-glass-highlight);
}

.import-brief span {
  color: var(--soc-warm-strong);
  font-size: 12px;
  font-weight: 750;
}

.import-brief strong {
  color: var(--soc-text);
  font-size: 16px;
}

.import-brief p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.import-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.result-alert {
  margin-bottom: 12px;
}
</style>
