const { chromium } = require("playwright");

const baseUrl = process.env.MS_BASE_URL || "http://127.0.0.1:8085";
const projectId = "100001100001";
const orgId = "100001";

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
      localStorage.setItem(
        "app",
        JSON.stringify({ currentProjectId, currentOrgId })
      );
    },
    { currentProjectId: projectId, currentOrgId: orgId }
  );
  await page.goto(`${baseUrl}/#/login`, { waitUntil: "domcontentloaded" });
  await page.locator(".login-input input").fill("admin");
  await page.locator(".login-password-input input").fill("metersphere");
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.hash.startsWith("#/login"), {
    timeout: 30000,
  });
  await page.waitForLoadState("networkidle");
}

async function api(page, url) {
  return page.evaluate(
    async ({ requestUrl, currentProjectId, currentOrgId }) => {
      const response = await fetch(requestUrl, {
        credentials: "include",
        headers: {
          "X-AUTH-TOKEN": localStorage.getItem("sessionId") || "",
          "CSRF-TOKEN": localStorage.getItem("csrfToken") || "",
          ORGANIZATION: currentOrgId,
          PROJECT: currentProjectId,
        },
      });
      return { status: response.status, body: await response.json() };
    },
    { requestUrl: url, currentProjectId: projectId, currentOrgId: orgId }
  );
}

async function openSavedGrpc(page) {
  const response = await api(page, "/api/debug/module/tree");
  const tree = response.body.data || response.body;
  const firstGrpcRequest = walkTree(
    tree,
    (node) => node.type === "API" && node.attachInfo?.protocol === "GRPC"
  );
  const request =
    walkTree(
      tree,
      (node) =>
        node.type === "API" &&
        node.attachInfo?.protocol === "GRPC" &&
        String(node.name || "").includes("Polar.v1")
    ) || firstGrpcRequest;
  if (!request) throw new Error("No saved gRPC debug request was found");
  await page.goto(
    `${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}&id=${request.id}`,
    {
      waitUntil: "networkidle",
    }
  );
  await page.reload({ waitUntil: "networkidle" });
  await page
    .locator(".request-composition:visible")
    .waitFor({ state: "visible", timeout: 30000 });
  return request;
}

async function readTabs(page) {
  const tabs = page.locator(".ms-editable-tab-nav > .ms-editable-tab");
  return {
    count: await tabs.count(),
    labels: (await tabs.allInnerTexts()).map((text) => text.trim()),
    active: (
      await page
        .locator(".ms-editable-tab-nav > .ms-editable-tab.active")
        .allInnerTexts()
    ).map((text) => text.trim()),
  };
}

async function verifyWorkspaceCache(page) {
  await openSavedGrpc(page);
  const addButton = page
    .locator(".ms-editable-tab-container > .ms-editable-tab-button")
    .first();
  await addButton.click();
  const before = await readTabs(page);

  await page
    .locator(".layout-sider")
    .getByText("\u5de5\u4f5c\u53f0", { exact: true })
    .click();
  await page.waitForTimeout(300);
  const leaveButton = page
    .locator(".arco-modal:visible")
    .getByRole("button", { name: "\u79bb\u5f00", exact: true });
  if (await leaveButton.isVisible()) {
    await leaveButton.click();
  }
  await page.waitForURL((url) => url.hash.includes("/workstation"), {
    timeout: 30000,
  });
  await page.waitForTimeout(700);
  await page
    .locator(".layout-sider")
    .getByText("\u63a5\u53e3\u6d4b\u8bd5", { exact: true })
    .click();
  await page.waitForURL((url) => url.hash.includes("/api-test/debug"), {
    timeout: 30000,
  });
  await page
    .locator(".request-composition:visible")
    .waitFor({ state: "visible", timeout: 30000 });

  const after = await readTabs(page);
  return {
    before,
    after,
    preserved:
      before.count === after.count &&
      JSON.stringify(before.labels) === JSON.stringify(after.labels) &&
      JSON.stringify(before.active) === JSON.stringify(after.active),
  };
}

async function openSaveMenu(page) {
  const composition = page.locator(".request-composition:visible");
  const saveGroup = composition
    .locator(".arco-btn-group")
    .filter({ hasText: "\u4fdd\u5b58" })
    .last();
  await saveGroup.locator("button").last().click();
  const menu = page.locator(".arco-dropdown:visible");
  await menu.waitFor({ state: "visible", timeout: 10000 });
  return menu;
}

