import { cloneDeep } from 'lodash-es';

import MSR from '@/api/http/index';
import { GrpcDebugParseUrl } from '@/api/requrls/api-test/grpc';

import type { EnableKeyValueParam, RequestResult } from '@/models/apiTest/common';
import type {
  GrpcAssertionCondition,
  GrpcAssertionItem,
  GrpcCaseAssertionConfig,
  GrpcCaseConfigAddParams,
  GrpcCaseConfigDetail,
  GrpcCaseDebugRequest,
  GrpcCaseExtractConfig,
  GrpcCaseReportContent,
  GrpcDefinitionAddParams,
  GrpcDefinitionDetail,
  GrpcExtractItem,
  GrpcInvokeResult,
  GrpcMetadataItem,
  GrpcMethodInfo,
  GrpcParseProtoParams,
  GrpcParseProtoResult,
  GrpcProtoVersionInfo,
  GrpcRequestState,
  GrpcServiceInfo,
  GrpcSettingsParams,
  GrpcUploadedMainProto,
} from '@/models/apiTest/grpc';
import { GRPC_PROTOCOL } from '@/models/apiTest/grpc';
import type CommonResponse from '@/models/common';
import { RequestComposition } from '@/enums/apiEnum';

import { defaultHeaderParamsItem } from '@/views/api-test/components/config';
import { filterKeyValParams } from '@/views/api-test/components/utils';
import type { AxiosResponse } from 'axios';

export const DEFAULT_GRPC_TIMEOUT = 60000;

export interface GrpcListRecord {
  protocol?: string;
  method?: string;
  path?: string;
  grpcDefinition?: GrpcDefinitionDetail;
  serviceName?: string;
  methodName?: string;
  fullMethodName?: string;
}

export interface GrpcParseErrorInfo {
  message: string;
  messageDetail?: string;
  code?: number;
  missingImports: string[];
}

const MISSING_PROTO_IMPORT_PATTERNS = [
  /missing import proto file:\s*([^\s;,\n]+)/gi,
  /File not found:\s*([^\s;,\n]+?\.proto)/gi,
  /([^\s;,\n]+?\.proto):\s*File not found/gi,
  /Import\s+"([^"]+\.proto)"\s+was not found/gi,
];

export function extractMissingProtoImports(text: string): string[] {
  if (!text?.trim()) {
    return [];
  }
  const found = new Set<string>();
  MISSING_PROTO_IMPORT_PATTERNS.forEach((pattern) => {
    pattern.lastIndex = 0;
    let match = pattern.exec(text);
    while (match) {
      const importPath = match[1]?.trim();
      if (importPath) {
        found.add(importPath);
      }
      match = pattern.exec(text);
    }
  });
  return [...found];
}

export function extractNonWellKnownProtoImports(protoContent: string): string[] {
  if (!protoContent?.trim()) {
    return [];
  }
  const found = new Set<string>();
  const pattern = /import\s+"([^"]+)"/g;
  let match = pattern.exec(protoContent);
  while (match) {
    const importPath = match[1]?.trim();
    if (importPath && !importPath.startsWith('google/protobuf/')) {
      found.add(importPath);
    }
    match = pattern.exec(protoContent);
  }
  return [...found];
}

export function resolveGrpcParseMissingImports(info: GrpcParseErrorInfo, protoContent?: string): string[] {
  if (info.missingImports.length) {
    return info.missingImports;
  }
  if (info.messageDetail) {
    return [];
  }
  return extractNonWellKnownProtoImports(protoContent || '');
}

export function extractGrpcParseErrorInfo(error: unknown): GrpcParseErrorInfo {
  if (!error) {
    return { message: '', missingImports: [] };
  }
  if (typeof error === 'string') {
    return {
      message: error,
      missingImports: extractMissingProtoImports(error),
    };
  }
  if (typeof error === 'object') {
    const err = error as Record<string, any>;
    const message = err.message || err.msg || err.errorMessage || '';
    const messageDetail = err.messageDetail || '';
    const detailSource = messageDetail || message;
    return {
      message,
      messageDetail: messageDetail || undefined,
      code: typeof err.code === 'number' ? err.code : undefined,
      missingImports: extractMissingProtoImports(detailSource),
    };
  }
  const message = String(error);
  return {
    message,
    missingImports: extractMissingProtoImports(message),
  };
}

