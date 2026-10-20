const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = process.env.MS_BASE_URL || 'http://127.0.0.1:8085';
const projectId = '100001100001';
const orgId = '100001';
const moduleName = process.env.MS_GRPC_MODULE || 'PolarControl';
const screenshotDir = path.resolve(
  __dirname,
  '..',
  'handoff/grpc-final-handoff/docs/images/question_records'
);

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
}

async function api(page, url) {
  return page.evaluate(
    async ({ requestUrl, currentProjectId, currentOrgId }) => {
      const response = await fetch(requestUrl, {
        credentials: 'include',
        headers: {
          'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
          'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
          ORGANIZATION: currentOrgId,
          PROJECT: currentProjectId,
        },
      });
      return { status: response.status, body: await response.json() };
    },
    { requestUrl: url, currentProjectId: projectId, currentOrgId: orgId }
  );
}

function walkTree(nodes, predicate) {
  for (const node of nodes || []) {
    if (predicate(node)) return node;
    const child = walkTree(node.children, predicate);
    if (child) return child;
  }
  return undefined;
}

async function main() {
  fs.mkdirSync(screenshotDir, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  });
  const context = await browser.newContext({ viewport: { width: 1912, height: 886 } });
  const page = await context.newPage();
  const consoleErrors = [];
  const serverErrors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('response', (response) => {
    if (response.status() >= 500) serverErrors.push(`${response.status()} ${response.url()}`);
  });

  try {
    await login(page);
    const treeResponse = await api(page, '/api/debug/module/tree');
    const tree = treeResponse.body.data || treeResponse.body;
    const moduleNode = walkTree(tree, (node) => node.type === 'MODULE' && node.name === moduleName);
    const seedRequest = walkTree(
      moduleNode?.children,
      (node) => node.type === 'API' && node.attachInfo?.protocol === 'GRPC'
    );
    if (!moduleNode || !seedRequest) throw new Error(`No saved gRPC request exists under ${moduleName}`);

    await page.goto(
      `${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}&id=${seedRequest.id}`,
      {
      waitUntil: 'networkidle',
      }
    );
    await page.reload({ waitUntil: 'networkidle' });
    await page.locator('.request-composition:visible').waitFor({ state: 'visible', timeout: 30000 });
    await page.getByText(moduleName, { exact: true }).first().waitFor({ state: 'visible', timeout: 30000 });

    const moduleTitle = page.getByText(moduleName, { exact: true }).first();
    const treeNode = moduleTitle.locator(
      'xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " arco-tree-node ")][1]'
    );
    await treeNode.hover();
    const addServiceButton = treeNode.locator('.ms-tree-node-extra__btn').first();
    await addServiceButton.waitFor({ state: 'visible', timeout: 10000 });

    const tabs = page.locator('.ms-editable-tab-nav > .ms-editable-tab');
    const before = {
      tabCount: await tabs.count(),
      activeTabText: (await page.locator('.ms-editable-tab-nav > .ms-editable-tab.active').innerText()).trim(),
      buttonDisabled: await addServiceButton.isDisabled(),
      buttonBox: await addServiceButton.boundingBox(),
    };
    await page.screenshot({
      path: path.join(screenshotDir, 'q015-add-grpc-service-before.png'),
      fullPage: true,
    });

    consoleErrors.length = 0;
    serverErrors.length = 0;
    await addServiceButton.click();
    await page.waitForTimeout(800);

    const activeTab = page.locator('.ms-editable-tab-nav > .ms-editable-tab.active').first();
    const serviceDefinition = page.getByText('\u670d\u52a1\u5b9a\u4e49', { exact: true }).last();
    const successMessage = page.locator('.arco-message-success').filter({ hasText: 'gRPC' });
    const after = {
      tabCount: await tabs.count(),
      activeTabText: (await activeTab.innerText()).trim(),
      serviceDefinitionVisible: await serviceDefinition.isVisible(),
      successMessageVisible: await successMessage.isVisible(),
      protocolText: (
        await page.locator('.request-composition:visible .arco-input-group .arco-select:visible').first().innerText()
      ).trim(),
      inheritedProtoVisible: await page.locator('.request-composition:visible').getByText(/\.proto$/).first().isVisible(),
      moduleId: moduleNode.id,
      seedRequestId: seedRequest.id,
    };

    await page.screenshot({
      path: path.join(screenshotDir, 'q015-add-grpc-service-after.png'),
      fullPage: true,
    });

    const result = { moduleName, before, after, consoleErrors, serverErrors };
    console.log(JSON.stringify(result, null, 2));
    if (before.buttonDisabled) throw new Error('Add gRPC service button is disabled');
    if (after.tabCount !== before.tabCount + 1) throw new Error('Click did not add exactly one debug tab');
    if (!after.activeTabText.includes('GRPC')) throw new Error('The new gRPC tab is not active');
    if (!after.serviceDefinitionVisible) throw new Error('Service definition panel is not visible');
    if (!after.successMessageVisible) throw new Error('Creation feedback message is not visible');
    if (after.protocolText !== 'GRPC') throw new Error(`Unexpected protocol: ${after.protocolText}`);
    if (!after.inheritedProtoVisible) throw new Error('The parsed Proto bundle was not inherited');
    if (consoleErrors.length || serverErrors.length) throw new Error('Browser errors were detected');
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
