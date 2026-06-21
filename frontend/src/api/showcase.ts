import {
  demoRangeEvidenceChain,
  generateReport,
  importDemoRangeBatch,
  listAlerts,
  listExternalEvents,
  listIncidents,
  listReports,
  listTickets,
  listVulnerabilities,
  topRiskAssets,
  type AlertItem,
  type ExternalEventItem,
  type IncidentClusterItem,
  type ReportItem,
  type TicketItem,
  type VulnerabilityItem,
  type DemoRangeBatchImportResult,
} from './soc'

export type ShowcaseDataSource = 'live' | 'offline'
export type ShowcaseStatus = 'severe' | 'attention' | 'safe'

export interface ShowcaseStep {
  key: string
  title: string
  businessSummary: string
  status: string
  countLabel: string
  count: number
  primaryAction: string
}

export interface ShowcaseEvidence {
  key: string
  title: string
  summary: string
  count: number
  sourceType: string
  eventType: string
  ruleId: string
  ruleName: string
  requestId: string
  demoCaseId: string
  normalizedEvent: Record<string, unknown>
  rawJson: Record<string, unknown>
}

export interface ShowcaseClosure {
  title: string
  assetIp: string
  targetUrl: string
  whyItMatters: string
  suggestion: string
  status: string
  alertId?: number
  ticketId?: number
}

export interface ShowcaseReportSummary {
  importedEvidence: number
  createdAlerts: number
  blockedCount: number
  vulnerabilityCount: number
  ticketStatus: string
  dryRunNotifications: number
}

export interface ShowcaseData {
  source: ShowcaseDataSource
  sourceLabel: string
  batchId: string
  status: ShowcaseStatus
  highestRisk: string
  pendingAlerts: number
  openTickets: number
  playbookSummary: string
  steps: ShowcaseStep[]
  evidence: ShowcaseEvidence[]
  closure: ShowcaseClosure
  report: ShowcaseReportSummary
  events: ExternalEventItem[]
  alerts: AlertItem[]
  vulnerabilities: VulnerabilityItem[]
  tickets: TicketItem[]
  reports: ReportItem[]
  incidentClusters: IncidentClusterItem[]
  diagnostics: string[]
}

const DEFAULT_BATCH_ID = 'DEMO-RANGE-OFFLINE-V1'

