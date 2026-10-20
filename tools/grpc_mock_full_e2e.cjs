const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const baseUrl = process.env.MS_BASE_URL || "http://127.0.0.1:8085";
const projectId = process.env.MS_PROJECT_ID || "100001100001";
const orgId = process.env.MS_ORG_ID || "100001";
const serverAddress =
  process.env.MS_GRPC_MOCK_ADDRESS || "host.docker.internal:50052";
const outputDir = path.resolve(
  __dirname,
  "..",
  "handoff/grpc-final-handoff/docs/images/question_records/q014"
);

const labels = {
  request: "\u8bf7\u6c42\u6d88\u606f",
  metadata: "\u5143\u6570\u636e",
  definition: "\u670d\u52a1\u5b9a\u4e49",
  pre: "\u524d\u7f6e",
  post: "\u540e\u7f6e",
  settings: "\u8bbe\u7f6e",
  execute: "\u670d\u52a1\u7aef\u6267\u884c",
};

function protoFile(fileName, relativePath, main = false) {
  return {
    fileName,
    content: fs.readFileSync(
      path.resolve(__dirname, "..", relativePath),
      "utf8"
    ),
    main,
  };
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

async function browserPost(page, url, body) {
  return page.evaluate(
    async ({ requestUrl, requestBody, currentProjectId, currentOrgId }) => {
      const response = await fetch(requestUrl, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-AUTH-TOKEN": localStorage.getItem("sessionId") || "",
          "CSRF-TOKEN": localStorage.getItem("csrfToken") || "",
          ORGANIZATION: currentOrgId,
          PROJECT: currentProjectId,
          "Accept-Language": "zh-CN",
        },
        body: JSON.stringify(requestBody),
      });
      const text = await response.text();
      let responseBody;
      try {
        responseBody = JSON.parse(text);
      } catch {
        responseBody = text;
      }
      return { status: response.status, body: responseBody };
    },
    {
      requestUrl: url,
      requestBody: body,
      currentProjectId: projectId,
      currentOrgId: orgId,
    }
  );
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const files = [
    protoFile(
      "scenario_main.proto",
      "handoff/grpc-final-handoff/test-fixtures/proto/scenario_main.proto",
      true
    ),
    protoFile(
      "scenario_common.proto",
      "handoff/grpc-final-handoff/test-fixtures/proto/scenario_common.proto"
    ),
  ];
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CHROME_PATH ||
      "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  });
  const context = await browser.newContext({
    viewport: { width: 1900, height: 1000 },
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

  let result;
  try {
    await login(page);
    consoleErrors.length = 0;
    serverErrors.length = 0;

    const parseResponse = await browserPost(page, "/api/grpc/debug/parse", {
      protoContent: files[0].content,
      fileName: files[0].fileName,
      mainFileName: files[0].fileName,
      protoFiles: files,
    });
    const parsed = parseResponse.body.data || parseResponse.body;
    const methods = (parsed.services || []).flatMap((service) =>
      (service.methods || [])
        .filter(
          (method) =>
            method.invokable !== false && method.methodType === "UNARY"
        )
        .map((method) => ({ service, method }))
    );
    const methodFullNames = methods.map(
      ({ service, method }) =>
        method.fullName || `${service.fullName || service.name}/${method.name}`
    );

    const batchRequest = {
      projectId,
      parentModuleId: "root",
      serverAddress,
      protoContent: files[0].content,
      fileName: files[0].fileName,
      mainFileName: files[0].fileName,
      protoFiles: files,
      methodFullNames,
    };
    const firstBatchResponse = await browserPost(
      page,
      "/api/debug/grpc/batch-save",
      batchRequest
    );
    const firstBatch = firstBatchResponse.body.data || firstBatchResponse.body;
    const savedItem = firstBatch.items?.find(
      (item) => item.apiDebugId && item.action !== "FAILED"
    );
    if (!savedItem)
      throw new Error("Mock batch save returned no usable debug request");

    await page.goto(
      `${baseUrl}/#/api-test/debug?orgId=${orgId}&pId=${projectId}&id=${savedItem.apiDebugId}`,
      { waitUntil: "networkidle" }
    );
    await page
      .locator(".request-composition:visible")
      .waitFor({ state: "visible", timeout: 30000 });
    const tabResults = {};
    for (const [key, label] of Object.entries(labels).filter(
      ([key]) => key !== "execute"
    )) {
      const tab = page.getByText(label, { exact: true }).first();
      await tab.waitFor({ state: "visible", timeout: 30000 });
      await tab.click();
      tabResults[key] = await tab.isVisible();
    }
    await page.getByText(labels.request, { exact: true }).first().click();
    const uiSendPromise = page.waitForResponse(
      (response) =>
        response.url().includes("/api/grpc/debug/send") &&
        response.request().method() === "POST",
      { timeout: 30000 }
    );
    await page
      .getByRole("button", { name: labels.execute, exact: true })
      .click();
    const uiSendResponse = await uiSendPromise;
    const uiSendBody = await uiSendResponse.json();
    await page.waitForTimeout(500);
    await page.screenshot({
      path: path.join(outputDir, "mock-ui-execute.png"),
      fullPage: true,
    });

    const invokes = [];
    for (const { service, method } of methods) {
      const response = await browserPost(page, "/api/grpc/debug/send", {
        serverAddress,
        descriptorContent: parsed.descriptorContent,
        serviceName: service.fullName || service.name,
        methodName: method.name,
        requestJson: method.requestJsonTemplate || "{}",
        metadata: [{ key: "x-ms-e2e", value: "q014", enabled: true }],
        timeout: 5000,
        connectTimeout: 3000,
        maxResponseSizeMb: 10,
        includeDefaultValues: true,
        useTls: false,
        verifyServerCertificate: true,
      });
      const body = response.body.data || response.body;
      invokes.push({
        fullMethodName: method.fullName,
        httpStatus: response.status,
        code: response.body.code,
        success: body.success,
        grpcStatus: body.grpcStatus,
        grpcCode: body.grpcCode,
        errorCode: body.errorCode,
        responseJson: body.responseJson,
      });
    }
    const secondBatchResponse = await browserPost(
      page,
      "/api/debug/grpc/batch-save",
      batchRequest
    );
    const secondBatch =
      secondBatchResponse.body.data || secondBatchResponse.body;

    result = {
      parse: {
        status: parseResponse.status,
        code: parseResponse.body.code,
        serviceCount: parsed.services?.length || 0,
        methodCount: methods.length,
        descriptorLength: String(parsed.descriptorContent || "").length,
      },
      firstBatch: {
        status: firstBatchResponse.status,
        code: firstBatchResponse.body.code,
        createdCount: firstBatch.createdCount,
        updatedCount: firstBatch.updatedCount,
        failedCount: firstBatch.failedCount,
        itemCount: firstBatch.items?.length || 0,
      },
      ui: {
        debugId: savedItem.apiDebugId,
        tabs: tabResults,
        sendStatus: uiSendResponse.status(),
        sendCode: uiSendBody.code,
        sendResult: uiSendBody.data || uiSendBody,
      },
      invokes,
      secondBatch: {
        status: secondBatchResponse.status,
        code: secondBatchResponse.body.code,
        createdCount: secondBatch.createdCount,
        updatedCount: secondBatch.updatedCount,
        failedCount: secondBatch.failedCount,
        itemCount: secondBatch.items?.length || 0,
      },
      consoleErrors,
      serverErrors,
    };
    fs.writeFileSync(
      path.join(outputDir, "mock-full-diagnostic.json"),
      JSON.stringify(result, null, 2)
    );
    console.log(JSON.stringify(result, null, 2));

    const allTabsVisible = Object.values(tabResults).every(Boolean);
    const allInvokesPassed =
      invokes.length === 10 && invokes.every((item) => item.success === true);
    if (
      parseResponse.status !== 200 ||
      parseResponse.body.code !== 100200 ||
      parsed.services?.length !== 6 ||
      methods.length !== 10 ||
      !parsed.descriptorContent ||
      firstBatchResponse.status !== 200 ||
      firstBatchResponse.body.code !== 100200 ||
      firstBatch.failedCount !== 0 ||
      firstBatch.items?.length !== 10 ||
      !allTabsVisible ||
      uiSendResponse.status() !== 200 ||
      uiSendBody.code !== 100200 ||
      (uiSendBody.data || uiSendBody).success !== true ||
      !allInvokesPassed ||
      secondBatchResponse.status !== 200 ||
      secondBatchResponse.body.code !== 100200 ||
      secondBatch.createdCount !== 0 ||
      secondBatch.updatedCount !== 10 ||
      secondBatch.failedCount !== 0 ||
      serverErrors.length > 0
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
