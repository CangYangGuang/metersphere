const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = process.env.MS_BASE_URL || 'http://127.0.0.1:8085';
const projectId = '100001100001';
const orgId = '100001';
const screenshotDir = path.resolve(__dirname, '..', 'handoff/grpc-final-handoff/docs/images/question_records');
const tempModelName = `AI-model-e2e-${Date.now()}`;

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
    async ({ url, options, projectId, orgId }) => {
      const response = await fetch(url, {
        credentials: 'include',
        ...options,
        headers: {
          'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
          'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
          ORGANIZATION: orgId,
          PROJECT: projectId,
          'Content-Type': 'application/json',
          ...(options.headers || {}),
        },
      });
      const body = await response.json().catch(() => undefined);
      return { status: response.status, body };
    },
    { url, options, projectId, orgId }
  );
}

async function findInternalModelAndProbe(page) {
  const query = { current: 1, pageSize: 100, keyword: '', providerName: 'Open AI' };
  const system = await api(page, '/ai/config/source/list', { method: 'POST', body: JSON.stringify(query) });
  let models = system.body?.data?.list || system.body?.data || [];
  let probeUrl = '/ai/config/probe';
  let model = models.find((item) => item.name === 'PolarXAPI') || models.find((item) => item.status);
  if (!model) {
    const personal = await api(page, '/personal/model/source/list', {
      method: 'POST',
      body: JSON.stringify({ ...query, owner: 'admin' }),
    });
    models = personal.body?.data?.list || personal.body?.data || [];
    model = models.find((item) => item.name === 'PolarXAPI') || models.find((item) => item.status);
    probeUrl = '/personal/model/probe';
  }
  if (!model?.id) throw new Error('No saved OpenAI-compatible model was found');
  const probe = await api(page, probeUrl, { method: 'POST', body: JSON.stringify({ id: model.id }) });
  const result = probe.body?.data || probe.body;
  if (probe.status !== 200 || probe.body?.code !== 100200 || !result?.connected || !result.models?.length) {
    throw new Error(`Saved model probe failed: status=${probe.status}, code=${probe.body?.code}`);
  }
  return {
    id: model.id,
    name: model.name,
    probeUrl,
    modelCount: result.models.length,
    expectedModelFound: result.models.includes('gpt-5.6-sol'),
  };
}

async function cleanupTemporaryModels(page) {
  const response = await api(page, '/personal/model/source/list', {
    method: 'POST',
    body: JSON.stringify({ current: 1, pageSize: 100, owner: 'admin', keyword: '', providerName: 'Open AI' }),
  });
  const models = response.body?.data?.list || response.body?.data || [];
  for (const model of models.filter((item) => item.name?.startsWith('AI-model-e2e-'))) {
    await api(page, `/personal/model/delete/${model.id}`);
  }
}

async function openModelSettings(page) {
  await page.goto(`${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}`, { waitUntil: 'networkidle' });
  await page.locator('.navbar .right-side li').first().locator('button').click();
  const aiDrawer = page.locator('.ms-drawer-docked .arco-drawer').last();
  await aiDrawer.waitFor({ state: 'visible', timeout: 30000 });
  await aiDrawer.getByText('\u6a21\u578b\u8bbe\u7f6e', { exact: true }).click();
  const settingsContainer = page.locator('.ms-drawer:visible').filter({ hasText: '\u4f9b\u5e94\u5546' }).last();
  const settingsDrawer = settingsContainer.locator('.arco-drawer:visible');
  await settingsDrawer.waitFor({ state: 'visible', timeout: 30000 });
  await settingsDrawer.getByText('OpenAI', { exact: true }).click();
  await settingsDrawer.getByRole('button', { name: '\u6dfb\u52a0\u6a21\u578b', exact: true }).waitFor();
  return settingsDrawer;
}

function cardFor(settingsDrawer, name) {
  return settingsDrawer
    .getByText(name, { exact: true })
    .locator('xpath=ancestor::div[contains(@class,"rounded-md") and contains(@class,"p-[24px]")][1]');
}

