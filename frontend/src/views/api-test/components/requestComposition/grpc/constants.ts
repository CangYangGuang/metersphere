import { cloneDeep } from 'lodash-es';

import type {
  GrpcCaseConfigDetail,
  GrpcCaseDebugRequest,
  GrpcCaseRequestParam,
  GrpcDefinitionDetail,
  GrpcRequestState,
} from '@/models/apiTest/grpc';
import { GRPC_PROTOCOL } from '@/models/apiTest/grpc';
import type { ApiCaseDetail, ApiDefinitionDetail } from '@/models/apiTest/management';
import { RequestComposition, RequestDefinitionStatus } from '@/enums/apiEnum';

import { defaultHeaderParamsItem } from '@/views/api-test/components/config';
import {
  applyGrpcCaseConfigToState,
  applyRequestJsonTemplateIfEmpty,
  createDefaultGrpcAssertionConfig,
  createDefaultGrpcExtractConfig,
  DEFAULT_GRPC_TIMEOUT,
  ensureGrpcMethodOptions,
  ensureGrpcServiceOptions,
  metadataToEnableKeyValue,
  resolveMethodInvokable,
  restoreGrpcDebugExecutableState,
  syncMethodInfoFromServices,
} from '@/views/api-test/components/requestComposition/grpc/utils';
import type { SelectOptionData } from '@arco-design/web-vue';

export { DEFAULT_GRPC_TIMEOUT } from '@/views/api-test/components/requestComposition/grpc/utils';

export function createDefaultGrpcState(): GrpcRequestState {
  return {
    protoContent: '',
    fileName: '',
    protoFiles: [],
    mainFileName: '',
    packageName: '',
    descriptorContent: '',
    serviceName: '',
    methodName: '',
    methodType: '',
    invokable: false,
    services: [],
    requestJson: '{}',
    serverAddress: '',
    metadata: [cloneDeep(defaultHeaderParamsItem)],
    timeout: DEFAULT_GRPC_TIMEOUT,
    parseLoading: false,
    assertionConfig: createDefaultGrpcAssertionConfig(),
    extractConfig: createDefaultGrpcExtractConfig(),
    // Postman 风格调用设置（前端 state，后端暂未接入，详见 GrpcRequestState 注释）
    verifyServerCertificate: false,
    serverNameOverride: '',
    includeDefaultValues: true,
    maxResponseSizeMb: undefined,
    connectTimeout: undefined,
    // 前置/后置脚本配置（前端结构，后端暂未消费）
    preProcessorConfig: { enableGlobal: true, processors: [] },
    postProcessorConfig: { enableGlobal: true, processors: [] },
    uploadedMainProtos: [],
  };
}

export function mapGrpcDefinitionToState(grpcDefinition?: GrpcDefinitionDetail): GrpcRequestState {
  const state = createDefaultGrpcState();
  if (!grpcDefinition) {
    return state;
  }
  state.serverAddress = grpcDefinition.serverAddress || '';
  if (grpcDefinition.reflection) {
    state.reflectionDiscovered = true;
    state.reflectionServerAddress = grpcDefinition.serverAddress || grpcDefinition.reflectionServerAddress || '';
    state.reflectionUseTls = grpcDefinition.reflectionUseTls;
    state.reflectionDiscoveredAt = grpcDefinition.reflectionDiscoveredAt;
    state.serverAddress = grpcDefinition.serverAddress || grpcDefinition.reflectionServerAddress || '';
    state.protoContent = '';
    state.protoFiles = [];
    state.fileName = grpcDefinition.fileName || '';
    state.mainFileName = grpcDefinition.mainFileName || grpcDefinition.fileName || '';
  } else if (grpcDefinition.protoFiles?.length) {
    state.protoFiles = grpcDefinition.protoFiles;
    state.mainFileName = grpcDefinition.mainFileName || grpcDefinition.fileName || '';
    state.fileName = state.mainFileName;
    state.protoContent = grpcDefinition.protoContent || '';
  } else {
    state.protoContent = grpcDefinition.protoContent || '';
    state.fileName = grpcDefinition.fileName || '';
    state.mainFileName = grpcDefinition.mainFileName || grpcDefinition.fileName || '';
    state.protoFiles = grpcDefinition.protoContent
      ? [{ fileName: grpcDefinition.fileName || 'upload.proto', content: grpcDefinition.protoContent, main: true }]
      : [];
  }
  state.packageName = grpcDefinition.packageName || '';
  state.descriptorContent = grpcDefinition.descriptorContent || '';
  state.serviceName = grpcDefinition.serviceName || '';
  state.methodName = grpcDefinition.methodName || '';
  state.methodType = grpcDefinition.methodType || '';
  state.invokable = resolveMethodInvokable(grpcDefinition.methodType, grpcDefinition.invokable);
  state.services = grpcDefinition.services || [];
  syncMethodInfoFromServices(state);
  applyRequestJsonTemplateIfEmpty(state);
  return state;
}

