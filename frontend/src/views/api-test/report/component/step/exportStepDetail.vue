<template>
  <section v-if="isGrpc" class="export-step-detail">
    <div v-if="failureReason" class="failure-reason"> <strong>失败原因：</strong>{{ failureReason }} </div>

    <div class="detail-grid">
      <div><span>服务</span>{{ content.serviceName || '-' }}</div>
      <div><span>方法</span>{{ content.methodName || '-' }}</div>
      <div><span>服务器地址</span>{{ content.serverAddress || serverAddressFromUrl || '-' }}</div>
      <div><span>执行耗时</span>{{ content.duration ?? props.detail.requestTime ?? 0 }} ms</div>
      <div><span>gRPC Status</span>{{ content.grpcStatus || '-' }}</div>
      <div><span>gRPC Code</span>{{ content.grpcCode ?? props.detail.code ?? '-' }}</div>
      <div><span>错误码</span>{{ content.errorCode || '-' }}</div>
      <div><span>错误消息</span>{{ content.errorMessage || '-' }}</div>
    </div>

    <div class="detail-block">
      <div class="detail-title">请求 JSON</div>
      <pre>{{ formatJson(content.requestJson) || '-' }}</pre>
    </div>
    <div class="detail-block">
      <div class="detail-title">响应 JSON</div>
      <pre>{{ formatJson(content.responseJson) || '-' }}</pre>
    </div>

    <div class="detail-block">
      <div class="detail-title">断言结果（{{ assertions.length }}）</div>
      <table v-if="assertions.length">
        <thead>
          <tr> <th>名称</th><th>类型</th><th>条件</th><th>期望值</th><th>实际值</th><th>结果</th><th>消息</th> </tr>
        </thead>
        <tbody>
          <tr v-for="(assertion, index) in assertions" :key="`${assertion.name}-${index}`">
            <td>{{ assertion.name || '-' }}</td>
            <td>{{ assertion.assertionType || '-' }}</td>
            <td>{{ assertion.condition || '-' }}</td>
            <td>{{ displayValue(assertion.expectedValue) }}</td>
            <td>{{ displayValue(assertion.actualValue) }}</td>
            <td :class="assertion.pass ? 'passed' : 'failed'">{{ assertion.pass ? '通过' : '失败' }}</td>
            <td>{{ assertion.message || '-' }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty-value">无断言</div>
    </div>

    <div class="detail-block">
      <div class="detail-title">变量提取结果（{{ extracts.length }}）</div>
      <table v-if="extracts.length">
        <thead
          ><tr><th>变量名</th><th>表达式</th><th>类型</th><th>提取值</th></tr></thead
        >
        <tbody>
          <tr v-for="(item, index) in extracts" :key="`${item.name}-${index}`">
            <td>{{ item.name || '-' }}</td>
            <td>{{ item.expression || '-' }}</td>
            <td>{{ item.type || '-' }}</td>
            <td>{{ displayValue(item.value) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty-value">无变量提取</div>
    </div>
  </section>
</template>

<script setup lang="ts">
  import type { GrpcCaseReportContent } from '@/models/apiTest/grpc';
  import type { ReportStepDetailItem } from '@/models/apiTest/report';

  import {
    formatGrpcExecutionErrorMessage,
    isGrpcReportContent,
  } from '@/views/api-test/components/requestComposition/grpc/utils';

  const props = defineProps<{ detail: ReportStepDetailItem }>();
  const content = computed(() => {
    const raw = props.detail.content as any;
    const response = raw?.responseResult || {};
    const urlParts = String(raw?.url || '')
      .split('/')
      .filter(Boolean);
    return {
      ...raw,
      serviceName: raw?.serviceName || (urlParts.length >= 2 ? urlParts[urlParts.length - 2] : undefined),
      methodName: raw?.methodName || (urlParts.length ? urlParts[urlParts.length - 1] : undefined),
      grpcStatus: raw?.grpcStatus || response.responseMessage,
      grpcCode: raw?.grpcCode ?? (response.responseCode === '' ? undefined : Number(response.responseCode)),
      success: raw?.success ?? raw?.isSuccessful,
      duration: raw?.duration ?? response.responseTime,
      requestJson: raw?.requestJson || raw?.body,
      responseJson: raw?.responseJson || response.body,
      errorCode: raw?.errorCode || raw?.fakeErrorCode,
      errorMessage: raw?.errorMessage || raw?.fakeErrorMessage,
      assertionResults: raw?.assertionResults || response.assertions || [],
      extractResults: raw?.extractResults || response.extractResults || [],
    } as GrpcCaseReportContent;
  });
  const isGrpc = computed(() => isGrpcReportContent(content.value));
  const assertions = computed(() => content.value.assertionResults || []);
  const extracts = computed(() => content.value.extractResults || []);
  const failedAssertion = computed(() => assertions.value.find((item) => item && item.pass === false));

  function displayValue(value?: string) {
    return value === undefined || value === null || value === '' ? '（空）' : String(value);
  }

  const failureReason = computed(() => {
    const assertion = failedAssertion.value;
    if (assertion) {
      return `${assertion.name || '断言'}未通过：期望 ${displayValue(assertion.expectedValue)}，实际 ${displayValue(
        assertion.actualValue
      )}${assertion.message ? `；${assertion.message}` : ''}`;
    }
    if (content.value.success === true) {
      return '';
    }
    const errorMessage = formatGrpcExecutionErrorMessage(content.value.errorCode, content.value.errorMessage);
    if (errorMessage) {
      return errorMessage;
    }
    if (content.value.success === false) {
      return `${content.value.grpcStatus || 'gRPC 调用失败'}（code ${content.value.grpcCode ?? '-'}）`;
    }
    return '';
  });
  const serverAddressFromUrl = computed(() => {
    const suffix = [content.value.serviceName, content.value.methodName].filter(Boolean).join('/');
    if (!content.value.url || !suffix || !content.value.url.endsWith(suffix)) {
      return content.value.url || '';
    }
    return content.value.url.slice(0, -(suffix.length + 1));
  });

  function formatJson(value?: string) {
    if (!value) return '';
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
</script>

<style scoped lang="less">
  .export-step-detail {
    margin-top: 12px;
    padding-top: 12px;
    width: 100%;
    font-size: 12px;
    border-top: 1px solid var(--color-text-n8);
    color: var(--color-text-1);
    line-height: 18px;
    page-break-inside: auto;
  }
  .failure-reason {
    margin-bottom: 12px;
    padding: 8px 10px;
    border-left: 3px solid rgb(var(--danger-6));
    white-space: normal;
    color: rgb(var(--danger-7));
    background: rgb(var(--danger-1));
    word-break: break-word;
  }
  .detail-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px 16px;
    margin-bottom: 12px;
    > div {
      min-width: 0;
      overflow-wrap: anywhere;
    }
    span {
      display: block;
      color: var(--color-text-3);
    }
  }
  .detail-block {
    margin-top: 12px;
  }
  .detail-title {
    margin-bottom: 6px;
    font-weight: 600;
  }
  pre {
    margin: 0;
    padding: 8px;
    border: 1px solid var(--color-text-n8);
    white-space: pre-wrap;
    background: var(--color-fill-1);
    overflow-wrap: anywhere;
    font: 11px/16px Consolas, monospace;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    table-layout: fixed;
  }
  th,
  td {
    padding: 6px;
    border: 1px solid var(--color-text-n8);
    text-align: left;
    overflow-wrap: anywhere;
  }
  th {
    color: var(--color-text-3);
    background: var(--color-fill-1);
  }
  .passed {
    color: rgb(var(--success-7));
  }
  .failed {
    font-weight: 600;
    color: rgb(var(--danger-7));
  }
  .empty-value {
    color: var(--color-text-3);
  }
</style>
