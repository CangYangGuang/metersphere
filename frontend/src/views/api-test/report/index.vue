<template>
  <MsCard simple no-content-padding>
    <a-tabs v-model:active-key="activeTab" class="no-content">
      <a-tab-pane v-for="item of realTabList" :key="item.value" :title="item.label" />
    </a-tabs>
    <a-divider margin="0" class="!mb-[8px]"></a-divider>
    <!-- 报告列表-->
    <ReportList :name="listName" :module-type="activeTab" />
  </MsCard>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { useRoute } from 'vue-router';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import ReportList from './component/reportList.vue';

  import { useI18n } from '@/hooks/useI18n';

  import { ReportEnum } from '@/enums/reportEnum';

  const { t } = useI18n();
  const route = useRoute();
  const activeTab = ref<keyof typeof ReportEnum>(ReportEnum.API_SCENARIO_REPORT);

  function resolveReportTab(type?: string | string[]) {
    const normalized = Array.isArray(type) ? type[0] : type;
    if (normalized === 'API_SCENARIO' || normalized === ReportEnum.API_SCENARIO_REPORT) {
      return ReportEnum.API_SCENARIO_REPORT;
    }
    if (
      normalized === 'API_CASE' ||
      normalized === 'API_REPORT' ||
      normalized === ReportEnum.API_REPORT ||
      route.query.id ||
      route.query.reportId
    ) {
      return ReportEnum.API_REPORT;
    }
    return ReportEnum.API_SCENARIO_REPORT;
  }

  activeTab.value = resolveReportTab(route.query.type as string | undefined);

  watch(
    () => [route.query.type, route.query.id, route.query.reportId],
    () => {
      activeTab.value = resolveReportTab(route.query.type as string | undefined);
    }
  );

  const realTabList = ref([
    {
      value: ReportEnum.API_SCENARIO_REPORT,
      label: t('report.api.scenario'),
    },
    {
      value: ReportEnum.API_REPORT,
      label: t('report.api.case'),
    },
  ]);

  const rightTabList = computed(() => {
    return realTabList.value;
  });

  const listName = computed(() => {
    return rightTabList.value.find((item) => item.value === activeTab.value)?.label || '';
  });
</script>

<style scoped lang="less">
  :deep(.arco-tabs-content) {
    padding-top: 0;
  }
</style>
