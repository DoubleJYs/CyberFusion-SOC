import { expect, test } from '@playwright/test'

test('admin session restores SOC routes and public error pages render', async ({ page }) => {
  await page.goto('/login?redirect=/soc/alerts')
  await expect(page.getByRole('heading', { name: /Sec Wazuh SOC/ })).toBeVisible()
  await page.getByPlaceholder('请输入密码').fill('Admin@123456')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/soc\/alerts/)
  await expect(page.getByText('批量确认')).toBeVisible()

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