export function extractApiErrorMessage(error: unknown): string {
  return extractGrpcParseErrorInfo(error).message;
}

export async function parseGrpcProtoWithErrorDetail(data: GrpcParseProtoParams): Promise<GrpcParseProtoResult> {
  try {
    const response = await MSR.post<CommonResponse<GrpcParseProtoResult>>(
      { url: GrpcDebugParseUrl, data },
      { isReturnNativeResponse: true, errorMessageMode: 'none' }
    );
    const axiosRes = response as unknown as AxiosResponse<CommonResponse<GrpcParseProtoResult>>;
    return axiosRes.data.data;
  } catch (error) {
    throw extractGrpcParseErrorInfo(error);
  }
}

export function validateRequestJson(requestJson: string): { valid: boolean; message?: string } {
  const trimmed = requestJson?.trim();
  if (!trimmed) {
    return { valid: true };
  }
  try {
    JSON.parse(trimmed);
    return { valid: true };
  } catch (error) {
    return {
      valid: false,
      message: extractApiErrorMessage(error) || 'Invalid JSON',
    };
  }
}

export function buildGrpcMetadata(metadata: EnableKeyValueParam[]) {
  return filterKeyValParams(metadata, defaultHeaderParamsItem, true)
    .validParams.filter((item) => item.key)
    .map((item) => ({
      key: item.key,
      value: item.value,
    }));
}

export function resolveMethodInvokable(methodType?: string, invokable?: boolean) {
  if (typeof invokable === 'boolean') {
    return invokable;
  }
  return methodType === 'UNARY';
}

const PROTO_IMPORT_PATTERN = /^\s*import\s+"([^"]+)"\s*;/gm;

export function normalizeProtoRelativePath(path: string): string {
  let normalized = path.replace(/\\/g, '/').trim().replace(/^\/+/, '');
  if (normalized.toLowerCase().startsWith('proto/')) {
    normalized = normalized.slice('proto/'.length);
  }
  return normalized;
}

export function extractProtoImports(protoContent: string): string[] {
  if (!protoContent?.trim()) {
    return [];
  }
  const imports: string[] = [];
  PROTO_IMPORT_PATTERN.lastIndex = 0;
  let match = PROTO_IMPORT_PATTERN.exec(protoContent);
  while (match) {
    imports.push(normalizeProtoRelativePath(match[1]));
    match = PROTO_IMPORT_PATTERN.exec(protoContent);
  }
  return imports;
}

export function findMatchingProtoImportPath(protoContent: string, baseFileName: string): string | undefined {
  const normalizedBase = baseFileName.replace(/\\/g, '/');
  const baseName = normalizedBase.includes('/') ? normalizedBase.split('/').pop()! : normalizedBase;
  const matches = extractProtoImports(protoContent).filter(
    (importPath) => importPath === baseName || importPath.endsWith(`/${baseName}`)
  );
  return matches.length === 1 ? matches[0] : undefined;
}

export function resolveProtoUploadFileName(file: File, state?: GrpcRequestState): string {
  const relativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath?.trim();
  if (relativePath) {
    return normalizeProtoRelativePath(relativePath);
  }
  const importPath = state?.protoContent ? findMatchingProtoImportPath(state.protoContent, file.name) : undefined;
  if (importPath) {
    return importPath;
  }
  const matchedFromBundle = (state?.protoFiles || [])
    .map((protoFile) => findMatchingProtoImportPath(protoFile.content, file.name))
    .find(Boolean);
  if (matchedFromBundle) {
    return matchedFromBundle;
  }
  return file.name;
}

export function formatGrpcProtoVersionLabel(version?: GrpcProtoVersionInfo, protoId?: string) {
  if (!version && !protoId) {
    return '';
  }
  const label = version?.versionLabel || '-';
  const fileName = version?.fileName || '';
  const hash = version?.contentHashShort || '';
  const parts = [fileName, label].filter(Boolean).join(' ');
  if (hash) {
    return `${parts} (${hash})`.trim();
  }
  if (protoId && !version) {
    return protoId;
  }
  return parts || protoId || '';
}

