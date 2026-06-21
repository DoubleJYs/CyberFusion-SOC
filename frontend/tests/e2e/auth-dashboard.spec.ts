import { expect, test } from '@playwright/test'

test('admin session restores SOC routes and public error pages render', async ({ page }) => {
  const passwords = process.env.CYBERFUSION_ADMIN_PASSWORD
    ? [process.env.CYBERFUSION_ADMIN_PASSWORD]
    : [process.env.CYBERFUSION_DEMO_PASSWORD || 'Admin@123456', 'admin123']
  await page.goto('/login?redirect=/soc/alerts')
  await expect(page.getByRole('heading', { name: /CyberFusion SOC/ })).toBeVisible()
  for (const password of passwords) {
    await page.getByPlaceholder('请输入密码').fill(password)
    await page.getByRole('button', { name: '登录' }).click()
    try {
      await expect(page).toHaveURL(/soc\/alerts/, { timeout: 3000 })
      break
    } catch {
      await page.goto('/login?redirect=/soc/alerts')
    }
  }
  await expect(page).toHaveURL(/soc\/alerts/)
  await expect(page.getByRole('button', { name: '批量确认' })).toBeVisible()

  await page.reload()
  await expect(page).toHaveURL(/soc\/alerts/)
  await expect(page.getByText('告警 ID')).toBeVisible()

  await page.goto('/401')
  await expect(page.getByRole('heading', { name: '401' })).toBeVisible()
  await page.goto('/403')
  await expect(page.getByRole('heading', { name: '403' })).toBeVisible()
  await page.goto('/500')
  await expect(page.getByRole('heading', { name: '500' })).toBeVisible()
  await page.goto('/missing-page')
  await expect(page.getByRole('heading', { name: '404' })).toBeVisible()
})
