import { expect, test } from '@playwright/test'

async function loginApi(request: import('@playwright/test').APIRequestContext, username: string, passwords?: string[]) {
  const defaultPasswords = username === 'admin'
    ? [process.env.CYBERFUSION_ADMIN_PASSWORD || process.env.CYBERFUSION_DEMO_PASSWORD || 'Admin@123456', 'admin123']
    : [process.env.CYBERFUSION_DEMO_PASSWORD || 'Admin@123456']
  for (const password of passwords || defaultPasswords) {
    const response = await request.post('/api/auth/login', {
      data: { username, password },
    })
    if (response.ok()) return response
  }
  return request.post('/api/auth/login', {
    data: { username, password: (passwords || defaultPasswords)[0] },
  })
}

async function login(page: import('@playwright/test').Page) {
  const passwords = process.env.CYBERFUSION_ADMIN_PASSWORD
    ? [process.env.CYBERFUSION_ADMIN_PASSWORD]
    : [process.env.CYBERFUSION_DEMO_PASSWORD || 'Admin@123456', 'admin123']
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: /CyberFusion SOC/ })).toBeVisible()
  for (const password of passwords) {
    await page.getByPlaceholder('请输入密码').fill(password)
    await page.getByRole('button', { name: '登录' }).click()
    try {
      await expect(page).toHaveURL(/soc\/dashboard/, { timeout: 3000 })
      return
    } catch {
      await page.goto('/login')
    }
  }
  await expect(page).toHaveURL(/soc\/dashboard/)
}

test('admin can use SOC P0 flows against the real backend API', async ({ page }) => {
  await login(page)
  await expect(page.locator('.kpi-grid').getByText('今日告警')).toBeVisible()
  await expect(page.locator('.kpi-grid').getByText('高危告警')).toBeVisible()
  await expect(page.getByText('管理驾驶舱')).toBeVisible()
  await page.getByRole('tab', { name: '分析员工作台' }).click()
  await expect(page.getByText('资产风险评分')).toBeVisible()
  await expect(page.getByText('告警优先级', { exact: true })).toBeVisible()
  await expect(page.getByText('安全事件时间线', { exact: true })).toBeVisible()

  await page.goto('/soc/alerts')
  await expect(page.getByText('告警 ID')).toBeVisible()
  await expect(page.getByRole('button', { name: '批量确认' })).toBeVisible()
  await expect(page.locator('body')).toContainText(/MOCK-|规则描述/)

  await page.goto('/soc/alert-noise')
  await expect(page.getByRole('heading', { name: '告警降噪' })).toBeVisible()
  await expect(page.locator('body')).toContainText(/重复告警聚合|已降噪|白名单|新增规则/)

  await page.goto('/soc/rules')
  await expect(page.locator('.rule-center-page').getByRole('heading', { name: '检测规则中心' })).toBeVisible()
  await expect(page.locator('body')).toContainText(/Adapter 字段映射|Sigma|WAF|Suricata|Wazuh/)

  await page.goto('/soc/assets')
  await expect(page.getByText('主机名')).toBeVisible()
  await expect(page.locator('body')).toContainText(/prod-app|finance-db|资产/)

  await page.goto('/soc/vulnerabilities')
  await expect(page.getByRole('columnheader', { name: 'CVE' })).toBeVisible()
  await expect(page.locator('body')).toContainText(/CVE-2024|修复建议|漏洞/)

  await page.goto('/soc/baselines')
  await expect(page.getByText('核查编码')).toBeVisible()
  await expect(page.locator('body')).toContainText(/SSH|密码策略|基线/)

  await page.goto('/soc/fim')
  await expect(page.getByText('事件 ID')).toBeVisible()
  await expect(page.locator('body')).toContainText(/FIM-|文件完整性|权限变化/)

  await page.goto('/soc/external-events')
  await expect(page.getByRole('heading', { name: '多源事件中心' })).toBeVisible()
  await expect(page.locator('body')).toContainText(/Suricata|EXT-SURICATA|规范化|导入数据/)

  await page.goto('/soc/tickets')
  await expect(page.getByText('工单号')).toBeVisible()
  await expect(page.locator('body')).toContainText(/INC-|工单/)

  await page.goto('/soc/reports')
  await expect(page.getByText('生成报表')).toBeVisible()
  await page.getByRole('button', { name: '生成报表' }).click()
  await expect(page.locator('body')).toContainText(/告警趋势|资产风险|报表/)

  await page.goto('/soc/settings')
  await expect(page.getByText('检查连接')).toBeVisible()
  await page.getByRole('button', { name: '检查连接' }).click()
  await expect(page.locator('body')).toContainText(/CONNECTED|Wazuh/)
})

