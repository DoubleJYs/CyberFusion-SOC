<template>
  <el-select
    :model-value="modelValue"
    :clearable="clearable"
    :disabled="disabled || loading"
    :loading="loading"
    :placeholder="placeholder"
    filterable
    @update:model-value="emit('update:modelValue', $event)"
    @change="emit('change', $event)"
  >
    <el-option
      v-for="option in options"
      :key="option.dictValue"
      :label="option.dictLabel"
      :value="option.dictValue"
    />
  </el-select>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { fetchDictData, fetchDictTypes } from '@/api/dict'
import type { DictDataRecord } from '@/types/system'

const props = withDefaults(defineProps<{
  modelValue?: string | number
  dictCode: string
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
}>(), {
  placeholder: '请选择',
  clearable: true,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number | undefined]
  change: [value: string | number | undefined]
}>()

const loading = ref(false)
const options = ref<DictDataRecord[]>([])

async function loadOptions() {
  if (!props.dictCode) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const typePage = await fetchDictTypes({ pageNum: 1, pageSize: 100 })
    const type = typePage.records.find((item) => item.dictCode === props.dictCode && item.status !== 0)
    if (!type) {
      options.value = []
      return
    }
    const dataPage = await fetchDictData({ pageNum: 1, pageSize: 200, dictTypeId: type.id })
    options.value = dataPage.records
      .filter((item) => item.status !== 0)
      .sort((left, right) => left.sortOrder - right.sortOrder)
  } finally {
    loading.value = false
  }
}

watch(() => props.dictCode, loadOptions)
onMounted(loadOptions)
</script>