export function isRequestJsonEmpty(requestJson?: string) {
  const trimmed = requestJson?.trim();
  if (!trimmed) {
    return true;
  }
  try {
    const parsed = JSON.parse(trimmed);
    return parsed != null && typeof parsed === 'object' && !Array.isArray(parsed) && Object.keys(parsed).length === 0;
  } catch {
    return false;
  }
}

function findSelectedGrpcMethod(state: GrpcRequestState): GrpcMethodInfo | undefined {
  if (!state.serviceName || !state.methodName || !state.services?.length) {
    return undefined;
  }
  const service = state.services.find((item) => item.name === state.serviceName || item.fullName === state.serviceName);
  return service?.methods?.find((item) => item.name === state.methodName || item.fullName === state.methodName);
}

export function findGrpcMethodTemplate(state: GrpcRequestState): string | undefined {
  return findSelectedGrpcMethod(state)?.requestJsonTemplate;
}

export function applyRequestJsonTemplateIfEmpty(state: GrpcRequestState, force = false) {
  const template = findGrpcMethodTemplate(state);
  if (!template) {
    return false;
  }
  if (!force && !isRequestJsonEmpty(state.requestJson)) {
    return false;
  }
  state.requestJson = template;
  return true;
}

export function formatGrpcRequestJson(requestJson?: string): string {
  const trimmed = requestJson?.trim();
  if (!trimmed) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(trimmed), null, 2);
  } catch {
    return requestJson || '';
  }
}

export function calcGrpcResponseSize(response?: GrpcInvokeResult): string {
  if (!response?.responseJson) {
    return '-';
  }
  const bytes = new TextEncoder().encode(response.responseJson).length;
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  return `${(bytes / 1024).toFixed(1)} KB`;
}

export function buildGrpcMethodSelectOptions(state: GrpcRequestState) {
  return (state.services || []).flatMap((service) =>
    (service.methods || []).map((method) => ({
      label: `${service.fullName || service.name} / ${method.name}`,
      value: `${service.name}::${method.name}`,
      serviceName: service.name,
      methodName: method.name,
      methodType: method.methodType || '',
      serviceLabel: service.fullName || service.name,
    }))
  );
}

export function resolveGrpcMethodSelectValue(state: GrpcRequestState): string {
  if (!state.serviceName || !state.methodName) {
    return '';
  }
  return `${state.serviceName}::${state.methodName}`;
}

export function countGrpcServicesAndMethods(state: GrpcRequestState) {
  const services = state.services || [];
  const methodCount = services.reduce((sum, service) => sum + (service.methods?.length || 0), 0);
  return { serviceCount: services.length, methodCount };
}

/** Normalize legacy GRPC tab key to GRPC_MESSAGE; migrate GRPC_SCRIPTS to PRECONDITION */
export function normalizeGrpcActiveTab(tab: RequestComposition): RequestComposition {
  if (tab === RequestComposition.GRPC) {
    return RequestComposition.GRPC_MESSAGE;
  }
  if (tab === RequestComposition.GRPC_SCRIPTS) {
    return RequestComposition.PRECONDITION;
  }
  return tab;
}

export function syncMethodInfoFromServices(state: GrpcRequestState) {
  const method = findSelectedGrpcMethod(state);
  if (method) {
    state.methodType = method.methodType || state.methodType;
    state.invokable = resolveMethodInvokable(method.methodType, method.invokable);
    return;
  }
  state.invokable = resolveMethodInvokable(state.methodType, state.invokable);
}

export function applyGrpcMethodSelection(
  state: GrpcRequestState,
  serviceName: string,
  methodName: string,
  options?: { forceTemplate?: boolean }
) {
  state.serviceName = serviceName;
  state.methodName = methodName;
  syncMethodInfoFromServices(state);
  if (options?.forceTemplate) {
    const template = findGrpcMethodTemplate(state);
    if (template) {
      state.requestJson = template;
    }
  } else {
    applyRequestJsonTemplateIfEmpty(state);
  }
}

