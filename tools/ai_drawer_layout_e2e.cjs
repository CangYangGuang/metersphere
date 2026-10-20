const { chromium } = require('playwright');

const baseUrl = process.env.MS_BASE_URL || 'http://127.0.0.1:8085';
const projectId = '100001100001';
const orgId = '100001';

async function login(page) {
  await page.addInitScript(
    ({ currentProjectId, currentOrgId }) => {
      localStorage.setItem('app', JSON.stringify({ currentProjectId, currentOrgId }));
    },
    { currentProjectId: projectId, currentOrgId: orgId }
  );
  await page.goto(`${baseUrl}/#/login`, { waitUntil: 'domcontentloaded' });
  await page.locator('.login-input input').fill('admin');
  await page.locator('.login-password-input input').fill('metersphere');
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.hash.startsWith('#/login'), { timeout: 30000 });
  await page.waitForLoadState('networkidle');
}

async function inspectLayout(page) {
  return page.evaluate(() => ({
    viewport: { width: window.innerWidth, height: window.innerHeight },
    docked: document.body.classList.contains('ms-drawer-layout-docked'),
    app: document.querySelector('#app')?.getBoundingClientRect().toJSON(),
    drawers: [...document.querySelectorAll('.ms-drawer-docked')].map((container) => {
      const drawer = container.querySelector('.arco-drawer');
      const style = drawer ? getComputedStyle(drawer) : undefined;
      return {
        containerClass: container.className,
        containerRect: container.getBoundingClientRect().toJSON(),
        drawerClass: drawer?.className,
        drawerRect: drawer?.getBoundingClientRect().toJSON(),
        display: style?.display,
        visibility: style?.visibility,
        transform: style?.transform,
      };
    }),
    workbench: {
      historyVisible: Boolean(document.querySelector('.ai-history-panel')),
      searchVisible: Boolean(document.querySelector('.ai-history-search')),
      conversationHeaderVisible: Boolean(document.querySelector('.ai-conversation-header')),
      senderVisible: Boolean(document.querySelector('.ai-command-sender')),
      activeTitle: document.querySelector('.conversation-title-display')?.textContent?.trim() || '',
      quickCommandCount: document.querySelectorAll('.ai-quick-command').length,
    },
  }));
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  });
  const context = await browser.newContext({ viewport: { width: 1912, height: 886 } });
  const page = await context.newPage();
  try {
    await login(page);
    await page.goto(`${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}`, { waitUntil: 'networkidle' });
    await page.evaluate(() => {
      localStorage.setItem('activeAiConversation', JSON.stringify({ id: 'deleted-conversation', title: 'stale' }));
    });
    await page.locator('.navbar .right-side li').first().locator('button').click();
    await page.waitForFunction(() => document.body.classList.contains('ms-drawer-layout-docked'));
    await page.waitForTimeout(500);
    const desktop = await inspectLayout(page);

    await page.getByRole('button', { name: '\u5f00\u542f\u65b0\u5bf9\u8bdd' }).click();
    await page.locator('.ai-quick-command').first().waitFor({ state: 'visible' });
    await page.screenshot({
      path: 'C:\\metersphere\\handoff\\grpc-final-handoff\\docs\\images\\question_records\\q020-ai-workbench.png',
      fullPage: true,
    });
    const newConversation = await inspectLayout(page);

    await page.setViewportSize({ width: 1100, height: 800 });
    await page.waitForFunction(() => !document.body.classList.contains('ms-drawer-layout-docked'));
    await page.waitForTimeout(500);
    const responsive = await inspectLayout(page);

    console.log(JSON.stringify({ desktop, newConversation, responsive }, null, 2));
    const visibleResponsiveDrawer = responsive.drawers.find(
      (drawer) => drawer.drawerRect?.width >= 1090 && drawer.drawerRect?.height > 0
    );
    if (
      desktop.app?.width !== 1272 ||
      !desktop.docked ||
      !desktop.workbench.historyVisible ||
      !desktop.workbench.searchVisible ||
      !desktop.workbench.conversationHeaderVisible ||
      !desktop.workbench.senderVisible ||
      !desktop.workbench.activeTitle ||
      newConversation.workbench.quickCommandCount !== 3 ||
      responsive.docked ||
      !visibleResponsiveDrawer
    ) {
      process.exitCode = 1;
    }
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
