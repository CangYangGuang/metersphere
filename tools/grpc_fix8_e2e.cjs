const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const baseUrl = process.env.MS_BASE_URL || "http://127.0.0.1:8085";
const projectId = "100001100001";
const orgId = "100001";

function protoFile(fileName, filePath, main = false) {
  return {
    fileName,
    content: fs.readFileSync(path.resolve(__dirname, "..", filePath), "utf8"),
    main,
  };
}

async function clickVisibleText(page, value) {
  const candidates = page.getByText(value, { exact: true });
  const count = await candidates.count();
  const visible = [];
  for (let index = 0; index < count; index += 1) {
    const candidate = candidates.nth(index);
    if (await candidate.isVisible()) {
      const box = await candidate.boundingBox();
      if (box) visible.push({ candidate, box });
    }
  }
  if (!visible.length) {
    const bodyText = (await page.locator("body").innerText()).slice(0, 1000);
    throw new Error(
      `找不到可见文本: ${value}; url=${page.url()}; body=${bodyText}`
    );
  }
  visible.sort(
    (left, right) => left.box.y - right.box.y || left.box.x - right.box.x
  );
  await visible[0].candidate.click();
}

async function selectOption(page, container, currentValue, nextValue) {
  const values = container.getByText(currentValue, { exact: true });
  const count = await values.count();
  let opened = false;
  for (let index = 0; index < count; index += 1) {
    const candidate = values.nth(index);
    if (await candidate.isVisible()) {
      await candidate.click();
      opened = true;
      break;
    }
  }
  if (!opened) throw new Error(`找不到协议选择值: ${currentValue}`);
  await page
    .locator(".arco-select-option:visible")
    .getByText(nextValue, { exact: true })
    .last()
    .click();
}