export function ensureGrpcServiceOptions(state: GrpcRequestState) {
  const options = (state.services || []).map((service: GrpcServiceInfo) => ({
    label: service.fullName || service.name,
    value: service.name,
  }));
  if (state.serviceName && !options.some((item) => item.value === state.serviceName)) {
    options.unshift({
      label: state.serviceName,
      value: state.serviceName,
    });
  }
  return options;
}

export function ensureGrpcMethodOptions(state: GrpcRequestState) {
  const service = state.services.find((item) => item.name === state.serviceName);
  const options = (service?.methods || []).map((method) => ({
    label: method.name,
    value: method.name,
  }));
  if (state.methodName && !options.some((item) => item.value === state.methodName)) {
    options.unshift({
      label: state.methodName,
      value: state.methodName,
    });
  }
  return options;
}

export function buildGrpcParseParams(state: GrpcRequestState): GrpcParseProtoParams {
  const files = (state.protoFiles || []).filter((item) => item.content?.trim());
  if (files.length > 1) {
    const mainFileName = state.mainFileName || state.fileName || files.find((item) => item.main)?.fileName;
    return {
      protoFiles: files.map((item) => ({
        fileName: item.fileName,
        content: item.content,
        main: item.main || item.fileName === mainFileName,
      })),
      mainFileName,
    };
  }
  const mainFile = files[0];
  return {
    protoContent: mainFile?.content || state.protoContent,
    fileName: mainFile?.fileName || state.fileName || undefined,
  };
}

export function isReflectionOnlyState(state: GrpcRequestState): boolean {
  const hasProtoSource =
    Boolean(state.protoContent?.trim()) || Boolean(state.protoFiles?.some((file) => file.content?.trim()));
  return Boolean(state.descriptorContent?.trim() && !hasProtoSource);
}

export function buildGrpcDefinitionSaveParams(state: GrpcRequestState): GrpcDefinitionAddParams {
  const reflectionOnly = isReflectionOnlyState(state);
  if (reflectionOnly) {
    return {
      serverAddress: state.serverAddress?.trim(),
      descriptorContent: state.descriptorContent,
      reflection: true,
      reflectionSourceType: 'REFLECTION',
      reflectionServerAddress: state.serverAddress?.trim(),
      reflectionUseTls: state.reflectionUseTls,
      reflectionServiceFullName: state.serviceName,
      reflectionDiscoveredAt: state.reflectionDiscoveredAt,
      serviceName: state.serviceName,
      methodName: state.methodName,
    };
  }
  const parseParams = buildGrpcParseParams(state);
  return {
    serverAddress: state.serverAddress?.trim(),
    protoContent: parseParams.protoContent || state.protoContent,
    fileName: parseParams.fileName || state.fileName || undefined,
    protoFiles: parseParams.protoFiles,
    mainFileName: parseParams.mainFileName,
    descriptorContent: state.descriptorContent || undefined,
    serviceName: state.serviceName,
    methodName: state.methodName,
  };
}

export function syncMainProtoContent(state: GrpcRequestState) {
  const mainName = state.mainFileName || state.fileName;
  const mainFile = state.protoFiles?.find((item) => item.fileName === mainName);
  if (mainFile) {
    mainFile.content = state.protoContent;
  }
}

export function ensureMainProtoFile(state: GrpcRequestState) {
  if (!state.protoFiles?.length && state.protoContent.trim()) {
    state.protoFiles = [
      {
        fileName: state.fileName || 'upload.proto',
        content: state.protoContent,
        main: true,
      },
    ];
    state.mainFileName = state.fileName || 'upload.proto';
  }
}

export function applyGrpcParseResult(state: GrpcRequestState, res: GrpcParseProtoResult) {
  state.packageName = res.packageName || '';
  state.descriptorContent = res.descriptorContent || '';
  state.services = res.services || [];
  if (!state.serviceName && state.services.length === 1) {
    state.serviceName = state.services[0].name;
  }
  if (state.serviceName && !state.methodName) {
    const service = state.services.find((item) => item.name === state.serviceName);
    if (service?.methods?.length === 1) {
      state.methodName = service.methods[0].name;
    }
  }
  syncMethodInfoFromServices(state);
  applyRequestJsonTemplateIfEmpty(state);
}

