import { expect, test } from '@playwright/test'

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: /Sec Wazuh SOC/ })).toBeVisible()
  await page.getByPlaceholder('请输入密码').fill('Admin@123456')
  await page.getByRole('button', { name: '登录' }).click()
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
  await expect(page.getByText('安全事件时间线')).toBeVisible()

  await page.goto('/soc/alerts')
  await expect(page.getByText('告警 ID')).toBeVisible()
  await expect(page.getByText('批量确认')).toBeVisible()
  await expect(page.locator('body')).toContainText(/MOCK-|规则描述/)

  await page.goto('/soc/alert-noise')
  await expect(page.getByRole('heading', { name: '告警降噪' })).toBeVisible()
  await expect(page.locator('body')).toContainText(/重复告警聚合|已降噪|白名单|新增规则/)

  await page.goto('/soc/assets')
  await expect(page.getByText('主机名')).toBeVisible()
  await expect(page.locator('body')).toContainText(/prod-app|finance-db|资产/)

  await page.goto('/soc/vulnerabilities')
  await expect(page.getByText('CVE')).toBeVisible()
  await expect(page.locator('body')).toContainText(/CVE-2024|修复建议|漏洞/)

  await page.goto('/soc/baselines')
  await expect(page.getByText('核查编码')).toBeVisible()
  await expect(page.locator('body')).toContainText(/SSH|密码策略|基线/)

  await page.goto('/soc/fim')
  await expect(page.getByText('事件 ID')).toBeVisible()
  await expect(page.locator('body')).toContainText(/FIM-|文件完整性|权限变化/)

  await page.goto('/soc/external-events')
  await expect(page.getByRole('heading', { name: '外部事件' })).toBeVisible()
  await expect(page.locator('body')).toContainText(/Suricata|EXT-SURICATA|规范化|导入 Suricata/)

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
  const adminLogin = await request.post('/api/auth/login', {
    data: { username: 'admin', password: 'Admin@123456' },
  })
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

  const auditorLogin = await request.post('/api/auth/login', {
    data: { username: 'auditor', password: 'Admin@123456' },
  })
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

  const operatorLogin = await request.post('/api/auth/login', {
    data: { username: 'operator', password: 'Admin@123456' },
  })
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
