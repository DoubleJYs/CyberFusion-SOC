import request from './request'
import type { ApiResult, PageResult } from '@/types/api'

export interface AlertItem {
  id: number
  alertUid: string
  sourceType: string
  level: number
  severity: string
  ruleId: string
  ruleDescription: string
  assetName: string
  assetIp: string
  sourceIp: string
  status: string
  tactic: string
  rawRef: string
  eventTime: string
  ticketId?: number
  whitelistHit?: boolean
  whitelistRuleName?: string
  noiseStatus?: string
  repeatCount?: number
  eventType?: string
  ruleName?: string
  targetUrl?: string
  action?: string
  evidenceSummary?: string
  demoCaseId?: string
  batchId?: string
  correlationKey?: string
  httpMethod?: string
  httpStatus?: string
  requestId?: string
  engine?: string
}

export interface AssetItem {
  id: number
  hostname: string
  ip: string
  osType: string
  sourceType: string
  riskLevel: string
  riskScore?: number
  deptName: string
  ownerName: string
  openAlertCount: number
  lastSeenAt: string
}

export interface AssetRiskSnapshot {
  id?: number
  assetId: number
  assetIp: string
  hostname: string
  score: number
  riskLevel: string
  policyId?: number
  factorSummaryJson?: string
  recommendationSummary?: string
  calculatedAt: string
}

export interface AssetRiskFactor {
  id?: number
  snapshotId?: number
  assetId?: number
  factorType: string
  factorName: string
  factorScore: number
  factorCount: number
  relatedBizType?: string
  relatedBizId?: number
  explanation: string
  recommendation: string
}

export interface AssetRiskCounts {
  criticalAlerts: number
  highAlerts: number
  mediumAlerts: number
  criticalVulnerabilities: number
  highVulnerabilities: number
  failedBaselines: number
  unreviewedFimEvents: number
  highExternalEvents: number
  overdueTickets: number
  openPlaybookTasks: number
  employeePendingTasks: number
  closedTickets: number
  completedPlaybookTasks: number
  internetExposed: number
}

export interface AssetRiskProfile {
  asset: AssetItem
  snapshot: AssetRiskSnapshot
  factors: AssetRiskFactor[]
  history: AssetRiskSnapshot[]
  recommendationSummary: string
  statusReason: string
  counts: AssetRiskCounts
}

export interface RiskScoringPolicyItem {
  id?: number
  policyCode: string
  policyName: string
  description?: string
  status: 'draft' | 'active' | 'disabled'
  enabled: number
  version: number
  criticalAssetWeight: number
  internetExposedWeight: number
  criticalAlertWeight: number
  highAlertWeight: number
  mediumAlertWeight: number
  criticalVulnerabilityWeight: number
  highVulnerabilityWeight: number
  baselineFailedWeight: number
  fimUnreviewedWeight: number
  externalEventWeight: number
  overdueTicketWeight: number
  openPlaybookTaskWeight: number
  employeePendingTaskWeight: number
  closedTicketReduceWeight: number
  completedPlaybookReduceWeight: number
  maxScore: number
  approvedAt?: string
  updatedAt?: string
}

export type RiskScoringPolicyPayload = Omit<RiskScoringPolicyItem, 'id' | 'version' | 'enabled' | 'approvedAt' | 'updatedAt'> & {
  enabled?: boolean
}

export interface RiskScoringValidationResult {
  passed: boolean
  message: string
}

export interface RiskScoringRecalculateResult {
  recalculatedAssets: number
  message: string
}

export interface TicketItem {
  id: number
  ticketNo: string
  alertId: number
  title: string
  severity: string
  status: string
  assigneeName: string
  reviewConclusion?: string
  resolution?: string
  dueAt?: string
}

export interface TimelineItem {
  id: number
  action: string
  fromStatus?: string
  toStatus?: string
  operatorName?: string
  remark?: string
  createdAt?: string
}

export interface ResponsePlaybookItem {
  id?: number
  playbookKey: string
  playbookName: string
  sourceType: string
  eventType?: string
  ruleIdPattern?: string
  minSeverity: string
  matchExpression?: string
  description?: string
  status: 'draft' | 'active' | 'disabled'
  enabled: number
  version: number
  sortOrder: number
  safetyNote?: string
  approvedAt?: string
  updatedAt?: string
}

export interface ResponsePlaybookStep {
  id?: number
  playbookId?: number
  stepKey: string
  stepName: string
  stepType: string
  ownerRole: string
  instruction: string
  expectedEvidence?: string
  requiresEmployee: number | boolean
  sortOrder: number
  enabled: number | boolean
}

export interface ResponsePlaybookPayload {
  playbookKey: string
  playbookName: string
  sourceType: string
  eventType?: string
  ruleIdPattern?: string
  minSeverity?: string
  matchExpression?: string
  description?: string
  status?: string
  enabled?: boolean
  sortOrder?: number
  safetyNote?: string
  steps: ResponsePlaybookStep[]
}

export interface ResponsePlaybookDetail {
  playbook: ResponsePlaybookItem
  steps: ResponsePlaybookStep[]
}

export interface PlaybookSuggestion {
  playbook: ResponsePlaybookItem
  steps: ResponsePlaybookStep[]
  matchReason: string
}

