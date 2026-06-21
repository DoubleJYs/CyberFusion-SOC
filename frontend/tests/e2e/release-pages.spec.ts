import { expect, test } from '@playwright/test'

const screenshotDir = '../docs/screenshots'

async function login(page: import('@playwright/test').Page) {
  const username = process.env.CYBERFUSION_ADMIN_USER || 'admin'
  const passwords = process.env.CYBERFUSION_ADMIN_PASSWORD
    ? [process.env.CYBERFUSION_ADMIN_PASSWORD]
    : [process.env.CYBERFUSION_DEMO_PASSWORD || 'Admin@123456', 'admin123']
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: /CyberFusion SOC/ })).toBeVisible()
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

test('release candidate pages render without blank screens or uncaught console errors', async ({ page }) => {
  const consoleErrors: string[] = []
  const serverErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => {
    consoleErrors.push(error.message)
  })
  page.on('response', (response) => {
    if (response.status() >= 500) {
      serverErrors.push(`${response.status()} ${response.url()}`)
    }
  })

  await page.goto('/login')
  await expect(page.locator('body')).toContainText('CyberFusion SOC')
  await page.screenshot({ path: `${screenshotDir}/acceptance-01-login.png`, fullPage: true })

  await login(page)
  await page.goto('/soc/dashboard')
  await expect(page.locator('body')).toContainText('Top 5 安全事件簇')

  const pages = [
    ['/showcase', 'acceptance-02-showcase.png'],
    ['/soc/demo-range', 'acceptance-03-demo-range.png'],
    ['/soc/alerts', 'acceptance-04-alerts.png'],
    ['/soc/policies', 'acceptance-05-policies.png'],
    ['/soc/reports', 'acceptance-06-reports.png'],
    ['/client/workbench', 'acceptance-07-client-workbench.png'],
    ['/client/local-range?ip=10.20.1.15&host=prod-app-01&os=Linux', 'acceptance-08-client-local-range.png'],
    ['/soc/incidents', 'acceptance-09-incidents.png'],
  ] as const

  for (const [path, file] of pages) {
    await page.goto(path)
    await expect(page.locator('body')).not.toHaveText(/^\s*$/)
    await expect(page.locator('body')).not.toContainText('Request failed with status code 500')
    if (path === '/soc/incidents') {
      await expect(page.locator('body')).toContainText('安全事件簇')
      await expect(page.locator('body')).toContainText('执行关联')
    }
    if (path === '/soc/policies') {
      await expect(page.locator('body')).toContainText('事件关联规则')
    }
    await page.screenshot({ path: `${screenshotDir}/${file}`, fullPage: true })
  }

  await page.goto('/soc/alerts')
  const firstAlertRow = page.locator('.el-table__body-wrapper tbody tr').first()
  if ((await firstAlertRow.count()) > 0) {
    await firstAlertRow.click()
    await expect(page.locator('body')).toContainText('关联事件簇')
  }

  expect(serverErrors).toEqual([])
  expect(consoleErrors.filter((message) => !message.includes('favicon'))).toEqual([])
})