export function invalidateGrpcReflectionForAddressChange(state: GrpcRequestState): boolean {
  if (!state.reflectionDiscovered) {
    return false;
  }
  const currentAddress = state.serverAddress?.trim() || '';
  const sourceAddress = state.reflectionServerAddress?.trim();
  if (!sourceAddress) {
    state.reflectionServerAddress = currentAddress;
    return false;
  }
  if (sourceAddress === currentAddress) {
    return false;
  }

  state.packageName = '';
  state.descriptorContent = '';
  state.services = [];
  state.serviceName = '';
  state.methodName = '';
  state.methodType = '';
  state.invokable = false;
  state.requestJson = '{}';
  state.debugResponse = undefined;
  state.reflectionDiscovered = false;
  state.reflectionServerAddress = undefined;
  state.reflectionUseTls = false;
  state.reflectionDiscoveredAt = undefined;
  return true;
}

export function getGrpcListMethodLabel(record: GrpcListRecord) {
  if (record.protocol !== GRPC_PROTOCOL) {
    return record.method || '-';
  }
  if (record.grpcDefinition?.serviceName && record.grpcDefinition?.methodName) {
    return `${record.grpcDefinition.serviceName} / ${record.grpcDefinition.methodName}`;
  }
  if (record.serviceName && record.methodName) {
    return `${record.serviceName} / ${record.methodName}`;
  }
  return '-';
}

export function getGrpcListPathLabel(record: GrpcListRecord) {
  if (record.protocol !== GRPC_PROTOCOL) {
    return record.path || '-';
  }
  if (record.grpcDefinition?.fullMethodName) {
    return record.grpcDefinition.fullMethodName;
  }
  if (record.fullMethodName) {
    return record.fullMethodName;
  }
  return '-';
}

export function createGrpcInvokeErrorResult(error: unknown): GrpcInvokeResult {
  const message = extractApiErrorMessage(error);
  return {
    success: false,
    errorMessage: message,
  };
}

function extractGrpcResponseMetadata(grpcResult: GrpcInvokeResult): Record<string, string> {
  const md =
    (grpcResult as Record<string, unknown>).responseMetadata ?? (grpcResult as Record<string, unknown>).metadata;
  if (!md) {
    return {};
  }
  if (Array.isArray(md)) {
    return Object.fromEntries(
      md.map((item: Record<string, unknown>) => [String(item.key ?? item.name ?? '-'), String(item.value ?? '')])
    );
  }
  return md as Record<string, string>;
}

/** 将 gRPC 调试结果映射为 HTTP RequestResult，供 response 组件复用 */
export function mapGrpcInvokeResultToRequestResult(
  grpcResult?: GrpcInvokeResult,
  serverAddress?: string
): RequestResult | undefined {
  if (!grpcResult) {
    return undefined;
  }
  const responseJson = grpcResult.responseJson || '';
  const responseSize = responseJson ? new TextEncoder().encode(responseJson).length : 0;
  const metadata = extractGrpcResponseMetadata(grpcResult);
  const metadataHeaders = Object.entries(metadata)
    .map(([key, value]) => `${key}: ${value}`)
    .join('\n');
  const grpcCode = grpcResult.grpcCode ?? (grpcResult.success ? 0 : -1);
  return {
    body: responseJson,
    headers: metadataHeaders,
    url: serverAddress || '',
    method: GRPC_PROTOCOL,
    isSuccessful: grpcResult.success === true,
    responseResult: {
      body: responseJson,
      contentType: 'application/json',
      headers: metadataHeaders,
      responseCode: grpcCode,
      responseTime: grpcResult.duration ?? 0,
      responseSize,
      dnsLookupTime: 0,
      downloadTime: 0,
      latency: 0,
      socketInitTime: 0,
      sslHandshakeTime: 0,
      tcpHandshakeTime: 0,
      transferStartTime: 0,
      vars: '',
      extractResults: [],
      assertions: [],
    },
  };
}

