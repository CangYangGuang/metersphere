const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = process.env.MS_BASE_URL || 'http://127.0.0.1:8085';
const projectId = '100001100001';
const orgId = '100001';
const screenshotDir = path.resolve(
  __dirname,
  '..',
  'handoff/grpc-final-handoff/docs/images/question_records'
);

function walkTree(nodes, predicate) {
  for (const node of nodes || []) {
    if (predicate(node)) return node;
    const child = walkTree(node.children, predicate);
    if (child) return child;
  }
  return undefined;
}

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

async function api(page, url, options = {}) {
  return page.evaluate(
    async ({ requestUrl, requestOptions, currentProjectId, currentOrgId }) => {
      const response = await fetch(requestUrl, {
        credentials: 'include',
        ...requestOptions,
        headers: {
          'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
          'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
          ORGANIZATION: currentOrgId,
          PROJECT: currentProjectId,
          'Content-Type': 'application/json',
          ...(requestOptions.headers || {}),
        },
      });
      const text = await response.text();
      let body;
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
      return { status: response.status, body };
    },
    {
      requestUrl: url,
      requestOptions: options,
      currentProjectId: projectId,
      currentOrgId: orgId,
    }
  );
}

async function openSavedGrpc(page) {
  const response = await api(page, '/api/debug/module/tree');
  const tree = response.body.data || response.body;
  const request = walkTree(
    tree,
    (node) => node.type === 'API' && node.attachInfo?.protocol === 'GRPC'
  );
  if (!request) throw new Error('No saved gRPC debug request was found');
  await page.goto(
    `${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}&id=${request.id}`,
    { waitUntil: 'networkidle' }
  );
  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('.request-composition:visible').waitFor({ state: 'visible', timeout: 30000 });
  return request;
}

async function reproduceSingleSaveAsApi(page, syncCase = false) {
  const request = await openSavedGrpc(page);
  const composition = page.locator('.request-composition:visible');
  const saveGroup = composition.locator('.arco-btn-group').filter({ hasText: '\u4fdd\u5b58' }).last();
  await saveGroup.locator('button').last().click();
  await page.getByText('\u53e6\u5b58\u4e3a\u63a5\u53e3', { exact: true }).click();

  const modal = page.locator('.arco-modal:visible');
  await modal.waitFor({ state: 'visible', timeout: 10000 });
  const name = `gRPC-save-as-api-fix16-${Date.now()}`;
  await modal.locator('input').first().fill(name);
  if (syncCase) {
    const syncCheckbox = modal.locator('input[type="checkbox"]').first();
    if (!(await syncCheckbox.isChecked())) {
      await modal.locator('.arco-checkbox').first().click();
      await page.waitForFunction(
        () => document.querySelector('.arco-modal:not([style*="display: none"]) input[type="checkbox"]')?.checked,
        undefined,
        { timeout: 10000 }
      );
    }
  }
  console.log(JSON.stringify({
    modalText: await modal.innerText(),
    modalButtons: await modal.getByRole('button').allTextContents(),
  }, null, 2));
  const confirmButton = modal.getByRole('button', { name: '\u786e\u8ba4', exact: true });
  const [response] = await Promise.all([
    page.waitForResponse(
      (candidate) => candidate.url().includes('/api/definition/add') && candidate.request().method() === 'POST',
      { timeout: 60000 }
    ),
    confirmButton.click(),
  ]);
  const responseBody = await response.json().catch(() => undefined);
  const requestBody = response.request().postDataJSON();
  await page.waitForTimeout(1500);
  await page.screenshot({
    path: path.join(screenshotDir, 'q016-single-save-as-api-before-fix.png'),
    fullPage: true,
  });
  return {
    sourceId: request.id,
    name,
    status: response.status(),
    responseBody,
    requestBody,
    syncCase,
    modalVisible: await modal.isVisible(),
  };
}