async function verifySingleSaveNavigation(page) {
  await openSavedGrpc(page);
  const menu = await openSaveMenu(page);
  await menu
    .getByText("\u53e6\u5b58\u4e3a\u63a5\u53e3", { exact: true })
    .click();
  const modal = page.locator(".arco-modal:visible");
  await modal.waitFor({ state: "visible", timeout: 10000 });
  const name = `Codex-save-as-definition-${Date.now()}`;
  await modal.locator("input").first().fill(name);
  const [response] = await Promise.all([
    page.waitForResponse(
      (candidate) =>
        candidate.url().includes("/api/definition/add") &&
        candidate.request().method() === "POST",
      { timeout: 60000 }
    ),
    modal.getByRole("button", { name: "\u786e\u8ba4", exact: true }).click(),
  ]);
  const body = await response.json();
  const definitionId = (body.data || body).id;
  await page.waitForURL(
    (url) =>
      url.hash.includes("/api-test/management") &&
      url.hash.includes(`id=${definitionId}`),
    { timeout: 30000 }
  );
  const savedDefinitionTab = page
    .locator(".ms-editable-tab-nav > .ms-editable-tab")
    .filter({ hasText: name });
  await savedDefinitionTab.waitFor({ state: "visible", timeout: 30000 });
  return {
    status: response.status(),
    code: body.code,
    definitionId,
    url: page.url(),
    definitionVisible: await savedDefinitionTab.isVisible(),
  };
}

async function verifyBatchSaveNavigation(page) {
  await openSavedGrpc(page);
  const menu = await openSaveMenu(page);
  await menu
    .getByText("\u6279\u91cf\u53e6\u5b58\u4e3a\u63a5\u53e3", { exact: true })
    .click();
  const modal = page.locator(".arco-modal:visible");
  await modal.waitFor({ state: "visible", timeout: 30000 });
  await page.waitForFunction(
    () =>
      document.querySelectorAll(
        '.arco-modal:not([style*="display: none"]) input[type="checkbox"]'
      ).length > 0,
    undefined,
    { timeout: 30000 }
  );
  const [response] = await Promise.all([
    page.waitForResponse(
      (candidate) =>
        candidate.url().includes("/api/definition/grpc/batch-save-as-api") &&
        candidate.request().method() === "POST",
      { timeout: 120000 }
    ),
    modal.getByRole("button", { name: "\u786e\u5b9a", exact: true }).click(),
  ]);
  const body = await response.json();
  const result = body.data || body;
  await page.waitForURL((url) => url.hash.includes("/api-test/management"), {
    timeout: 30000,
  });
  await page
    .locator(".ms-table:visible, .arco-table:visible")
    .first()
    .waitFor({ state: "visible", timeout: 30000 });
  return {
    status: response.status(),
    code: body.code,
    createdCount: result.createdCount,
    updatedCount: result.updatedCount,
    failedCount: result.failedCount,
    itemCount: result.items?.length,
    url: page.url(),
  };
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH ||
      "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  });
  const context = await browser.newContext({
    viewport: { width: 1912, height: 886 },
  });
  const page = await context.newPage();
  const consoleErrors = [];
  const serverErrors = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("response", (response) => {
    if (response.status() >= 500)
      serverErrors.push(`${response.status()} ${response.url()}`);
  });

  try {
    await login(page);
    consoleErrors.length = 0;
    serverErrors.length = 0;
    const workspaceCache = await verifyWorkspaceCache(page);
    const singleSave = await verifySingleSaveNavigation(page);
    const batchSave = await verifyBatchSaveNavigation(page);
    const result = {
      workspaceCache,
      singleSave,
      batchSave,
      consoleErrors,
      serverErrors,
    };
    console.log(JSON.stringify(result, null, 2));
    if (
      !workspaceCache.preserved ||
      singleSave.status !== 200 ||
      singleSave.code !== 100200 ||
      !singleSave.definitionId ||
      !singleSave.definitionVisible ||
      batchSave.status !== 200 ||
      batchSave.code !== 100200 ||
      batchSave.failedCount !== 0 ||
      !batchSave.itemCount ||
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
