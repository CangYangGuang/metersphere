const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const baseUrl = process.env.MS_BASE_URL || "http://127.0.0.1:8085";
const projectId = "100001100001";
const orgId = "100001";
const screenshotDir = path.resolve(
  __dirname,
  "..",
  "handoff/grpc-final-handoff/docs/images/question_records"
);

const labels = {
  batchSave: "\u6279\u91cf\u4fdd\u5b58",
  batchTitle: "\u6279\u91cf\u4fdd\u5b58 gRPC \u8bf7\u6c42",
  confirm: "\u786e\u5b9a",
  requestMessage: "\u8bf7\u6c42\u6d88\u606f",
  serviceDefinition: "\u670d\u52a1\u5b9a\u4e49",
  serverExecute: "\u670d\u52a1\u7aef\u6267\u884c",
};

function protoFile(fileName, filePath, main = false) {
  return {
    fileName,
    content: fs.readFileSync(path.resolve(__dirname, "..", filePath), "utf8"),
    main,
  };
}

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

async function api(page, url, options = {}) {
  return page.evaluate(
    async ({ requestUrl, requestOptions, currentProjectId, currentOrgId }) => {
      const headers = {
        "X-AUTH-TOKEN": localStorage.getItem("sessionId") || "",
        "CSRF-TOKEN": localStorage.getItem("csrfToken") || "",
        ORGANIZATION: currentOrgId,
        PROJECT: currentProjectId,
        "Accept-Language": "zh-CN",
        ...(requestOptions.headers || {}),
      };
      const response = await fetch(requestUrl, {
        credentials: "include",
        ...requestOptions,
        headers,
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

async function getModuleTree(page) {
  const response = await api(page, "/api/debug/module/tree");
  if (response.status !== 200)
    throw new Error(`Module tree request failed: ${response.status}`);
  return response.body.data || response.body;
}

async function openDebug(page, id) {
  await page.goto(
    `${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}&id=${id}`,
    {
      waitUntil: "networkidle",
    }
  );
  await page.reload({ waitUntil: "networkidle" });
  await page
    .locator(".request-composition:visible")
    .waitFor({ state: "visible", timeout: 30000 });
  await page
    .getByText(labels.requestMessage, { exact: true })
    .waitFor({ state: "visible", timeout: 30000 });
}

async function inspectProtocolControl(page) {
  const composition = page.locator(".request-composition:visible");
  const select = composition
    .locator(".arco-input-group .arco-select:visible")
    .first();
  await select.waitFor({ state: "visible" });
  const text = (await select.innerText()).trim();
  const details = await select.evaluate((element) => {
    const style = getComputedStyle(element);
    const input = element.querySelector(".arco-select-view-single");
    const inputStyle = input ? getComputedStyle(input) : null;
    return {
      tagCount: element.querySelectorAll(".arco-tag").length,
      hasArrow: Boolean(
        element.querySelector(
          ".arco-select-view-arrow, .arco-select-view-icon, svg"
        )
      ),
      backgroundColor: inputStyle?.backgroundColor || style.backgroundColor,
      borderColor: inputStyle?.borderColor || style.borderColor,
      width: element.getBoundingClientRect().width,
    };
  });
  await select.click();
  const grpcOption = page
    .locator(".arco-select-dropdown:visible .arco-select-option")
    .filter({ hasText: "GRPC" });
  await grpcOption.first().waitFor({ state: "visible" });
  const optionCount = await grpcOption.count();
  await page.keyboard.press("Escape");
  return { text, optionCount, ...details };
}

async function runBatchSave(page, moduleName) {
  const composition = page.locator(".request-composition:visible");
  await composition
    .getByRole("button", { name: labels.batchSave, exact: true })
    .click();
  const modal = page.locator(".arco-modal:visible");
  await modal
    .getByText(labels.batchTitle, { exact: true })
    .waitFor({ state: "visible" });

  const moduleSelect = modal.locator(".arco-select:visible").first();
  await moduleSelect.click();
  const option = page
    .locator(".arco-select-dropdown:visible .arco-select-option")
    .filter({ hasText: moduleName });
  await option.first().waitFor({ state: "visible" });
  await option.first().click();

  await modal
    .locator(".arco-form-item")
    .nth(1)
    .locator("input")
    .fill("host.docker.internal:9099");

  const checkboxes = modal.locator('input[type="checkbox"]');
  await page.waitForFunction(
    () =>
      document.querySelectorAll(
        '.arco-modal:not([style*="display: none"]) input[type="checkbox"]'
      ).length >= 59,
    undefined,
    { timeout: 30000 }
  );
  const checkboxCount = await checkboxes.count();
  let checkedCount = 0;
  for (let index = 0; index < checkboxCount; index += 1) {
    if (await checkboxes.nth(index).isChecked()) checkedCount += 1;
  }

  const responsePromise = page.waitForResponse(
    (response) =>
      response.url().includes("/api/debug/grpc/batch-save") &&
      response.request().method() === "POST",
    { timeout: 120000 }
  );
  await modal
    .getByRole("button", { name: labels.confirm, exact: true })
    .click();
  const response = await responsePromise;
  const body = await response.json();
  await modal.waitFor({ state: "hidden", timeout: 120000 });
  return {
    status: response.status(),
    code: body.code,
    data: body.data || body,
    checkboxCount,
    checkedCount,
  };
}

async function main() {
  fs.mkdirSync(screenshotDir, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH ||
      "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
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

    const initialTree = await getModuleTree(page);
    const polarModule = walkTree(
      initialTree,
      (node) => node.type === "MODULE" && node.name === "PolarControl"
    );
    if (!polarModule)
      throw new Error("PolarControl debug module was not found");
    const seedRequest = walkTree(
      polarModule.children,
      (node) => node.type === "API" && node.attachInfo?.protocol === "GRPC"
    );
    if (!seedRequest)
      throw new Error("No saved GRPC request exists under PolarControl");

    await openDebug(page, seedRequest.id);
    const protocolBefore = await inspectProtocolControl(page);
    await page.screenshot({
      path: path.join(screenshotDir, "q013-grpc-protocol-select-fix9.png"),
      fullPage: true,
    });

    const firstBatch = await runBatchSave(page, polarModule.name);
    const firstData = firstBatch.data;
    const generated = firstData.items.find(
      (item) => item.action !== "FAILED" && item.apiDebugId
    );
    if (!generated)
      throw new Error("Batch save returned no usable debug request");

    await page.waitForTimeout(1000);
    const treeAfterFirst = await getModuleTree(page);
    const polarAfterFirst = walkTree(
      treeAfterFirst,
      (node) => node.id === polarModule.id
    );
    const apiChildrenAfterFirst = (polarAfterFirst?.children || []).filter(
      (node) => node.type === "API"
    );

    const detailResponse = await api(
      page,
      `/api/debug/get/${generated.apiDebugId}`
    );
    const detail = detailResponse.body.data || detailResponse.body;
    const config = detail.request?.grpcCaseConfig || {};
    await openDebug(page, generated.apiDebugId);
    await page
      .getByText(labels.serviceDefinition, { exact: true })
      .waitFor({ state: "visible" });
    const methodLabel = `${config.serviceName} / ${config.methodName}`;
    const methodControlText = (
      await page
        .locator(
          ".request-composition:visible .arco-input-group .arco-select:visible"
        )
        .nth(1)
        .innerText()
    ).trim();
    const methodVisible =
      methodControlText.includes(config.serviceName) &&
      methodControlText.includes(config.methodName);
    const executeButton = page.getByRole("button", {
      name: labels.serverExecute,
      exact: true,
    });
    const executeVisible = await executeButton.isVisible();
    const executeDisabled = await executeButton.isDisabled();
    const expectedExecuteDisabled = !String(
      config.serverAddress || detail.path || ""
    ).trim();
    const executeStateCorrect =
      executeVisible && executeDisabled === expectedExecuteDisabled;

    const secondBatch = await runBatchSave(page, polarModule.name);
    await page.reload({ waitUntil: "networkidle" });
    await page
      .locator(".request-composition:visible")
      .waitFor({ state: "visible", timeout: 30000 });
    const protocolAfterReload = await inspectProtocolControl(page);
    await page.screenshot({
      path: path.join(screenshotDir, "q013-grpc-batch-save-fix9.png"),
      fullPage: true,
    });

    const protoFiles = config.protoFiles || [];
    const descriptor = config.descriptorContent || "";
    const requestTemplate = config.requestJson || "";
    const result = {
      module: {
        id: polarModule.id,
        initialApiCount: (polarModule.children || []).filter(
          (node) => node.type === "API"
        ).length,
        apiCountAfterFirst: apiChildrenAfterFirst.length,
      },
      protocolBefore,
      firstBatch: {
        status: firstBatch.status,
        code: firstBatch.code,
        checkboxCount: firstBatch.checkboxCount,
        checkedCount: firstBatch.checkedCount,
        createdCount: firstData.createdCount,
        updatedCount: firstData.updatedCount,
        failedCount: firstData.failedCount,
        itemCount: firstData.items.length,
      },
      generated: {
        id: generated.apiDebugId,
        fullMethodName: generated.fullMethodName,
        detailStatus: detailResponse.status,
        detailCode: detailResponse.body.code,
        protocol: detail.protocol,
        moduleId: detail.moduleId,
        protoFileCount: protoFiles.length,
        descriptorLength: String(descriptor).length,
        requestTemplate,
        methodLabel,
        methodControlText,
        methodVisible,
        executeVisible,
        executeDisabled,
        expectedExecuteDisabled,
        executeStateCorrect,
      },
      secondBatch: {
        status: secondBatch.status,
        code: secondBatch.code,
        createdCount: secondBatch.data.createdCount,
        updatedCount: secondBatch.data.updatedCount,
        failedCount: secondBatch.data.failedCount,
        itemCount: secondBatch.data.items.length,
      },
      protocolAfterReload,
      consoleErrors,
      serverErrors,
    };
    console.log(JSON.stringify(result, null, 2));

    const protocolIsSelect = (control) =>
      control.text === "GRPC" &&
      control.optionCount === 1 &&
      control.tagCount === 0 &&
      control.hasArrow &&
      control.width >= 90;
    const firstTotal =
      firstData.createdCount + firstData.updatedCount + firstData.failedCount;
    const secondTotal =
      secondBatch.data.createdCount +
      secondBatch.data.updatedCount +
      secondBatch.data.failedCount;
    if (
      !protocolIsSelect(protocolBefore) ||
      firstBatch.status !== 200 ||
      firstBatch.code !== 100200 ||
      firstBatch.checkedCount !== 59 ||
      firstData.failedCount !== 0 ||
      firstData.items.length !== 59 ||
      firstTotal !== 59 ||
      apiChildrenAfterFirst.length < 59 ||
      detailResponse.status !== 200 ||
      detailResponse.body.code !== 100200 ||
      detail.protocol !== "GRPC" ||
      detail.moduleId !== polarModule.id ||
      protoFiles.length < 3 ||
      !descriptor ||
      !methodVisible ||
      !requestTemplate ||
      !executeStateCorrect ||
      secondBatch.status !== 200 ||
      secondBatch.code !== 100200 ||
      secondBatch.data.createdCount !== 0 ||
      secondBatch.data.updatedCount !== 59 ||
      secondBatch.data.failedCount !== 0 ||
      secondBatch.data.items.length !== 59 ||
      secondTotal !== 59 ||
      !protocolIsSelect(protocolAfterReload) ||
      serverErrors.length
    ) {
      process.exitCode = 1;
    }
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
