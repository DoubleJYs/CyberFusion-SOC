import { expect, test } from '@playwright/test'

async function login(page: import('@playwright/test').Page) {
  const username = process.env.CYBERFUSION_ADMIN_USER || 'admin'
  const passwords = process.env.CYBERFUSION_ADMIN_PASSWORD
    ? [process.env.CYBERFUSION_ADMIN_PASSWORD]
    : [process.env.CYBERFUSION_DEMO_PASSWORD || 'Admin@123456', 'admin123']
  await page.goto('/login')
  await expect(page.locator('body')).toContainText('CyberFusion SOC')
  await page.getByPlaceholder('请输入账号').fill(username)
  for (const password of passwords) {
    await page.getByPlaceholder('请输入密码').fill(password)
    await page.getByRole('button', { name: '登录' }).click()
    try {
      await expect(page).toHaveURL(/showcase|soc\/dashboard/, { timeout: 3000 })
      return
    } catch {
      await page.goto('/login')
      await page.getByPlaceholder('请输入账号').fill(username)
    }
  }
  await expect(page).toHaveURL(/showcase|soc\/dashboard/)
}

test('P4.5 key routes and latest visible sections are available', async ({ page }) => {
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => {
    consoleErrors.push(error.message)
  })

  await login(page)

  await expect(page.locator('body')).toContainText('安全运营演示台')

  await page.goto('/showcase')
  await expect(page.getByRole('heading', { name: 'CyberFusion 安全运营演示台' })).toBeVisible()

  await page.goto('/soc/dashboard')
  await expect(page.locator('body')).toContainText('Top 5 安全事件簇')

  await page.goto('/soc/policies')
  await expect(page.locator('body')).toContainText('本机检查策略')
  await expect(page.locator('body')).toContainText('事件适配映射')
  await expect(page.locator('body')).toContainText('处置剧本')
  await expect(page.locator('body')).toContainText('事件关联规则')

  await page.goto('/soc/incidents')
  await expect(page.locator('body')).toContainText('安全事件簇')
  await expect(page.locator('body')).toContainText('执行关联')

  await page.goto('/soc/alerts')
  await expect(page.locator('body')).toContainText('告警处置')
  await expect(page.locator('body')).not.toContainText('Request failed with status code 500')
  const firstAlertRow = page.locator('.el-table__body-wrapper tbody tr').first()
  if ((await firstAlertRow.count()) > 0) {
    await firstAlertRow.click()
    await expect(page.locator('body')).toContainText('关联事件簇')
  }

  await page.goto('/soc/tickets')
  await expect(page.locator('body')).toContainText(/工单中心|处置剧本任务|任务清单/)
  await expect(page.locator('body')).not.toContainText('Request failed with status code 500')

  await page.goto('/client/workbench')
  await expect(page.locator('body')).toContainText('我的电脑')

  await page.goto('/client/tasks')
  await expect(page.locator('body')).toContainText('我的待办')

  await page.goto('/client/local-range?ip=10.20.1.15&host=prod-app-01&os=Linux')
  await expect(page.locator('body')).toContainText('本机检查')
  await expect(page.locator('textarea, input').filter({ hasText: /自由命令|任意命令/ })).toHaveCount(0)
  await expect(page.locator('body')).not.toContainText('自由命令')

  expect(consoleErrors.filter((message) => !message.includes('favicon'))).toEqual([])
})