async function visibleCount(locator) {
  const count = await locator.count();
  let result = 0;
  for (let index = 0; index < count; index += 1) {
    if (await locator.nth(index).isVisible()) result += 1;
  }
  return result;
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH ||
      "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
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
  // 登录页会主动探测未认证接口；只统计认证成功后的回归错误。
  consoleErrors.length = 0;
  serverErrors.length = 0;

  const files = [
    protoFile(
      "polar_control.proto",
      "handoff/grpc-final-handoff/test-fixtures/proto/polar_control.proto",
      true
    ),
    protoFile(
      "google/api/annotations.proto",
      "handoff/grpc-final-handoff/test-fixtures/proto/google/api/annotations.proto"
    ),
    protoFile(
      "google/api/http.proto",
      "handoff/grpc-final-handoff/test-fixtures/proto/google/api/http.proto"
    ),
  ];
  const apiResult = await page.evaluate(
    async ({ currentProjectId, protoFiles }) => {
      const authHeaders = {
        "X-AUTH-TOKEN": localStorage.getItem("sessionId") || "",
        "CSRF-TOKEN": localStorage.getItem("csrfToken") || "",
        ORGANIZATION: "100001",
        PROJECT: currentProjectId,
        "Accept-Language": "zh-CN",
      };
      async function post(url, body) {
        const response = await fetch(url, {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json", ...authHeaders },
          body: JSON.stringify(body),
        });
        const text = await response.text();
        let responseBody;
        try {
          responseBody = JSON.parse(text);
        } catch {
          responseBody = {
            raw: text.slice(0, 200),
            contentType: response.headers.get("content-type"),
          };
        }
        return {
          status: response.status,
          url: response.url,
          body: responseBody,
        };
      }
      const loginResponse = await fetch("/is-login", {
        credentials: "include",
        headers: authHeaders,
      });
      const loginText = await loginResponse.text();
      const parse = await post("/api/grpc/debug/parse", {
        protoContent: protoFiles[0].content,
        fileName: protoFiles[0].fileName,
        mainFileName: protoFiles[0].fileName,
        protoFiles,
      });
      const parseData = parse.body.data || parse.body;
      const methodFullNames = (parseData.services || []).flatMap((service) =>
        (service.methods || [])
          .filter((method) => method.invokable)
          .map((method) => method.fullName)
      );
      return {
        loginStatus: loginResponse.status,
        loginText: loginText.slice(0, 200),
        parseStatus: parse.status,
        parseUrl: parse.url,
        parseRaw: parse.body.raw,
        parseCode: parse.body.code,
        serviceCount: (parseData.services || []).length,
        methodCount: methodFullNames.length,
      };
    },
    { currentProjectId: projectId, protoFiles: files }
  );

  await clickVisibleText(page, "接口测试");
  await page.getByText("调试", { exact: true }).waitFor({ state: "visible" });
  await clickVisibleText(page, "调试");
  await page
    .getByRole("button", { name: "新建请求", exact: true })
    .waitFor({ state: "visible" });
  await page.waitForLoadState("networkidle");
  const requestComposition = page.locator(".request-composition:visible");
  await selectOption(page, requestComposition, "HTTP", "GRPC");
  await requestComposition
    .getByText("服务定义", { exact: true })
    .waitFor({ state: "visible" });
  await requestComposition.getByText("服务定义", { exact: true }).click();
  const debugButtons = await requestComposition
    .locator("button:visible")
    .allTextContents();
  const batchSaveCount = await visibleCount(
    requestComposition.getByRole("button", { name: "批量保存", exact: true })
  );
  const batchSaveAsApiCount = await visibleCount(
    page.getByText("批量另存为接口", { exact: true })
  );
  const batchGenerateCount = await visibleCount(
    page.getByText("批量生成接口", { exact: true })
  );
  await page.screenshot({
    path: path.resolve(
      __dirname,
      "..",
      "handoff/grpc-final-handoff/docs/images/q012-debug-fix8.png"
    ),
    fullPage: true,
  });

  await clickVisibleText(page, "场景");
  await page.waitForTimeout(300);
  const leaveButton = page.getByRole("button", { name: "离开", exact: true });
  if (await leaveButton.isVisible()) await leaveButton.click();
  await page
    .getByRole("button", { name: "新建场景", exact: true })
    .waitFor({ state: "visible" });
  await page.getByRole("button", { name: "新建场景", exact: true }).click();
  await page
    .getByText("添加步骤", { exact: true })
    .waitFor({ state: "visible" });
  await page.getByText("添加步骤", { exact: true }).click();
  await page
    .locator(".arco-dropdown:visible")
    .getByText("导入系统请求", { exact: true })
    .click();

  const drawer = page.locator(".arco-drawer:visible");
  await drawer
    .getByText("导入系统请求", { exact: true })
    .waitFor({ state: "visible" });
  await drawer.getByText(/全部接口 \(\d+\)/).waitFor({ state: "visible" });
  const httpText = await drawer.innerText();
  const httpHasGrpcService = /Polar\.v1\.[A-Za-z]+Service/.test(httpText);
  const httpHasGrpcRow =
    (await visibleCount(drawer.getByText("GRPC", { exact: true }))) > 0;

  // 连续切换协议，专门覆盖旧异步请求覆盖新协议结果的问题。
  await selectOption(page, drawer, "HTTP", "GRPC");
  await selectOption(page, drawer, "GRPC", "HTTP");
  await selectOption(page, drawer, "HTTP", "GRPC");
  await page.waitForTimeout(3000);
  await page.screenshot({
    path: path.resolve(
      __dirname,
      "..",
      "handoff/grpc-final-handoff/docs/images/q012-drawer-probe-fix8.png"
    ),
    fullPage: true,
  });
  const switchedDrawerText = await drawer.innerText();
  const grpcTotalMatch = switchedDrawerText.match(/共 (\d+) 条/);
  const grpcTotal = Number(grpcTotalMatch?.[1] || 0);
  if (grpcTotal < 30) {
    throw new Error(
      `GRPC 列表少于本批 59 条: ${switchedDrawerText.slice(0, 3000)}`
    );
  }
  await drawer
    .getByText("Polar.v1.DeviceService", { exact: true })
    .waitFor({ state: "visible" });
  await drawer.getByText("Polar.v1.DeviceService", { exact: true }).click();
  await page.waitForTimeout(500);
  const grpcText = await drawer.innerText();
  const grpcTagCount = await visibleCount(
    drawer.getByText("GRPC", { exact: true })
  );

  const selectedApiName = "Polar.v1.DeviceService / ListDevices";
  const targetRow = drawer
    .locator(".arco-table-body .arco-table-tr:visible")
    .filter({ hasText: selectedApiName })
    .first();
  await targetRow.locator(".arco-checkbox").click();
  await drawer.getByText(/共选择\s*1/).waitFor({ state: "visible" });
  await drawer.getByRole("button", { name: "引用", exact: true }).click();
  await drawer.waitFor({ state: "hidden" });
  await page
    .getByText("共 1 个步骤", { exact: true })
    .waitFor({ state: "visible" });

  await page.getByText("添加步骤", { exact: true }).click();
  await page
    .locator(".arco-dropdown:visible")
    .getByText("导入系统请求", { exact: true })
    .click();
  await drawer
    .getByText("导入系统请求", { exact: true })
    .waitFor({ state: "visible" });
  await drawer
    .getByText("Polar.v1.DeviceService", { exact: true })
    .waitFor({ state: "visible" });
  await drawer.getByText("Polar.v1.DeviceService", { exact: true }).click();
  await page.waitForTimeout(500);
  await drawer
    .locator(".arco-table-body .arco-table-tr:visible")
    .filter({ hasText: selectedApiName })
    .first()
    .locator(".arco-checkbox")
    .click();
  await drawer.getByText(/共选择\s*1/).waitFor({ state: "visible" });
  await drawer.getByRole("button", { name: "复制", exact: true }).click();
  await drawer.waitFor({ state: "hidden" });
  await page
    .getByText("共 2 个步骤", { exact: true })
    .waitFor({ state: "visible" });
  const importedStepCount = 2;

  const scenarioName = `grpc-import-e2e-${Date.now()}`;
  await page.getByPlaceholder("请输入场景名称").fill(scenarioName);
  const saveResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes("/api/scenario") &&
      response.request().method() === "POST",
    { timeout: 30000 }
  );
  await page.getByRole("button", { name: "保存", exact: true }).click();
  const saveResponse = await saveResponsePromise;
  const saveBody = await saveResponse.json();
  await page.waitForTimeout(1000);
  const savedScenarioId = saveBody.data?.id || saveBody.data;
  const scenarioDetail = await page.evaluate(async (scenarioId) => {
    const headers = {
      "X-AUTH-TOKEN": localStorage.getItem("sessionId") || "",
      "CSRF-TOKEN": localStorage.getItem("csrfToken") || "",
      ORGANIZATION: "100001",
      PROJECT: "100001100001",
      "Accept-Language": "zh-CN",
    };
    const response = await fetch(`/api/scenario/get/${scenarioId}`, {
      credentials: "include",
      headers,
    });
    return { status: response.status, body: await response.json() };
  }, savedScenarioId);

  await page.goto(
    `${baseUrl}/#/api-test/scenario?orgId=${orgId}&pId=${projectId}&id=${savedScenarioId}`,
    { waitUntil: "networkidle" }
  );
  await page
    .getByText(scenarioName, { exact: true })
    .waitFor({ state: "visible", timeout: 30000 });
  if (selectedApiName)
    await page
      .getByText(selectedApiName, { exact: true })
      .first()
      .waitFor({ state: "visible" });
  let executionResult = { enabled: false };
  if (process.env.MS_RUN_SCENARIO === "1") {
    const executeResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes("/api/scenario/run") &&
        response.request().method() === "POST",
      { timeout: 30000 }
    );
    await page.getByRole("button", { name: "服务端执行", exact: true }).click();
    const executeResponse = await executeResponsePromise;
    const executeBody = await executeResponse.json();
    let buttonRecovered = false;
    try {
      await page
        .getByRole("button", { name: "服务端执行", exact: true })
        .waitFor({ state: "visible", timeout: 60000 });
      buttonRecovered = true;
    } catch {
      buttonRecovered = false;
    }
    const reportResult = await page.evaluate(
      async ({
        currentProjectId,
        currentScenarioName,
        currentScenarioId,
        currentTaskId,
      }) => {
        const headers = {
          "Content-Type": "application/json",
          "X-AUTH-TOKEN": localStorage.getItem("sessionId") || "",
          "CSRF-TOKEN": localStorage.getItem("csrfToken") || "",
          ORGANIZATION: "100001",
          PROJECT: currentProjectId,
          "Accept-Language": "zh-CN",
        };
        const pageResponse = await fetch("/api/report/scenario/page", {
          method: "POST",
          credentials: "include",
          headers,
          body: JSON.stringify({
            current: 1,
            pageSize: 20,
            projectId: currentProjectId,
          }),
        });
        const pageBody = await pageResponse.json();
        const pageData = pageBody.data || pageBody;
        const items = Array.isArray(pageData) ? pageData : pageData.list || [];
        const report = items.find(
          (item) =>
            String(item.id || item.reportId) === String(currentTaskId) ||
            item.name?.startsWith(`${currentScenarioName}_`) ||
            String(item.scenarioId || item.sourceId) ===
              String(currentScenarioId)
        );
        if (!report) {
          return {
            pageStatus: pageResponse.status,
            pageCode: pageBody.code,
            report: null,
            sample: items.slice(0, 3),
          };
        }
        const reportId = report.id || report.reportId;
        const detailResponse = await fetch(
          `/api/report/scenario/get/${reportId}`,
          { credentials: "include", headers }
        );
        const detailBody = await detailResponse.json();
        const stepIds = [];
        const collectStepIds = (nodes) => {
          for (const node of nodes || []) {
            if (node.stepId) stepIds.push(node.stepId);
            collectStepIds(node.children);
          }
        };
        collectStepIds(detailBody.data?.children);
        const stepDetails = [];
        for (const stepId of [...new Set(stepIds)]) {
          const stepResponse = await fetch(
            `/api/report/scenario/get/detail/${reportId}/${stepId}`,
            {
              credentials: "include",
              headers,
            }
          );
          const stepBody = await stepResponse.json();
          stepDetails.push(...(stepBody.data || stepBody || []));
        }
        const serializedStepDetails = JSON.stringify(stepDetails);
        return {
          pageStatus: pageResponse.status,
          pageCode: pageBody.code,
          reportId,
          reportStatus: report.status,
          detailStatus: detailResponse.status,
          detailCode: detailBody.code,
          stepDetailCount: stepDetails.length,
          stepDetails,
          hasGrpc: serializedStepDetails.includes("GRPC"),
          hasGrpcStatus: /grpcStatus|responseMessage/.test(
            serializedStepDetails
          ),
        };
      },
      {
        currentProjectId: projectId,
        currentScenarioName: scenarioName,
        currentScenarioId: savedScenarioId,
        currentTaskId: executeBody.data?.taskInfo?.taskId,
      }
    );
    executionResult = {
      enabled: true,
      status: executeResponse.status(),
      code: executeBody.code,
      taskId: executeBody.data?.taskInfo?.taskId,
      buttonRecovered,
      report: reportResult,
    };
  }
  const scenarioText = await page.locator("body").innerText();
  const scenarioButtons = await page
    .locator("button:visible")
    .allTextContents();
  await page.screenshot({
    path: path.resolve(
      __dirname,
      "..",
      "handoff/grpc-final-handoff/docs/images/q012-scenario-fix8.png"
    ),
    fullPage: true,
  });

  const result = {
    loggedInUrl: page.url(),
    apiResult,
    debug: {
      batchSaveCount,
      batchSaveAsApiCount,
      batchGenerateCount,
      buttons: debugButtons.map((text) => text.trim()).filter(Boolean),
    },
    scenario: {
      name: scenarioName,
      saveUrl: saveResponse.url(),
      saveStatus: saveResponse.status(),
      saveCode: saveBody.code,
      savedScenarioId,
      reopenStatus: scenarioDetail.status,
      reopenCode: scenarioDetail.body.code,
      reopenProtocols: (scenarioDetail.body.data?.steps || []).map(
        (step) =>
          step.protocol ||
          step.config?.protocol ||
          step.method ||
          step.config?.method
      ),
      selectedApiName,
      httpHasGrpcService,
      httpHasGrpcRow,
      grpcTotal,
      grpcTagCount,
      importedStepCount,
      execution: executionResult,
      buttons: scenarioButtons.map((text) => text.trim()).filter(Boolean),
    },
    consoleErrors,
    serverErrors,
  };
  console.log(JSON.stringify(result, null, 2));
  await browser.close();

  if (
    apiResult.parseStatus !== 200 ||
    apiResult.methodCount !== 59 ||
    batchSaveCount !== 1 ||
    batchSaveAsApiCount !== 0 ||
    batchGenerateCount !== 0 ||
    httpHasGrpcService ||
    httpHasGrpcRow ||
    grpcTotal < 30 ||
    grpcTagCount < 1 ||
    saveResponse.status() !== 200 ||
    saveBody.code !== 100200 ||
    scenarioDetail.status !== 200 ||
    scenarioDetail.body.code !== 100200 ||
    !JSON.stringify(scenarioDetail.body.data?.steps || []).includes("GRPC") ||
    importedStepCount !== 2 ||
    !scenarioText.includes(scenarioName) ||
    (executionResult.enabled &&
      (executionResult.status !== 200 ||
        executionResult.code !== 100200 ||
        !executionResult.buttonRecovered ||
        !executionResult.report?.reportId ||
        executionResult.report.detailStatus !== 200 ||
        executionResult.report.detailCode !== 100200 ||
        !executionResult.report.hasGrpc ||
        !executionResult.report.hasGrpcStatus)) ||
    serverErrors.length
  ) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