export const offlineShowcaseData: ShowcaseData = {
  source: 'offline',
  sourceLabel: '离线演示数据',
  batchId: DEFAULT_BATCH_ID,
  status: 'severe',
  highestRisk: '企业门户上传策略风险被 Web 网关阻断，仍需形成处置工单和验证报告。',
  pendingAlerts: 3,
  openTickets: 1,
  playbookSummary: '已匹配处置剧本，生成 5 个处置任务，其中 1 个需要员工配合。',
  steps: [
    { key: 'scenario', title: '选择场景', businessSummary: '确认本次客户演示关注企业门户风险，不展示攻击细节。', status: '已准备', countLabel: '演示场景', count: 5, primaryAction: '开始安全验证' },
    { key: 'import', title: '导入证据', businessSummary: '导入离线样例，展示证据如何进入统一安全运营链路。', status: '可导入', countLabel: '证据来源', count: 6, primaryAction: '导入离线证据' },
    { key: 'alert', title: '查看告警', businessSummary: '查看归一化证据如何触发告警和优先级判断。', status: '有告警', countLabel: '关联告警', count: 3, primaryAction: '查看最高优先级告警' },
    { key: 'ticket', title: '转为工单', businessSummary: '把需要跟进的告警转为处置任务，进入闭环。', status: '待处置', countLabel: '待关闭工单', count: 1, primaryAction: '进入工单处置' },
    { key: 'report', title: '生成报告', businessSummary: '输出本次安全验证报告，并保留通知 dry-run 记录。', status: '可生成', countLabel: '报告指标', count: 6, primaryAction: '生成安全验证报告' },
  ],
  evidence: [
    {
      key: 'waf',
      title: 'Web 网关阻断证据',
      summary: '网关识别异常访问并执行阻断，保留规则、请求编号和业务路径。',
      count: 4,
      sourceType: 'waf',
      eventType: 'waf_block',
      ruleId: 'WAF-DEMO-ACCESS',
      ruleName: '企业门户访问控制风险模拟',
      requestId: 'REQ-DEMO-1001',
      demoCaseId: 'DEMO-ACCESS-001',
      normalizedEvent: { action: 'block', severity: 'high', assetIp: '10.20.1.15', targetUrl: '/portal/orders' },
      rawJson: { demo: true, sourceType: 'waf', action: 'block', engine: 'offline-demo' },
    },
    {
      key: 'zap',
      title: 'Web 风险扫描结果',
      summary: 'Baseline 样例提示输入校验和响应头配置需要复核。',
      count: 3,
      sourceType: 'zap',
      eventType: 'passive_findings',
      ruleId: 'ZAP-DEMO-HEADER',
      ruleName: '安全响应头风险模拟',
      requestId: 'REQ-DEMO-1002',
      demoCaseId: 'DEMO-HEADER-001',
      normalizedEvent: { severity: 'medium', assetIp: '10.20.1.15', evidenceSummary: '缺少关键安全响应头' },
      rawJson: { demo: true, sourceType: 'zap', scanMode: 'baseline' },
    },
    {
      key: 'trivy',
      title: '组件漏洞发现',
      summary: '依赖组件样例中存在高风险版本，进入漏洞中心跟踪。',
      count: 2,
      sourceType: 'trivy',
      eventType: 'dependency_vulnerability',
      ruleId: 'TRIVY-DEMO-DEPENDENCY',
      ruleName: '依赖组件风险模拟',
      requestId: 'REQ-DEMO-1003',
      demoCaseId: 'DEMO-DEPENDENCY-001',
      normalizedEvent: { severity: 'high', softwareName: 'demo-library', fixSuggestion: '升级到安全版本' },
      rawJson: { demo: true, sourceType: 'trivy', artifact: 'demo-enterprise-portal' },
    },
    {
      key: 'wazuh',
      title: '主机文件变更',
      summary: '主机侧产生文件变更记录，用于展示端点证据进入 SOC。',
      count: 2,
      sourceType: 'wazuh',
      eventType: 'fim_change',
      ruleId: 'WAZUH-DEMO-FIM',
      ruleName: '关键配置变更模拟',
      requestId: 'REQ-DEMO-1004',
      demoCaseId: 'DEMO-FIM-001',
      normalizedEvent: { severity: 'medium', assetIp: '10.20.1.15', filePath: '/etc/demo/app.conf' },
      rawJson: { demo: true, sourceType: 'wazuh', category: 'fim' },
    },
    {
      key: 'ids',
      title: '网络检测事件',
      summary: '网络侧样例展示 Suricata/Zeek 证据如何归一化进入多源事件。',
      count: 3,
      sourceType: 'suricata',
      eventType: 'ids_detect',
      ruleId: 'IDS-DEMO-NETWORK',
      ruleName: '企业门户异常流量模拟',
      requestId: 'REQ-DEMO-1005',
      demoCaseId: 'DEMO-NETWORK-001',
      normalizedEvent: { severity: 'medium', srcIp: '10.20.1.40', destIp: '10.20.1.15' },
      rawJson: { demo: true, sourceType: 'suricata', zeek: 'offline-summary' },
    },
  ],
  closure: {
    title: '高优先级 Web 网关阻断告警',
    assetIp: '10.20.1.15',
    targetUrl: '/portal/orders',
    whyItMatters: '同一业务资产同时出现网关阻断、Web 风险提示和组件漏洞，需要形成处置记录。',
    suggestion: '确认业务入口策略、修复依赖组件，并在报告中记录验证结果。',
    status: '待转工单',
  },
  report: {
    importedEvidence: 14,
    createdAlerts: 3,
    blockedCount: 4,
    vulnerabilityCount: 2,
    ticketStatus: '1 个待关闭',
    dryRunNotifications: 2,
  },
  events: [],
  alerts: [],
  vulnerabilities: [],
  tickets: [],
  reports: [],
  incidentClusters: [
    {
      id: 0,
      clusterNo: 'CL-OFFLINE-DEMO',
      title: '企业门户同资产多源验证链路',
      summary: 'WAF 阻断、ZAP 风险提示、Trivy 漏洞和主机/网络证据被聚合为一条可处置事件链。',
      severity: 'high',
      status: 'open',
      score: 88,
      correlationKey: 'offline-demo-chain',
      primaryAssetIp: '10.20.1.15',
      primaryHostname: 'prod-app-01',
      batchId: DEFAULT_BATCH_ID,
      demoCaseId: 'DEMO-ACCESS-001',
      sourceTypes: 'waf,zap,trivy,wazuh,suricata,zeek',
      eventCount: 8,
      alertCount: 3,
      vulnerabilityCount: 2,
      ruleKey: 'demo_cross_source_chain',
    },
  ],
  diagnostics: ['当前使用离线演示数据，不代表生产环境。', '离线数据不会写入后端数据库。'],
}

