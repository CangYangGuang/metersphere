<template>
  <div class="grpc-request-editor flex h-full min-h-0 flex-col">
    <MsTab
      v-model:active-key="activeTab"
      :content-tab-list="contentTabList"
      no-content
      :show-badge="false"
      class="sticky-content no-content relative top-0 mx-[16px] border-b"
    />
    <div :class="`request-content-and-response min-h-0 flex-1 ${activeLayout}`">
      <a-spin class="request block h-full w-full" :loading="executeLoading">
        <div class="request-tab-pane flex flex-col p-[16px] pt-[8px]">
          <grpcDocsTab v-show="isActiveTab(RequestComposition.GRPC_DOCS)" :grpc="grpcState" />
          <grpcMessageTab
            v-show="isActiveTab(RequestComposition.GRPC_MESSAGE)"
            v-model:grpc="grpcState"
            @change="emitChange"
          />
          <grpcMetadataTab
            v-show="isActiveTab(RequestComposition.GRPC_METADATA)"
            v-model:grpc="grpcState"
            @change="emitChange"
          />
          <grpcServiceDefinitionTab
            v-show="isActiveTab(RequestComposition.GRPC_SERVICE_DEFINITION)"
            v-model:grpc="grpcState"
            @change="emitChange"
          />
          <div v-show="activeTab === RequestComposition.PRECONDITION">
            <a-alert type="normal" class="mb-[8px] text-[12px]">{{ t('apiTestGrpc.preProcessorHint') }}</a-alert>
            <precondition v-model:config="grpcPreProcessorConfig" is-definition @change="emitChange" />
          </div>
          <div v-show="activeTab === RequestComposition.POST_CONDITION">
            <a-alert type="normal" class="mb-[8px] text-[12px]">{{ t('apiTestGrpc.postProcessorHint') }}</a-alert>
            <postcondition
              v-model:config="grpcPostProcessorConfig"
              is-definition
              :response="grpcState.debugResponse?.responseJson"
              @change="emitChange"
            />
          </div>
          <grpcSettingsTab
            v-show="isActiveTab(RequestComposition.GRPC_SETTINGS)"
            v-model:grpc="grpcState"
            @change="emitChange"
          />
        </div>
      </a-spin>
      <response
        v-show="showResponse"
        ref="responseRef"
        v-model:active-layout="activeLayout"
        v-model:active-tab="responseActiveTab"
        v-model:is-expanded="isVerticalExpanded"
        class="response"
        :is-http-protocol="false"
        :is-grpc-protocol="true"
        :grpc-raw-json="grpcRawJson"
        :request-url="grpcState.serverAddress"
        :request-result="displayRequestResult"
        :loading="executeLoading"
        is-definition
        @execute="emit('execute', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import MsTab from '@/components/pure/ms-tab/index.vue';
  import grpcDocsTab from '@/views/api-test/components/requestComposition/grpc/grpcDocsTab.vue';
  import grpcMessageTab from '@/views/api-test/components/requestComposition/grpc/grpcMessageTab.vue';
  import grpcMetadataTab from '@/views/api-test/components/requestComposition/grpc/grpcMetadataTab.vue';
  import grpcServiceDefinitionTab from '@/views/api-test/components/requestComposition/grpc/grpcServiceDefinitionTab.vue';
  import grpcSettingsTab from '@/views/api-test/components/requestComposition/grpc/grpcSettingsTab.vue';
  import postcondition from '@/views/api-test/components/requestComposition/postcondition.vue';
  import precondition from '@/views/api-test/components/requestComposition/precondition.vue';
  import response from '@/views/api-test/components/requestComposition/response/index.vue';

  import { useI18n } from '@/hooks/useI18n';

  import type { RequestResult } from '@/models/apiTest/common';
  import type { GrpcRequestState } from '@/models/apiTest/grpc';
  import { RequestComposition, ResponseComposition } from '@/enums/apiEnum';

  import {
    createDefaultGrpcAssertionConfig,
    createDefaultGrpcExtractConfig,
    formatGrpcInvokeResultRaw,
    mapGrpcInvokeResultToRequestResult,
  } from '@/views/api-test/components/requestComposition/grpc/utils';

  const props = withDefaults(
    defineProps<{
      requestResult?: RequestResult;
      executeLoading?: boolean;
      /** 始终展示响应区域（调试页 gRPC 行为） */
      alwaysShowResponse?: boolean;
    }>(),
    {
      executeLoading: false,
      alwaysShowResponse: true,
    }
  );

  const grpcState = defineModel<GrpcRequestState>('grpc', { required: true });
  const activeTab = defineModel<RequestComposition>('activeTab', { required: true });
  const responseActiveTab = defineModel<ResponseComposition>('responseActiveTab', {
    default: ResponseComposition.BODY,
  });

  const emit = defineEmits<{
    (e: 'change'): void;
    (e: 'execute', executeType: 'localExec' | 'serverExec'): void;
  }>();

  const { t } = useI18n();

  function ensureGrpcDefaults(state: GrpcRequestState) {
    if (!state.assertionConfig) {
      state.assertionConfig = createDefaultGrpcAssertionConfig();
    }
    if (!state.extractConfig) {
      state.extractConfig = createDefaultGrpcExtractConfig();
    }
    if (!state.preProcessorConfig) {
      state.preProcessorConfig = { enableGlobal: true, processors: [] };
    }
    if (!state.postProcessorConfig) {
      state.postProcessorConfig = { enableGlobal: true, processors: [] };
    }
    if (state.includeDefaultValues === undefined) {
      state.includeDefaultValues = true;
    }
    if (!state.uploadedMainProtos) {
      state.uploadedMainProtos = [];
    }
    return state;
  }

  watch(
    () => grpcState.value,
    (val) => {
      if (val) {
        ensureGrpcDefaults(val);
      }
    },
    { immediate: true, deep: true }
  );

  const grpcPreProcessorConfig = computed({
    get: () => ensureGrpcDefaults(grpcState.value).preProcessorConfig!,
    set: (val) => {
      ensureGrpcDefaults(grpcState.value).preProcessorConfig = val;
    },
  });

  const grpcPostProcessorConfig = computed({
    get: () => ensureGrpcDefaults(grpcState.value).postProcessorConfig!,
    set: (val) => {
      ensureGrpcDefaults(grpcState.value).postProcessorConfig = val;
    },
  });

  const contentTabList = computed(() => [
    { value: RequestComposition.GRPC_DOCS, label: t('apiTestGrpc.docsTab') },
    { value: RequestComposition.GRPC_MESSAGE, label: t('apiTestGrpc.messageTab') },
    { value: RequestComposition.GRPC_METADATA, label: t('apiTestGrpc.metadataTab') },
    { value: RequestComposition.GRPC_SERVICE_DEFINITION, label: t('apiTestGrpc.serviceDefinitionTab') },
    { value: RequestComposition.PRECONDITION, label: t('apiTestDebug.prefix') },
    { value: RequestComposition.POST_CONDITION, label: t('apiTestDebug.post') },
    { value: RequestComposition.GRPC_SETTINGS, label: t('apiTestGrpc.settingsTab') },
  ]);

  function isActiveTab(tab: RequestComposition) {
    return activeTab.value === tab;
  }

  function emitChange() {
    emit('change');
  }

  const activeLayout = ref<'horizontal' | 'vertical'>('vertical');
  const isVerticalExpanded = ref(true);
  const responseRef = ref<InstanceType<typeof response>>();

  const grpcRequestResult = computed(() =>
    mapGrpcInvokeResultToRequestResult(grpcState.value.debugResponse, grpcState.value.serverAddress)
  );

  const grpcRawJson = computed(() => formatGrpcInvokeResultRaw(grpcState.value.debugResponse));

  const displayRequestResult = computed(() => props.requestResult || grpcRequestResult.value);

  const showResponse = computed(
    () => props.alwaysShowResponse || !!displayRequestResult.value?.responseResult || props.executeLoading
  );

  watch(
    () => showResponse.value,
    (val) => {
      nextTick(() => {
        if (val) {
          responseRef.value?.changeExpand(true);
        } else {
          responseRef.value?.changeExpand(false);
        }
      });
    },
    { immediate: true }
  );
</script>

<style lang="less" scoped>
  :deep(.no-content) {
    .arco-tabs-content {
      display: none;
    }
  }
  :deep(.arco-tabs-tab:first-child) {
    margin-left: 0;
  }
  :deep(.arco-tabs-tab) {
    @apply leading-none;
  }
  .sticky-content {
    @apply sticky;

    z-index: 101;
    background-color: var(--color-text-fff);
  }
  .request-content-and-response {
    display: flex;
    overflow: hidden;
    &.vertical {
      flex-direction: column;
      .response :deep(.response-head) {
        @apply sticky;

        top: 46px;
        z-index: 11;
        background-color: var(--color-text-fff);
      }
      .request-tab-pane {
        min-height: 400px;
      }
    }
    &.horizontal {
      flex-direction: row;
      min-height: calc(100% - 49px);
      .request {
        flex: 1;
        overflow-x: auto;
        border-right: 1px solid var(--color-text-n8);
        .ms-scroll-bar();
        .request-tab-pane {
          min-width: 800px;
        }
      }
      .response {
        flex: 1;
        min-width: 300px;
      }
    }
  }
</style>