async function verifySyncedCase(page, saved) {
  const definitionId = saved.responseBody.data.id;
  const pageResponse = await api(page, '/api/case/page', {
    method: 'POST',
    body: JSON.stringify({
      current: 1,
      pageSize: 100,
      projectId,
      apiDefinitionId: definitionId,
      protocols: ['GRPC'],
    }),
  });
  const data = pageResponse.body.data || pageResponse.body;
  const cases = Array.isArray(data) ? data : data.list || data.records || [];
  const savedCase = cases[0];
  if (!savedCase?.id) throw new Error(`No synchronized case was found for definition ${definitionId}`);
  const detailResponse = await api(page, `/api/case/get-detail/${savedCase.id}`);
  const detail = detailResponse.body.data || detailResponse.body;
  const runResponse = await api(page, `/api/case/run/${savedCase.id}`);
  const run = runResponse.body.data || runResponse.body;
  return {
    definitionId,
    caseId: savedCase.id,
    pageStatus: pageResponse.status,
    pageCode: pageResponse.body.code,
    detailStatus: detailResponse.status,
    detailCode: detailResponse.body.code,
    protocol: detail.protocol,
    serverAddress: detail.grpcCaseConfig?.serverAddress,
    descriptorLength: String(detail.grpcCaseConfig?.descriptorContent || '').length,
    runStatus: runResponse.status,
    runCode: runResponse.body.code,
    success: run.success,
    grpcStatus: run.grpcStatus,
    grpcCode: run.grpcCode,
    reportId: run.reportId,
  };
}

async function reopenDefinition(page, saved) {
  const detailResponse = await api(page, `/api/definition/get-detail/${saved.responseBody.data.id}`);
  const detail = detailResponse.body.data || detailResponse.body;
  await page.goto(
    `${baseUrl}/#/api-test/management?orgId=${orgId}&pId=${projectId}&id=${detail.id}`,
    { waitUntil: 'networkidle' }
  );
  await page.locator('.request-composition:visible').waitFor({ state: 'visible', timeout: 30000 });
  const composition = page.locator('.request-composition:visible');
  const protocol = (await composition.locator('.arco-input-group .arco-select:visible').first().innerText()).trim();
  const method = (await composition.locator('.arco-input-group .arco-select:visible').nth(1).innerText()).trim();
  await page.screenshot({
    path: path.join(screenshotDir, 'q016-single-save-as-api-reopened.png'),
    fullPage: true,
  });
  return {
    status: detailResponse.status,
    code: detailResponse.body.code,
    id: detail.id,
    protocol,
    method,
    serverAddress: detail.grpcDefinition?.serverAddress,
    descriptorLength: String(detail.grpcDefinition?.descriptorContent || '').length,
  };
}

async function runBatchSaveAsApi(page) {
  await openSavedGrpc(page);
  const composition = page.locator('.request-composition:visible');
  const saveGroup = composition.locator('.arco-btn-group').filter({ hasText: '\u4fdd\u5b58' }).last();
  await saveGroup.locator('button').last().click();
  const menu = page.locator('.arco-dropdown:visible');
  await menu.waitFor({ state: 'visible', timeout: 10000 });
  const menuText = await menu.innerText();
  await menu.getByText('\u6279\u91cf\u53e6\u5b58\u4e3a\u63a5\u53e3', { exact: true }).click();

  const modal = page.locator('.arco-modal:visible');
  await modal.getByText('\u6279\u91cf\u53e6\u5b58 gRPC \u63a5\u53e3', { exact: true }).waitFor({
    state: 'visible',
    timeout: 30000,
  });
  await page.waitForFunction(
    () => document.querySelectorAll('.arco-modal:not([style*="display: none"]) input[type="checkbox"]').length > 0,
    undefined,
    { timeout: 30000 }
  );
  const checkboxCount = await modal.locator('input[type="checkbox"]').count();
  const responsePromise = page.waitForResponse(
    (response) =>
      response.url().includes('/api/definition/grpc/batch-save-as-api') &&
      response.request().method() === 'POST',
    { timeout: 120000 }
  );
  await modal.getByRole('button', { name: '\u786e\u5b9a', exact: true }).click();
  const response = await responsePromise;
  const body = await response.json();
  await modal.waitFor({ state: 'hidden', timeout: 120000 });
  return {
    menuText,
    checkboxCount,
    status: response.status(),
    code: body.code,
    data: body.data || body,
  };
}