export async function loadLiveShowcaseData(batchId = DEFAULT_BATCH_ID): Promise<ShowcaseData> {
  const [chainResponse, eventResponse, alertResponse, vulnerabilityResponse, ticketResponse, reportResponse, incidentResponse] = await Promise.all([
    demoRangeEvidenceChain(batchId),
    listExternalEvents({ pageNum: 1, pageSize: 8, keyword: batchId }),
    listAlerts({ pageNum: 1, pageSize: 8, keyword: batchId }),
    listVulnerabilities({ pageNum: 1, pageSize: 8 }),
    listTickets({ pageNum: 1, pageSize: 8 }),
    listReports({ pageNum: 1, pageSize: 8, reportType: 'security_validation' }),
    listIncidents({ pageNum: 1, pageSize: 5, keyword: batchId }),
  ])

  const chain = chainResponse.data.data
  const events = chain.events?.length ? chain.events : eventResponse.data.data.records
  const alerts = chain.alerts?.length ? chain.alerts : alertResponse.data.data.records
  const vulnerabilities = chain.vulnerabilities?.length ? chain.vulnerabilities : vulnerabilityResponse.data.data.records
  const tickets = chain.tickets?.length ? chain.tickets : ticketResponse.data.data.records
  const reports = chain.reports?.length ? chain.reports : reportResponse.data.data.records
  const incidentClusters = incidentResponse.data.data.records
  const topAlert = alerts[0]
  const topRiskProfile = await topRiskAssets(1)
    .then((response) => response.data.data[0])
    .catch(() => undefined)
  const topRiskText = topRiskProfile
    ? `${topRiskProfile.asset.hostname} 风险分 ${topRiskProfile.snapshot.score}：${topRiskProfile.statusReason || topRiskProfile.recommendationSummary}`
    : undefined

  return {
    source: 'live',
    sourceLabel: '接口实时数据',
    batchId: chain.summary.batchId || batchId,
    status: resolveStatus(alerts, tickets),
    highestRisk: topRiskText || topAlert?.evidenceSummary || topAlert?.ruleDescription || '当前批次暂无高优先级告警。',
    pendingAlerts: alerts.filter((item) => !['closed', 'ignored', 'false_positive'].includes(item.status)).length,
    openTickets: tickets.filter((item) => !['closed', 'resolved'].includes(item.status)).length,
    playbookSummary: tickets.length ? '已匹配处置剧本，处置任务会进入工单详情和员工待办。' : '导入批次后可在告警详情应用推荐处置剧本。',
    steps: buildLiveSteps(chain.summary.eventCount, chain.summary.alertCount, tickets.length, reports.length),
    evidence: buildEvidenceSummary(events, vulnerabilities),
    closure: {
      title: topAlert?.ruleName || topAlert?.ruleDescription || '暂无告警',
      assetIp: topRiskProfile?.asset.ip || topAlert?.assetIp || events[0]?.assetIp || '待确认',
      targetUrl: topAlert?.targetUrl || '待确认',
      whyItMatters: topAlert?.evidenceSummary || '本批次证据会在告警、工单和报告中持续关联。',
      suggestion: topAlert ? '进入告警处置完成确认，必要时转为处置工单。' : '请先导入演示批次或刷新证据链。',
      status: topAlert?.status || '待导入',
      alertId: topAlert?.id,
      ticketId: topAlert?.ticketId,
    },
    report: {
      importedEvidence: chain.summary.eventCount,
      createdAlerts: chain.summary.alertCount,
      blockedCount: chain.summary.blockedCount,
      vulnerabilityCount: chain.summary.vulnerabilityCount,
      ticketStatus: `${chain.summary.ticketCount} 个工单`,
      dryRunNotifications: chain.summary.notificationLogCount,
    },
    events,
    alerts,
    vulnerabilities,
    tickets,
    reports,
    incidentClusters,
    diagnostics: [`batchId=${chain.summary.batchId}`, `sourceCoverage=${chain.summary.sourceCoverage || '待确认'}`],
  }
}