export function formatGrpcInvokeResultRaw(grpcResult?: GrpcInvokeResult): string {
  if (!grpcResult) {
    return '';
  }
  try {
    return JSON.stringify(grpcResult, null, 2);
  } catch {
    return '';
  }
}

function snapshotCurrentMainProto(state: GrpcRequestState): GrpcUploadedMainProto | undefined {
  const fileName = state.mainFileName || state.fileName;
  if (!fileName || !state.protoContent?.trim()) {
    return undefined;
  }
  const dependencies = (state.protoFiles || []).filter((item) => !item.main && item.fileName !== fileName);
  return {
    fileName,
    content: state.protoContent,
    dependencies: dependencies.map((item) => ({ ...item })),
  };
}

/** 将当前主 Proto 快照写入 uploadedMainProtos */
export function upsertUploadedMainProto(state: GrpcRequestState) {
  const snapshot = snapshotCurrentMainProto(state);
  if (!snapshot) {
    return;
  }
  if (!state.uploadedMainProtos) {
    state.uploadedMainProtos = [];
  }
  const index = state.uploadedMainProtos.findIndex((item) => item.fileName === snapshot.fileName);
  if (index >= 0) {
    state.uploadedMainProtos[index] = snapshot;
  } else {
    state.uploadedMainProtos.unshift(snapshot);
  }
}

/** 从 uploadedMainProtos 加载指定主 Proto 到 state（不含解析） */
export function applyUploadedMainProto(state: GrpcRequestState, fileName: string) {
  const bundle = state.uploadedMainProtos?.find((item) => item.fileName === fileName);
  if (!bundle) {
    return;
  }
  state.mainFileName = bundle.fileName;
  state.fileName = bundle.fileName;
  state.protoContent = bundle.content;
  state.protoFiles = [
    { fileName: bundle.fileName, content: bundle.content, main: true },
    ...bundle.dependencies.map((item) => ({ ...item, main: false })),
  ];
}

export function removeUploadedMainProto(state: GrpcRequestState, fileName: string) {
  if (!state.uploadedMainProtos?.length) {
    return;
  }
  state.uploadedMainProtos = state.uploadedMainProtos.filter((item) => item.fileName !== fileName);
  const wasActive = (state.mainFileName || state.fileName) === fileName;
  if (!wasActive) {
    return;
  }
  const next = state.uploadedMainProtos[0];
  if (next) {
    applyUploadedMainProto(state, next.fileName);
  } else {
    state.protoContent = '';
    state.fileName = '';
    state.mainFileName = '';
    state.protoFiles = [];
    state.services = [];
    state.serviceName = '';
    state.methodName = '';
    state.descriptorContent = '';
  }
}

export function countDependencyProtos(state: GrpcRequestState): number {
  const mainName = state.mainFileName || state.fileName;
  return (state.protoFiles || []).filter((item) => item.fileName !== mainName && !item.main).length;
}

export function restoreGrpcDebugExecutableState(state: GrpcRequestState) {
  if (isReflectionOnlyState(state)) {
    state.reflectionDiscovered = true;
    state.reflectionServerAddress = state.serverAddress?.trim();
  }
  if (state.protoFiles?.length) {
    const mainName = state.mainFileName || state.fileName;
    const mainFile = state.protoFiles.find((item) => item.main || item.fileName === mainName);
    if (mainFile) {
      state.protoContent = mainFile.content;
      state.fileName = mainFile.fileName;
      state.mainFileName = mainFile.fileName;
    }
    upsertUploadedMainProto(state);
  } else if (state.protoContent?.trim()) {
    ensureMainProtoFile(state);
    upsertUploadedMainProto(state);
  }

  if (!state.descriptorContent?.trim() || !state.serviceName || !state.methodName) {
    return;
  }

  if (!state.services?.length) {
    state.services = [
      {
        name: state.serviceName,
        fullName: state.serviceName,
        methods: [
          {
            name: state.methodName,
            methodType: state.methodType || 'UNARY',
            invokable: true,
          },
        ],
      },
    ];
  }
  syncMethodInfoFromServices(state);
  if (!state.invokable) {
    state.invokable = resolveMethodInvokable(state.methodType, true);
  }
}

