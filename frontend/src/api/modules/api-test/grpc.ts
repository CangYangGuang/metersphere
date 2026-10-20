import MSR from '@/api/http/index';
import { GrpcDebugParseUrl, GrpcDebugReflectionDescribeUrl, GrpcDebugSendUrl } from '@/api/requrls/api-test/grpc';

import type {
  GrpcBatchGenerateParams,
  GrpcBatchGenerateResult,
  GrpcInvokeParams,
  GrpcInvokeResult,
  GrpcParseProtoParams,
  GrpcParseProtoResult,
  GrpcReflectionDescribeParams,
} from '@/models/apiTest/grpc';

export function parseGrpcProto(data: GrpcParseProtoParams) {
  return MSR.post<GrpcParseProtoResult>({
    url: GrpcDebugParseUrl,
    data,
  });
}

/** Discover services and methods via gRPC Server Reflection. */
export function describeGrpcReflection(data: GrpcReflectionDescribeParams) {
  return MSR.post<GrpcParseProtoResult>({
    url: GrpcDebugReflectionDescribeUrl,
    data,
  });
}

export function sendGrpcDebug(data: GrpcInvokeParams) {
  return MSR.post<GrpcInvokeResult>({
    url: GrpcDebugSendUrl,
    data,
  });
}

export function batchSaveGrpcDebugRequests(data: GrpcBatchGenerateParams) {
  return MSR.post<GrpcBatchGenerateResult>({
    url: '/api/debug/grpc/batch-save',
    data,
  });
}

export function batchSaveGrpcDefinitions(data: GrpcBatchGenerateParams) {
  return MSR.post<GrpcBatchGenerateResult>({
    url: '/api/definition/grpc/batch-save-as-api',
    data,
  });
}
