<template>
  <div ref="chartRef" class="risk-trend-chart" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { socEchartsTheme } from '@/styles/echarts-theme'

const props = defineProps<{ labels: string[]; values: number[]; name?: string }>()
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | undefined

function render() {
  if (!chartRef.value) return
  chart ||= echarts.init(chartRef.value, socEchartsTheme)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: props.labels },
    yAxis: { type: 'value' },
    series: [{
      name: props.name || '告警',
      type: 'line',
      smooth: true,
      symbolSize: 7,
      areaStyle: { opacity: 0.12 },
      data: props.values,
    }],
  })
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
watch(() => [props.labels, props.values], render, { deep: true })

function resize() {
  chart?.resize()
}
</script>

<style scoped>
.risk-trend-chart {
  width: 100%;
  height: 280px;
}
</style>
