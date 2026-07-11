const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const base = 'http://127.0.0.1:5174';
const out = __dirname;
const routes = [
  ['01-soc-dashboard-current.png', '/soc/dashboard'],
  ['02-policy-algorithm-current.png', '/soc/policies'],
  ['03-role-current.png', '/system/role'],
  ['04-external-events-current.png', '/soc/external-events'],
  ['05-vulnerabilities-current.png', '/soc/vulnerabilities'],
  ['06-rules-current.png', '/soc/rules'],
  ['07-client-workbench-current.png', '/client/workbench'],
  ['08-showcase-current.png', '/showcase'],
  ['09-alerts-current.png', '/soc/alerts'],
  ['10-tickets-current.png', '/soc/tickets'],
  ['11-client-tasks-current.png', '/client/tasks'],
  ['12-reports-current.png', '/soc/reports'],
  ['13-platform-dashboard-current.png', '/dashboard'],
];

const workspaceButtons = {
  '/soc/external-events': '进入外部事件',
  '/soc/vulnerabilities': '进入漏洞中心',
  '/soc/alerts': '进入告警处置',
};

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    colorScheme: 'light',
  });
  const page = await context.newPage();
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.getByPlaceholder('请输入账号').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('Admin@123456');
  await Promise.all([
    page.waitForURL(url => !url.pathname.endsWith('/login'), { timeout: 15000 }),
    page.getByRole('button', { name: '登录' }).click(),
  ]);

  const results = [];
  for (const [filename, route] of routes) {
    const response = await page.goto(base + route, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(900);
    if (page.url().includes('/soc/user-workspaces') && workspaceButtons[route]) {
      const enter = page.getByRole('button', { name: workspaceButtons[route] }).first();
      if (await enter.count()) {
        await enter.click();
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(700);
      }
    }
    await page.evaluate(() => window.scrollTo(0, 0));
    const title = await page.title();
    const bodyText = await page.locator('body').innerText();
    const target = path.join(out, filename);
    await page.screenshot({ path: target, fullPage: false });
    results.push({ filename, route, status: response && response.status(), title, chars: bodyText.length, finalUrl: page.url() });
  }
  fs.writeFileSync(path.join(out, 'capture-manifest.json'), JSON.stringify(results, null, 2));
  await browser.close();
  console.log(JSON.stringify(results, null, 2));
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
