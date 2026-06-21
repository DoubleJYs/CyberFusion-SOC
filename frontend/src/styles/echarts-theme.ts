export const socEchartsTheme = {
  color: ['#15a8bd', '#3978d8', '#d4934a', '#f08a24', '#e5484d'],
  backgroundColor: 'transparent',
  textStyle: { color: '#1f2937', fontFamily: 'Inter, PingFang SC, Microsoft YaHei, sans-serif' },
  grid: { left: 36, right: 18, top: 28, bottom: 28 },
  categoryAxis: {
    axisLine: { lineStyle: { color: 'rgba(126, 117, 101, 0.35)' } },
    axisTick: { show: false },
    axisLabel: { color: '#667085' },
    splitLine: { show: false },
  },
  valueAxis: {
    axisLine: { show: false },
    axisLabel: { color: '#667085' },
    splitLine: { lineStyle: { color: 'rgba(126, 117, 101, 0.18)' } },
  },
  legend: { textStyle: { color: '#667085' } },
  tooltip: {
    borderColor: 'rgba(179, 173, 163, 0.55)',
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
    textStyle: { color: '#1f2937' },
    extraCssText: 'box-shadow: 0 14px 32px rgba(91, 77, 53, 0.14); backdrop-filter: blur(16px);',
  },
}
