const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = process.env.MS_BASE_URL || 'http://127.0.0.1:8085';
const projectId = process.env.MS_PROJECT_ID || '100001100001';
const orgId = process.env.MS_ORG_ID || '100001';
const scenarioName = process.env.MS_SCENARIO_NAME || 'PB';
const outputDir = path.resolve(
  __dirname,
  '..',
  'handoff/grpc-final-handoff/docs/images/question_records/q014'
);

function writeJson(name, value) {
  fs.writeFileSync(path.join(outputDir, name), JSON.stringify(value, null, 2));
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

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  });
  const context = await browser.newContext({ viewport: { width: 1900, height: 1000 } });
  const page = await context.newPage();
  const consoleErrors = [];
  const serverErrors = [];
  const responses = [];
  const webSocketFrames = [];

  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('response', async (response) => {
    const url = response.url();
    if (response.status() >= 500) serverErrors.push({ status: response.status(), url });
    if (!url.includes('/api/')) return;
    if (!/(scenario|report)/i.test(url)) return;
    let body;
    try {
      body = await response.json();
    } catch {
      body = undefined;
    }
    responses.push({ method: response.request().method(), status: response.status(), url, body });
  });
  page.on('websocket', (socket) => {
    socket.on('framereceived', (event) => webSocketFrames.push(event.payload));
  });

  let caughtError;
  let verification;
  try {
    await login(page);
    // Ignore the expected unauthenticated /is-login probes emitted by the login page.
    consoleErrors.length = 0;
    await page.goto(`${baseUrl}/#/api-test/scenario?orgId=${orgId}&pId=${projectId}`, {
      waitUntil: 'networkidle',
    });
    const scenarioRow = page.locator('.arco-table-tr').filter({ hasText: scenarioName }).last();
    await scenarioRow.waitFor({ state: 'visible', timeout: 30000 });
    const scenarioDetailResponse = page.waitForResponse(
      (response) => /\/api\/scenario\/get\//.test(response.url()),
      { timeout: 30000 }
    );
    await scenarioRow.getByText('编辑', { exact: true }).click();
    await scenarioDetailResponse;
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(outputDir, 'pb-scenario-before.png'), fullPage: true });

    const runResponsePromise = page.waitForResponse(
      (response) =>
        /\/api\/scenario\/run(?:\?|$)/.test(response.url()) && response.request().method() === 'POST',
      { timeout: 30000 }
    );
    await page.getByRole('button', { name: '服务端执行', exact: true }).click();
    await runResponsePromise;
    const viewReport = page.getByText('查看报告', { exact: true });
    await viewReport.waitFor({ state: 'visible', timeout: 180000 });
    await page.screenshot({ path: path.join(outputDir, 'pb-scenario-after.png'), fullPage: true });
    await viewReport.click();
    await page.waitForTimeout(3000);
    await page.screenshot({ path: path.join(outputDir, 'pb-report.png'), fullPage: true });

    const visibleText = await page.locator('body').innerText();
    const stepCountMatch = visibleText.match(/共\s+(\d+)\s+个步骤/);
    const reportMatch = visibleText.match(
      /步骤分析\s+总数\(个\)\s+(\d+)\s+通过\s+(\d+)\s+[\d.]+%\s+误报\s+(\d+)\s+[\d.]+%\s+失败\s+(\d+)\s+[\d.]+%\s+未执行\s+(\d+)/
    );
    verification = {
      stepCount: Number(stepCountMatch?.[1] || 0),
      reportTotal: Number(reportMatch?.[1] || 0),
      passedCount: Number(reportMatch?.[2] || 0),
      fakeErrorCount: Number(reportMatch?.[3] || 0),
      failedCount: Number(reportMatch?.[4] || 0),
      pendingCount: Number(reportMatch?.[5] || 0),
    };
    verification.executedCount =
      verification.passedCount + verification.fakeErrorCount + verification.failedCount;

    console.log(
      JSON.stringify(
        {
          url: page.url(),
          consoleErrorCount: consoleErrors.length,
          serverErrorCount: serverErrors.length,
          responseCount: responses.length,
          verification,
          outputDir,
        },
        null,
        2
      )
    );
    if (
      verification.stepCount !== 30 ||
      verification.reportTotal !== 30 ||
      verification.executedCount !== 30 ||
      verification.fakeErrorCount !== 0 ||
      verification.pendingCount !== 0 ||
      consoleErrors.length > 0 ||
      serverErrors.length > 0
    ) {
      throw new Error(`PB scenario verification failed: ${JSON.stringify(verification)}`);
    }
  } catch (error) {
    caughtError = error;
    throw error;
  } finally {
    await page.screenshot({ path: path.join(outputDir, 'last-page.png'), fullPage: true }).catch(() => {});
    writeJson('diagnostic.json', {
      url: page.url(),
      title: await page.title().catch(() => ''),
      visibleText: await page.locator('body').innerText().catch(() => ''),
      error: caughtError ? String(caughtError.stack || caughtError) : undefined,
      consoleErrors,
      serverErrors,
      verification,
      responses,
      webSocketFrames,
    });
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