export function createSecurityValidationReport(batchId: string) {
  return generateReport('security_validation', batchId)
}

export async function importShowcaseBatch(batchId?: string): Promise<DemoRangeBatchImportResult> {
  const response = await importDemoRangeBatch({ batchId, linkAlerts: true })
  return response.data.data
}

function buildLiveSteps(eventCount: number, alertCount: number, ticketCount: number, reportCount: number): ShowcaseStep[] {
  return [
    { key: 'scenario', title: '选择场景', businessSummary: '用企业门户风险作为客户演示主线。', status: '已准备', countLabel: '演示场景', count: 5, primaryAction: '开始安全验证' },
    { key: 'import', title: '导入证据', businessSummary: '把离线样例归一化为 SOC 证据。', status: eventCount ? '已导入' : '待导入', countLabel: '证据', count: eventCount, primaryAction: '进入安全验证' },
    { key: 'alert', title: '查看告警', businessSummary: '展示证据如何生成或关联告警。', status: alertCount ? '有告警' : '待生成', countLabel: '告警', count: alertCount, primaryAction: '查看告警处置' },
    { key: 'ticket', title: '转为工单', businessSummary: '把优先级最高的告警纳入处置队列。', status: ticketCount ? '有工单' : '待转工单', countLabel: '工单', count: ticketCount, primaryAction: '进入工单中心' },
    { key: 'report', title: '生成报告', businessSummary: '汇总本批次验证结果和 dry-run 通知留痕。', status: reportCount ? '已有报告' : '可生成', countLabel: '报告', count: reportCount, primaryAction: '生成安全验证报告' },
  ]
}

function buildEvidenceSummary(events: ExternalEventItem[], vulnerabilities: VulnerabilityItem[]): ShowcaseEvidence[] {
  const bySource = new Map<string, ExternalEventItem[]>()
  events.forEach((event) => {
    const key = event.sourceType || 'unknown'
    bySource.set(key, [...(bySource.get(key) || []), event])
  })
  const summary = offlineShowcaseData.evidence.map((template) => {
    const sourceEvents = bySource.get(template.sourceType) || []
    const first = sourceEvents[0]
    return {
      ...template,
      count: template.sourceType === 'trivy' ? vulnerabilities.length : sourceEvents.length,
      eventType: first?.eventType || template.eventType,
      ruleId: first?.ruleId || template.ruleId,
      ruleName: first?.ruleName || template.ruleName,
      demoCaseId: extractJsonField(first?.normalizedEvent, 'demoCaseId') || template.demoCaseId,
      normalizedEvent: parseJsonObject(first?.normalizedEvent, template.normalizedEvent),
      rawJson: parseJsonObject(first?.rawEvent, template.rawJson),
    }
  })
  return summary
}

function resolveStatus(alerts: AlertItem[], tickets: TicketItem[]): ShowcaseStatus {
  const severeAlert = alerts.some((alert) => ['critical', 'high'].includes(String(alert.severity || '').toLowerCase()))
  if (severeAlert) return 'severe'
  if (alerts.length || tickets.some((ticket) => !['closed', 'resolved'].includes(ticket.status))) return 'attention'
  return 'safe'
}

function parseJsonObject(value: string | undefined, fallback: Record<string, unknown>) {
  if (!value) return fallback
  try {
    const parsed = JSON.parse(value) as unknown
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : fallback
  } catch {
    return fallback
  }
}

function extractJsonField(value: string | undefined, field: string) {
  const object = parseJsonObject(value, {})
  const result = object[field]
  return typeof result === 'string' ? result : ''
}
