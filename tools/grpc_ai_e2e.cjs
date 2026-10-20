const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const baseUrl = process.env.MS_BASE_URL || "http://127.0.0.1:8085";
const projectId = process.env.MS_PROJECT_ID || "100001100001";
const orgId = process.env.MS_ORG_ID || "100001";
const preferredDefinitionId =
  process.env.MS_GRPC_DEFINITION_ID || "1599332176195829760";
const outputDir = path.resolve(
  __dirname,
  "..",
  "handoff/grpc-final-handoff/docs/images/question_records/q014"
);

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
        ...(requestOptions.body ? { "Content-Type": "application/json" } : {}),
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

function dataOf(response) {
  return response.body?.data ?? response.body;
}

function listOf(data) {
  if (Array.isArray(data)) return data;
  return data?.list || data?.records || [];
}

function postBody(value) {
  return { method: "POST", body: JSON.stringify(value) };
}

function requestTemplate(grpcDefinition) {
  for (const service of grpcDefinition.services || []) {
    const serviceName = service.fullName || service.name;
    if (serviceName !== grpcDefinition.serviceName) continue;
    const method = (service.methods || []).find(
      (item) => item.name === grpcDefinition.methodName
    );
    if (method?.requestJsonTemplate) return method.requestJsonTemplate;
  }
  return "{}";
}

