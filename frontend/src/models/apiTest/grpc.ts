import type { TabItem } from '@/components/pure/ms-editable-tab/types';

import type {
  EnableKeyValueParam,
  ExecuteApiRequestFullParams,
  ExecuteConditionConfig,
  RequestTaskResult,
} from '@/models/apiTest/common';
import type { RequestComposition } from '@/enums/apiEnum';

export const GRPC_PROTOCOL = 'GRPC';

export interface GrpcMethodInfo {
  name: string;
  fullName?: string;
  methodType?: string;
  invokable?: boolean;
  inputType?: string;
  outputType?: string;
  requestJsonTemplate?: string;
}

export interface GrpcServiceInfo {
  name: string;
  fullName?: string;
  methods?: GrpcMethodInfo[];
}

export interface GrpcProtoFile {
  fileName: string;
  content: string;
  main?: boolean;
}

/** 会话内已上传的主 Proto 及其依赖（Service definition 下拉切换） */
export interface GrpcUploadedMainProto {
  fileName: string;
  content: string;
  dependencies: GrpcProtoFile[];
}

export interface GrpcParseProtoParams {
  protoContent?: string;
  fileName?: string;
  protoFiles?: GrpcProtoFile[];
  mainFileName?: string;
}

export interface GrpcMetadataItem {
  key: string;
  value: string;
}

/** Reflection discovery request; response reuses {@link GrpcParseProtoResult}. */
export interface GrpcReflectionDescribeParams {
  serverAddress: string;
  useTls?: boolean;
  authority?: string;
  metadata?: GrpcMetadataItem[];
  timeout?: number;
}

export interface GrpcParseProtoResult {
  packageName?: string;
  descriptorContent?: string;
  services?: GrpcServiceInfo[];
  warnings?: string[];
}

export interface GrpcBatchGenerateParams extends GrpcParseProtoParams {
  projectId: string;
  parentModuleId: string;
  serverAddress?: string;
  descriptorContent?: string;
  reflection?: boolean;
  reflectionSourceType?: string;
  reflectionServerAddress?: string;
  reflectionUseTls?: boolean;
  reflectionAuthority?: string;
  reflectionServiceFullName?: string;
  reflectionDiscoveredAt?: string;
  methodFullNames: string[];
}

export interface GrpcBatchGenerateItem {
  fullMethodName: string;
  apiDebugId?: string;
  apiDefinitionId?: string;
  moduleId?: string;
  action: 'CREATED' | 'UPDATED' | 'FAILED';
  errorMessage?: string;
}

export interface GrpcBatchGenerateResult {
  createdCount: number;
  updatedCount: number;
  failedCount: number;
  items: GrpcBatchGenerateItem[];
}

/** Reflection `/reflection/describe` response; aligned with proto parse result. */
export type GrpcReflectionDescribeResponse = GrpcParseProtoResult;

export interface GrpcInvokeParams {
  serverAddress: string;
  descriptorContent?: string;
  protoContent?: string;
  serviceName: string;
  methodName: string;
  requestJson?: string;
  metadata?: GrpcMetadataItem[];
  timeout?: number;
  useTls?: boolean;
  verifyServerCertificate?: boolean;
  serverNameOverride?: string;
  authority?: string;
  includeDefaultValues?: boolean;
  maxResponseSizeMb?: number;
  connectTimeout?: number;
}

export type GrpcAssertionType = 'JSON_PATH' | 'GRPC_STATUS' | 'GRPC_CODE';

export type GrpcAssertionCondition = 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'EXISTS' | 'NOT_EXISTS';

export interface GrpcAssertionItem {
  type: GrpcAssertionType;
  name?: string;
  expression?: string;
  condition: GrpcAssertionCondition;
  expectedValue?: string;
  enable?: boolean;
}

export interface GrpcCaseAssertionConfig {
  items: GrpcAssertionItem[];
}

export interface GrpcExtractItem {
  variableName: string;
  expression: string;
  enable?: boolean;
}

export interface GrpcCaseExtractConfig {
  items: GrpcExtractItem[];
}

export interface GrpcAssertionResult {
  name?: string;
  assertionType?: string;
  condition?: string;
  expectedValue?: string;
  actualValue?: string;
  pass?: boolean;
  message?: string;
}

