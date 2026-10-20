<template>
  <MsDrawer
    v-model:visible="innerVisible"
    :title="modelTitle"
    :width="800"
    :show-continue="!props.currentModelId"
    no-content-padding
    unmount-on-close
    :ok-loading="loading"
    :ok-text="props.currentModelId ? t('common.update') : t('common.save')"
    @confirm="handleDrawerConfirm"
    @continue="handleDrawerConfirm(true)"
    @cancel="handleDrawerCancel"
  >
    <div class="p-[16px]">
      <a-form ref="formRef" :model="form" layout="vertical">
        <div>
          <expandCollapseWrap :title="t('system.config.modelConfig.baseInfo')">
            <template #content>
              <a-form-item
                field="name"
                :label="t('system.config.modelConfig.modelName')"
                :rules="[
                  { required: true, message: t('common.notNull', { value: t('system.config.modelConfig.modelName') }) },
                ]"
                class="model-name-form"
                asterisk-position="end"
              >
                <template #label>
                  <div class="flex items-center">
                    {{ t('system.config.modelConfig.modelName') }}
                    <a-tooltip :content="t('system.config.modelConfig.modelNameTooltip')" position="top" mini>
                      <icon-question-circle
                        class="ml-[4px] text-[var(--color-text-4)] hover:text-[rgb(var(--primary-6))]"
                      />
                    </a-tooltip>
                  </div>
                </template>
                <a-input
                  v-model="form.name"
                  :max-length="255"
                  :placeholder="t('system.config.modelConfig.modelNamePlaceholder')"
                />
              </a-form-item>
              <a-form-item
                field="type"
                :label="t('system.config.modelConfig.modelType')"
                :rules="[
                  { required: true, message: t('common.notNull', { value: t('system.config.modelConfig.modelType') }) },
                ]"
                asterisk-position="end"
                class="w-[400px]"
              >
                <MsSelect v-model:model-value="form.type" :options="modelTypeOptions" />
              </a-form-item>
              <a-form-item
                :rules="[
                  {
                    required: true,
                    message: t('common.notNull', { value: t('system.config.modelConfig.apiHostName') }),
                  },
                ]"
                field="apiUrl"
                asterisk-position="end"
                :label="t('system.config.modelConfig.apiHostName')"
              >
                <a-input
                  v-model="form.apiUrl"
                  :max-length="255"
                  :placeholder="t('system.config.modelConfig.apiHostNamePlaceholder')"
                  @change="clearProbeResult"
                />
              </a-form-item>
              <a-form-item
                :rules="[
                  { required: true, message: t('common.notNull', { value: t('system.config.modelConfig.apiKey') }) },
                ]"
                field="appKey"
                asterisk-position="end"
                :label="t('system.config.modelConfig.apiKey')"
              >
                <a-input-password
                  v-model="form.appKey"
                  v-model:visibility="visibility"
                  :default-visibility="true"
                  :max-length="255"
                  :placeholder="t('system.config.modelConfig.apiKeyPlaceholder')"
                  allow-clear
                  @change="clearProbeResult"
                />
              </a-form-item>
              <div class="mb-[16px] flex justify-end">
                <a-button
                  type="outline"
                  :loading="probeLoading"
                  :disabled="!form.apiUrl || !form.appKey"
                  @click="probeRemoteModels"
                >
                  <template #icon><icon-refresh /></template>
                  {{ t('system.config.modelConfig.testAndLoadModels') }}
                </a-button>
              </div>
              <a-alert v-if="probeResult" :type="probeResult.models.length ? 'success' : 'warning'" show-icon>
                <template #title>{{ probeResult.message }}</template>
                <div v-if="probeResult.models.length">
                  {{ t('system.config.modelConfig.remoteModelsLoaded', { count: probeResult.models.length }) }}
                </div>
                <div v-if="probeResult.modelAvailable === false" class="mt-[4px]">
                  {{ t('system.config.modelConfig.selectedModelUnavailable') }}
                </div>
              </a-alert>
              <a-form-item
                field="baseName"
                :label="t('system.config.modelConfig.baseModel')"
                class="mt-[16px]"
                :rules="[
                  { required: true, message: t('common.notNull', { value: t('system.config.modelConfig.baseModel') }) },
                ]"
                asterisk-position="end"
              >
                <a-auto-complete
                  v-model:model-value="form.baseName"
                  :data="baseModelTypeOptions.filter((e) => e.isShow === true)"
                  :filter-option="false"
                  allow-clear
                  :placeholder="t('system.config.modelConfig.baseModelPlaceholder')"
                  @search="(val) => handleSearchParams(val)"
                  @select="(val) => selectAutoComplete(val)"
                  @change="(val) => selectAutoComplete(val)"
                  @clear="clearBaseName"
                >
                  <template #option="{ data: opt }">
                    <div class="flex items-center gap-[8px]">
                      <div>{{ opt.raw.remote ? opt.raw.label : t(opt.raw.label) }}</div>
                      <a-tag v-if="opt.raw.remote" size="small" color="arcoblue">
                        {{ t('system.config.modelConfig.remoteModel') }}
                      </a-tag>
                      <a-tooltip v-if="opt.raw.tooltip" :content="t(opt.raw.tooltip)">
                        <MsIcon
                          type="icon-icon_info_outlined"
                          class="text-[var(--color-text-4)] hover:text-[rgb(var(--primary-5))]"
                          size="16"
                        />
                      </a-tooltip>
                    </div>
                  </template>
                </a-auto-complete>
              </a-form-item>
            </template>
          </expandCollapseWrap>
        </div>
        <div v-if="form.advSettingDTOList.length" class="collapse-more-top my-[16px] pt-[16px]">
          <expandCollapseWrap :title="t('system.config.modelConfig.advancedSettings')">
            <template #content>
              <MsBatchForm
                ref="batchFormRef"
                :models="batchFormModels"
                enable-type="circle"
                :form-mode="baseModelForm"
                :default-vals="form.advSettingDTOList"
                show-enable
                hide-add
                @change="handleBatchFormChange"
              />
            </template>
          </expandCollapseWrap>
        </div>
      </a-form>
    </div>
  </MsDrawer>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { FormInstance, Message, SelectOptionData } from '@arco-design/web-vue';
  import { cloneDeep } from 'lodash-es';

  import MsDrawer from '@/components/pure/ms-drawer/index.vue';
  import MsBatchForm from '@/components/business/ms-batch-form/index.vue';
  import type { FormItemModel } from '@/components/business/ms-batch-form/types';
  import MsSelect from '@/components/business/ms-select';
  import expandCollapseWrap from './expandCollapseWrap.vue';

  import { editModelConfig, getModelConfigDetail, probeModelConfig } from '@/api/modules/setting/config';
  import { editPersonalModelConfig, getPersonalModelConfigDetail, probePersonalModelConfig } from '@/api/modules/user';
  import { baseModelTypeMap, getModelDefaultConfig, modelTypeOptions } from '@/config/modelConfig';
  import { useI18n } from '@/hooks/useI18n';
  import { useUserStore } from '@/store';

  import type { ModelFormConfigParams, ModelProbeResult, SupplierModelItem } from '@/models/setting/modelConfig';
  import { ModelBaseTypeEnum, ModelOwnerTypeTypeEnum, ModelPermissionTypeEnum, ModelTypeEnum } from '@/enums/modelEnum';

  const { t } = useI18n();

  const userStore = useUserStore();

  const props = defineProps<{
    supplierModelItem: SupplierModelItem;
    currentModelId: string;
    modelKey: 'personal' | 'system';
  }>();

  const emit = defineEmits<{
    (e: 'close'): void;
    (e: 'refresh'): void;
  }>();

  const innerVisible = defineModel<boolean>('visible', {
    required: true,
  });

  const modelDetailApiMap = {
    personal: getPersonalModelConfigDetail,
    system: getModelConfigDetail,
  }[props.modelKey];

  const modelEditApiMap = {
    personal: editPersonalModelConfig,
    system: editModelConfig,
  }[props.modelKey];

  const modelProbeApiMap = {
    personal: probePersonalModelConfig,
    system: probeModelConfig,
  }[props.modelKey];

  const getPrecisionFun = (_: FormItemModel, element: FormItemModel) => (element.name === 'maxTokens' ? 0 : 1);

  const visibility = ref(true);
  const baseModelForm = ref<'create' | 'edit'>('create');
  const batchFormRef = ref<InstanceType<typeof MsBatchForm>>();
  const batchFormModels: Ref<FormItemModel[]> = ref([
    {
      field: 'name',
      type: 'input',
      label: 'system.config.modelConfig.params',
      rules: [{ required: true, message: t('common.notNull', { value: t('system.config.modelConfig.params') }) }],
      placeholder: 'common.pleaseInput',
      disabled: true,
    },
    {
      field: 'label',
      type: 'input',
      label: 'system.config.modelConfig.displayName',
      rules: [{ required: true, message: t('common.notNull', { value: t('system.config.modelConfig.displayName') }) }],
      placeholder: 'common.pleaseInput',
      disabled: true,
    },
    {
      field: 'value',
      type: 'inputNumber',
      label: 'system.config.modelConfig.paramsValue',
      rules: [{ required: true, message: t('common.notNull', { value: t('system.config.modelConfig.paramsValue') }) }],
      placeholder: 'common.pleaseInput',
      maxKey: 'maxValue',
      minKey: 'minValue',
      getPrecisionFun,
    },
  ]);

  const modelTitle = computed(
    () =>
      `${props.currentModelId ? t('system.config.modelConfig.editModel') : t('system.config.modelConfig.addModel')}（${
        props.supplierModelItem.name
      }）`
  );

  const initForm = {
    id: '',
    name: '',
    type: ModelTypeEnum.LLM,
    providerName: ModelBaseTypeEnum.DeepSeek,
    permissionType: props.modelKey === 'personal' ? ModelPermissionTypeEnum.PRIVATE : ModelPermissionTypeEnum.PUBLIC,
    status: true,
    owner: (props.modelKey === 'personal' ? userStore?.id : '') || '',
    ownerType: props.modelKey === 'personal' ? ModelOwnerTypeTypeEnum.PERSONAL : ModelOwnerTypeTypeEnum.SYSTEM,
    baseName: '',
    appKey: '',
    apiUrl: '',
    advSettingDTOList: [],
  };

  const form = ref<ModelFormConfigParams>(cloneDeep(initForm));

  const baseModelTypeOptions = ref<SelectOptionData[]>([]);
  const remoteModelNames = ref<string[]>([]);
  const probeLoading = ref(false);
  const probeResult = ref<ModelProbeResult>();
  const isBatchFormChange = ref(false);
  function handleBatchFormChange() {
    isBatchFormChange.value = true;
  }

  function handleSearchParams(val: string) {
    baseModelTypeOptions.value = baseModelTypeOptions.value?.map((e) => {
      e.isShow = (e.label || '').toLowerCase().includes(val.toLowerCase());
      return e;
    });
  }

  function initBaseModelName() {
    const staticOptions = baseModelTypeMap[props.supplierModelItem.value];
    const staticValues = new Set(staticOptions.map((item) => item.value));
    const remoteOptions = remoteModelNames.value
      .filter((name) => !staticValues.has(name))
      .map((name) => ({ label: name, value: name, remote: true, isShow: true }));
    baseModelTypeOptions.value = [...remoteOptions, ...staticOptions.map((e) => ({ ...e, isShow: true }))];
  }

  function selectAutoComplete(val: string) {
    form.value.baseName = val;
    if (!form.value.name && val) {
      form.value.name = val;
    }
    form.value.advSettingDTOList = getModelDefaultConfig(props.supplierModelItem.value, form.value.baseName);
  }

  function clearBaseName() {
    form.value.advSettingDTOList = [];
    initBaseModelName();
  }

  function clearProbeResult() {
    probeResult.value = undefined;
  }

  async function probeRemoteModels() {
    if (!form.value.apiUrl || !form.value.appKey) {
      Message.warning(t('system.config.modelConfig.connectionRequiredFields'));
      return;
    }
    probeLoading.value = true;
    probeResult.value = undefined;
    try {
      const result = await modelProbeApiMap({
        id: form.value.id,
        providerName: form.value.providerName,
        apiUrl: form.value.apiUrl,
        appKey: form.value.appKey,
        baseName: form.value.baseName,
      });
      probeResult.value = result;
      remoteModelNames.value = result.models;
      form.value.apiUrl = result.apiUrl;
      initBaseModelName();
      Message.success(result.message);
    } catch (error) {
      remoteModelNames.value = [];
      initBaseModelName();
    } finally {
      probeLoading.value = false;
    }
  }

  const formRef = ref<FormInstance | null>(null);
  function handleDrawerCancel() {
    innerVisible.value = false;
    formRef.value?.resetFields();
    form.value = cloneDeep(initForm);
    remoteModelNames.value = [];
    probeResult.value = undefined;
    initBaseModelName();
    emit('close');
  }

  const loading = ref(false);
  async function handleSave(cb: (data: ModelFormConfigParams) => Promise<any>, isContinue = false) {
    try {
      loading.value = true;
      await cb(form.value);
      Message.success(form.value.id ? t('common.updateSuccess') : t('common.createSuccess'));
      emit('refresh');
      if (isContinue) {
        form.value = cloneDeep(initForm);
      } else {
        handleDrawerCancel();
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }

  function handleDrawerConfirm(isContinue = false) {
    formRef.value?.validate(async (errors) => {
      if (!errors) {
        if (form.value.advSettingDTOList.length) {
          batchFormRef.value?.formValidate(async (list: any) => {
            form.value.advSettingDTOList = [...list];
            handleSave(modelEditApiMap, isContinue);
          });
        } else {
          handleSave(modelEditApiMap, isContinue);
        }
      }
    });
  }

  async function getDetail() {
    if (props.currentModelId) {
      try {
        form.value = await modelDetailApiMap(props.currentModelId);
      } catch (error) {
        // eslint-disable-next-line no-console
        console.log(error);
      }
    }
  }

  watch(
    () => innerVisible.value,
    (val) => {
      if (val) {
        visibility.value = true;
        remoteModelNames.value = [];
        probeResult.value = undefined;
        getDetail();
        form.value.providerName = props.supplierModelItem.value;
        initBaseModelName();
      }
    }
  );
</script>

<style lang="less">
  .model-name-form {
    .arco-form-item-label {
      @apply flex flex-nowrap items-center;
    }
  }
  .collapse-more-top {
    border-top: 1px dotted var(--color-text-n8);
  }
</style>