test('security modules enforce import, read-only, and data-scope permissions', async ({ request }) => {
  const adminLogin = await loginApi(request, 'admin')
  expect(adminLogin.ok()).toBeTruthy()
  const adminToken = (await adminLogin.json()).data.accessToken as string
  const adminHeaders = { Authorization: `Bearer ${adminToken}` }
  const suricataImport = await request.post('/api/soc/external-events/suricata/import', {
    headers: adminHeaders,
    data: {
      linkAlerts: true,
      content: '{"timestamp":"2026-05-27T22:55:00+08:00","event_type":"alert","src_ip":"203.0.113.88","dest_ip":"10.20.1.15","alert":{"signature_id":20260527,"signature":"ET SCAN Playwright Suricata import","severity":1}}'
    },
  })
  expect(suricataImport.ok()).toBeTruthy()
  expect((await suricataImport.json()).data.importedEvents).toBe(1)
  const importedAlerts = await request.get('/api/soc/alerts?pageNum=1&pageSize=10&keyword=Playwright%20Suricata', { headers: adminHeaders })
  expect((await importedAlerts.json()).data.total).toBeGreaterThanOrEqual(1)
  const analytics = await request.get('/api/soc/dashboard/risk-analytics', { headers: adminHeaders })
  expect(analytics.ok()).toBeTruthy()
  const analyticsData = (await analytics.json()).data
  expect(analyticsData.assetRisks.length).toBeGreaterThanOrEqual(1)
  expect(analyticsData.alertPriorities.length).toBeGreaterThanOrEqual(1)
  expect(analyticsData.operationMetrics.slaRate).toBeGreaterThanOrEqual(0)

  const rules = await request.get('/api/soc/rules?pageNum=1&pageSize=10', { headers: adminHeaders })
  expect(rules.ok()).toBeTruthy()
  const ruleRows = (await rules.json()).data.records
  expect(ruleRows.length).toBeGreaterThanOrEqual(5)
  expect(ruleRows.map((item: { sourceType: string }) => item.sourceType)).toEqual(expect.arrayContaining(['wazuh', 'suricata']))
  const mappings = await request.get('/api/soc/rules/adapter-mappings', { headers: adminHeaders })
  expect(mappings.ok()).toBeTruthy()
  expect((await mappings.json()).data.map((item: { adapter: string }) => item.adapter)).toEqual(expect.arrayContaining(['waf', 'zap', 'trivy', 'wazuh', 'suricata', 'zeek', 'sigma']))
  const firstRule = ruleRows[0] as { sourceType: string; ruleId: string }
  const hits = await request.get(`/api/soc/rules/hits?sourceType=${encodeURIComponent(firstRule.sourceType)}&ruleId=${encodeURIComponent(firstRule.ruleId)}`, { headers: adminHeaders })
  expect(hits.ok()).toBeTruthy()
  const hitData = (await hits.json()).data
  expect(hitData.sourceType).toBe(firstRule.sourceType)

  const auditorLogin = await loginApi(request, 'auditor')
  expect(auditorLogin.ok()).toBeTruthy()
  const auditorToken = (await auditorLogin.json()).data.accessToken as string
  const auditorHeaders = { Authorization: `Bearer ${auditorToken}` }

  const vulnList = await request.get('/api/soc/vulnerabilities?pageNum=1&pageSize=10', { headers: auditorHeaders })
  const baselineList = await request.get('/api/soc/baselines?pageNum=1&pageSize=10', { headers: auditorHeaders })
  const fimList = await request.get('/api/soc/fim?pageNum=1&pageSize=10', { headers: auditorHeaders })
  const externalList = await request.get('/api/soc/external-events?pageNum=1&pageSize=10', { headers: auditorHeaders })
  expect((await vulnList.json()).data.total).toBeGreaterThanOrEqual(4)
  expect((await baselineList.json()).data.total).toBeGreaterThanOrEqual(5)
  expect((await fimList.json()).data.total).toBeGreaterThanOrEqual(4)
  expect((await externalList.json()).data.total).toBeGreaterThanOrEqual(2)

  const forbiddenWrite = await request.post('/api/soc/vulnerabilities/1/status', {
    headers: auditorHeaders,
    data: { targetStatus: 'reviewing', remark: 'auditor should be read-only' },
  })
  expect(forbiddenWrite.status()).toBe(403)
  const forbiddenExternalWrite = await request.post('/api/soc/external-events/1/status', {
    headers: auditorHeaders,
    data: { targetStatus: 'linked', remark: 'auditor should be read-only' },
  })
  expect(forbiddenExternalWrite.status()).toBe(403)

  const operatorLogin = await loginApi(request, 'operator')
  expect(operatorLogin.ok()).toBeTruthy()
  const operatorToken = (await operatorLogin.json()).data.accessToken as string
  const operatorHeaders = { Authorization: `Bearer ${operatorToken}` }
  const operatorVulns = await request.get('/api/soc/vulnerabilities?pageNum=1&pageSize=10', { headers: operatorHeaders })
  const operatorBaselines = await request.get('/api/soc/baselines?pageNum=1&pageSize=10', { headers: operatorHeaders })
  const operatorFim = await request.get('/api/soc/fim?pageNum=1&pageSize=10', { headers: operatorHeaders })
  expect((await operatorVulns.json()).data.total).toBeGreaterThanOrEqual(2)
  expect((await operatorBaselines.json()).data.total).toBeGreaterThanOrEqual(2)
  expect((await operatorFim.json()).data.total).toBeGreaterThanOrEqual(2)
})