async function verifyAiAndModelSettings(page) {
  await page.goto(`${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}`, {
    waitUntil: 'networkidle',
  });
  const appBoxBeforeOpen = await page.locator('#app').boundingBox();
  await page.locator('.navbar .right-side li').first().locator('button').click();
  await page.waitForTimeout(1500);
  const visibleDrawers = page.locator('.arco-drawer:visible');
  console.log(JSON.stringify({
    visibleDrawerCount: await visibleDrawers.count(),
    visibleDrawerTexts: await visibleDrawers.allInnerTexts(),
  }, null, 2));
  await page.screenshot({
    path: path.join(screenshotDir, 'q017-ai-drawer-docked.png'),
    fullPage: true,
  });
  const aiDrawer = page.locator('.ms-drawer-docked .arco-drawer').last();
  await aiDrawer.waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForFunction(() => document.body.classList.contains('ms-drawer-layout-docked'));
  const box = await aiDrawer.boundingBox();
  const appBox = await page.locator('#app').boundingBox();
  const navbarBox = await page.locator('.layout-navbar').boundingBox();
  const dockedBodyClass = await page.locator('body').evaluate((body) =>
    body.classList.contains('ms-drawer-layout-docked')
  );
  const appDrawerGap = box && appBox ? box.x - (appBox.x + appBox.width) : undefined;
  const navbarDrawerGap = box && navbarBox ? box.x - (navbarBox.x + navbarBox.width) : undefined;
  const actionBoxes = await Promise.all([
    aiDrawer.getByText('\u751f\u6210\u529f\u80fd\u7528\u4f8b', { exact: true }).last().boundingBox(),
    aiDrawer.getByText('\u751f\u6210\u63a5\u53e3\u7528\u4f8b', { exact: true }).last().boundingBox(),
    aiDrawer.getByRole('button', { name: '\u5f00\u542f\u65b0\u5bf9\u8bdd' }).boundingBox(),
  ]);
  const actionsInsideDrawer = Boolean(
    box &&
      actionBoxes.every(
        (actionBox) => actionBox && actionBox.x >= box.x && actionBox.x + actionBox.width <= box.x + box.width + 1
      )
  );
  const visibleMaskCount = await page.locator('.arco-drawer-mask:visible').count();

  await aiDrawer.getByText('\u5168\u5c4f', { exact: true }).click();
  await page.waitForFunction(() => !document.body.classList.contains('ms-drawer-layout-docked'));
  await page.waitForTimeout(300);
  const fullScreenBox = await aiDrawer.boundingBox();
  const appBoxDuringFullScreen = await page.locator('#app').boundingBox();
  await aiDrawer.getByText('\u9000\u51fa\u5168\u5c4f', { exact: true }).click();
  await page.waitForFunction(() => document.body.classList.contains('ms-drawer-layout-docked'));
  await page.waitForTimeout(300);
  const appBoxAfterExitFullScreen = await page.locator('#app').boundingBox();

  await page.setViewportSize({ width: 1100, height: 800 });
  await page.waitForFunction(() => !document.body.classList.contains('ms-drawer-layout-docked'));
  await page.waitForTimeout(300);
  const responsiveBox = await aiDrawer.boundingBox();
  const responsiveFullScreenButtonCount = await aiDrawer.getByText('\u5168\u5c4f', { exact: true }).count();
  await page.screenshot({
    path: path.join(screenshotDir, 'q017-ai-drawer-responsive-fullscreen.png'),
    fullPage: true,
  });
  await page.setViewportSize({ width: 1912, height: 886 });
  await page.waitForFunction(() => document.body.classList.contains('ms-drawer-layout-docked'));
  await page.waitForTimeout(300);

  await page.getByText('\u5b9a\u4e49', { exact: true }).first().click();
  await page.waitForURL((url) => url.hash.includes('/api-test/management'), { timeout: 30000 });
  const remainedOpenAfterNavigation = await aiDrawer.isVisible();

  await aiDrawer.getByText('\u6a21\u578b\u8bbe\u7f6e', { exact: true }).click();
  const settingsDrawerContainer = page.locator('.ms-drawer:visible').filter({ hasText: '\u4f9b\u5e94\u5546' }).last();
  const settingsDrawer = settingsDrawerContainer.locator('.arco-drawer:visible');
  await settingsDrawer.waitFor({ state: 'visible', timeout: 30000 });
  await settingsDrawer.getByText('OpenAI', { exact: true }).click();
  const addButton = settingsDrawer.getByRole('button', { name: '\u6dfb\u52a0\u6a21\u578b', exact: true });
  await addButton.waitFor({ state: 'visible', timeout: 30000 });
  await addButton.click();
  const editDrawer = page.locator('.ms-drawer:visible').last().locator('.arco-drawer:visible');
  await editDrawer.getByText('API \u57df\u540d', { exact: true }).waitFor({ state: 'visible', timeout: 30000 });
  const editText = await editDrawer.innerText();
  const fieldsVisible = ['\u6a21\u578b\u540d\u79f0', '\u6a21\u578b\u7c7b\u578b', '\u57fa\u7840\u6a21\u578b', 'API \u57df\u540d', 'Api Key'].every(
    (label) => editText.includes(label)
  );
  await page.screenshot({
    path: path.join(screenshotDir, 'q016-ai-model-settings.png'),
    fullPage: true,
  });
  return {
    width: box?.width,
    appWidthBeforeOpen: appBoxBeforeOpen?.width,
    appWidthWhileDocked: appBox?.width,
    appDrawerGap,
    navbarDrawerGap,
    dockedBodyClass,
    actionsInsideDrawer,
    fullScreenWidth: fullScreenBox?.width,
    appWidthDuringFullScreen: appBoxDuringFullScreen?.width,
    appWidthAfterExitFullScreen: appBoxAfterExitFullScreen?.width,
    responsiveWidth: responsiveBox?.width,
    responsiveFullScreenButtonCount,
    visibleMaskCount,
    remainedOpenAfterNavigation,
    settingsVisible: await settingsDrawer.isVisible(),
    openAiSupplierVisible: await settingsDrawer.getByText('OpenAI', { exact: true }).isVisible(),
    fieldsVisible,
  };
}

