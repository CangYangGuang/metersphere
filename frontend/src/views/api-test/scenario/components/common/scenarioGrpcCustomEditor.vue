<template>
  <grpcRequestEditor
    v-model:grpc="grpcState"
    v-model:active-tab="activeTab"
    v-model:response-active-tab="responseActiveTab"
    class="scenario-grpc-editor h-full"
    :always-show-response="false"
    :request-result="requestResult"
    :execute-loading="executeLoading"
    @change="emitChange"
    @execute="emit('execute', $event)"
  />
</template>

<script setup lang="ts">
  import grpcRequestEditor from '@/views/api-test/components/requestComposition/grpc/grpcRequestEditor.vue';

  import type { RequestResult } from '@/models/apiTest/common';
  import type { GrpcRequestState } from '@/models/apiTest/grpc';
  import { RequestComposition, ResponseComposition } from '@/enums/apiEnum';

  defineProps<{
    requestResult?: RequestResult;
    executeLoading?: boolean;
  }>();

  const grpcState = defineModel<GrpcRequestState>('grpc', { required: true });
  const activeTab = defineModel<RequestComposition>('activeTab', { required: true });
  const responseActiveTab = defineModel<ResponseComposition>('responseActiveTab', {
    default: ResponseComposition.BODY,
  });

  const emit = defineEmits<{
    (e: 'change'): void;
    (e: 'execute', executeType: 'localExec' | 'serverExec'): void;
  }>();

  function emitChange() {
    emit('change');
  }
</script>
