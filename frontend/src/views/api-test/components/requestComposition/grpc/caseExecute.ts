import { sendGrpcDebug } from '@/api/modules/api-test/grpc';
import { debugCase, runCase } from '@/api/modules/api-test/management';

import type { GrpcInvokeResult, GrpcRequestState } from '@/models/apiTest/grpc';
import type { ExecuteRequestParams } from '@/models/apiTest/common';

import {
  buildGrpcCaseDebugRequest,
  buildGrpcMetadata,
  createGrpcInvokeErrorResult,
  isUnsavedGrpcCaseId,
  validateRequestJson,
} from './utils';

export interface ExecuteGrpcCaseOptions {
  caseId?: string;
  projectId: string;
  apiDefinitionId: string;
  environmentId?: string;
  grpc: GrpcRequestState;
  useRunCase?: boolean;
}

export async function executeGrpcCase(options: ExecuteGrpcCaseOptions): Promise<GrpcInvokeResult> {
  const { caseId, projectId, apiDefinitionId, environmentId, grpc, useRunCase } = options;
  const jsonValidation = validateRequestJson(grpc.requestJson);
  if (!jsonValidation.valid) {
    throw new Error(jsonValidation.message || 'Invalid requestJson');
  }
  if (!grpc.serverAddress?.trim()) {
    throw new Error('serverAddress required');
  }
  if (!grpc.descriptorContent?.trim()) {
    throw new Error('descriptorContent required');
  }

  if (!isUnsavedGrpcCaseId(caseId)) {
    const params = {
      id: caseId as string,
      projectId,
      environmentId: environmentId || '',
      apiDefinitionId,
      request: buildGrpcCaseDebugRequest(grpc),
      uploadFileIds: [],
      linkFileIds: [],
    } as unknown as ExecuteRequestParams;
    return useRunCase ? runCase(params) : debugCase(params);
  }

  return sendGrpcDebug({
    serverAddress: grpc.serverAddress,
    descriptorContent: grpc.descriptorContent,
    protoContent: grpc.protoContent,
    serviceName: grpc.serviceName,
    methodName: grpc.methodName,
    requestJson: grpc.requestJson,
    metadata: buildGrpcMetadata(grpc.metadata),
    timeout: grpc.timeout,
  });
}

export function wrapGrpcCaseExecuteError(error: unknown): GrpcInvokeResult {
  return createGrpcInvokeErrorResult(error);
}