async function createModel(page, settingsDrawer, expectedErrors) {
  await settingsDrawer.getByRole('button', { name: '\u6dfb\u52a0\u6a21\u578b', exact: true }).click();
  const drawer = page.locator('.ms-drawer:visible').last().locator('.arco-drawer:visible');
  const apiUrlInput = drawer.getByPlaceholder('\u8bf7\u8f93\u5165 API \u57df\u540d');
  const probeButton = drawer.getByRole('button', { name: '\u68c0\u6d4b\u5e76\u83b7\u53d6\u6a21\u578b', exact: true });
  const baseInput = drawer.getByPlaceholder('\u57fa\u7840\u6a21\u578b\u540d\u79f0\uff0c\u4e5f\u53ef\u56de\u8f66\u65b0\u5efa');
  await apiUrlInput.waitFor();
  const keyInput = drawer.getByPlaceholder('\u8bf7\u8f93\u5165 Api Key');
  if (!(await probeButton.isDisabled())) throw new Error('Probe button should be disabled before connection fields are filled');
  const positions = await Promise.all([apiUrlInput, keyInput, probeButton, baseInput].map((locator) => locator.boundingBox()));
  if (positions.some((position) => !position) || !(positions[0].y < positions[1].y && positions[1].y < positions[2].y && positions[2].y < positions[3].y)) {
    throw new Error(`Unexpected connection form order: ${JSON.stringify(positions)}`);
  }
  const keyRenderState = await keyInput.evaluate((input) => ({
    type: input.getAttribute('type'),
    textSecurity: getComputedStyle(input).webkitTextSecurity,
  }));
  if (keyRenderState.type !== 'password' && keyRenderState.textSecurity !== 'disc') {
    throw new Error(`API key is not masked by default: ${JSON.stringify(keyRenderState)}`);
  }
  await apiUrlInput.fill('http://host.docker.internal:3015/v1/');
  await keyInput.fill('wrong-key');
  if (await probeButton.isDisabled()) throw new Error('Probe button stayed disabled after connection fields were filled');
  expectedErrors.value += 1;
  expectedErrors.console += 1;
  const badResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/probe') && r.request().method() === 'POST'),
    probeButton.click(),
  ]).then(([response]) => response);
  if (badResponse.status() < 400) throw new Error('Wrong API key was unexpectedly accepted');

  await keyInput.fill('e2e-model-key');
  const goodResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/probe') && r.request().method() === 'POST'),
    probeButton.click(),
  ]).then(([response]) => response);
  const goodBody = await goodResponse.json();
  if (goodResponse.status() !== 200 || !goodBody.data?.models?.includes('e2e-model')) {
    throw new Error('Remote model discovery did not return e2e-model');
  }
  const apiUrlValue = await drawer.getByPlaceholder('\u8bf7\u8f93\u5165 API \u57df\u540d').inputValue();
  if (apiUrlValue !== 'http://host.docker.internal:3015/v1') throw new Error(`Unexpected normalized URL: ${apiUrlValue}`);
  await baseInput.click();
  await page.getByText('e2e-model', { exact: true }).last().click();
  await drawer.getByText('\u6a21\u578b\u7c7b\u578b', { exact: true }).click();
  await page.waitForTimeout(5000);
  await page.screenshot({ path: path.join(screenshotDir, 'q019-ai-model-probe-form.png'), fullPage: true });
  await drawer.getByPlaceholder('\u8bf7\u8f93\u5165\u6a21\u578b\u540d\u79f0').fill(tempModelName);
  const saveResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/edit-source') && r.request().method() === 'POST'),
    drawer.getByRole('button', { name: '\u4fdd\u5b58', exact: true }).click(),
  ]).then(([response]) => response);
  if (saveResponse.status() !== 200) throw new Error(`Create model failed: ${saveResponse.status()}`);
  await settingsDrawer.getByText(tempModelName, { exact: true }).waitFor({ timeout: 30000 });
}

async function editAndProbeModel(page, settingsDrawer) {
  let card = cardFor(settingsDrawer, tempModelName);
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/probe') && r.request().method() === 'POST'),
    card.getByRole('button', { name: '\u68c0\u6d4b', exact: true }).click(),
  ]);
  await card.getByText('\u8fde\u63a5\u53ef\u7528', { exact: true }).waitFor();
  await page.waitForTimeout(5000);
  await page.screenshot({ path: path.join(screenshotDir, 'q018-ai-model-created-and-tested.png'), fullPage: true });
  await card.getByRole('button', { name: '\u7f16\u8f91', exact: true }).click();
  const drawer = page.locator('.ms-drawer:visible').last().locator('.arco-drawer:visible');
  const keyInput = drawer.getByPlaceholder('\u8bf7\u8f93\u5165 Api Key');
  await keyInput.waitFor();
  if ((await keyInput.getAttribute('type')) !== 'password') throw new Error('Saved API key is visible in edit mode');
  const maskedKey = await keyInput.inputValue();
  if (!maskedKey.includes('****') || maskedKey.includes('e2e-model-key')) throw new Error('Saved API key was not masked');
  const probeResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/probe') && r.request().method() === 'POST'),
    drawer.getByRole('button', { name: '\u68c0\u6d4b\u5e76\u83b7\u53d6\u6a21\u578b', exact: true }).click(),
  ]).then(([response]) => response);
  if (probeResponse.status() !== 200) throw new Error('Saved masked credential could not be probed');
  const updateResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/edit-source') && r.request().method() === 'POST'),
    drawer.getByRole('button', { name: '\u66f4\u65b0', exact: true }).click(),
  ]).then(([response]) => response);
  if (updateResponse.status() !== 200) throw new Error('Model update failed');
  await settingsDrawer.getByText(tempModelName, { exact: true }).waitFor({ timeout: 30000 });
  card = cardFor(settingsDrawer, tempModelName);
  return card;
}

