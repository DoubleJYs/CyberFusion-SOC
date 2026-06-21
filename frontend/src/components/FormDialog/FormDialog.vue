<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    destroy-on-close
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :before-close="beforeClose"
    @update:model-value="handleModelUpdate"
  >
    <slot :loading="busy" />
    <template #footer>
      <el-button :disabled="busy" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="busy" @click="submit">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DialogBeforeCloseFn } from 'element-plus'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  width?: string
  loading?: boolean
  confirmLoading?: boolean
  confirmText?: string
  closeOnClickModal?: boolean
  closeOnPressEscape?: boolean
  preventCloseWhenLoading?: boolean
}>(), {
  width: '560px',
  loading: false,
  confirmLoading: false,
  confirmText: '保存',
  closeOnClickModal: false,
  closeOnPressEscape: false,
  preventCloseWhenLoading: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  submit: []
  close: []
  cancel: []
}>()

const busy = computed(() => props.loading || props.confirmLoading)

function canClose() {
  return !(props.preventCloseWhenLoading && busy.value)
}

const beforeClose: DialogBeforeCloseFn = (done) => {
  if (!canClose()) return
  emit('close')
  done()
}

function handleModelUpdate(value: boolean) {
  if (value) {
    emit('update:modelValue', true)
    return
  }
  requestClose()
}

function requestClose() {
  if (!canClose()) return
  emit('cancel')
  emit('close')
  emit('update:modelValue', false)
}

function submit() {
  emit('submit')
  emit('confirm')
}
</script>
