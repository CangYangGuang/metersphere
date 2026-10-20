<template>
  <a-spin
    :loading="batchLoading || loading"
    :tip="batchLoading ? batchExportTip : t('report.detail.exportingPdf')"
    class="report-detail-container"
  >
    <div id="report-detail" class="report-detail">
      <div class="report-header">
        <div class="flex-1 break-all">{{ detail.name }}</div>
        <div class="one-line-text">
          <span class="text-[var(--color-text-4)]">{{ t('report.detail.api.executionTime') }}</span>
          {{ detail.startTime ? dayjs(detail.startTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          <span class="text-[var(--color-text-4)]">{{ t('report.detail.api.executionTimeTo') }}</span>
          {{ detail.endTime ? dayjs(detail.endTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </div>
      </div>
      <div class="analysis-wrapper" :data-cards="cardCount">
        <div class="analysis min-w-[330px]">
          <div class="block-title">{{ t('report.detail.api.reportAnalysis') }}</div>
          <ReportMetricsItem
            v-for="analysisItem in reportAnalysisList"
            :key="analysisItem.name"
            :item-info="analysisItem"
          />
        </div>
        <div class="analysis min-w-[410px]">
          <ExecuteAnalysis :detail="detail" :animation="false" />
        </div>
        <div v-if="functionalCaseTotal" class="analysis min-w-[330px]">
          <div class="block-title">{{ t('report.detail.useCaseAnalysis') }}</div>
          <div class="flex">
            <div class="mr-[24px] flex-1">
              <SingleStatusProgress :detail="detail" type="FUNCTIONAL" status="pending" />
              <SingleStatusProgress :detail="detail" type="FUNCTIONAL" status="success" />
              <SingleStatusProgress :detail="detail" type="FUNCTIONAL" status="block" />
              <SingleStatusProgress :detail="detail" type="FUNCTIONAL" status="error" class="!mb-0" />
            </div>
            <div class="relative">
              <div class="charts">
                <div class="text-[12px] !text-[var(--color-text-4)]">{{ t('report.passRate') }}</div>
                <div class="flex justify-center text-[16px] font-medium">
                  <div class="one-line-text max-w-[100px] text-[var(--color-text-1)]">{{ functionCasePassRate }} </div>
                </div>
              </div>
              <div class="flex h-full w-full items-center justify-center">
                <MsChart width="100px" height="100px" :options="functionCaseOptions" />
              </div>
            </div>
          </div>
        </div>
        <div v-if="apiCaseTotal" class="analysis min-w-[330px]">
          <div class="block-title">{{ t('report.detail.apiUseCaseAnalysis') }}</div>
          <div class="flex">
            <div class="mr-[24px] flex-1">
              <SingleStatusProgress type="API" :detail="detail" status="pending" />
              <SingleStatusProgress type="API" :detail="detail" status="success" />
              <SingleStatusProgress type="API" :detail="detail" status="fakeError" />
              <SingleStatusProgress type="API" :detail="detail" status="error" class="!mb-0" />
            </div>
            <div class="relative">
              <div class="charts">
                <div class="text-[12px] !text-[var(--color-text-4)]">{{ t('report.passRate') }}</div>
                <div class="flex justify-center text-[16px] font-medium">
                  <div class="one-line-text max-w-[100px] text-[var(--color-text-1)]">{{ apiCasePassRate }} </div>
                </div>
              </div>
              <div class="flex h-full w-full items-center justify-center">
                <MsChart width="100px" height="100px" :options="apiCaseOptions" />
              </div>
            </div>
          </div>
        </div>
        <div v-if="scenarioCaseTotal" class="analysis min-w-[330px]">
          <div class="block-title">{{ t('report.detail.scenarioUseCaseAnalysis') }}</div>
          <div class="flex">
            <div class="mr-[24px] flex-1">
              <SingleStatusProgress type="SCENARIO" :detail="detail" status="pending" />
              <SingleStatusProgress type="SCENARIO" :detail="detail" status="success" />
              <SingleStatusProgress type="SCENARIO" :detail="detail" status="fakeError" />
              <SingleStatusProgress type="SCENARIO" :detail="detail" status="error" class="!mb-0" />
            </div>
            <div class="relative">
              <div class="charts">
                <div class="text-[12px] !text-[var(--color-text-4)]">{{ t('report.passRate') }}</div>
                <div class="flex justify-center text-[16px] font-medium">
                  <div class="one-line-text max-w-[100px] text-[var(--color-text-1)]">{{ scenarioCasePassRate }} </div>
                </div>
              </div>
              <div class="flex h-full w-full items-center justify-center">
                <MsChart width="100px" height="100px" :options="scenarioCaseOptions" />
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="mt-[16px]">
        <div v-for="item of innerCardList" :id="`${item.value}`" :key="item.id" class="card-item mt-[16px]">
          <div class="wrapper-preview-card">
            <div class="flex items-center justify-between">
              <div class="mb-[8px] font-medium">
                {{ t(item.label) }}
              </div>
            </div>
            <ReportDetailTable
              v-if="item.value === ReportCardTypeEnum.SUB_PLAN_DETAIL"
              v-model:current-mode="currentMode"
              :report-id="detail.id"
              :share-id="shareId"
              is-preview
            />
            <div v-else-if="item.value === ReportCardTypeEnum.CUSTOM_CARD" ref="customCardRef">
              <MsRichText :raw="item.content" :editable="false" />
            </div>
            <div
              v-else-if="item.value === ReportCardTypeEnum.SUMMARY && (isDefaultLayout ? detail.summary : item.content)"
              ref="summaryRef"
            >
              <MsRichText :raw="isDefaultLayout ? detail.summary : item.content" :editable="false" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </a-spin>
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import { cloneDeep } from 'lodash-es';
  import dayjs from 'dayjs';

  import MsChart from '@/components/pure/chart/index.vue';
  import MsRichText from '@/components/pure/ms-rich-text/MsRichText.vue';
  import type { MsTableColumn } from '@/components/pure/ms-table/type';
  import { lastExecuteResultMap } from '@/components/business/ms-case-associate/utils';
  import { IconType } from '@/views/api-test/report/component/reportStatus.vue';
  import SingleStatusProgress from '@/views/test-plan/report/component/singleStatusProgress.vue';
  import ExecuteAnalysis from '@/views/test-plan/report/detail/component/system-card/executeAnalysis.vue';
  import ReportDetailTable from '@/views/test-plan/report/detail/component/system-card/reportDetailTable.vue';
  import ReportMetricsItem from '@/views/test-plan/report/detail/component/system-card/ReportMetricsItem.vue';

  import {
    getApiPage,
    getReportBugList,
    getReportDetail,
    getReportDetailPage,
    getReportDetailSharePage,
    getReportFeatureCaseList,
    getReportLayout,
    getReportShareBugList,
    getReportShareFeatureCaseList,
    getScenarioPage,
    logTestPlanReportBatchExport,
    logTestPlanReportExport,
    reportCaseDetail,
    reportCaseStepDetail,
    reportScenarioDetail,
    reportStepDetail,
    testPlanBatchReportExportGetIds,
  } from '@/api/modules/test-plan/report';
  import {
    commonConfig,
    defaultCount,
    defaultReportDetail,
    seriesConfig,
    statusConfig,
    toolTipConfig,
  } from '@/config/testPlan';
  import exportPDF, { PAGE_PDF_WIDTH_RATIO, PdfTableConfig } from '@/hooks/useExportPDF';
  import { useI18n } from '@/hooks/useI18n';
  import useTableStore from '@/hooks/useTableStore';
  import useTestPlanReportStore from '@/store/modules/testPlan/testPlanReport';
  import { addCommasToNumber, characterLimit } from '@/utils';

  import type { ReportDetail, ScenarioItemType } from '@/models/apiTest/report';
  import { BatchApiParams } from '@/models/common';
  import type { ApiOrScenarioCaseItem } from '@/models/testPlan/report';
  import type {
    configItem,
    countDetail,
    PlanReportDetail,
    ReportMetricsItemModel,
    StatusListType,
  } from '@/models/testPlan/testPlanReport';
  import { TableKeyEnum } from '@/enums/tableEnum';
  import { ReportCardTypeEnum } from '@/enums/testPlanReportEnum';

  import { defaultGroupConfig, defaultSingleConfig, iconTypeStatus } from './component/reportConfig';
  import { getSummaryDetail } from '@/views/test-plan/report/utils';
  import { ColumnInput, RowInput } from 'jspdf-autotable';

  const { t } = useI18n();
  const route = useRoute();
  const testPlanReportStore = useTestPlanReportStore();

  interface PdfStepDetail {
    id?: string;
    stepId?: string;
    status?: string;
    requestTime?: number;
    code?: string;
    fakeCode?: string;
    content?: any;
  }

  const innerCardList = ref<configItem[]>([]);
  const layoutShowCards = ref<any[]>([]);

  const detail = ref<PlanReportDetail>({ ...cloneDeep(defaultReportDetail) });
  const reportId = ref<string>(route.query.id as string);
  const isGroup = computed(() => route.query.type === 'GROUP');
  const loading = ref<boolean>(true);
  const customCardRef = ref<(HTMLElement | null)[]>([]);
  const summaryRef = ref<(HTMLElement | null)[]>([]);

  /**
   * 分享share
   */
  const shareId = ref<string>(route.query.shareId as string);

  // 功能用例分析
  const functionCaseOptions = ref({
    ...commonConfig,
    tooltip: {
      ...toolTipConfig,
    },
    animation: false, // 关闭渲染动画
    series: {
      ...seriesConfig,
      data: [
        {
          value: 0,
          name: t('common.success'),
          itemStyle: {
            color: '#00C261',
          },
        },
      ],
    },
  });
  // 接口用例分析
  const apiCaseOptions = ref({
    ...commonConfig,
    tooltip: {
      ...toolTipConfig,
    },
    animation: false, // 关闭渲染动画
    series: {
      ...seriesConfig,
      data: [
        {
          value: 0,
          name: t('common.success'),
          itemStyle: {
            color: '#00C261',
          },
        },
      ],
    },
  });
  // 场景用例分析
  const scenarioCaseOptions = ref({
    ...commonConfig,
    tooltip: {
      ...toolTipConfig,
    },
    animation: false, // 关闭渲染动画
    series: {
      ...seriesConfig,
      data: [
        {
          value: 0,
          name: t('common.success'),
          itemStyle: {
            color: '#00C261',
          },
        },
      ],
    },
  });

  // 获取通过率
  function getPassRateData(caseDetailCount: countDetail) {
    const caseCountDetail = caseDetailCount || defaultCount;
    const passRateData = statusConfig.filter((item) => ['success'].includes(item.value));
    const { success } = caseCountDetail;
    const valueList = success ? statusConfig : passRateData;
    const chartBorderWidth = valueList.filter((e) => Number(caseCountDetail[e.value]) > 0).length === 1 ? 0 : 2;
    return valueList
      .filter((item) => caseCountDetail[item.value] > 0)
      .map((item: StatusListType) => {
        return {
          value: caseCountDetail[item.value] || 0,
          name: t(item.label),
          itemStyle: {
            color: success ? item.color : '#D4D4D8',
            borderWidth: chartBorderWidth,
            borderColor: '#ffffff',
          },
        };
      });
  }

  // 初始化图表
  function initOptionsData() {
    const { functionalCount, apiCaseCount, apiScenarioCount } = detail.value;
    functionCaseOptions.value.series.data = getPassRateData(functionalCount);
    apiCaseOptions.value.series.data = getPassRateData(apiCaseCount);
    scenarioCaseOptions.value.series.data = getPassRateData(apiScenarioCount);
  }

  const functionCasePassRate = computed(() => {
    const apiCaseDetail = getSummaryDetail(detail.value.functionalCount || defaultCount);
    return apiCaseDetail.successRate;
  });

  const apiCasePassRate = computed(() => {
    const apiCaseDetail = getSummaryDetail(detail.value.apiCaseCount || defaultCount);
    return apiCaseDetail.successRate;
  });

  const scenarioCasePassRate = computed(() => {
    const apiScenarioDetail = getSummaryDetail(detail.value.apiScenarioCount || defaultCount);
    return apiScenarioDetail.successRate;
  });
  const functionalCaseTotal = computed(() => getSummaryDetail(detail.value.functionalCount).caseTotal);
  const apiCaseTotal = computed(() => getSummaryDetail(detail.value.apiCaseCount).caseTotal);
  const scenarioCaseTotal = computed(() => getSummaryDetail(detail.value.apiScenarioCount).caseTotal);

  const reportAnalysisList = computed<ReportMetricsItemModel[]>(() => {
    if (isGroup.value) {
      return [
        {
          name: t('report.detail.testPlanTotal'),
          value: detail.value.planCount,
          unit: t('report.detail.number'),
          icon: 'plan_total',
        },
        {
          name: t('report.detail.testPlanCaseTotal'),
          value: detail.value.caseTotal,
          unit: t('report.detail.number'),
          icon: 'case_total',
        },
        {
          name: t('report.passRate'),
          value: detail.value.passRate,
          unit: '%',
          icon: 'passRate',
        },
        {
          name: t('report.detail.totalDefects'),
          value: addCommasToNumber(detail.value.bugCount),
          unit: t('report.detail.number'),
          icon: 'bugTotal',
        },
      ];
    }
    return [
      {
        name: t('report.detail.threshold'),
        value: detail.value.passThreshold,
        unit: '%',
        icon: 'threshold',
      },
      {
        name: t('report.passRate'),
        value: detail.value.passRate,
        unit: '%',
        icon: 'passRate',
      },
      {
        name: t('report.detail.performCompletion'),
        value: detail.value.executeRate,
        unit: '%',
        icon: 'passRate',
      },
      {
        name: t('report.detail.totalDefects'),
        value: addCommasToNumber(detail.value.bugCount),
        unit: t('report.detail.number'),
        icon: 'bugTotal',
      },
    ];
  });

  const cardCount = computed(() => {
    const totalList = [functionalCaseTotal.value, apiCaseTotal.value, scenarioCaseTotal.value];
    let count = 2;
    totalList.forEach((item: number) => {
      if (item > 0) {
        count++;
      }
    });
    return count;
  });
  const currentMode = ref<string>('drawer');

  async function getDefaultLayout() {
    try {
      const res = await getReportLayout(detail.value.id, shareId.value);
      layoutShowCards.value = res;
      innerCardList.value = res
        .filter((e: any) => [ReportCardTypeEnum.CUSTOM_CARD, ReportCardTypeEnum.SUMMARY].includes(e.name))
        .map((item: any) => {
          return {
            id: item.id,
            value: item.name,
            label: item.label,
            content: item.value || '',
            type: item.type,
            enableEdit: false,
            richTextTmpFileIds: item.richTextTmpFileIds,
          };
        });
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    }
  }

  const isDefaultLayout = ref<boolean>(false);
  const testSetColumns = ref<MsTableColumn>([
    {
      title: 'ms.case.associate.testSet',
      dataIndex: 'collectionName',
    },
  ]);

  /** 缺陷明细 */
  const bugDefaultColumns: MsTableColumn = [
    {
      title: 'ID',
      dataIndex: 'num',
      width: 80,
    },
    {
      title: 'bugManagement.bugName',
      dataIndex: 'title',
    },
    {
      title: 'bugManagement.status',
      dataIndex: 'status',
      width: 80,
    },
    {
      title: 'bugManagement.handleMan',
      dataIndex: 'handleUserName',
      width: 150,
    },
    {
      title: 'bugManagement.numberOfCase',
      dataIndex: 'relationCaseCount',
      width: 70,
    },
  ];
  const reportBugList = () => {
    return !shareId.value ? getReportBugList : getReportShareBugList;
  };

  /** 用例明细 */
  const staticColumns: MsTableColumn = [
    {
      title: 'ID',
      dataIndex: 'num',
      width: 80,
    },
    {
      title: 'case.caseName',
      dataIndex: 'name',
    },
    {
      title: 'common.executionResult',
      dataIndex: 'executeResult',
      slotName: 'lastExecResult',
      width: 80,
    },
  ];
  const lastStaticColumns: MsTableColumn = [
    {
      title: 'common.belongModule',
      dataIndex: 'moduleName',
      width: 200,
    },
    {
      title: 'case.caseLevel',
      dataIndex: 'priority',
      slotName: 'caseLevel',
      width: 80,
    },

    {
      title: 'testPlan.featureCase.executor',
      dataIndex: 'executeUser',
      width: 150,
    },
    {
      title: 'testPlan.featureCase.bugCount',
      dataIndex: 'bugCount',
      width: 70,
    },
  ];

  const testPlanNameColumns: MsTableColumn = [
    {
      title: 'report.plan.name',
      dataIndex: 'planName',
      width: 200,
    },
  ];

  const caseDefaultColumns = computed(() => {
    if (isGroup.value) {
      return [...staticColumns, ...testPlanNameColumns, ...lastStaticColumns];
    }
    return [...staticColumns, ...lastStaticColumns];
  });

  const reportFeatureCaseList = () => {
    return !shareId.value ? getReportFeatureCaseList : getReportShareFeatureCaseList;
  };

  /** 接口/场景明细 */
  const apiStaticColumns: MsTableColumn = [
    {
      title: 'ID',
      dataIndex: 'num',
      width: 110,
    },
    {
      title: 'common.name',
      dataIndex: 'name',
    },
    {
      title: 'report.detail.level',
      dataIndex: 'priority',
      slotName: 'priority',
      width: 80,
    },
    {
      title: 'common.executionResult',
      dataIndex: 'executeResult',
      slotName: 'lastExecResult',
      width: 100,
    },
  ];

  const apiDefaultColumns = computed(() => {
    if (isGroup.value) {
      return [
        ...apiStaticColumns,
        ...testPlanNameColumns,
        ...lastStaticColumns.filter((e) => e.dataIndex !== 'priority'),
      ];
    }
    return [...apiStaticColumns, ...lastStaticColumns.filter((e) => e.dataIndex !== 'priority')];
  });

  const fullCaseList = ref<any>([]);
  async function initCaseList() {
    fullCaseList.value = (
      await reportFeatureCaseList()({
        current: 1,
        pageSize: 500,
        reportId: reportId.value,
        shareId: shareId.value ?? undefined,
        startPager: false,
      })
    ).list;
  }

  const fullBugList = ref<any>([]);
  async function initBugList() {
    fullBugList.value = (
      await reportBugList()({
        current: 1,
        pageSize: 500,
        reportId: reportId.value,
        shareId: shareId.value ?? undefined,
        startPager: false,
      })
    ).list;
  }

  const fullApiList = ref<any>([]);
  async function initApiList() {
    fullApiList.value = (
      await getApiPage({
        current: 1,
        pageSize: 500,
        reportId: reportId.value,
        shareId: shareId.value ?? undefined,
        startPager: false,
      })
    ).list;
  }

  const fullScenarioList = ref<any>([]);
  async function initScenarioList() {
    fullScenarioList.value = (
      await getScenarioPage({
        current: 1,
        pageSize: 500,
        reportId: reportId.value,
        shareId: shareId.value ?? undefined,
        startPager: false,
      })
    ).list;
  }

  const groupColumns: MsTableColumn = [
    {
      title: 'report.plan.name',
      dataIndex: 'testPlanName',
      width: 180,
    },
    {
      title: 'report.detail.testPlanGroup.result',
      dataIndex: 'resultStatus',
      width: 200,
    },
    {
      title: 'report.detail.threshold',
      dataIndex: 'passThreshold',
      width: 150,
    },
    {
      title: 'report.passRate',
      dataIndex: 'passRate',
      width: 150,
    },
    {
      title: 'report.detail.testPlanGroup.useCasesCount',
      dataIndex: 'caseTotal',
      width: 100,
    },
  ];

  const reportDetailList = () => {
    return !shareId.value ? getReportDetailPage : getReportDetailSharePage;
  };
  const fullGroupList = ref<PlanReportDetail[]>([]);
  async function initGroupList() {
    fullGroupList.value = (
      await reportDetailList()({
        current: 1,
        pageSize: 500,
        reportId: reportId.value,
        shareId: shareId.value ?? undefined,
        startPager: false,
      })
    ).list;
  }
  function getExecutionResult(status?: string): IconType {
    return status && iconTypeStatus[status] ? iconTypeStatus[status] : iconTypeStatus.DEFAULT;
  }

  function doExport(name: string, tableArr: PdfTableConfig[], detailTableTitleMap: Record<string, string> = {}) {
    exportPDF(
      name,
      'report-detail',
      tableArr,
      {
        group: t('report.detail.subPlanDetails'),
        bug: t('report.detail.bugDetails'),
        case: t('report.detail.featureCaseDetails'),
        apiCase: t('report.detail.apiCaseDetails'),
        scenario: t('report.detail.scenarioCaseDetails'),
        ...detailTableTitleMap,
      },
      () => {
        loading.value = false;
        Message.success(t('report.detail.exportPdfSuccess', { name: characterLimit(name, 50) }));
      }
    );
  }

  const tableStore = useTableStore();
  const keyMap: Record<string, any> = {
    GROUP: {
      [ReportCardTypeEnum.API_CASE_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_API_TABLE_GROUP,
      [ReportCardTypeEnum.SCENARIO_CASE_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_SCENARIO_TABLE_GROUP,
      [ReportCardTypeEnum.BUG_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_BUG_TABLE_DETAIL_GROUP,
      [ReportCardTypeEnum.FUNCTIONAL_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_FUNCTIONAL_TABLE_GROUP,
    },
    TEST_PLAN: {
      [ReportCardTypeEnum.API_CASE_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_API_TABLE,
      [ReportCardTypeEnum.SCENARIO_CASE_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_SCENARIO_TABLE,
      [ReportCardTypeEnum.BUG_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_BUG_TABLE_DETAIL,
      [ReportCardTypeEnum.FUNCTIONAL_DETAIL]: TableKeyEnum.TEST_PLAN_REPORT_FUNCTIONAL_TABLE,
    },
  };

  function getTableKey(type: ReportCardTypeEnum) {
    if (isGroup.value) {
      return keyMap.GROUP[type];
    }
    return keyMap.TEST_PLAN[type];
  }

  async function getColumns(type: ReportCardTypeEnum, defaultColumns: MsTableColumn) {
    let columns = (await tableStore.getShowInTableColumns(getTableKey(type))).filter(
      (e) => e.dataIndex !== 'operation'
    );
    if (columns.length === 0) {
      columns = defaultColumns;
    }
    if (
      (isGroup.value && !testPlanReportStore.getTestStatus(isGroup.value, type)) ||
      (!isGroup.value && testPlanReportStore.getTestStatus(isGroup.value, type))
    ) {
      columns.splice(3, 0, ...testPlanNameColumns);
    }
    if (testPlanReportStore.getTestStatus(isGroup.value, type)) {
      columns = testSetColumns.value.concat(columns);
    }
    return columns;
  }

  async function initTablesColumns() {
    const bugColumns = await tableStore.getShowInTableColumns(getTableKey(ReportCardTypeEnum.BUG_DETAIL));
    const functionalCaseColumns = await getColumns(ReportCardTypeEnum.FUNCTIONAL_DETAIL, caseDefaultColumns.value);
    const apiColumns = await getColumns(ReportCardTypeEnum.API_CASE_DETAIL, apiDefaultColumns.value);
    const scenarioColumns = await getColumns(ReportCardTypeEnum.SCENARIO_CASE_DETAIL, apiDefaultColumns.value);

    return {
      apiColumns,
      scenarioColumns,
      bugColumns: bugColumns.length > 0 ? bugColumns : bugDefaultColumns,
      functionalCaseColumns,
    };
  }

  function flattenReportSteps(
    steps: ScenarioItemType[] = [],
    result: ScenarioItemType[] = [],
    seen = new Set<string>()
  ) {
    steps.forEach((step) => {
      if (step.stepId && !seen.has(step.stepId)) {
        seen.add(step.stepId);
        result.push(step);
      }
      if (step.children?.length) flattenReportSteps(step.children, result, seen);
    });
    return result;
  }

  function displayDetailValue(value: unknown): string {
    if (value === undefined || value === null || value === '') return '-';
    if (typeof value === 'string') {
      try {
        return JSON.stringify(JSON.parse(value), null, 2);
      } catch {
        return value;
      }
    }
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }

  function statusText(status?: string) {
    return (
      {
        SUCCESS: '成功',
        ERROR: '失败',
        FAKE_ERROR: '误报',
        PENDING: '未执行',
        RUNNING: '执行中',
        COMPLETED: '已完成',
      }[status || ''] ||
      status ||
      '-'
    );
  }

  function assertionText(assertions: any[] = []) {
    if (!assertions.length) return '无断言';
    return assertions
      .map((assertion, index) => {
        let result = '-';
        if (assertion?.pass === true) result = '通过';
        if (assertion?.pass === false) result = '失败';
        return `${index + 1}. ${assertion?.name || '断言'} | ${assertion?.assertionType || '-'} ${
          assertion?.condition || '-'
        } | 期望: ${displayDetailValue(assertion?.expectedValue)} | 实际: ${displayDetailValue(
          assertion?.actualValue
        )} | ${result}${assertion?.message ? ` | ${assertion.message}` : ''}`;
      })
      .join('\n');
  }

  function extractText(extracts: any[] = []) {
    if (!extracts.length) return '无变量提取';
    return extracts
      .map(
        (item, index) =>
          `${index + 1}. ${item?.name || '-'} | ${item?.type || '-'} | ${
            item?.expression || '-'
          } | ${displayDetailValue(item?.value)}`
      )
      .join('\n');
  }

  function failureReason(status: string | undefined, content: any, assertions: any[]) {
    const failedAssertion = assertions.find((assertion) => assertion?.pass === false);
    if (failedAssertion) {
      return `${failedAssertion.name || '断言'}未通过：期望 ${displayDetailValue(
        failedAssertion.expectedValue
      )}，实际 ${displayDetailValue(failedAssertion.actualValue)}${
        failedAssertion.message ? `；${failedAssertion.message}` : ''
      }`;
    }
    if (status === 'SUCCESS' || content.success === true || content.isSuccessful === true) return '-';
    return (
      content.errorMessage ||
      content.fakeErrorMessage ||
      content.responseResult?.responseMessage ||
      content.grpcStatus ||
      (status === 'ERROR' ? '执行失败，未返回错误消息' : '-')
    );
  }

  function buildStepDetailRows(
    resourceType: string,
    item: ApiOrScenarioCaseItem,
    step: ScenarioItemType,
    detailItems: PdfStepDetail[]
  ) {
    const normalizedItems = detailItems.length ? detailItems : [{ content: {} }];
    return normalizedItems.flatMap((detailItem, detailIndex) => {
      const raw = (detailItem.content || {}) as any;
      const response = raw.responseResult || {};
      const urlParts = String(raw.url || '')
        .split('/')
        .filter(Boolean);
      const serviceName = raw.serviceName || (urlParts.length >= 2 ? urlParts[urlParts.length - 2] : '');
      const methodName = raw.methodName || (urlParts.length ? urlParts[urlParts.length - 1] : raw.method);
      const suffix = [serviceName, methodName].filter(Boolean).join('/');
      const serverAddress =
        raw.serverAddress ||
        (raw.url && suffix && raw.url.endsWith(suffix) ? raw.url.slice(0, -(suffix.length + 1)) : raw.url);
      const assertions = raw.assertionResults || response.assertions || [];
      const extracts = raw.extractResults || response.extractResults || [];
      const status = detailItem.status || step.status;
      const prefix = detailItems.length > 1 ? `子请求 ${detailIndex + 1} - ` : '';
      return [
        { field: `${prefix}类型`, value: resourceType },
        { field: `${prefix}用例`, value: `${item.num || '-'} ${item.name || '-'}` },
        { field: `${prefix}步骤`, value: `${step.index || step.sort || '-'} ${step.name || step.requestName || '-'}` },
        { field: `${prefix}执行结果`, value: statusText(status) },
        { field: `${prefix}失败原因`, value: failureReason(status, raw, assertions) },
        { field: `${prefix}协议`, value: raw.protocol || raw.method || '-' },
        { field: `${prefix}服务`, value: serviceName || '-' },
        { field: `${prefix}方法`, value: methodName || '-' },
        { field: `${prefix}服务器地址`, value: serverAddress || '-' },
        {
          field: `${prefix}执行耗时`,
          value: `${raw.duration ?? response.responseTime ?? detailItem.requestTime ?? step.requestTime ?? 0} ms`,
        },
        { field: `${prefix}gRPC Status`, value: raw.grpcStatus || response.responseMessage || '-' },
        {
          field: `${prefix}gRPC Code`,
          value: displayDetailValue(raw.grpcCode ?? response.responseCode ?? detailItem.code ?? step.code),
        },
        { field: `${prefix}错误码`, value: raw.errorCode || raw.fakeErrorCode || detailItem.fakeCode || '-' },
        { field: `${prefix}错误消息`, value: raw.errorMessage || raw.fakeErrorMessage || '-' },
        { field: `${prefix}请求 JSON`, value: displayDetailValue(raw.requestJson || raw.body) },
        { field: `${prefix}响应 JSON`, value: displayDetailValue(raw.responseJson || response.body) },
        { field: `${prefix}断言结果`, value: assertionText(assertions) },
        { field: `${prefix}变量提取结果`, value: extractText(extracts) },
      ];
    });
  }

  async function loadReportDetailTables(
    resourceType: string,
    items: ApiOrScenarioCaseItem[],
    reportLoader: (id: string, detailShareId?: string) => Promise<ReportDetail>,
    stepLoader: (detailReportId: string, stepId: string, detailShareId?: string) => Promise<any>
  ) {
    const tables: PdfTableConfig[] = [];
    const titles: Record<string, string> = {};
    const validItems = items.filter((item) => item.reportId);
    const reportConcurrency = 4;
    for (let index = 0; index < validItems.length; index += reportConcurrency) {
      const batch = validItems.slice(index, index + reportConcurrency);
      // eslint-disable-next-line no-await-in-loop
      const bundles = await Promise.all(
        batch.map(async (item) => {
          try {
            const report = await reportLoader(item.reportId, shareId.value || undefined);
            const steps = flattenReportSteps(report.children);
            const detailEntries = await Promise.all(
              steps.map(async (step) => {
                try {
                  const response = await stepLoader(report.id, step.stepId, shareId.value || undefined);
                  let details: PdfStepDetail[] = [];
                  if (Array.isArray(response)) details = response;
                  else if (response) details = [response];
                  return [step.stepId, details] as const;
                } catch (error) {
                  return [
                    step.stepId,
                    [
                      {
                        id: `${step.stepId}-load-error`,
                        stepId: step.stepId,
                        status: step.status,
                        content: { errorMessage: `步骤详情加载失败：${String(error)}` },
                      },
                    ] as PdfStepDetail[],
                  ] as const;
                }
              })
            );
            const detailMap = Object.fromEntries(detailEntries) as Record<string, PdfStepDetail[]>;
            return steps.map((step, stepIndex) => {
              const tableId = `execution-${resourceType}-${item.reportId}-${stepIndex}`;
              return {
                table: {
                  tableId,
                  columnStyles: {
                    field: { cellWidth: 150 / PAGE_PDF_WIDTH_RATIO },
                    value: { cellWidth: 1040 / PAGE_PDF_WIDTH_RATIO },
                  },
                  columns: [
                    { title: '字段', dataKey: 'field' },
                    { title: '内容', dataKey: 'value' },
                  ] as ColumnInput[],
                  body: buildStepDetailRows(resourceType, item, step, detailMap[step.stepId] || []) as RowInput[],
                } as PdfTableConfig,
                title: `${resourceType} ${item.num || '-'} ${item.name || '-'} - 步骤 ${
                  step.index || step.sort || stepIndex + 1
                } ${step.name || step.requestName || '-'}`,
              };
            });
          } catch (error) {
            const tableId = `execution-${resourceType}-${item.reportId}-load-error`;
            return [
              {
                table: {
                  tableId,
                  columnStyles: {
                    field: { cellWidth: 150 / PAGE_PDF_WIDTH_RATIO },
                    value: { cellWidth: 1040 / PAGE_PDF_WIDTH_RATIO },
                  },
                  columns: [
                    { title: '字段', dataKey: 'field' },
                    { title: '内容', dataKey: 'value' },
                  ] as ColumnInput[],
                  body: [
                    { field: '用例', value: `${item.num || '-'} ${item.name || '-'}` },
                    { field: '失败原因', value: `报告明细加载失败：${String(error)}` },
                  ] as RowInput[],
                } as PdfTableConfig,
                title: `${resourceType} ${item.num || '-'} ${item.name || '-'} - 明细加载失败`,
              },
            ];
          }
        })
      );
      bundles.flat(2).forEach((bundle) => {
        tables.push(bundle.table);
        titles[bundle.table.tableId as string] = bundle.title;
      });
    }
    return { tables, titles };
  }

  async function buildExecutionDetailTables() {
    const apiDetails = await loadReportDetailTables(
      '接口用例',
      fullApiList.value,
      reportCaseDetail,
      reportCaseStepDetail
    );
    const scenarioDetails = await loadReportDetailTables(
      '场景用例',
      fullScenarioList.value,
      reportScenarioDetail,
      reportStepDetail
    );
    return {
      tables: [...apiDetails.tables, ...scenarioDetails.tables],
      titles: { ...apiDetails.titles, ...scenarioDetails.titles },
    };
  }

  async function realExportPdf(name: string) {
    const tableArr: PdfTableConfig[] = [];
    let detailTableTitleMap: Record<string, string> = {};
    if (!isDefaultLayout.value) {
      await getDefaultLayout();
    } else {
      innerCardList.value = (isGroup.value ? cloneDeep(defaultGroupConfig) : cloneDeep(defaultSingleConfig)).filter(
        (e: any) => [ReportCardTypeEnum.CUSTOM_CARD, ReportCardTypeEnum.SUMMARY].includes(e.value)
      );
      layoutShowCards.value = isGroup.value ? cloneDeep(defaultGroupConfig) : cloneDeep(defaultSingleConfig);
    }
    if (isGroup.value) {
      await initGroupList();
      tableArr.push({
        tableId: 'group',
        columnStyles: {
          testPlanName: { cellWidth: 710 / PAGE_PDF_WIDTH_RATIO },
          resultStatus: { cellWidth: 130 / PAGE_PDF_WIDTH_RATIO },
          passThreshold: { cellWidth: 130 / PAGE_PDF_WIDTH_RATIO },
          passRate: { cellWidth: 130 / PAGE_PDF_WIDTH_RATIO },
          caseTotal: { cellWidth: 90 / PAGE_PDF_WIDTH_RATIO },
        },
        columns: groupColumns.map((item) => ({
          ...item,
          title: t(item.title as string),
          dataKey: item.dataIndex,
        })) as ColumnInput[],
        body: fullGroupList.value.map((e) => ({
          ...e,
          resultStatus: t(getExecutionResult(e.resultStatus).label),
          passRate: `${e.passRate}%`,
          passThreshold: `${e.passThreshold}%`,
        })) as RowInput[],
      });
    }
    const pageRequest = [];
    if (
      isDefaultLayout.value ||
      (!isDefaultLayout.value && layoutShowCards.value.some((e) => e.name === ReportCardTypeEnum.BUG_DETAIL))
    ) {
      pageRequest.push(initBugList());
    }
    if (
      isDefaultLayout.value ||
      (!isDefaultLayout.value && layoutShowCards.value.some((e) => e.name === ReportCardTypeEnum.FUNCTIONAL_DETAIL))
    ) {
      pageRequest.push(initCaseList());
    }
    if (
      isDefaultLayout.value ||
      (!isDefaultLayout.value && layoutShowCards.value.some((e) => e.name === ReportCardTypeEnum.API_CASE_DETAIL))
    ) {
      pageRequest.push(initApiList());
    }
    if (
      isDefaultLayout.value ||
      (!isDefaultLayout.value && layoutShowCards.value.some((e) => e.name === ReportCardTypeEnum.SCENARIO_CASE_DETAIL))
    ) {
      pageRequest.push(initScenarioList());
    }
    await Promise.all(pageRequest);
    const { apiColumns, scenarioColumns, bugColumns, functionalCaseColumns } = await initTablesColumns();
    if (fullBugList.value.length > 0) {
      tableArr.push({
        tableId: 'bug',
        columnStyles: {
          num: { cellWidth: 120 / PAGE_PDF_WIDTH_RATIO },
          title: { cellWidth: 600 / PAGE_PDF_WIDTH_RATIO },
          status: { cellWidth: 110 / PAGE_PDF_WIDTH_RATIO },
          handleUserName: { cellWidth: 270 / PAGE_PDF_WIDTH_RATIO },
          relationCaseCount: { cellWidth: 90 / PAGE_PDF_WIDTH_RATIO },
        },
        columns: bugColumns.map((item) => ({
          ...item,
          title: t(item.title as string),
          dataKey: item.dataIndex,
        })) as ColumnInput[],
        body: fullBugList.value,
      });
    }
    if (fullCaseList.value.length > 0) {
      const columnStyles: Record<string, any> = {
        collectionName: { cellWidth: 140 / PAGE_PDF_WIDTH_RATIO },
        num: { cellWidth: 100 / PAGE_PDF_WIDTH_RATIO },
        name: { cellWidth: 240 / PAGE_PDF_WIDTH_RATIO },
        executeResult: { cellWidth: 110 / PAGE_PDF_WIDTH_RATIO },
        planName: { cellWidth: 140 / PAGE_PDF_WIDTH_RATIO },
        priority: { cellWidth: 110 / PAGE_PDF_WIDTH_RATIO },
        moduleName: { cellWidth: 140 / PAGE_PDF_WIDTH_RATIO },
        executeUser: { cellWidth: 100 / PAGE_PDF_WIDTH_RATIO },
        bugCount: { cellWidth: 110 / PAGE_PDF_WIDTH_RATIO },
      };
      if (!isGroup.value && !testPlanReportStore.getTestStatus(isGroup.value, ReportCardTypeEnum.FUNCTIONAL_DETAIL)) {
        delete columnStyles.collectionName;
        delete columnStyles.planName;
        columnStyles.name.cellWidth = 520 / PAGE_PDF_WIDTH_RATIO;
      } else if (
        isGroup.value &&
        !testPlanReportStore.getTestStatus(isGroup.value, ReportCardTypeEnum.FUNCTIONAL_DETAIL)
      ) {
        delete columnStyles.collectionName;
        columnStyles.name.cellWidth = 380 / PAGE_PDF_WIDTH_RATIO;
        columnStyles.planName.cellWidth = 140 / PAGE_PDF_WIDTH_RATIO;
      }
      tableArr.push({
        tableId: 'case',
        columnStyles,
        columns: functionalCaseColumns.map((item) => ({
          ...item,
          title: t(item.title as string),
          dataKey: item.dataIndex,
        })) as ColumnInput[],
        body: fullCaseList.value.map((e: any) => ({
          ...e,
          executeResult: t(lastExecuteResultMap[e.executeResult]?.statusText || '-'),
          executeUser: e.executeUser || '-',
        })) as RowInput[],
      });
    }
    const apiColumnStyles: Record<string, any> = {
      collectionName: { cellWidth: 120 / PAGE_PDF_WIDTH_RATIO },
      num: { cellWidth: 150 / PAGE_PDF_WIDTH_RATIO },
      name: { cellWidth: 180 / PAGE_PDF_WIDTH_RATIO },
      executeResult: { cellWidth: 110 / PAGE_PDF_WIDTH_RATIO },
      planName: { cellWidth: 120 / PAGE_PDF_WIDTH_RATIO },
      priority: { cellWidth: 80 / PAGE_PDF_WIDTH_RATIO },
      requestTime: { cellWidth: 100 / PAGE_PDF_WIDTH_RATIO },
      moduleName: { cellWidth: 120 / PAGE_PDF_WIDTH_RATIO },
      executeUser: { cellWidth: 120 / PAGE_PDF_WIDTH_RATIO },
      bugCount: { cellWidth: 100 / PAGE_PDF_WIDTH_RATIO },
    };
    const scenarioColumnStyles = cloneDeep(apiColumnStyles);
    if (!isGroup.value && !testPlanReportStore.getTestStatus(isGroup.value, ReportCardTypeEnum.API_CASE_DETAIL)) {
      delete apiColumnStyles.collectionName;
      delete apiColumnStyles.planName;
      apiColumnStyles.name.cellWidth = 420 / PAGE_PDF_WIDTH_RATIO;
    } else if (isGroup.value && !testPlanReportStore.getTestStatus(isGroup.value, ReportCardTypeEnum.API_CASE_DETAIL)) {
      delete apiColumnStyles.collectionName;
      apiColumnStyles.name.cellWidth = 300 / PAGE_PDF_WIDTH_RATIO;
      apiColumnStyles.planName.cellWidth = 120 / PAGE_PDF_WIDTH_RATIO;
    }
    if (fullApiList.value.length > 0) {
      tableArr.push({
        tableId: 'apiCase',
        columnStyles: apiColumnStyles,
        columns: apiColumns.map((item) => ({
          ...item,
          title: t(item.title as string),
          dataKey: item.dataIndex,
        })) as ColumnInput[],
        body: fullApiList.value.map((e: any) => ({
          ...e,
          executeResult: t(lastExecuteResultMap[e.executeResult]?.statusText || '-'),
          executeUser: e.executeUser || '-',
        })) as RowInput[],
      });
    }
    if (!isGroup.value && !testPlanReportStore.getTestStatus(isGroup.value, ReportCardTypeEnum.SCENARIO_CASE_DETAIL)) {
      delete scenarioColumnStyles.collectionName;
      delete scenarioColumnStyles.planName;
      scenarioColumnStyles.name.cellWidth = 420 / PAGE_PDF_WIDTH_RATIO;
    } else if (
      isGroup.value &&
      !testPlanReportStore.getTestStatus(isGroup.value, ReportCardTypeEnum.SCENARIO_CASE_DETAIL)
    ) {
      delete scenarioColumnStyles.collectionName;
      scenarioColumnStyles.name.cellWidth = 300 / PAGE_PDF_WIDTH_RATIO;
      scenarioColumnStyles.planName.cellWidth = 120 / PAGE_PDF_WIDTH_RATIO;
    }
    if (fullScenarioList.value.length > 0) {
      tableArr.push({
        tableId: 'scenario',
        columnStyles: scenarioColumnStyles,
        columns: scenarioColumns.map((item) => ({
          ...item,
          title: t(item.title as string),
          dataKey: item.dataIndex,
        })) as ColumnInput[],
        body: fullScenarioList.value.map((e: any) => ({
          ...e,
          executeResult: t(lastExecuteResultMap[e.executeResult]?.statusText || '-'),
          executeUser: e.executeUser || '-',
        })) as RowInput[],
      });
    }
    const executionDetails = await buildExecutionDetailTables();
    tableArr.push(...executionDetails.tables);
    detailTableTitleMap = executionDetails.titles;
    await nextTick(async () => {
      let customCardImages: HTMLImageElement[] = [];
      let summaryImages: HTMLImageElement[] = [];
      if (customCardRef.value[0]) {
        // 可能存在多个自定义卡片
        customCardRef.value.forEach((item) => {
          if (item) {
            customCardImages = customCardImages.concat(
              Array.from(item.querySelectorAll('img')).filter((e) => e.className !== 'ProseMirror-separator')
            );
          }
        });
      }
      if (summaryRef.value[0]) {
        // 只有一个总结
        summaryImages = Array.from(summaryRef.value[0].querySelectorAll('img')).filter(
          (e) => e.className !== 'ProseMirror-separator'
        );
      }
      if (customCardImages.length > 0 || summaryImages.length > 0) {
        let loadedImageCount = 0;
        await new Promise((resolve) => {
          const images = [...customCardImages, ...summaryImages].filter((image) => !image.complete); // 图片的加载是异步的，与 JS 解析是同时进行，所以需要过滤掉此时已经加载完毕的图片
          if (images.length > 0) {
            // 此时还有未加载完成的图片，则给图片绑定加载事件，等待图片加载完毕后导出
            images.forEach((image) => {
              image.onload = () => {
                loadedImageCount += 1;
                if (loadedImageCount === images.length) {
                  doExport(name, tableArr, detailTableTitleMap);
                  resolve(true);
                }
              };
            });
          } else {
            // 此时图片都已加载完毕，则直接导出 PDF
            doExport(name, tableArr, detailTableTitleMap);
            resolve(true);
          }
        });
      } else {
        doExport(name, tableArr, detailTableTitleMap);
      }
    });
  }

  async function getDetail(_reportId?: string) {
    try {
      loading.value = true;
      if (_reportId) {
        reportId.value = _reportId;
      }
      detail.value = await getReportDetail(reportId.value);
      const { defaultLayout, name } = detail.value;
      isDefaultLayout.value = defaultLayout;
      initOptionsData();
      await realExportPdf(name);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
      loading.value = false;
    }
  }

  async function logExport(params?: BatchApiParams) {
    if (params) {
      await logTestPlanReportBatchExport(params);
    } else {
      await logTestPlanReportExport(route.query.id as string);
    }
  }

  const batchLoading = ref<boolean>(false);
  const batchIds = ref<string[]>([]);
  const exportCurrent = ref<number>(0);
  const exportTotal = ref<number>(0);
  const batchExportTip = computed(() =>
    t('report.detail.batchExportingPdf', { current: exportCurrent.value, total: exportTotal.value })
  );

  async function initBatchIds(params: BatchApiParams) {
    try {
      batchLoading.value = true;
      batchIds.value = await testPlanBatchReportExportGetIds(params);
      exportTotal.value = batchIds.value.length;
      while (batchIds.value.length > 0) {
        exportCurrent.value += 1;
        // eslint-disable-next-line no-await-in-loop
        await getDetail(batchIds.value.shift());
      }
      nextTick(() => {
        // 等最后一条成功提示后清空
        Message.clear();
        Message.success(t('report.detail.batchExportPdfSuccess'));
      });
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      batchLoading.value = false;
    }
  }

  onBeforeMount(() => {
    window.addEventListener('message', (event) => {
      if (event.origin !== window.location.origin) {
        return;
      }
      const { data, eventId } = JSON.parse(event.data);
      // 发送响应回旧标签页
      window.opener.postMessage(eventId, window.location.origin);
      // 初始化批量导出报告 id 集合
      initBatchIds(data);
      logExport(data);
    });
    if (reportId.value) {
      getDetail();
      logExport();
    }
  });
</script>

<style lang="less">
  .arco-spin-mask-icon {
    @apply !fixed;
  }
</style>

<style lang="less" scoped>
  .report-detail-container {
    @apply flex justify-center;
    .report-detail {
      @apply overflow-x-auto;

      padding: 16px;
      width: 1190px;
      .ms-scroll-bar();
    }
  }
  .report-header {
    @apply mb-4 flex items-center;

    padding: 16px;
    border-radius: var(--border-radius-large);
    background-color: var(--color-text-fff);
    box-shadow: 0 0 10px rgb(120 56 135 / 5%);
  }
  .block-title {
    @apply mb-4 font-medium;
  }
  .analysis-wrapper {
    @apply mb-4 grid items-center gap-4;
    .analysis {
      padding: 24px;
      height: 250px;
      border: 1px solid transparent;
      background-color: var(--color-text-fff);
      box-shadow: 0 0 10px rgba(120 56 135/ 5%);
      @apply rounded-xl;
      .charts {
        @apply absolute text-center;

        top: 50%;
        right: 0;
        bottom: 0;
        left: 50%;
        z-index: 99;
        width: 70px;
        height: 42px;
        transform: translateY(-50%) translateX(-50%);
      }
    }
    &[data-cards='2'],
    &[data-cards='4'] {
      grid-template-columns: repeat(2, 1fr);
    }
    &[data-cards='3'] {
      grid-template-columns: repeat(3, 1fr);
    }
    // 有5个的时候，上面2个，下面3个
    &[data-cards='5'] {
      grid-template-columns: repeat(6, 1fr);
      & > .analysis:nth-child(1),
      & > .analysis:nth-child(2) {
        grid-column: span 3;
      }
      & > .analysis:nth-child(n + 3) {
        grid-column: span 2;
      }
    }
  }
  .card-item {
    position: relative;
    width: 100%;
    border: 1px solid transparent;
    border-radius: 12px;
    .action {
      position: absolute;
      top: -14px;
      left: 50%;
      transform: translateX(-50%);
      z-index: 9 !important;
      background: var(--color-text-fff);
      opacity: 0;
      @apply flex items-center justify-end;
      .actionList {
        padding: 4px;
        border-radius: 4px;
        @apply flex items-center justify-center;
      }
    }
    &:hover > .action {
      opacity: 1;
    }
    &:hover > .action > .actionList {
      color: rgb(var(--primary-5));
      box-shadow: 0 4px 10px -1px rgba(100 100 102/ 15%);
    }
  }
  .wrapper-preview-card {
    display: flex;
    padding: 16px;
    border-radius: 12px;
    background-color: var(--color-text-fff);
    box-shadow: 0 0 10px rgb(120 56 135 / 5%);
    @apply flex-col;
  }
  :deep(.arco-table-body) {
    max-height: 100% !important;
  }
  :deep(#ms-table-footer-wrapper) {
    @apply hidden;
  }
  .rich-wrapper {
    @apply border-none;
    :deep(.halo-rich-text-editor) {
      @apply !p-0;
      .editor-header {
        @apply hidden;
      }
      .tiptap {
        @apply !p-0;
      }
    }
  }
</style>