async function toggleAndDeleteModel(page, settingsDrawer) {
  let card = cardFor(settingsDrawer, tempModelName);
  await card.locator('.arco-switch').click();
  const closeModal = page.locator('.arco-modal:visible');
  const disableResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/edit-source') && r.request().method() === 'POST'),
    closeModal.getByRole('button', { name: '\u786e\u8ba4\u5173\u95ed', exact: true }).click(),
  ]).then(([response]) => response);
  if (disableResponse.status() !== 200) throw new Error('Disable model failed');
  await page.waitForTimeout(800);
  card = cardFor(settingsDrawer, tempModelName);
  const enableResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/edit-source') && r.request().method() === 'POST'),
    card.locator('.arco-switch').click(),
  ]).then(([response]) => response);
  if (enableResponse.status() !== 200) throw new Error('Enable model failed');
  await page.waitForTimeout(800);
  card = cardFor(settingsDrawer, tempModelName);
  await card.getByRole('button', { name: '\u5220\u9664', exact: true }).click();
  const deleteModal = page.locator('.arco-modal:visible');
  const deleteResponse = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/personal/model/delete/')),
    deleteModal.getByRole('button', { name: '\u786e\u8ba4\u5220\u9664', exact: true }).click(),
  ]).then(([response]) => response);
  if (deleteResponse.status() !== 200) throw new Error('Delete model failed');
  await settingsDrawer.getByText(tempModelName, { exact: true }).waitFor({ state: 'detached', timeout: 30000 });
}

async function verifyAiChat(page, model) {
  const conversationId = `ai-model-e2e-${Date.now()}`;
  const request = { prompt: '\u53ea\u56de\u590d OK', chatModelId: model.id, conversationId, organizationId: orgId };
  try {
    const add = await api(page, '/ai/conversation/add', { method: 'POST', body: JSON.stringify(request) });
    const chat = await api(page, '/ai/conversation/chat', { method: 'POST', body: JSON.stringify(request) });
    const reply = chat.body?.data || chat.body;
    if (add.status !== 200 || chat.status !== 200 || !String(reply || '').trim()) throw new Error('Internal AI chat failed');
    return { addStatus: add.status, chatStatus: chat.status, replyPresent: true };
  } finally {
    await api(page, `/ai/conversation/delete/${conversationId}`).catch(() => undefined);
  }
}

async function main() {
  fs.mkdirSync(screenshotDir, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  });
  const page = await browser.newPage({ viewport: { width: 1912, height: 886 } });
  const consoleErrors = [];
  const unexpectedServerErrors = [];
  const expectedErrors = { value: 0, console: 0 };
  page.on('console', (message) => {
    if (
      message.type() === 'error' &&
      expectedErrors.console > 0 &&
      message.text().includes('Failed to load resource')
    ) {
      expectedErrors.console -= 1;
    } else if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });
  page.on('response', (response) => {
    if (response.status() >= 500 && expectedErrors.value > 0 && response.url().includes('/personal/model/probe')) {
      expectedErrors.value -= 1;
    } else if (response.status() >= 500) {
      unexpectedServerErrors.push(`${response.status()} ${response.url()}`);
    }
  });
  try {
    await login(page);
    consoleErrors.length = 0;
    await cleanupTemporaryModels(page);
    const internalModel = await findInternalModelAndProbe(page);
    const aiChat = await verifyAiChat(page, internalModel);
    const settingsDrawer = await openModelSettings(page);
    await createModel(page, settingsDrawer, expectedErrors);
    await editAndProbeModel(page, settingsDrawer);
    await toggleAndDeleteModel(page, settingsDrawer);
    const result = { internalModel, aiChat, tempModelDeleted: true, consoleErrors, unexpectedServerErrors };
    fs.writeFileSync(path.join(screenshotDir, 'q018-ai-model-management-e2e.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result, null, 2));
    if (!internalModel.expectedModelFound || consoleErrors.length || unexpectedServerErrors.length) process.exitCode = 1;
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error?.stack || error);
  process.exit(1);
});
