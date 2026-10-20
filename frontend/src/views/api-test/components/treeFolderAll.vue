<template>
  <MsFolderAll
    v-model:is-expand-all="isExpandAll"
    :active-folder="props.activeFolder"
    :folder-name="props.folderName"
    :all-count="props.allCount"
    :expand-disabled="props.expandDisabled"
    @set-active-folder="(val: string) => emit('setActiveFolder', val)"
  >
    <template #expandLeft>
      <!-- 显示请求icon -->
      <a-tooltip :content="!isExpandApi ? t('apiTestManagement.expandApi') : t('apiTestManagement.collapseApi')">
        <MsButton
          v-show="!props.notShowOperation && showExpandApi"
          type="icon"
          status="secondary"
          class="!mr-[4px] p-[4px]"
          @click="changeApiExpand"
        >
          <MsIcon :type="`${!isExpandApi ? 'icon-icon_visible_outlined1' : 'icon-icon_preview_close_one'}`" />
        </MsButton>
      </a-tooltip>
      <!-- 协议icon -->
      <a-dropdown v-model:popup-visible="visible" :hide-on-select="false">
        <a-popconfirm
          :popup-visible="protocolIsEmptyVisible"
          class="ms-pop-confirm--hidden-confirm"
          :cancel-text="t('common.gotIt')"
          position="bottom"
          @cancel="protocolIsEmptyVisible = false"
        >
          <a-tooltip :content="t('ms.paramsInput.protocol')">
            <MsButton
              v-show="!props.notShowOperation"
              type="icon"
              status="secondary"
              :class="`!mr-[4px] p-[4px] ${visible ? 'bg-[rgb(var(--primary-1))] !text-[rgb(var(--primary-4))]' : ''}`"
              @click="visible = !visible"
            >
              <MsIcon type="icon-icon_protocol" />
            </MsButton>
          </a-tooltip>
          <template #content>
            <div class="flex flex-col gap-[8px]">
              <div class="font-medium">{{ t('apiTestManagement.protocolIsEmpty') }}</div>
              <div class="text-[var(--color-text-2)]">{{ t('apiTestManagement.protocolEmptyTip') }}</div>
            </div>
          </template>
        </a-popconfirm>
        <template #content>
          <a-checkbox
            class="checkbox-all"
            :model-value="isCheckedAll"
            :indeterminate="indeterminate"
            @change="handleChangeAll"
            >{{ t('common.all') }}
          </a-checkbox>
          <a-checkbox-group direction="vertical" :model-value="selectedProtocols" @change="handleGroupChange">
            <a-checkbox v-for="item in allProtocolList" :key="item" :value="item">
              {{ item }}
            </a-checkbox>
          </a-checkbox-group>
        </template>
      </a-dropdown>
    </template>
    <template #expandRight>
      <slot name="expandRight"></slot>
    </template>
  </MsFolderAll>
</template>