async function selectDefinition(page) {
  const pageResponse = await api(
    page,
    "/api/definition/page",
    postBody({
      current: 1,
      pageSize: 200,
      projectId,
      protocols: ["GRPC"],
      deleted: false,
    })
  );
  if (pageResponse.status !== 200 || pageResponse.body?.code !== 100200) {
    throw new Error(`gRPC definition page failed: ${JSON.stringify(pageResponse)}`);
  }
  const definitions = listOf(dataOf(pageResponse));
  const ordered = [...definitions].sort((left, right) => {
    if (String(left.id) === preferredDefinitionId) return -1;
    if (String(right.id) === preferredDefinitionId) return 1;
    return 0;
  });
  let fallback;
  for (const definition of ordered) {
    const detailResponse = await api(
      page,
      `/api/definition/get-detail/${definition.id}`
    );
    const detail = dataOf(detailResponse);
    const grpc = detail?.grpcDefinition;
    if (
      detailResponse.status !== 200 ||
      detailResponse.body?.code !== 100200 ||
      !grpc?.serverAddress ||
      !grpc?.descriptorContent ||
      !grpc?.serviceName ||
      !grpc?.methodName ||
      grpc?.invokable === false
    ) {
      continue;
    }
    const candidate = { definition: detail, grpc, detailResponse };
    if (grpc.serverAddress === "host.docker.internal:50052") return candidate;
    fallback ||= candidate;
  }
  if (!fallback) throw new Error("No executable gRPC definition with an address was found");
  return fallback;
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
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

  try {
    await login(page);
    consoleErrors.length = 0;
    serverErrors.length = 0;

    const selected = await selectDefinition(page);
    const caseName = `grpc-ai-e2e-${Date.now()}`;
    const prompt = [
      "apiCaseStart",
      `## \u7528\u4f8b\u540d\u79f0: ${caseName}`,
      `## serviceName: ${selected.grpc.serviceName}`,
      `## methodName: ${selected.grpc.methodName}`,
      "## requestJson",
      "```json",
      requestTemplate(selected.grpc),
      "```",
      "## assertionConfig",
      "```json",
      '{"items":[]}',
      "```",
      "## extractConfig",
      "```json",
      '{"items":[]}',
      "```",
      "apiCaseEnd",
    ].join("\n");
    const aiRequest = {
      apiDefinitionId: selected.definition.id,
      prompt,
    };

    const transformResponse = await api(
      page,
      "/api/case/ai/transform",
      postBody(aiRequest)
    );
    const transformed = dataOf(transformResponse);
    const saveResponse = await api(
      page,
      "/api/case/ai/batch/save",
      postBody(aiRequest)
    );
    const saveResult = dataOf(saveResponse);
    const casePageResponse = await api(
      page,
      "/api/case/page",
      postBody({
        current: 1,
        pageSize: 100,
        projectId,
        apiDefinitionId: selected.definition.id,
        protocols: ["GRPC"],
      })
    );
    const savedCase = listOf(dataOf(casePageResponse)).find(
      (item) => item.name === caseName
    );
    if (!savedCase) throw new Error(`Saved AI case was not found: ${caseName}`);

    const detailResponse = await api(
      page,
      `/api/case/get-detail/${savedCase.id}`
    );
    const detail = dataOf(detailResponse);
    const runResponse = await api(page, `/api/case/run/${savedCase.id}`);
    const runResult = dataOf(runResponse);

    await page.goto(
      `${baseUrl}/#/api-test/management?orgId=${orgId}&pId=${projectId}&id=${selected.definition.id}`,
      { waitUntil: "networkidle" }
    );
    await page.screenshot({
      path: path.join(outputDir, "grpc-ai-case.png"),
      fullPage: true,
    });

    const result = {
      definition: {
        id: selected.definition.id,
        name: selected.definition.name,
        serviceName: selected.grpc.serviceName,
        methodName: selected.grpc.methodName,
        serverAddress: selected.grpc.serverAddress,
        protoId: selected.grpc.protoId,
        descriptorLength: String(selected.grpc.descriptorContent).length,
      },
      transform: {
        status: transformResponse.status,
        code: transformResponse.body?.code,
        protocol: transformed?.protocol,
        name: transformed?.name,
        serverAddress: transformed?.grpcCaseConfig?.serverAddress,
        protoId: transformed?.grpcCaseConfig?.protoId,
        mainFileName: transformed?.grpcCaseConfig?.mainFileName,
        protoFileCount: transformed?.grpcCaseConfig?.protoFiles?.length || 0,
        descriptorLength: String(
          transformed?.grpcCaseConfig?.descriptorContent || ""
        ).length,
      },
      save: {
        status: saveResponse.status,
        code: saveResponse.body?.code,
        successCount: saveResult?.successCount,
        errorCount: saveResult?.errorCount,
        errorDetail: saveResult?.errorDetail,
      },
      reopen: {
        pageStatus: casePageResponse.status,
        pageCode: casePageResponse.body?.code,
        detailStatus: detailResponse.status,
        detailCode: detailResponse.body?.code,
        caseId: savedCase.id,
        protocol: detail?.protocol,
        aiCreate: detail?.aiCreate,
        serverAddress: detail?.grpcCaseConfig?.serverAddress,
        protoId: detail?.grpcCaseConfig?.protoId,
        mainFileName: detail?.grpcCaseConfig?.mainFileName,
        protoFileCount: detail?.grpcCaseConfig?.protoFiles?.length || 0,
        descriptorLength: String(
          detail?.grpcCaseConfig?.descriptorContent || ""
        ).length,
        requestJson: detail?.grpcCaseConfig?.requestJson,
      },
      run: {
        status: runResponse.status,
        code: runResponse.body?.code,
        success: runResult?.success,
        grpcStatus: runResult?.grpcStatus,
        grpcCode: runResult?.grpcCode,
        errorCode: runResult?.errorCode,
        errorMessage: runResult?.errorMessage,
        reportId: runResult?.reportId,
      },
      consoleErrors,
      serverErrors,
    };
    fs.writeFileSync(
      path.join(outputDir, "grpc-ai-diagnostic.json"),
      JSON.stringify(result, null, 2)
    );
    console.log(JSON.stringify(result, null, 2));

    const platformErrorCodes = new Set([
      "GRPC_ADDRESS_INVALID",
      "GRPC_CASE_CONFIG_NOT_FOUND",
      "GRPC_DEFINITION_NOT_FOUND",
      "GRPC_PROTO_COMPILE_FAILED",
    ]);
    if (
      transformResponse.status !== 200 ||
      transformResponse.body?.code !== 100200 ||
      transformed?.protocol !== "GRPC" ||
      transformed?.grpcCaseConfig?.serverAddress !==
        selected.grpc.serverAddress ||
      !transformed?.grpcCaseConfig?.descriptorContent ||
      saveResponse.status !== 200 ||
      saveResponse.body?.code !== 100200 ||
      saveResult?.successCount !== 1 ||
      saveResult?.errorCount !== 0 ||
      casePageResponse.status !== 200 ||
      casePageResponse.body?.code !== 100200 ||
      detailResponse.status !== 200 ||
      detailResponse.body?.code !== 100200 ||
      detail?.protocol !== "GRPC" ||
      detail?.aiCreate !== true ||
      detail?.grpcCaseConfig?.serverAddress !== selected.grpc.serverAddress ||
      !detail?.grpcCaseConfig?.descriptorContent ||
      runResponse.status !== 200 ||
      runResponse.body?.code !== 100200 ||
      !runResult?.reportId ||
      platformErrorCodes.has(runResult?.errorCode) ||
      consoleErrors.length > 0 ||
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