export function formatGrpcDebugSummary(response?: GrpcInvokeResult) {
  if (!response) {
    return '';
  }
  const parts = [
    response.grpcStatus ? `status=${response.grpcStatus}` : '',
    response.grpcCode != null ? `code=${response.grpcCode}` : '',
    response.errorCode ? `errorCode=${response.errorCode}` : '',
    response.errorMessage || '',
  ].filter(Boolean);
  return parts.join(' | ');
}

export function formatGrpcExecutionErrorMessage(errorCode?: string, errorMessage?: string) {
  if (errorCode === 'GRPC_BATCH_NO_ENABLED_STEPS') {
    return '场景步骤已禁用，本次未执行';
  }
  return errorMessage || '';
}

export function metadataToEnableKeyValue(metadata?: GrpcMetadataItem[]): EnableKeyValueParam[] {
  if (!metadata?.length) {
    return [cloneDeep(defaultHeaderParamsItem)];
  }
  return metadata.map((item) => ({
    ...cloneDeep(defaultHeaderParamsItem),
    key: item.key || '',
    value: item.value || '',
    enable: true,
  }));
}

export function createDefaultGrpcAssertionConfig(): GrpcCaseAssertionConfig {
  return { items: [] };
}

export function createDefaultGrpcExtractConfig(): GrpcCaseExtractConfig {
  return { items: [] };
}

export function createDefaultGrpcAssertionItem(): GrpcAssertionItem {
  return {
    type: 'JSON_PATH',
    name: '',
    expression: '',
    condition: 'EQUALS',
    expectedValue: '',
    enable: true,
  };
}

export function createDefaultGrpcExtractItem(): GrpcExtractItem {
  return {
    variableName: '',
    expression: '',
    enable: true,
  };
}

export function mapGrpcConditionToBackend(condition: GrpcAssertionCondition): string {
  if (condition === 'EXISTS') {
    return 'NOT_EMPTY';
  }
  if (condition === 'NOT_EXISTS') {
    return 'EMPTY';
  }
  return condition;
}

export function mapGrpcConditionFromBackend(condition?: string): GrpcAssertionCondition {
  if (condition === 'NOT_EMPTY') {
    return 'EXISTS';
  }
  if (condition === 'EMPTY') {
    return 'NOT_EXISTS';
  }
  return (condition as GrpcAssertionCondition) || 'EQUALS';
}

function serializeGrpcAssertionConfig(config?: GrpcCaseAssertionConfig): GrpcCaseAssertionConfig {
  return {
    items: (config?.items || []).map((item) => ({
      ...item,
      condition: mapGrpcConditionToBackend(item.condition) as GrpcAssertionCondition,
    })),
  };
}

function deserializeGrpcAssertionConfig(config?: GrpcCaseAssertionConfig): GrpcCaseAssertionConfig {
  if (!config?.items?.length) {
    return createDefaultGrpcAssertionConfig();
  }
  return {
    items: config.items.map((item) => ({
      ...item,
      condition: mapGrpcConditionFromBackend(item.condition as string),
      enable: item.enable !== false,
    })),
  };
}

function deserializeGrpcExtractConfig(config?: GrpcCaseExtractConfig): GrpcCaseExtractConfig {
  if (!config?.items?.length) {
    return createDefaultGrpcExtractConfig();
  }
  return {
    items: config.items.map((item) => ({
      ...item,
      enable: item.enable !== false,
    })),
  };
}

export function buildGrpcSettingsFromState(grpc: GrpcRequestState): GrpcSettingsParams {
  return {
    useTls: grpc.useTls,
    verifyServerCertificate: grpc.verifyServerCertificate,
    serverNameOverride: grpc.serverNameOverride?.trim() || undefined,
    authority: grpc.authority?.trim() || undefined,
    includeDefaultValues: grpc.includeDefaultValues,
    maxResponseSizeMb: grpc.maxResponseSizeMb ?? undefined,
    connectTimeout: grpc.connectTimeout ?? undefined,
  };
}

