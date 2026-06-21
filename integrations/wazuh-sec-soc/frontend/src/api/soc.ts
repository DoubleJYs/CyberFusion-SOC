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
}

export interface AssetItem {
  id: number
  hostname: string
  ip: string
  osType: string
  sourceType: string
  riskLevel: string
  deptName: string
  ownerName: string
  openAlertCount: number
  lastSeenAt: string
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

export interface SuricataImportPayload {
  content: string
  linkAlerts?: boolean
}

export interface SuricataImportResult {
  importedEvents: number
  createdEvents: number
  updatedEvents: number
  linkedAlerts: number
  skippedLines: number
  errors: string[]
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

export function alertAction(id: number, action: 'acknowledge' | 'false-positive' | 'ignore' | 'close' | 'ticket', remark: string) {
  return request.post<ApiResult<AlertItem | TicketItem>>(`/soc/alerts/${id}/${action}`, { remark })
}

export function listAssets(params: PageQuery) {
  return request.get<ApiResult<PageResult<AssetItem>>>('/soc/assets', { params })
}

export function listTickets(params: PageQuery) {
  return request.get<ApiResult<PageResult<TicketItem>>>('/soc/tickets', { params })
}

export function ticketDetail(id: number) {
  return request.get<ApiResult<{ ticket: TicketItem; timeline: TimelineItem[] }>>(`/soc/tickets/${id}`)
}

export function transitionTicket(id: number, targetStatus: string, remark: string) {
  return request.post<ApiResult<TicketItem>>(`/soc/tickets/${id}/transition`, { targetStatus, remark })
}

export function listReports(params: PageQuery) {
  return request.get<ApiResult<PageResult<ReportItem>>>('/soc/reports', { params })
}

export function generateReport(reportType: string) {
  return request.post<ApiResult<ReportItem>>('/soc/reports/generate', { reportType })
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

export function externalEventSummary() {
  return request.get<ApiResult<ExternalSourceSummary[]>>('/soc/external-events/summary')
}
