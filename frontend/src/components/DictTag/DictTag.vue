<template>
  <el-tag :type="tagType" effect="light">
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { fetchDictData, fetchDictTypes } from '@/api/dict'
import type { TagProps } from 'element-plus'
import type { DictDataRecord } from '@/types/system'

const props = defineProps<{
  dictCode: string
  value?: string | number
}>()

const options = ref<DictDataRecord[]>([])

const label = computed(() => {
  const value = String(props.value ?? '')
  return options.value.find((item) => item.dictValue === value)?.dictLabel || value || '-'
})

const tagType = computed<TagProps['type']>(() => {
  const value = String(props.value ?? '')
  if (value === '1' || value === 'SUCCESS') return 'success'
  if (value === '0' || value === 'FAIL') return 'info'
  return undefined
})

async function loadOptions() {
  if (!props.dictCode) {
    options.value = []
    return
  }
  const typePage = await fetchDictTypes({ pageNum: 1, pageSize: 100 })
  const type = typePage.records.find((item) => item.dictCode === props.dictCode && item.status !== 0)
  if (!type) {
    options.value = []
    return
  }
  const dataPage = await fetchDictData({ pageNum: 1, pageSize: 200, dictTypeId: type.id })
  options.value = dataPage.records.filter((item) => item.status !== 0)
}

watch(() => props.dictCode, loadOptions)
onMounted(loadOptions)
</script>