export interface GrpcExtractResult {
  name?: string;
  value?: string;
  type?: string;
  expression?: string;
}

export interface GrpcInvokeResult {
  success?: boolean;
  grpcStatus?: string;
  grpcCode?: number;
  duration?: number;
  responseJson?: string;
  errorCode?: string;
  errorMessage?: string;
  assertionResults?: GrpcAssertionResult[];
  extractResults?: GrpcExtractResult[];
}

/** gRPC 用例报告详情 content（api_report_detail BLOB） */
export interface GrpcCaseReportContent {
  method?: string;
  protocol?: string;
  serviceName?: string;
  methodName?: string;
  serverAddress?: string;
  grpcStatus?: string;
  grpcCode?: number;
  success?: boolean;
  duration?: number;
  requestJson?: string;
  responseJson?: string;
  errorCode?: string;
  errorMessage?: string;
  assertionResults?: GrpcAssertionResult[];
  extractResults?: GrpcExtractResult[];
  url?: string;
}

export interface GrpcDefinitionAddParams {
  protoContent?: string;
  fileName?: string;
  protoFiles?: GrpcProtoFile[];
  mainFileName?: string;
  serverAddress?: string;
  descriptorContent?: string;
  reflection?: boolean;
  reflectionSourceType?: string;
  reflectionServerAddress?: string;
  reflectionUseTls?: boolean;
  reflectionAuthority?: string;
  reflectionServiceFullName?: string;
  reflectionDiscoveredAt?: string;
  serviceName: string;
  methodName: string;
}

export interface GrpcProtoMessageChange {
  messageName?: string;
  changeType?: string;
  fieldName?: string;
  detail?: string;
  impactLevel?: string;
}

export interface GrpcProtoAffectedDefinition {
  apiDefinitionId?: string;
  serviceName?: string;
  methodName?: string;
}

export interface GrpcProtoAffectedCase {
  apiCaseId?: string;
  serviceName?: string;
  methodName?: string;
  protoId?: string;
}

export interface GrpcProtoChangeImpact {
  impactLevel?: string;
  impactSummary?: string;
  changedServices?: string[];
  changedMethods?: string[];
  changedMessages?: GrpcProtoMessageChange[];
  affectedDefinitions?: GrpcProtoAffectedDefinition[];
  affectedCases?: GrpcProtoAffectedCase[];
}

export interface GrpcProtoVersionInfo {
  protoId?: string;
  versionNo?: number;
  versionLabel?: string;
  fileName?: string;
  contentHash?: string;
  contentHashShort?: string;
  createTime?: number;
}

export interface GrpcDefinitionDetail extends GrpcDefinitionAddParams {
  protoId?: string;
  mainFileName?: string;
  protoFiles?: GrpcProtoFile[];
  packageName?: string;
  fullMethodName?: string;
  methodType?: string;
  invokable?: boolean;
  requestType?: string;
  responseType?: string;
  descriptorContent?: string;
  services?: GrpcServiceInfo[];
  protoVersion?: GrpcProtoVersionInfo;
  protoVersionHistory?: GrpcProtoVersionInfo[];
  protoChangeImpact?: GrpcProtoChangeImpact;
}

/** gRPC 运行设置，与后端 GrpcCaseSettingsDTO 对应 */
export interface GrpcSettingsParams {
  useTls?: boolean;
  verifyServerCertificate?: boolean;
  serverNameOverride?: string;
  authority?: string;
  includeDefaultValues?: boolean;
  maxResponseSizeMb?: number;
  connectTimeout?: number;
}

export interface GrpcCaseConfigDetail {
  id?: string;
  apiCaseId?: string;
  apiDefinitionId?: string;
  protoId?: string;
  protoVersion?: GrpcProtoVersionInfo;
  protoVersionOutdated?: boolean;
  protoChangeImpact?: GrpcProtoChangeImpact;
  serviceName?: string;
  methodName?: string;
  serverAddress?: string;
  descriptorContent?: string;
  protoContent?: string;
  fileName?: string;
  mainFileName?: string;
  protoFiles?: GrpcProtoFile[];
  requestJson?: string;
  metadata?: GrpcMetadataItem[];
  timeout?: number;
  assertionConfig?: GrpcCaseAssertionConfig;
  extractConfig?: GrpcCaseExtractConfig;
  settings?: GrpcSettingsParams;
  preProcessorConfig?: ExecuteConditionConfig;
  postProcessorConfig?: ExecuteConditionConfig;
}

