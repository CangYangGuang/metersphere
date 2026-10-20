<template>
  <a-spin :loading="loading" class="w-full">
    <GrpcScenarioResponse v-if="isGrpc && grpcRequestResult" :request-result="grpcRequestResult" show-detail-fields />
    <StepDetailContent
      v-else-if="!loading && stepDetailItem"
      v-bind="forwardProps"
      static
      :static-info="stepDetailItem"
    />
  </a-spin>
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router';
  import { cloneDeep } from 'lodash-es';

  import GrpcScenarioResponse from '@/views/api-test/scenario/components/common/grpcScenarioResponse.vue';
  import StepDetailContent from '@/views/api-test/components/requestComposition/response/result/index.vue';

  import { reportStepDetail } from '@/api/modules/api-test/report';

  import type { RequestResult } from '@/models/apiTest/common';
  import type { ReportStepDetailItem, ScenarioItemType } from '@/models/apiTest/report';

  const props = defineProps<{
    mode: 'tiled' | 'tab';
    hideResponseTime?: boolean;
    stepItem?: ScenarioItemType;
    console?: string;
    isDefinition?: boolean;
    showType: 'API' | 'CASE';
    isResponseModel?: boolean;
    reportId?: string;
    steps?: ScenarioItemType[];
    getReportStepDetail?: (...args: any) => Promise<any>;
  }>();

  const route = useRoute();
  const loading = ref(false);
  const isGrpc = ref(false);
  const grpcRequestResult = ref<RequestResult>();
  const stepDetailItem = ref<ReportStepDetailItem>();

  const forwardProps = computed(() => ({
    mode: props.mode,
    hideResponseTime: props.hideResponseTime,
    stepItem: props.stepItem,
    console: props.console,
    isDefinition: props.isDefinition,
    showType: props.showType,
    isResponseModel: props.isResponseModel,
    reportId: props.reportId,
    steps: props.steps,
    getReportStepDetail: props.getReportStepDetail,
  }));

  function isGrpcRequestResult(content: unknown): content is RequestResult {
    return !!content && typeof content === 'object' && (content as RequestResult).method === 'GRPC';
  }

  async function loadStepDetail() {
    if (!props.stepItem?.stepId) {
      return;
    }
    try {
      loading.value = true;
      const reportId = (props.stepItem.reportId || props.reportId) as string;
      let res: ReportStepDetailItem[] | undefined;
      if (props.getReportStepDetail) {
        res = (await props.getReportStepDetail(
          reportId,
          props.stepItem.stepId,
          route.query.shareId as string | undefined
        )) as ReportStepDetailItem[];
      } else {
        res = (await reportStepDetail(
          reportId,
          props.stepItem.stepId,
          route.query.shareId as string | undefined
        )) as ReportStepDetailItem[];
      }
      const detail = res?.[0] ? (cloneDeep(res[0]) as ReportStepDetailItem) : undefined;
      if (!detail) {
        isGrpc.value = false;
        grpcRequestResult.value = undefined;
        stepDetailItem.value = undefined;
        return;
      }
      stepDetailItem.value = detail;
      if (isGrpcRequestResult(detail.content)) {
        isGrpc.value = true;
        grpcRequestResult.value = detail.content as RequestResult;
      } else {
        isGrpc.value = false;
        grpcRequestResult.value = undefined;
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }

  watch(
    () => [props.stepItem?.stepId, props.stepItem?.reportId, props.reportId],
    () => {
      loadStepDetail();
    },
    { immediate: true }
  );
</script>