test('Demo Range batch follows event-alert-ticket-report-notification closure via API', async ({ request }) => {
  const login = await loginApi(request, 'admin')
  expect(login.ok()).toBeTruthy()
  const token = (await login.json()).data.accessToken as string
  const headers = { Authorization: `Bearer ${token}` }
  const batchId = 'DEMO-RANGE-E2E-CLOSURE'

  const batchImport = await request.post('/api/soc/demo-range/batches/import', {
    headers,
    data: { batchId, linkAlerts: true },
  })
  expect(batchImport.ok()).toBeTruthy()
  const importData = (await batchImport.json()).data
  expect(importData.batchId).toBe(batchId)
  expect(importData.importedEvents).toBeGreaterThanOrEqual(5)
  expect(importData.createdAlerts).toBeGreaterThanOrEqual(5)
  expect(importData.createdVulnerabilities).toBeGreaterThanOrEqual(1)

  const chain = await request.get(`/api/soc/demo-range/batches/${encodeURIComponent(batchId)}/evidence-chain`, { headers })
  expect(chain.ok()).toBeTruthy()
  const chainData = (await chain.json()).data
  expect(chainData.summary.batchId).toBe(batchId)
  expect(chainData.summary.eventCount).toBeGreaterThanOrEqual(5)
  expect(chainData.summary.alertCount).toBeGreaterThanOrEqual(5)

  const events = await request.get(`/api/soc/external-events?pageNum=1&pageSize=10&keyword=${encodeURIComponent(batchId)}`, { headers })
  expect((await events.json()).data.total).toBeGreaterThanOrEqual(5)

  const alerts = await request.get(`/api/soc/alerts?pageNum=1&pageSize=10&keyword=${encodeURIComponent(batchId)}`, { headers })
  const alertList = (await alerts.json()).data.records
  expect(alertList.length).toBeGreaterThanOrEqual(1)
  const alertId = alertList[0].id as number

  const alertDetail = await request.get(`/api/soc/alerts/${alertId}`, { headers })
  expect(alertDetail.ok()).toBeTruthy()
  const alertData = (await alertDetail.json()).data
  expect(alertData.batchId).toBe(batchId)
  expect(alertData.evidenceSummary || alertData.rawRef).toBeTruthy()

  const correlate = await request.post('/api/soc/incidents/correlate', { headers })
  expect(correlate.ok()).toBeTruthy()
  expect((await correlate.json()).data.activeRules).toBeGreaterThanOrEqual(1)

  const incidents = await request.get(`/api/soc/incidents?pageNum=1&pageSize=10&keyword=${encodeURIComponent(batchId)}`, { headers })
  expect(incidents.ok()).toBeTruthy()
  const incidentRows = (await incidents.json()).data.records
  expect(incidentRows.length).toBeGreaterThanOrEqual(1)
  expect(incidentRows[0].evidenceCount || incidentRows[0].eventCount).toBeGreaterThanOrEqual(1)

  const incidentDetail = await request.get(`/api/soc/incidents/${incidentRows[0].id}`, { headers })
  expect(incidentDetail.ok()).toBeTruthy()
  const incidentData = (await incidentDetail.json()).data
  expect(incidentData.evidence.length).toBeGreaterThanOrEqual(1)
  expect(incidentData.evidence.every((item: { relationReason?: string }) => Boolean(item.relationReason))).toBeTruthy()

  const relatedIncidents = await request.get(`/api/soc/alerts/${alertId}/related-incidents`, { headers })
  expect(relatedIncidents.ok()).toBeTruthy()
  expect((await relatedIncidents.json()).data.length).toBeGreaterThanOrEqual(1)

  const incidentTicket = await request.post(`/api/soc/incidents/${incidentRows[0].id}/ticket`, {
    headers,
    data: { remark: `Incident E2E ticket ${batchId}` },
  })
  expect(incidentTicket.ok()).toBeTruthy()
  expect((await incidentTicket.json()).data.ticketNo).toMatch(/^INC-/)

  const ticket = await request.post(`/api/soc/alerts/${alertId}/ticket`, {
    headers,
    data: { remark: `Demo Range E2E closure ${batchId}` },
  })
  expect(ticket.ok()).toBeTruthy()
  const ticketData = (await ticket.json()).data
  expect(ticketData.ticketNo).toMatch(/^INC-/)

  const ticketDetail = await request.get(`/api/soc/tickets/${ticketData.id}`, { headers })
  expect(ticketDetail.ok()).toBeTruthy()
  const timelineText = JSON.stringify((await ticketDetail.json()).data.timeline)
  expect(timelineText).toContain('Demo Range')
  expect(timelineText).toContain(batchId)

  const report = await request.post('/api/soc/reports/generate', {
    headers,
    data: { reportType: 'security_validation', batchId },
  })
  expect(report.ok()).toBeTruthy()
  const reportData = (await report.json()).data
  expect(reportData.reportType).toBe('security_validation')
  expect(reportData.title).toContain(batchId)
  expect(reportData.summary).toContain('通知 dry-run')

  const dryRun = await request.post('/api/soc/external-events/shuffle/demo-notification', { headers })
  expect(dryRun.ok()).toBeTruthy()
  expect((await dryRun.json()).data.status).toBe('DRY_RUN')

  const logs = await request.get(`/api/soc/settings/notification-logs?pageNum=1&pageSize=10&keyword=${encodeURIComponent(batchId)}`, { headers })
  expect(logs.ok()).toBeTruthy()
  expect((await logs.json()).data.total).toBeGreaterThanOrEqual(1)
})