export function buildGrpcCaseConfigFromState(grpc: GrpcRequestState): GrpcCaseConfigAddParams {
  ensureMainProtoFile(grpc);
  syncMainProtoContent(grpc);
  const parseParams = buildGrpcParseParams(grpc);
  return {
    serviceName: grpc.serviceName,
    methodName: grpc.methodName,
    serverAddress: grpc.serverAddress?.trim() || undefined,
    descriptorContent: grpc.descriptorContent,
    protoContent: parseParams.protoContent || grpc.protoContent || undefined,
    fileName: parseParams.fileName || grpc.fileName || undefined,
    mainFileName: parseParams.mainFileName || grpc.mainFileName || undefined,
    protoFiles: parseParams.protoFiles || (grpc.protoFiles?.length ? grpc.protoFiles : undefined),
    requestJson: grpc.requestJson,
    metadata: buildGrpcMetadata(grpc.metadata),
    timeout: grpc.timeout || DEFAULT_GRPC_TIMEOUT,
    assertionConfig: serializeGrpcAssertionConfig(grpc.assertionConfig),
    extractConfig: grpc.extractConfig || createDefaultGrpcExtractConfig(),
    settings: buildGrpcSettingsFromState(grpc),
    preProcessorConfig: grpc.preProcessorConfig,
    postProcessorConfig: grpc.postProcessorConfig,
  };
}

export function applyGrpcCaseConfigToState(state: GrpcRequestState, caseConfig?: GrpcCaseConfigDetail) {
  if (!caseConfig) {
    return;
  }
  state.assertionConfig = deserializeGrpcAssertionConfig(caseConfig.assertionConfig);
  state.extractConfig = deserializeGrpcExtractConfig(caseConfig.extractConfig);
  const { settings } = caseConfig;
  if (settings) {
    if (settings.useTls !== undefined) {
      state.useTls = settings.useTls;
    }
    if (settings.verifyServerCertificate !== undefined) {
      state.verifyServerCertificate = settings.verifyServerCertificate;
    }
    if (settings.serverNameOverride !== undefined) {
      state.serverNameOverride = settings.serverNameOverride;
    }
    if (settings.authority !== undefined) {
      state.authority = settings.authority;
    }
    if (settings.includeDefaultValues !== undefined) {
      state.includeDefaultValues = settings.includeDefaultValues;
    }
    if (settings.maxResponseSizeMb !== undefined) {
      state.maxResponseSizeMb = settings.maxResponseSizeMb;
    }
    if (settings.connectTimeout !== undefined) {
      state.connectTimeout = settings.connectTimeout;
    }
  }
  if (caseConfig.preProcessorConfig) {
    state.preProcessorConfig = caseConfig.preProcessorConfig;
  }
  if (caseConfig.postProcessorConfig) {
    state.postProcessorConfig = caseConfig.postProcessorConfig;
  }
}

export function buildGrpcCaseDebugRequest(grpc: GrpcRequestState): GrpcCaseDebugRequest {
  return {
    serverAddress: grpc.serverAddress,
    grpcCaseConfig: buildGrpcCaseConfigFromState(grpc),
  };
}

export function isUnsavedGrpcCaseId(id?: string) {
  return !id || id.startsWith('case-');
}

export function isGrpcReportContent(content?: { method?: string; protocol?: string } | null) {
  return content?.method === GRPC_PROTOCOL || content?.protocol === GRPC_PROTOCOL;
}

export function mapGrpcReportContentToInvokeResult(
  content?: GrpcCaseReportContent | null
): GrpcInvokeResult | undefined {
  if (!content) {
    return undefined;
  }
  return {
    success: content.success,
    grpcStatus: content.grpcStatus,
    grpcCode: content.grpcCode,
    duration: content.duration,
    responseJson: content.responseJson,
    errorCode: content.errorCode,
    errorMessage: content.errorMessage,
    assertionResults: content.assertionResults,
    extractResults: content.extractResults,
  };
}

export function formatGrpcReportRequestJson(requestJson?: string) {
  if (!requestJson) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(requestJson), null, 2);
  } catch {
    return requestJson;
  }
}
