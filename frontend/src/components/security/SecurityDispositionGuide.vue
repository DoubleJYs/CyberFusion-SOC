<template>
  <section class="security-disposition-guide">
    <div class="guide-head">
      <div>
        <strong>研判与处置说明</strong>
        <span>把技术字段转换为可执行的安全运营判断。</span>
      </div>
      <el-tag :type="severityType" effect="plain">{{ categoryLabel }}</el-tag>
    </div>
    <div class="guide-grid">
      <article>
        <span>这是什么</span>
        <p>{{ whatText }}</p>
      </article>
      <article>
        <span>为什么关注</span>
        <p>{{ whyText }}</p>
      </article>
      <article>
        <span>影响范围</span>
        <p>{{ scopeText }}</p>
      </article>
    </div>
    <div class="guide-next">
      <strong>建议下一步</strong>
      <ol>
        <li v-for="step in steps" :key="step">{{ step }}</li>
      </ol>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type GuideCategory = 'alert' | 'incident' | 'vulnerability' | 'fim' | 'baseline' | 'ticket' | 'record' | 'external' | 'asset'

const props = withDefaults(defineProps<{
  category: GuideCategory
  subject: string
  source?: string
  severity?: string
  status?: string
  asset?: string
  reason?: string
  recommendation?: string
}>(), {
  source: '',
  severity: '',
  status: '',
  asset: '',
  reason: '',
  recommendation: '',
})

const labels: Record<GuideCategory, string> = {
  alert: '告警研判',
  incident: '事件簇研判',
  vulnerability: '漏洞研判',
  fim: '完整性研判',
  baseline: '基线研判',
  ticket: '工单处置',
  record: '证据研判',
  external: '外部风险研判',
  asset: '资产研判',
}

const categoryLabel = computed(() => labels[props.category])
const severityType = computed(() => {
  const value = props.severity.toLowerCase()
  if (value === 'critical' || value === 'high') return value === 'critical' ? 'danger' : 'warning'
  if (value === 'medium') return 'primary'
  return 'info'
})
const whatText = computed(() => {
  const subject = props.subject || '待补充的安全对象'
  const definitions: Record<GuideCategory, string> = {
    alert: `这是平台根据规则归一化生成的告警信号，当前命中内容为“${subject}”。它需要先由分析员确认是否代表真实风险。`,
    incident: `这是围绕“${subject}”聚合的多源证据链，用于判断多个告警、事件或漏洞是否属于同一处置问题。`,
    vulnerability: `这是资产软件组件暴露的漏洞风险“${subject}”。是否可利用、资产重要性和修复窗口决定其真实优先级。`,
    fim: `这是对“${subject}”执行完整性核查后生成的变更或快照证据，用于发现未经预期的文件与目录变化。`,
    baseline: `这是针对“${subject}”的主机或安全配置核查结果，用来确认当前环境是否满足既定安全基线。`,
    ticket: `这是用于推进“${subject}”处置闭环的工单记录，任务状态和证据决定是否可以完成或关闭。`,
    record: `这是来源系统归一化后的原始安全证据“${subject}”，可作为告警、事件簇和报告的可追溯依据。`,
    external: `这是外部访问、主机外联、扫描结果或 IOC 情报形成的风险事件“${subject}”，用于确认外部风险是否已触达受管资产。`,
    asset: `这是资产“${subject}”的风险视图，汇总了该主机相关的告警、漏洞、基线和处置状态。`,
  }
  return definitions[props.category]
})
const whyText = computed(() => {
  if (props.reason) return props.reason
  const details = [props.source ? `来源：${props.source}` : '', props.severity ? `等级：${props.severity}` : '', props.status ? `当前状态：${props.status}` : ''].filter(Boolean)
  return details.length
    ? `${details.join('；')}。需结合资产重要性、重复出现情况和关联证据确认是否升级处置。`
    : '当前尚未提供完整的触发上下文，需要先核对数据来源、时间、资产和关联证据。'
})
const scopeText = computed(() => props.asset
  ? `当前直接关联对象为 ${props.asset}。查看关联事件簇、工单和同类记录后，再判断是否需要扩展排查范围。`
  : '当前未识别到明确资产范围。应先补充资产、IP、时间窗口和来源系统信息，再决定是否横向排查。')
const steps = computed(() => {
  const first = props.recommendation || defaultFirstStep(props.category)
  return [
    first,
    '核对时间、资产、来源和关联证据，确认是否存在重复、白名单或已处置记录。',
    finalStep(props.category),
  ]
})

function defaultFirstStep(category: GuideCategory) {
  const values: Record<GuideCategory, string> = {
    alert: '先确认告警规则与证据摘要是否反映真实风险，避免直接关闭或升级。',
    incident: '先查看事件链时间线，验证聚合证据是否属于同一资产、时间窗口和攻击链。',
    vulnerability: '先确认受影响软件版本、资产暴露面和补丁可用性，再安排修复窗口。',
    fim: '先核对变更是否来自授权发布、运维操作或预期目录快照。',
    baseline: '先确认核查项、当前配置和例外审批是否一致。',
    ticket: '先按任务顺序补齐处置证据，并记录每次流转原因。',
    record: '先核对原始证据字段是否完整，并确认是否需要关联告警或事件簇。',
    external: '先确认源和目的方向、关联资产、时间窗口及工具结论，再决定是否需要告警、工单或扩大排查。',
    asset: '先检查风险画像中的高分因子和未关闭告警，确定首要处置对象。',
  }
  return values[category]
}

function finalStep(category: GuideCategory) {
  if (category === 'ticket') return '完成后填写复核结论；仅在任务、证据和责任人均明确时关闭工单。'
  if (category === 'asset') return '完成处置后重新计算风险分，确认高风险因子是否已下降。'
  return '依据核查结论选择确认、忽略、转工单或关闭，并在处置说明中记录决策依据。'
}
</script>

<style scoped>
.security-disposition-guide {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(77, 123, 164, 0.25);
  border-radius: 8px;
  background: rgba(247, 250, 253, 0.84);
}

.guide-head,
.guide-head > div {
  display: flex;
  gap: 10px;
}

.guide-head {
  align-items: flex-start;
  justify-content: space-between;
}

.guide-head > div {
  min-width: 0;
  flex-direction: column;
}

.guide-head strong,
.guide-next strong {
  color: var(--soc-text, #1f2937);
}

.guide-head span,
.guide-grid span,
.guide-next li {
  color: var(--soc-text-muted, #68758c);
  font-size: 13px;
  line-height: 1.5;
}

.guide-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.guide-grid article {
  display: grid;
  gap: 5px;
  padding: 10px;
  border: 1px solid rgba(129, 143, 166, 0.2);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.68);
}

.guide-grid p {
  margin: 0;
  color: var(--soc-text, #1f2937);
  font-size: 13px;
  line-height: 1.55;
}

.guide-next {
  display: grid;
  gap: 6px;
}

.guide-next ol {
  display: grid;
  gap: 5px;
  margin: 0;
  padding-left: 20px;
}

@media (max-width: 640px) {
  .guide-grid {
    grid-template-columns: 1fr;
  }
}
</style>
