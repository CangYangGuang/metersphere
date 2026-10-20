<template>
  <a-spin :loading="loading" class="w-full">
    <GrpcReportDetailPanel v-if="isGrpc && grpcContent" :content="grpcContent" />
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

  import GrpcReportDetailPanel from '@/views/api-test/components/requestComposition/grpc/grpcReportDetailPanel.vue';
  import StepDetailContent from '@/views/api-test/components/requestComposition/response/result/index.vue';

  import { reportCaseStepDetail } from '@/api/modules/api-test/report';

  import type { GrpcCaseReportContent } from '@/models/apiTest/grpc';
  import type { ReportStepDetailItem, ScenarioItemType } from '@/models/apiTest/report';

  import { isGrpcReportContent } from '@/views/api-test/components/requestComposition/grpc/utils';

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
  const grpcContent = ref<GrpcCaseReportContent | null>(null);
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
        res = (await reportCaseStepDetail(
          reportId,
          props.stepItem.stepId,
          route.query.shareId as string | undefined
        )) as ReportStepDetailItem[];
      }
      const detail = res?.[0] ? (cloneDeep(res[0]) as ReportStepDetailItem) : undefined;
      if (!detail) {
        isGrpc.value = false;
        grpcContent.value = null;
        stepDetailItem.value = undefined;
        return;
      }
      stepDetailItem.value = detail;
      if (isGrpcReportContent(detail.content)) {
        isGrpc.value = true;
        grpcContent.value = detail.content as GrpcCaseReportContent;
      } else {
        isGrpc.value = false;
        grpcContent.value = null;
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