export function mapGrpcCaseToState(
  grpcCaseConfig?: GrpcCaseConfigDetail,
  grpcDefinition?: GrpcDefinitionDetail
): GrpcRequestState {
  const state = mapGrpcDefinitionToState(grpcDefinition);
  if (grpcCaseConfig?.protoFiles?.length) {
    state.protoFiles = grpcCaseConfig.protoFiles;
    state.mainFileName = grpcCaseConfig.mainFileName || grpcCaseConfig.fileName || state.mainFileName;
    state.fileName = state.mainFileName;
    state.protoContent = grpcCaseConfig.protoContent || state.protoContent;
  }
  if (!grpcCaseConfig) {
    return state;
  }
  if (grpcCaseConfig.serviceName) {
    state.serviceName = grpcCaseConfig.serviceName;
  }
  if (grpcCaseConfig.methodName) {
    state.methodName = grpcCaseConfig.methodName;
  }
  if (grpcCaseConfig.serverAddress?.trim()) {
    state.serverAddress = grpcCaseConfig.serverAddress.trim();
  }
  if (grpcCaseConfig.descriptorContent) {
    state.descriptorContent = grpcCaseConfig.descriptorContent;
  }
  if (grpcCaseConfig.requestJson != null) {
    state.requestJson = grpcCaseConfig.requestJson;
  }
  if (grpcCaseConfig.timeout != null) {
    state.timeout = grpcCaseConfig.timeout;
  }
  if (grpcCaseConfig.metadata?.length) {
    state.metadata = metadataToEnableKeyValue(grpcCaseConfig.metadata);
  }
  applyGrpcCaseConfigToState(state, grpcCaseConfig);
  syncMethodInfoFromServices(state);
  return state;
}

export function mapGrpcDebugRequestToState(request?: GrpcCaseDebugRequest): GrpcRequestState {
  const state = mapGrpcCaseToState(request?.grpcCaseConfig);
  if (request?.serverAddress) {
    state.serverAddress = request.serverAddress;
  }
  restoreGrpcDebugExecutableState(state);
  return state;
}

export function isGrpcAiCaseRecord(protocol?: string, grpcCaseConfig?: GrpcCaseConfigDetail): boolean {
  return protocol === GRPC_PROTOCOL || !!grpcCaseConfig;
}

export function buildGrpcAiCasePreviewForm(
  aiRecord: Pick<ApiCaseDetail, 'name' | 'grpcCaseConfig'> | undefined,
  defaultCaseParams: GrpcCaseRequestParam,
  grpcDefinition?: GrpcDefinitionDetail
): GrpcCaseRequestParam {
  return {
    ...cloneDeep(defaultCaseParams),
    name: aiRecord?.name ?? '',
    priority: 'P0',
    status: RequestDefinitionStatus.PROCESSING,
    protocol: GRPC_PROTOCOL,
    grpc: mapGrpcCaseToState(aiRecord?.grpcCaseConfig, grpcDefinition),
    grpcDefinition,
    grpcCaseConfig: aiRecord?.grpcCaseConfig,
    activeTab: RequestComposition.GRPC_MESSAGE,
    isNew: true,
    aiCreate: true,
  } as GrpcCaseRequestParam;
}

export function buildGrpcCaseTabData(
  caseDetail: ApiCaseDetail,
  defaultCaseParams: GrpcCaseRequestParam,
  definitionDetail?: ApiDefinitionDetail
): GrpcCaseRequestParam {
  const grpcDefinition = definitionDetail?.grpcDefinition;
  return {
    ...cloneDeep(defaultCaseParams),
    ...caseDetail,
    url: caseDetail.path,
    grpc: mapGrpcCaseToState(caseDetail.grpcCaseConfig, grpcDefinition),
    grpcDefinition,
    grpcCaseConfig: caseDetail.grpcCaseConfig,
    activeTab: RequestComposition.GRPC_MESSAGE,
  } as GrpcCaseRequestParam;
}

export { ensureGrpcMethodOptions, ensureGrpcServiceOptions };

export function ensureGrpcProtocolOption(protocolOptions: SelectOptionData[]) {
  if (protocolOptions.some((item) => item.value === GRPC_PROTOCOL)) {
    return protocolOptions;
  }
  const httpIndex = protocolOptions.findIndex((item) => item.value === 'HTTP');
  const grpcOption: SelectOptionData = { label: GRPC_PROTOCOL, value: GRPC_PROTOCOL };
  if (httpIndex >= 0) {
    return [...protocolOptions.slice(0, httpIndex + 1), grpcOption, ...protocolOptions.slice(httpIndex + 1)];
  }
  return [grpcOption, ...protocolOptions];
}