async function verifyInternalAiApi(page) {
  const modelResponse = await api(page, '/ai/config/source/name/list');
  const models = modelResponse.body.data || modelResponse.body || [];
  const model = models.find((item) => item.name === 'PolarXAPI') || models[0];
  if (!model?.id) throw new Error('No enabled AI model is available');
  const conversationId = `fix16-${Date.now()}`;
  const request = {
    prompt: '\u53ea\u56de\u590d OK',
    chatModelId: model.id,
    conversationId,
    organizationId: orgId,
  };
  let addResponse;
  let chatResponse;
  try {
    addResponse = await api(page, '/ai/conversation/add', {
      method: 'POST',
      body: JSON.stringify(request),
    });
    chatResponse = await api(page, '/ai/conversation/chat', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  } finally {
    await api(page, `/ai/conversation/delete/${conversationId}`).catch(() => undefined);
  }
  return {
    modelName: model.name,
    modelStatus: model.status,
    listStatus: modelResponse.status,
    listCode: modelResponse.body.code,
    addStatus: addResponse?.status,
    addCode: addResponse?.body?.code,
    chatStatus: chatResponse?.status,
    chatCode: chatResponse?.body?.code,
    reply: chatResponse?.body?.data || chatResponse?.body,
  };
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
    consoleErrors.length = 0;
    serverErrors.length = 0;
    const singleSaveAsApi = await reproduceSingleSaveAsApi(page);
    const reopenedDefinition = await reopenDefinition(page, singleSaveAsApi);
    const saveAsApiWithCase = await reproduceSingleSaveAsApi(page, true);
    const synchronizedCase = await verifySyncedCase(page, saveAsApiWithCase);
    const firstBatchSaveAsApi = await runBatchSaveAsApi(page);
    const secondBatchSaveAsApi = await runBatchSaveAsApi(page);
    const internalAiApi = await verifyInternalAiApi(page);
    const aiAndModelSettings = await verifyAiAndModelSettings(page);
    const result = {
      singleSaveAsApi,
      reopenedDefinition,
      saveAsApiWithCase,
      synchronizedCase,
      firstBatchSaveAsApi,
      secondBatchSaveAsApi,
      internalAiApi,
      aiAndModelSettings,
      consoleErrors,
      serverErrors,
    };
    fs.writeFileSync(
      path.join(screenshotDir, 'q016-fix16-diagnostic.json'),
      JSON.stringify(result, null, 2)
    );
    console.log(JSON.stringify(result, null, 2));

    const first = firstBatchSaveAsApi.data;
    const second = secondBatchSaveAsApi.data;
    if (
      singleSaveAsApi.status !== 200 ||
      singleSaveAsApi.responseBody.code !== 100200 ||
      singleSaveAsApi.modalVisible ||
      reopenedDefinition.status !== 200 ||
      reopenedDefinition.code !== 100200 ||
      reopenedDefinition.protocol !== 'GRPC' ||
      !reopenedDefinition.serverAddress ||
      !reopenedDefinition.descriptorLength ||
      saveAsApiWithCase.status !== 200 ||
      saveAsApiWithCase.responseBody.code !== 100200 ||
      !saveAsApiWithCase.requestBody.saveApiAsCase ||
      synchronizedCase.pageStatus !== 200 ||
      synchronizedCase.pageCode !== 100200 ||
      synchronizedCase.detailStatus !== 200 ||
      synchronizedCase.detailCode !== 100200 ||
      synchronizedCase.protocol !== 'GRPC' ||
      !synchronizedCase.serverAddress ||
      !synchronizedCase.descriptorLength ||
      synchronizedCase.runStatus !== 200 ||
      synchronizedCase.runCode !== 100200 ||
      synchronizedCase.success !== true ||
      synchronizedCase.grpcStatus !== 'OK' ||
      synchronizedCase.grpcCode !== 0 ||
      !synchronizedCase.reportId ||
      !firstBatchSaveAsApi.menuText.includes('\u53e6\u5b58\u4e3a\u63a5\u53e3') ||
      !firstBatchSaveAsApi.menuText.includes('\u6279\u91cf\u53e6\u5b58\u4e3a\u63a5\u53e3') ||
      firstBatchSaveAsApi.status !== 200 ||
      firstBatchSaveAsApi.code !== 100200 ||
      first.failedCount !== 0 ||
      first.items?.length !== 10 ||
      secondBatchSaveAsApi.status !== 200 ||
      secondBatchSaveAsApi.code !== 100200 ||
      second.createdCount !== 0 ||
      second.updatedCount !== second.items?.length ||
      second.failedCount !== 0 ||
      internalAiApi.listStatus !== 200 ||
      internalAiApi.listCode !== 100200 ||
      internalAiApi.addStatus !== 200 ||
      internalAiApi.addCode !== 100200 ||
      internalAiApi.chatStatus !== 200 ||
      internalAiApi.chatCode !== 100200 ||
      !String(internalAiApi.reply || '').trim() ||
      aiAndModelSettings.width < 630 ||
      !aiAndModelSettings.dockedBodyClass ||
      !Number.isFinite(aiAndModelSettings.appWidthBeforeOpen) ||
      !Number.isFinite(aiAndModelSettings.appWidthWhileDocked) ||
      aiAndModelSettings.appWidthWhileDocked >= aiAndModelSettings.appWidthBeforeOpen ||
      !Number.isFinite(aiAndModelSettings.appDrawerGap) ||
      Math.abs(aiAndModelSettings.appDrawerGap) > 2 ||
      !Number.isFinite(aiAndModelSettings.navbarDrawerGap) ||
      Math.abs(aiAndModelSettings.navbarDrawerGap) > 2 ||
      !aiAndModelSettings.actionsInsideDrawer ||
      aiAndModelSettings.fullScreenWidth < 1500 ||
      aiAndModelSettings.appWidthDuringFullScreen < 1500 ||
      Math.abs(aiAndModelSettings.appWidthAfterExitFullScreen - aiAndModelSettings.appWidthWhileDocked) > 2 ||
      aiAndModelSettings.responsiveWidth < 1090 ||
      aiAndModelSettings.responsiveFullScreenButtonCount !== 0 ||
      aiAndModelSettings.visibleMaskCount !== 0 ||
      !aiAndModelSettings.remainedOpenAfterNavigation ||
      !aiAndModelSettings.settingsVisible ||
      !aiAndModelSettings.openAiSupplierVisible ||
      !aiAndModelSettings.fieldsVisible ||
      consoleErrors.length ||
      serverErrors.length
    ) {
      process.exitCode = 1;
    }
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error?.stack || error);
  process.exit(1);
});