<script setup lang="ts">
  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsFolderAll from '@/components/business/ms-folder-all/index.vue';

  import { getProtocolList } from '@/api/modules/api-test/common';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import { getLocalStorage, setLocalStorage } from '@/utils/local-storage';

  import { GRPC_PROTOCOL } from '@/models/apiTest/grpc';
  import { ProtocolKeyEnum } from '@/enums/apiEnum';

  const props = defineProps<{
    expandDisabled?: boolean;
    activeFolder?: string; // 选中的节点
    folderName: string; // 名称
    allCount: number; // 总数
    showExpandApi?: boolean; // 展示 展开请求的开关
    notShowOperation?: boolean; // 是否展示操作按钮
    protocolKey: ProtocolKeyEnum;
  }>();
  const emit = defineEmits<{
    (e: 'setActiveFolder', val: string): void;
    (e: 'changeApiExpand'): void;
    (e: 'selectedProtocolsChange', protocols: string[]): void;
  }>();

  const isExpandAll = defineModel<boolean | undefined>('isExpandAll', {
    required: false,
    default: undefined,
  });
  const isExpandApi = defineModel<boolean>('isExpandApi', {
    required: false,
    default: undefined,
  });
  const selectedProtocols = ref<string[]>([]);

  const { t } = useI18n();
  const appStore = useAppStore();

  const visible = ref(false);
  const protocolIsEmptyVisible = ref(false);
  const allProtocolList = ref<string[]>([]); // 全部
  const protocolSelectionReady = ref(false);
  const isCheckedAll = computed(() => {
    return selectedProtocols.value.length === allProtocolList.value.length;
  });
  const indeterminate = computed(() => {
    return selectedProtocols.value.length > 0 && selectedProtocols.value.length < allProtocolList.value.length;
  });
  const handleChangeAll = (value: boolean | (string | number | boolean)[]) => {
    if (value) {
      selectedProtocols.value = allProtocolList.value;
    } else {
      selectedProtocols.value = [];
    }
  };
  const handleGroupChange = (value: (string | number | boolean)[]) => {
    selectedProtocols.value = value as string[];
  };

  async function initProtocolList() {
    try {
      const res = await getProtocolList(appStore.currentOrgId);
      allProtocolList.value = res.map((e) => e.protocol);
      // 内置 gRPC 协议不在 /api/test/protocol 返回列表中，关联用例筛选需手动补充
      if (!allProtocolList.value.includes(GRPC_PROTOCOL)) {
        const httpIndex = allProtocolList.value.indexOf('HTTP');
        if (httpIndex >= 0) {
          allProtocolList.value.splice(httpIndex + 1, 0, GRPC_PROTOCOL);
        } else {
          allProtocolList.value.unshift(GRPC_PROTOCOL);
        }
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    }
  }

  function changeApiExpand() {
    isExpandApi.value = !isExpandApi.value;
    nextTick(() => {
      setLocalStorage(`${props.protocolKey}_EXPAND_API`, isExpandApi.value);
      emit('changeApiExpand');
    });
  }

  function notifySelectedProtocolsChange(val: string[]) {
    // 存储取消的项
    const protocols = allProtocolList.value.filter((item) => !val.includes(item));
    setLocalStorage(props.protocolKey, protocols);
    emit('selectedProtocolsChange', [...val]);
    if (props.notShowOperation) return;
    protocolIsEmptyVisible.value = !val.length;
  }

  watch(
    () => selectedProtocols.value,
    (val) => {
      if (protocolSelectionReady.value) {
        notifySelectedProtocolsChange(val);
      }
    },
    { flush: 'sync' }
  );

  watch(
    () => props.notShowOperation,
    (val) => {
      if (val) {
        isExpandAll.value = undefined;
      } else {
        isExpandAll.value = false;
      }
    }
  );

  onBeforeMount(async () => {
    await initProtocolList();
    isExpandApi.value = getLocalStorage(`${props.protocolKey}_EXPAND_API`) === 'true';
    const protocols = getLocalStorage<string[]>(props.protocolKey);
    if (!protocols) {
      selectedProtocols.value = allProtocolList.value;
    } else {
      selectedProtocols.value = allProtocolList.value.filter((item) => !protocols.includes(item as string));
    }
    protocolSelectionReady.value = true;
    notifySelectedProtocolsChange(selectedProtocols.value);
  });

  defineExpose({
    selectedProtocols,
    allProtocolList,
  });
</script>

<style lang="less" scoped>
  .arco-dropdown {
    padding: 8px;
    .arco-dropdown-list .arco-dropdown-option {
      width: 107px;
    }
    .checkbox-all {
      border-bottom: 1px solid var(--color-text-n8);
    }
    .arco-checkbox {
      padding: 6px 12px;
      line-height: 24px;
    }
    .arco-switch {
      margin-left: 8px;
    }
  }
  .api-expend {
    :deep(.arco-dropdown-option-content) {
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
    }
  }
</style>