export interface TicketTaskItem {
  id: number
  ticketId: number
  alertId?: number
  playbookId?: number
  playbookStepId?: number
  taskKey: string
  taskName: string
  taskType: string
  assigneeType: string
  assigneeId?: number
  assigneeName?: string
  instruction: string
  expectedEvidence?: string
  evidenceText?: string
  status: string
  sortOrder: number
  startedAt?: string
  completedAt?: string
  skippedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ApplyPlaybookResult {
  playbook: ResponsePlaybookItem
  ticket: TicketItem
  tasks: TicketTaskItem[]
  createdTasks: number
  employeeTasks: number
  message: string
}

export interface ReportItem {
  id: number
  reportNo: string
  reportType: string
  periodStart: string
  periodEnd: string
  title: string
  status: string
  summary: string
  recommendation: string
  generatedAt: string
}

export interface VulnerabilityItem {
  id: number
  cveId: string
  severity: string
  assetName: string
  assetIp: string
  softwareName: string
  softwareVersion?: string
  fixSuggestion: string
  status: string
  sourceType: string
  detectedAt: string
  fixedAt?: string
}

export interface BaselineItem {
  id: number
  checkCode: string
  category: string
  checkItem: string
  assetName: string
  assetIp: string
  result: string
  severity: string
  passRate: number
  remediation: string
  status: string
  sourceType: string
  checkedAt: string
  reviewedAt?: string
}

export interface FileIntegrityItem {
  id: number
  eventUid: string
  action: string
  severity: string
  hostname: string
  assetIp: string
  filePath: string
  ruleName: string
  status: string
  sourceType: string
  eventTime: string
  reviewedAt?: string
}

export interface ExternalEventItem {
  id: number
  eventUid: string
  sourceType: string
  eventType: string
  severity: string
  ruleId?: string
  ruleName?: string
  srcIp?: string
  destIp?: string
  assetName?: string
  assetIp?: string
  batchId?: string
  demoCaseId?: string
  targetUrl?: string
  action?: string
  requestId?: string
  correlationKey?: string
  ioc?: string
  rawEvent?: string
  normalizedEvent?: string
  alertId?: number
  status: string
  eventTime: string
  reviewedAt?: string
}

export interface ExternalSourceSummary {
  sourceType: string
  total: number
  highRisk: number
  linkedAlerts: number
}

export interface ClientDeviceMetrics {
  riskScore: number
  alerts: number
  openVulnerabilities: number
  failedBaselines: number
  pendingFileIntegrity: number
  pendingExternalEvents: number
  summary: string
}

export interface ClientEvidenceItem {
  id: string
  type: string
  title: string
  severity?: string
  status?: string
  occurredAt?: string
  description?: string
}

export interface ClientDeviceProfile {
  asset: AssetItem
  metrics: ClientDeviceMetrics
  alerts: AlertItem[]
  vulnerabilities: VulnerabilityItem[]
  baselines: BaselineItem[]
  fileIntegrityEvents: FileIntegrityItem[]
  externalEvents: ExternalEventItem[]
  timeline: ClientEvidenceItem[]
}

export interface SecurityKeeperCheckup {
  id: number
  checkupNo: string
  assetId: number
  assetIp: string
  assetName: string
  osType?: string
  score: number
  status: 'safe' | 'attention' | 'serious'
  summary: string
  recommendationSummary?: string
  checkedAt: string
}

export interface SecurityKeeperRiskItem {
  itemType: string
  itemName: string
  severity: string
  count: number
  summary: string
  recommendation: string
}

export interface SecurityKeeperCheckupResult {
  checkup: SecurityKeeperCheckup
  riskItems: SecurityKeeperRiskItem[]
  recommendations: string[]
}

export interface SecurityKeeperCheckupSummary {
  id: number
  checkupNo: string
  assetIp: string
  assetName: string
  score: number
  status: 'safe' | 'attention' | 'serious'
  summary: string
  checkedAt: string
}

export interface SecurityKeeperRepairRecommendation {
  id: string
  riskTitle: string
  severity: string
  impact: string
  recommendedAction: string
  relatedType: 'task' | 'alert' | 'vulnerability'
  relatedAlertId?: number
  relatedVulnerabilityId?: number
  relatedTicketId?: number
  relatedTaskId?: number
  assetIp: string
  assetName?: string
  status: string
}

export interface SecurityKeeperLogItem {
  id: string
  type: 'checkup' | 'local_check' | 'local_check_record' | 'log_submission' | 'ticket_task' | 'risk_status_change' | 'employee_confirmation' | 'security_event'
  title: string
  description: string
  status?: string
  severity?: string
  assetIp?: string
  assetName?: string
  occurredAt?: string
}

export interface ClientSecurityPostureMetrics {
  totalAssets: number
  checkedAssets: number
  checkupCoverageRate: number
  seriousRiskAssets: number
  pendingEmployeeTasks: number
  localCheckSubmissions: number
  waitingReviewRecords: number
  latestCheckupAt?: string
}

export interface ClientSecurityHighRiskAsset {
  assetId: number
  hostname: string
  assetIp: string
  osType?: string
  ownerName?: string
  deptName?: string
  riskScore: number
  riskLevel: string
  checkupStatus?: string
  latestCheckupAt?: string
  openAlerts: number
  pendingTasks: number
}

export interface ClientSecurityRiskDownAsset {
  assetId: number
  hostname: string
  assetIp: string
  previousScore: number
  currentScore: number
  previousStatus?: string
  currentStatus?: string
  changedAt?: string
}

export interface ClientSecurityReviewRecord {
  eventId: number
  assetName?: string
  assetIp?: string
  eventType: string
  severity: string
  status: string
  summary: string
  occurredAt?: string
}

export interface ClientSecurityPosture {
  metrics: ClientSecurityPostureMetrics
  highRiskAssets: ClientSecurityHighRiskAsset[]
  riskDownAssets: ClientSecurityRiskDownAsset[]
  reviewRecords: ClientSecurityReviewRecord[]
}

export interface ClientLabEventPayload {
  assetIp: string
  actionType: string
  targetName?: string
  targetType?: string
  targetAddress?: string
  targetScope?: string
  sessionId?: string
  sessionName?: string
  sessionPhase?: string
  operatorNote?: string
  note?: string
  linkAlert?: boolean
}

export interface ClientLabEventResult {
  event: ExternalEventItem
  alertId?: number
  message: string
}

export interface ClientTerminalCommandPayload {
  assetIp: string
  commandKey: string
  osType?: string
  note?: string
  linkAlert?: boolean
}

export interface ClientTerminalCommandResult {
  commandKey: string
  command: string
  output: string[]
  exitCode: number
  timeout: boolean
  event: ExternalEventItem
  alertId?: number
}

export interface ClientSnapshotSection {
  key: string
  label: string
  command: string
  output: string[]
  exitCode: number
  timeout: boolean
  severity: string
}

export interface ClientSecuritySnapshotResult {
  snapshotId: string
  sections: ClientSnapshotSection[]
  event: ExternalEventItem
  alertId?: number
  message: string
}

export interface RuntimePlatform {
  osFamily: string
  osName: string
  arch: string
  javaVersion: string
  browserFamily: string
}

export interface RuntimeDataRoot {
  status: string
  environmentRoot: boolean
  outsideSourceRoot: boolean
  displayName: string
}

export interface RuntimeCapability {
  key: string
  label: string
  status: string
  message: string
}

export interface ClientRuntimeCompatibility {
  platform: RuntimePlatform
  dataRoot: RuntimeDataRoot
  adapter: string
  capabilities: RuntimeCapability[]
  checkedAt: string
}

export interface SuricataImportPayload {
  content: string
  linkAlerts?: boolean
}

export interface CyberFusionImportPayload {
  sourceType: string
  content: string
  linkAlerts?: boolean
}

export interface CyberFusionImportResult {
  sourceType: string
  importedEvents: number
  createdEvents: number
  updatedEvents: number
  linkedAlerts: number
  importedVulnerabilities?: number
  skippedLines: number
  errors: string[]
}

export interface DemoRangeBatchImportPayload {
  batchId?: string
  linkAlerts?: boolean
}

export interface DemoRangeSourceImportResult {
  sourceType: string
  importedEvents: number
  createdEvents: number
  updatedEvents: number
  linkedAlerts: number
  importedVulnerabilities: number
  skippedItems: number
  errors: string[]
}

export interface DemoRangeBatchImportResult {
  batchId: string
  importedEvents: number
  createdAlerts: number
  createdVulnerabilities: number
  skippedItems: number
  failedItems: number
  updatedEvents: number
  sources: DemoRangeSourceImportResult[]
  dedupRule: string
  errors: string[]
}

export interface DemoRangeChainSummary {
  batchId: string
  eventCount: number
  alertCount: number
  vulnerabilityCount: number
  blockedCount: number
  ticketCount: number
  reportCount: number
  notificationLogCount: number
  sourceCoverage: string
}

export interface DemoRangeEvidenceChain {
  summary: DemoRangeChainSummary
  events: ExternalEventItem[]
  alerts: AlertItem[]
  vulnerabilities: VulnerabilityItem[]
  tickets: TicketItem[]
  reports: ReportItem[]
  notificationLogs: NotificationLogItem[]
}

export interface RuleItem {
  ruleId: string
  ruleName: string
  sourceType: string
  severity: string
  enabled: boolean
  version: string
  lastHitAt?: string
  hitCount: number
  falsePositiveCount: number
}

export interface RuleHits {
  sourceType: string
  ruleId: string
  events: ExternalEventItem[]
  alerts: AlertItem[]
}

export interface AdapterFieldMapping {
  adapter: string
  sourceField: string
  normalizedField: string
  requirement: string
  severityMapping: string
  dedupKey: string
  alertLinkRule: string
  sampleFile: string
  failureCase: string
}

export interface LocalCheckCommandOption {
  key: string
  label: string
  osType: string
  category: string
  description?: string
  command: string
  phase: string
  severity: string
  builtInFallback: boolean
  sortOrder: number
}

export interface LocalCheckPolicyItem {
  id?: number
  commandKey: string
  displayName: string
  osType: string
  category: string
  description?: string
  commandArgvJson: string
  timeoutSeconds: number
  outputLimitKb: number
  enabled: number
  status: 'draft' | 'active' | 'disabled'
  version: number
  sortOrder: number
  safetyNote?: string
  createdBy?: number
  updatedBy?: number
  approvedBy?: number
  approvedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface LocalCheckPolicyPayload {
  commandKey: string
  displayName: string
  osType: string
  category: string
  description?: string
  commandArgvJson: string
  timeoutSeconds?: number
  outputLimitKb?: number
  enabled?: boolean
  status?: string
  sortOrder?: number
  safetyNote?: string
}

export interface LocalCheckPrecheckResult {
  passed: boolean
  message: string
  argv: string[]
}

export interface EventAdapterProfileItem {
  id?: number
  sourceType: 'waf' | 'zap' | 'trivy' | 'wazuh' | 'suricata' | 'zeek'
  displayName: string
  description?: string
  status: 'draft' | 'active' | 'disabled'
  enabled: number
  version: number
  sortOrder: number
  sampleFile?: string
  createdBy?: number
  updatedBy?: number
  approvedBy?: number
  approvedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface EventAdapterProfilePayload {
  sourceType: string
  displayName: string
  description?: string
  status?: string
  enabled?: boolean
  sortOrder?: number
  sampleFile?: string
}

export interface EventFieldMappingItem {
  id?: number
  adapterId?: number
  sourceFieldPath: string
  normalizedField: string
  required: number | boolean
  transformType: string
  defaultValue?: string
  exampleValue?: string
  sortOrder: number
  enabled: number | boolean
}

export interface EventSeverityMappingItem {
  id?: number
  adapterId?: number
  sourceValue: string
  normalizedSeverity: string
  riskScore: number
  enabled: number | boolean
}

export interface EventAlertLinkRuleItem {
  id?: number
  adapterId?: number
  eventType: string
  minSeverity: string
  linkAlertsDefault: number | boolean
  alertRuleIdField?: string
  alertNameTemplate?: string
  dedupKeyFieldsJson: string
  enabled: number | boolean
}

export interface EventAdapterMappingsPayload {
  fieldMappings: EventFieldMappingItem[]
  severityMappings: EventSeverityMappingItem[]
  alertLinkRules: EventAlertLinkRuleItem[]
}

export interface EventAdapterValidationResult {
  passed: boolean
  message: string
  errors: string[]
}

export interface EventAdapterPreviewResult {
  normalizedEvent: Record<string, unknown>
  severity: string
  dedupKey: string
  willCreateAlert: boolean
  validationErrors: string[]
}

export interface IncidentEvidenceItem {
  id: number
  clusterId: number
  evidenceType: 'external_event' | 'alert' | 'vulnerability' | 'ticket' | 'fim' | 'baseline'
  evidenceId: number
  evidenceUid?: string
  sourceType?: string
  eventType?: string
  severity?: string
  ruleId?: string
  assetIp?: string
  hostname?: string
  targetUrl?: string
  batchId?: string
  demoCaseId?: string
  eventTime?: string
  relationScore: number
  relationReason: string
}

export interface IncidentClusterItem {
  id: number
  clusterNo: string
  title: string
  summary?: string
  recommendation?: string
  severity: string
  status: string
  score: number
  correlationKey: string
  assetId?: number
  assetIp?: string
  hostname?: string
  primaryAssetIp?: string
  primaryHostname?: string
  batchId?: string
  demoCaseId?: string
  sourceSummary?: string
  sourceTypes?: string
  evidenceCount?: number
  eventCount: number
  alertCount: number
  vulnerabilityCount: number
  firstSeenAt?: string
  lastSeenAt?: string
  ruleId?: number
  ruleKey?: string
  ticketId?: number
  evidence?: IncidentEvidenceItem[]
}

export interface IncidentCorrelateResult {
  upsertedClusters: number
  createdClusters: number
  evidenceWritten: number
  activeRules: number
}

export interface CorrelationRuleItem {
  id?: number
  ruleKey: string
  ruleName: string
  ruleType: 'event_count' | 'value_count' | 'frequency' | 'temporal' | 'temporal_ordered' | 'cross_source_chain'
  sourceTypesJson?: string
  eventTypesJson?: string
  groupByJson?: string
  threshold: number
  timeframeSeconds: number
  sequenceJson?: string
  severityFloor?: string
  enabled: boolean | number
  status: 'draft' | 'active' | 'disabled'
  version: number
  description?: string
  safetyNote?: string
  approvedAt?: string
  updatedAt?: string
}

export interface CorrelationRulePayload {
  ruleKey: string
  ruleName: string
  ruleType: string
  sourceTypesJson?: string
  eventTypesJson?: string
  groupByJson?: string
  threshold: number
  timeframeSeconds: number
  sequenceJson?: string
  severityFloor?: string
  enabled?: boolean
  status?: string
  version?: number
  description?: string
  safetyNote?: string
}

export type SuricataImportResult = CyberFusionImportResult

export interface CyberChefAnalysis {
  fieldName: string
  suggestedOperations: string[]
  findings: Record<string, string>
  note: string
}

export interface AutomationDemoResult {
  adapter: string
  status: string
  message: string
}

export interface NotificationChannelItem {
  id: number
  channelName: string
  channelType: string
  target: string
  enabled: number
  minSeverity: string
  triggerEvent: string
  sendMode: string
  lastStatus?: string
  lastSentAt?: string
  remark?: string
}

export interface NotificationLogItem {
  id: number
  channelId?: number
  channelType: string
  eventType: string
  severity?: string
  bizType: string
  bizId?: number
  title: string
  content: string
  target: string
  status: string
  errorMessage?: string
  sentAt?: string
  createdAt?: string
}

export interface AlertWhitelistItem {
  id: number
  ruleName: string
  ruleId?: string
  assetIp?: string
  sourceIp?: string
  severity?: string
  reason: string
  enabled: number
  matchCount: number
  lastMatchedAt?: string
  expiresAt?: string
}

export interface AlertWhitelistPayload {
  ruleName: string
  ruleId?: string
  assetIp?: string
  sourceIp?: string
  severity?: string
  reason: string
  enabled?: number
  expiresAt?: string
}

export interface AlertAggregationItem {
  ruleId: string
  ruleDescription: string
  severity: string
  assetName: string
  assetIp: string
  sourceIp?: string
  repeatCount: number
  latestEventTime?: string
  whitelistRuleName?: string
}

export interface AlertNoiseSummary {
  activeWhitelists: number
  whitelistHits: number
  falsePositiveAlerts: number
  duplicateGroups: number
}

export interface AssetRiskScore {
  hostname: string
  ip: string
  deptName?: string
  riskLevel: string
  score: number
  explanation: string
  alertWeight: number
  vulnerabilityWeight: number
  baselineWeight: number
  exposureWeight: number
  handlingWeight: number
}

export interface AlertPriorityScore {
  alertUid: string
  severity: string
  ruleDescription: string
  assetName: string
  assetIp: string
  score: number
  reason: string
  repeatCount: number
  iocHit: boolean
  highRiskAsset: boolean
  status: string
  eventTime: string
}

export interface DepartmentRisk {
  deptName: string
  assets: number
  score: number
  highAlerts: number
  openVulnerabilities: number
  failedBaselines: number
  pendingTickets: number
}

export interface OperationMetrics {
  pendingTickets: number
  overdueTickets: number
  closedTickets: number
  slaMetTickets: number
  falsePositiveAlerts: number
  duplicateGroups: number
  slaRate: number
  falsePositiveRate: number
  averageCloseHours: number
}

export interface SecurityTimelineItem {
  occurredAt: string
  type: string
  title: string
  severity: string
  status: string
  assetName?: string
  operatorName?: string
}

export interface RiskAnalytics {
  assetRisks: AssetRiskScore[]
  alertPriorities: AlertPriorityScore[]
  departmentRisks: DepartmentRisk[]
  operationMetrics: OperationMetrics
  eventTimeline: SecurityTimelineItem[]
}

export interface PageQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  severity?: string
  status?: string
  riskLevel?: string
  reportType?: string
  category?: string
  result?: string
  action?: string
  sourceType?: string
  eventType?: string
}

export function dashboardOverview() {
  return request.get<ApiResult<{ todayAlerts: number; highAlerts: number; pendingTickets: number; assets: number; unhandledAlerts: number }>>('/soc/dashboard/overview')
}

export function alertTrend() {
  return request.get<ApiResult<Array<{ date: string; count: number }>>>('/soc/dashboard/alert-trend')
}

export function severityDistribution() {
  return request.get<ApiResult<Array<{ name: string; value: number }>>>('/soc/dashboard/severity-distribution')
}

export function affectedAssets() {
  return request.get<ApiResult<Array<{ name: string; value: number }>>>('/soc/dashboard/affected-assets')
}

export function riskAnalytics() {
  return request.get<ApiResult<RiskAnalytics>>('/soc/dashboard/risk-analytics')
}

export function listAlerts(params: PageQuery) {
  return request.get<ApiResult<PageResult<AlertItem>>>('/soc/alerts', { params })
}

export function alertDetail(id: number) {
  return request.get<ApiResult<AlertItem>>(`/soc/alerts/${id}`)
}

export function alertRelatedIncidents(id: number) {
  return request.get<ApiResult<IncidentClusterItem[]>>(`/soc/alerts/${id}/related-incidents`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function alertAction(id: number, action: 'acknowledge' | 'false-positive' | 'ignore' | 'close' | 'ticket', remark: string) {
  return request.post<ApiResult<AlertItem | TicketItem>>(`/soc/alerts/${id}/${action}`, { remark })
}

export function listAssets(params: PageQuery) {
  return request.get<ApiResult<PageResult<AssetItem>>>('/soc/assets', { params })
}

export function assetRiskProfile(id: number) {
  return request.get<ApiResult<AssetRiskProfile>>(`/soc/assets/${id}/risk-profile`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function assetRiskHistory(id: number) {
  return request.get<ApiResult<AssetRiskSnapshot[]>>(`/soc/assets/${id}/risk-history`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function assetRelatedIncidents(id: number) {
  return request.get<ApiResult<IncidentClusterItem[]>>(`/soc/assets/${id}/incidents`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function listIncidents(params: PageQuery) {
  return request.get<ApiResult<PageResult<IncidentClusterItem>>>('/soc/incidents', {
    params,
    headers: { 'X-Silent-Error': '1' },
  })
}

export function incidentDetail(id: number) {
  return request.get<ApiResult<IncidentClusterItem>>(`/soc/incidents/${id}`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function correlateIncidents() {
  return request.post<ApiResult<IncidentCorrelateResult>>('/soc/incidents/correlate')
}

export function ticketIncident(id: number, remark?: string) {
  return request.post<ApiResult<TicketItem>>(`/soc/incidents/${id}/ticket`, { remark })
}

export function closeIncident(id: number, remark?: string) {
  return request.post<ApiResult<IncidentClusterItem>>(`/soc/incidents/${id}/close`, { remark })
}

export function topRiskAssets(limit = 5) {
  return request.get<ApiResult<AssetRiskProfile[]>>('/soc/risk-scoring/top-assets', {
    params: { limit },
    headers: { 'X-Silent-Error': '1' },
  })
}

export function recalculateAssetRisk(id: number) {
  return request.post<ApiResult<AssetRiskProfile>>(`/soc/risk-scoring/recalculate/${id}`)
}

export function recalculateAllAssetRisks() {
  return request.post<ApiResult<RiskScoringRecalculateResult>>('/soc/risk-scoring/recalculate')
}

export function listRiskScoringPolicies(params: PageQuery) {
  return request.get<ApiResult<PageResult<RiskScoringPolicyItem>>>('/soc/risk-scoring/policies', {
    params,
    headers: { 'X-Silent-Error': '1' },
  })
}

export function createRiskScoringPolicy(data: RiskScoringPolicyPayload) {
  return request.post<ApiResult<RiskScoringPolicyItem>>('/soc/risk-scoring/policies', data)
}

export function updateRiskScoringPolicy(id: number, data: RiskScoringPolicyPayload) {
  return request.put<ApiResult<RiskScoringPolicyItem>>(`/soc/risk-scoring/policies/${id}`, data)
}

export function validateRiskScoringPolicy(id: number) {
  return request.post<ApiResult<RiskScoringValidationResult>>(`/soc/risk-scoring/policies/${id}/validate`)
}

export function publishRiskScoringPolicy(id: number) {
  return request.post<ApiResult<RiskScoringPolicyItem>>(`/soc/risk-scoring/policies/${id}/publish`)
}

export function disableRiskScoringPolicy(id: number) {
  return request.post<ApiResult<RiskScoringPolicyItem>>(`/soc/risk-scoring/policies/${id}/disable`)
}

export function listTickets(params: PageQuery) {
  return request.get<ApiResult<PageResult<TicketItem>>>('/soc/tickets', { params })
}

export function ticketDetail(id: number) {
  return request.get<ApiResult<{ ticket: TicketItem; timeline: TimelineItem[]; tasks?: TicketTaskItem[] }>>(`/soc/tickets/${id}`)
}

export function transitionTicket(id: number, targetStatus: string, remark: string) {
  return request.post<ApiResult<TicketItem>>(`/soc/tickets/${id}/transition`, { targetStatus, remark })
}

export function listReports(params: PageQuery) {
  return request.get<ApiResult<PageResult<ReportItem>>>('/soc/reports', { params })
}

export function generateReport(reportType: string, batchId?: string) {
  return request.post<ApiResult<ReportItem>>('/soc/reports/generate', { reportType, batchId })
}

export function reportExportUrl(id: number, format: 'xlsx' | 'pdf') {
  return `/api/soc/reports/${id}/export?format=${format}`
}

export function wazuhConfigs() {
  return request.get<ApiResult<unknown[]>>('/soc/settings/wazuh-configs')
}

export function syncTasks() {
  return request.get<ApiResult<unknown[]>>('/soc/settings/sync-tasks')
}

export function wazuhHealth() {
  return request.get<ApiResult<Record<string, unknown>>>('/soc/settings/wazuh-health')
}

export function notificationChannels() {
  return request.get<ApiResult<NotificationChannelItem[]>>('/soc/settings/notification-channels')
}

export function notificationLogs(params: PageQuery) {
  return request.get<ApiResult<PageResult<NotificationLogItem>>>('/soc/settings/notification-logs', { params })
}

export function testNotificationChannel(id: number) {
  return request.post<ApiResult<NotificationLogItem>>(`/soc/settings/notification-channels/${id}/test`)
}

export function alertNoiseSummary() {
  return request.get<ApiResult<AlertNoiseSummary>>('/soc/alert-noise/summary')
}

export function listAlertWhitelists(params: PageQuery) {
  return request.get<ApiResult<PageResult<AlertWhitelistItem>>>('/soc/alert-noise/whitelists', { params })
}

export function createAlertWhitelist(data: AlertWhitelistPayload) {
  return request.post<ApiResult<AlertWhitelistItem>>('/soc/alert-noise/whitelists', data)
}

export function updateAlertWhitelist(id: number, data: AlertWhitelistPayload) {
  return request.put<ApiResult<AlertWhitelistItem>>(`/soc/alert-noise/whitelists/${id}`, data)
}

export function updateAlertWhitelistStatus(id: number, targetStatus: 'enabled' | 'disabled', remark: string) {
  return request.post<ApiResult<AlertWhitelistItem>>(`/soc/alert-noise/whitelists/${id}/status`, { targetStatus, remark })
}

export function listAlertAggregations(params: PageQuery) {
  return request.get<ApiResult<AlertAggregationItem[]>>('/soc/alert-noise/aggregations', { params })
}

export function listVulnerabilities(params: PageQuery) {
  return request.get<ApiResult<PageResult<VulnerabilityItem>>>('/soc/vulnerabilities', { params })
}

export function vulnerabilityDetail(id: number) {
  return request.get<ApiResult<VulnerabilityItem>>(`/soc/vulnerabilities/${id}`)
}

export function updateVulnerabilityStatus(id: number, targetStatus: string, remark: string) {
  return request.post<ApiResult<VulnerabilityItem>>(`/soc/vulnerabilities/${id}/status`, { targetStatus, remark })
}

export function vulnerabilitySummary() {
  return request.get<ApiResult<Array<{ name: string; value: number }>>>('/soc/vulnerabilities/summary')
}

export function listBaselines(params: PageQuery) {
  return request.get<ApiResult<PageResult<BaselineItem>>>('/soc/baselines', { params })
}

export function baselineDetail(id: number) {
  return request.get<ApiResult<BaselineItem>>(`/soc/baselines/${id}`)
}

export function updateBaselineStatus(id: number, targetStatus: string, remark: string) {
  return request.post<ApiResult<BaselineItem>>(`/soc/baselines/${id}/status`, { targetStatus, remark })
}

export function baselineSummary() {
  return request.get<ApiResult<Array<{ name: string; value: number }>>>('/soc/baselines/summary')
}

export function listFileIntegrityEvents(params: PageQuery) {
  return request.get<ApiResult<PageResult<FileIntegrityItem>>>('/soc/fim', { params })
}

export function fileIntegrityDetail(id: number) {
  return request.get<ApiResult<FileIntegrityItem>>(`/soc/fim/${id}`)
}

export function updateFileIntegrityStatus(id: number, targetStatus: string, remark: string) {
  return request.post<ApiResult<FileIntegrityItem>>(`/soc/fim/${id}/status`, { targetStatus, remark })
}

export function fileIntegritySummary() {
  return request.get<ApiResult<Array<{ name: string; value: number }>>>('/soc/fim/summary')
}

export function listExternalEvents(params: PageQuery) {
  return request.get<ApiResult<PageResult<ExternalEventItem>>>('/soc/external-events', { params })
}

export function externalEventDetail(id: number) {
  return request.get<ApiResult<ExternalEventItem>>(`/soc/external-events/${id}`)
}

export function updateExternalEventStatus(id: number, targetStatus: string, remark: string) {
  return request.post<ApiResult<ExternalEventItem>>(`/soc/external-events/${id}/status`, { targetStatus, remark })
}

export function importSuricataEvents(data: SuricataImportPayload) {
  return request.post<ApiResult<SuricataImportResult>>('/soc/external-events/suricata/import', data)
}

export function importCyberFusionEvents(data: CyberFusionImportPayload) {
  return request.post<ApiResult<CyberFusionImportResult>>('/soc/external-events/cyberfusion/import', data)
}

export function analyzeCyberChefField(value: string, fieldName?: string) {
  return request.post<ApiResult<CyberChefAnalysis>>('/soc/external-events/cyberchef/analyze', { value, fieldName })
}

export function sendShuffleDemoNotification() {
  return request.post<ApiResult<AutomationDemoResult>>('/soc/external-events/shuffle/demo-notification')
}

export function externalEventSummary() {
  return request.get<ApiResult<ExternalSourceSummary[]>>('/soc/external-events/summary')
}

export function listRules(params: PageQuery) {
  return request.get<ApiResult<PageResult<RuleItem>>>('/soc/rules', { params })
}

export function ruleHits(sourceType: string, ruleId: string) {
  return request.get<ApiResult<RuleHits>>('/soc/rules/hits', { params: { sourceType, ruleId } })
}

export function adapterFieldMappings() {
  return request.get<ApiResult<AdapterFieldMapping[]>>('/soc/rules/adapter-mappings')
}

export function listClientLocalCommands(os: string) {
  return request.get<ApiResult<LocalCheckCommandOption[]>>('/client/local-terminal/commands', {
    params: { os },
    headers: { 'X-Silent-Error': '1' },
  })
}

export function listLocalCheckPolicies(params: PageQuery & { osType?: string }) {
  return request.get<ApiResult<PageResult<LocalCheckPolicyItem>>>('/soc/policies/local-check-commands', {
    params,
    headers: { 'X-Silent-Error': '1' },
  })
}

export function createLocalCheckPolicy(data: LocalCheckPolicyPayload) {
  return request.post<ApiResult<LocalCheckPolicyItem>>('/soc/policies/local-check-commands', data)
}

export function updateLocalCheckPolicy(id: number, data: LocalCheckPolicyPayload) {
  return request.put<ApiResult<LocalCheckPolicyItem>>(`/soc/policies/local-check-commands/${id}`, data)
}

export function publishLocalCheckPolicy(id: number) {
  return request.post<ApiResult<LocalCheckPolicyItem>>(`/soc/policies/local-check-commands/${id}/publish`)
}

export function disableLocalCheckPolicy(id: number) {
  return request.post<ApiResult<LocalCheckPolicyItem>>(`/soc/policies/local-check-commands/${id}/disable`)
}

export function changeLocalCheckPolicyEnabled(id: number, enabled: boolean) {
  return request.post<ApiResult<LocalCheckPolicyItem>>(`/soc/policies/local-check-commands/${id}/enabled`, { enabled })
}

export function precheckLocalCheckPolicy(data: LocalCheckPolicyPayload) {
  return request.post<ApiResult<LocalCheckPrecheckResult>>('/soc/policies/local-check-commands/precheck', data)
}

export function validateLocalCheckPolicy(id: number) {
  return request.post<ApiResult<LocalCheckPrecheckResult>>(`/soc/policies/local-check-commands/${id}/validate`)
}

export function localCheckPolicyAudits() {
  return request.get<ApiResult<LocalCheckPolicyItem[]>>('/soc/policies/local-check-commands/audits', {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function listEventAdapters(params: PageQuery & { sourceType?: string }) {
  return request.get<ApiResult<PageResult<EventAdapterProfileItem>>>('/soc/policies/event-adapters', { params })
}

export function createEventAdapter(data: EventAdapterProfilePayload) {
  return request.post<ApiResult<EventAdapterProfileItem>>('/soc/policies/event-adapters', data)
}

export function updateEventAdapter(id: number, data: EventAdapterProfilePayload) {
  return request.put<ApiResult<EventAdapterProfileItem>>(`/soc/policies/event-adapters/${id}`, data)
}

export function validateEventAdapter(id: number) {
  return request.post<ApiResult<EventAdapterValidationResult>>(`/soc/policies/event-adapters/${id}/validate`)
}

export function publishEventAdapter(id: number) {
  return request.post<ApiResult<EventAdapterProfileItem>>(`/soc/policies/event-adapters/${id}/publish`)
}

export function disableEventAdapter(id: number) {
  return request.post<ApiResult<EventAdapterProfileItem>>(`/soc/policies/event-adapters/${id}/disable`)
}

export function listCorrelationRules(params: PageQuery & { type?: string }) {
  return request.get<ApiResult<PageResult<CorrelationRuleItem>>>('/soc/correlation-rules', {
    params,
    headers: { 'X-Silent-Error': '1' },
  })
}

export function createCorrelationRule(data: CorrelationRulePayload) {
  return request.post<ApiResult<CorrelationRuleItem>>('/soc/correlation-rules', data)
}

export function updateCorrelationRule(id: number, data: CorrelationRulePayload) {
  return request.put<ApiResult<CorrelationRuleItem>>(`/soc/correlation-rules/${id}`, data)
}

export function validateCorrelationRule(id: number) {
  return request.post<ApiResult<{ passed: boolean; message: string }>>(`/soc/correlation-rules/${id}/validate`)
}

export function publishCorrelationRule(id: number) {
  return request.post<ApiResult<CorrelationRuleItem>>(`/soc/correlation-rules/${id}/publish`)
}

export function disableCorrelationRule(id: number) {
  return request.post<ApiResult<CorrelationRuleItem>>(`/soc/correlation-rules/${id}/disable`)
}

export function eventAdapterMappings(id: number) {
  return request.get<ApiResult<EventAdapterMappingsPayload>>(`/soc/policies/event-adapters/${id}/mappings`)
}

export function updateEventAdapterMappings(id: number, data: EventAdapterMappingsPayload) {
  return request.put<ApiResult<EventAdapterMappingsPayload>>(`/soc/policies/event-adapters/${id}/mappings`, data)
}

export function previewEventAdapter(id: number, payload: string) {
  return request.post<ApiResult<EventAdapterPreviewResult>>(`/soc/policies/event-adapters/${id}/preview`, { payload })
}

export function listResponsePlaybooks(params: PageQuery & { sourceType?: string }) {
  return request.get<ApiResult<PageResult<ResponsePlaybookItem>>>('/soc/policies/playbooks', { params })
}

export function responsePlaybookDetail(id: number) {
  return request.get<ApiResult<ResponsePlaybookDetail>>(`/soc/policies/playbooks/${id}`)
}

export function createResponsePlaybook(data: ResponsePlaybookPayload) {
  return request.post<ApiResult<ResponsePlaybookDetail>>('/soc/policies/playbooks', data)
}

export function updateResponsePlaybook(id: number, data: ResponsePlaybookPayload) {
  return request.put<ApiResult<ResponsePlaybookDetail>>(`/soc/policies/playbooks/${id}`, data)
}

export function validateResponsePlaybook(id: number) {
  return request.post<ApiResult<{ passed: boolean; message: string }>>(`/soc/policies/playbooks/${id}/validate`)
}

export function publishResponsePlaybook(id: number) {
  return request.post<ApiResult<ResponsePlaybookDetail>>(`/soc/policies/playbooks/${id}/publish`)
}

export function disableResponsePlaybook(id: number) {
  return request.post<ApiResult<ResponsePlaybookDetail>>(`/soc/policies/playbooks/${id}/disable`)
}

export function alertPlaybookSuggestions(id: number) {
  return request.get<ApiResult<PlaybookSuggestion[]>>(`/soc/alerts/${id}/playbook-suggestions`, {
    headers: { 'X-Silent-Error': 'true' },
  })
}

export function applyAlertPlaybook(id: number, playbookId: number, remark: string) {
  return request.post<ApiResult<ApplyPlaybookResult>>(`/soc/alerts/${id}/apply-playbook`, { playbookId, remark })
}

export function ticketTasks(id: number) {
  return request.get<ApiResult<TicketTaskItem[]>>(`/soc/tickets/${id}/tasks`)
}

export function updateTicketTask(ticketId: number, taskId: number, action: 'start' | 'complete' | 'skip', data?: { remark?: string; evidenceText?: string }) {
  return request.post<ApiResult<TicketTaskItem>>(`/soc/tickets/${ticketId}/tasks/${taskId}/${action}`, data || {})
}

export function importDemoRangeBatch(data?: DemoRangeBatchImportPayload) {
  return request.post<ApiResult<DemoRangeBatchImportResult>>('/soc/demo-range/batches/import', data || {})
}

export function demoRangeEvidenceChain(batchId: string) {
  return request.get<ApiResult<DemoRangeEvidenceChain>>(`/soc/demo-range/batches/${encodeURIComponent(batchId)}/evidence-chain`)
}

export function clientDevices() {
  return request.get<ApiResult<PageResult<AssetItem> | AssetItem[]>>('/client/devices', {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function clientDeviceProfile(ip: string) {
  return request.get<ApiResult<ClientDeviceProfile>>(`/client/devices/${encodeURIComponent(ip)}/profile`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function clientDeviceRiskProfile(ip: string) {
  return request.get<ApiResult<AssetRiskProfile>>(`/client/devices/${encodeURIComponent(ip)}/risk-profile`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function runSecurityKeeperCheckup(assetIp: string) {
  return request.post<ApiResult<SecurityKeeperCheckupResult>>('/client/security-keeper/checkup', { assetIp }, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function listSecurityKeeperCheckups(assetIp?: string) {
  return request.get<ApiResult<SecurityKeeperCheckupSummary[]>>('/client/security-keeper/checkups', {
    params: { assetIp },
    headers: { 'X-Silent-Error': '1' },
  })
}

export function getSecurityKeeperCheckup(id: number) {
  return request.get<ApiResult<SecurityKeeperCheckupResult>>(`/client/security-keeper/checkups/${id}`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function listSecurityKeeperLogs(assetIp: string) {
  return request.get<ApiResult<SecurityKeeperLogItem[]>>('/client/security-keeper/logs', {
    params: { assetIp },
    headers: { 'X-Silent-Error': '1' },
  })
}

export function clientSecurityPosture() {
  return request.get<ApiResult<ClientSecurityPosture>>('/soc/client-security/overview', {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function listSecurityKeeperRecommendations(assetIp: string) {
  return request.get<ApiResult<SecurityKeeperRepairRecommendation[]>>('/client/security-keeper/recommendations', {
    params: { assetIp },
    headers: { 'X-Silent-Error': '1' },
  })
}

export function confirmSecurityKeeperRecommendation(id: string, data?: { note?: string }) {
  return request.post<ApiResult<SecurityKeeperRepairRecommendation>>(`/client/security-keeper/recommendations/${encodeURIComponent(id)}/confirm`, data || {})
}

export function submitSecurityKeeperRecommendationNote(id: string, data?: { note?: string }) {
  return request.post<ApiResult<SecurityKeeperRepairRecommendation>>(`/client/security-keeper/recommendations/${encodeURIComponent(id)}/submit-note`, data || {})
}

export function submitClientLabEvent(data: ClientLabEventPayload) {
  return request.post<ApiResult<ClientLabEventResult>>('/client/lab/events', data)
}

export function submitLocalDemoLabEvent(data: ClientLabEventPayload) {
  return request.post<ApiResult<ClientLabEventResult>>('/client/lab/local-events', data)
}

export function runClientTerminalCommand(data: ClientTerminalCommandPayload) {
  return request.post<ApiResult<ClientTerminalCommandResult>>('/client/local-terminal/run', data)
}

export function runLocalDemoTerminalCommand(data: ClientTerminalCommandPayload) {
  return request.post<ApiResult<ClientTerminalCommandResult>>('/client/local-terminal/local-run', data)
}

export function runClientSecuritySnapshot(data: { assetIp: string; osType?: string; note?: string; linkAlert?: boolean }) {
  return request.post<ApiResult<ClientSecuritySnapshotResult>>('/client/local-snapshot/run', data)
}

export function runLocalDemoSecuritySnapshot(data: { assetIp: string; osType?: string; note?: string; linkAlert?: boolean }) {
  return request.post<ApiResult<ClientSecuritySnapshotResult>>('/client/local-snapshot/local-run', data)
}

export function clientRuntimeCompatibility() {
  return request.get<ApiResult<ClientRuntimeCompatibility>>('/client/runtime/compatibility', {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function listClientTasks() {
  return request.get<ApiResult<TicketTaskItem[]>>('/client/tasks', {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function getClientTask(taskId: number) {
  return request.get<ApiResult<TicketTaskItem>>(`/client/tasks/${taskId}`, {
    headers: { 'X-Silent-Error': '1' },
  })
}

export function submitClientTaskEvidence(taskId: number, data: { remark?: string; evidenceText?: string }) {
  return request.post<ApiResult<TicketTaskItem>>(`/client/tasks/${taskId}/submit-evidence`, data)
}

export function confirmClientTask(taskId: number, data?: { remark?: string; evidenceText?: string }) {
  return request.post<ApiResult<TicketTaskItem>>(`/client/tasks/${taskId}/confirm`, data || {})
}