export interface GrpcCaseConfigAddParams {
  serviceName?: string;
  methodName?: string;
  serverAddress?: string;
  descriptorContent?: string;
  protoContent?: string;
  fileName?: string;
  mainFileName?: string;
  protoFiles?: GrpcProtoFile[];
  protoId?: string;
  requestJson?: string;
  metadata?: GrpcMetadataItem[];
  timeout?: number;
  assertionConfig?: GrpcCaseAssertionConfig;
  extractConfig?: GrpcCaseExtractConfig;
  settings?: GrpcSettingsParams;
  preProcessorConfig?: ExecuteConditionConfig;
  postProcessorConfig?: ExecuteConditionConfig;
}

export interface GrpcCaseDebugRequest {
  serverAddress: string;
  grpcCaseConfig: GrpcCaseConfigAddParams;
}

export interface GrpcRequestState {
  protoContent: string;
  fileName: string;
  protoFiles: GrpcProtoFile[];
  mainFileName: string;
  packageName: string;
  descriptorContent: string;
  serviceName: string;
  methodName: string;
  methodType: string;
  invokable: boolean;
  services: GrpcServiceInfo[];
  requestJson: string;
  serverAddress: string;
  metadata: EnableKeyValueParam[];
  timeout: number;
  parseLoading: boolean;
  debugResponse?: GrpcInvokeResult;
  assertionConfig: GrpcCaseAssertionConfig;
  extractConfig: GrpcCaseExtractConfig;
  reflectionDiscovered?: boolean;
  reflectionServerAddress?: string;
  reflectionUseTls?: boolean;
  reflectionDiscoveredAt?: string;
  /** Request execution TLS (UI state; invoke API may not consume yet) */
  useTls?: boolean;
  /** Request authority override (UI state) */
  authority?: string;
  /**
   * 以下为 Postman 风格 gRPC 调用设置，目前仅前端 state，后端 invoke/run DTO 暂未支持。
   * TODO(backend): GrpcInvokeRequest / ApiGrpcCaseConfigAddDTO 增加对应字段并在 GrpcInvokeService/GrpcChannelFactory 生效。
   */
  /** 启用服务端证书校验（TLS 下生效） */
  verifyServerCertificate?: boolean;
  /** 覆盖证书校验的服务名称（SSL server name override） */
  serverNameOverride?: string;
  /** 在响应中包含默认值字段（后端当前硬编码开启） */
  includeDefaultValues?: boolean;
  /** 最大响应消息大小（MB） */
  maxResponseSizeMb?: number;
  /** 连接超时时间（ms），区别于调用 deadline timeout */
  connectTimeout?: number;
  /**
   * gRPC 前置/后置脚本配置；复用 HTTP 处理器结构。
   * TODO(backend): gRPC 执行链路暂不消费脚本，仅保留前端结构。
   */
  preProcessorConfig?: ExecuteConditionConfig;
  postProcessorConfig?: ExecuteConditionConfig;
  /** 已上传主 Proto 历史（仅前端会话，Service definition 下拉） */
  uploadedMainProtos?: GrpcUploadedMainProto[];
}

/** Request composition tab payload for gRPC case tab builders (mirrors requestComposition RequestParam). */
export interface GrpcCaseRequestCustomAttr {
  type: 'api' | 'case' | 'mock' | 'doc';
  isNew: boolean;
  protocol: string;
  activeTab: RequestComposition;
  mode?: 'definition' | 'debug';
  executeLoading: boolean;
  isCopy?: boolean;
  isExecute?: boolean;
  aiCreate?: boolean;
  errorMessageInfo?: {
    [key: string]: Record<string, any>;
  };
}

export type GrpcCaseRequestParam = ExecuteApiRequestFullParams & {
  response?: RequestTaskResult;
  grpc?: GrpcRequestState;
  grpcDefinition?: GrpcDefinitionDetail;
  grpcCaseConfig?: GrpcCaseConfigDetail;
} & GrpcCaseRequestCustomAttr &
  TabItem;
