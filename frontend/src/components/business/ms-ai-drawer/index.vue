<template>
  <MsDrawer
    v-model:visible="visible"
    :title="t('ms.ai.workbench')"
    :width="760"
    :footer="false"
    :mask="false"
    :esc-to-close="false"
    :mask-closable="false"
    :closable="false"
    show-full-screen
    docked
    no-content-padding
    @close="handleDrawerClose"
  >
    <template #headerLeft>
      <span class="ai-mode-label">{{ modeLabel }}</span>
    </template>
    <template #tbutton>
      <div class="flex items-center gap-[4px]">
        <a-tooltip :content="t('system.config.modelConfig.modelConfigSet')">
          <a-button
            type="text"
            class="ai-header-icon"
            size="mini"
            :aria-label="t('system.config.modelConfig.modelConfigSet')"
            @click="modelConfigVisible = true"
          >
            <icon-settings :size="20" />
            <span class="sr-only">{{ t('system.config.modelConfig.modelConfigSet') }}</span>
          </a-button>
        </a-tooltip>
        <a-tooltip :content="t('common.close')">
          <a-button
            type="text"
            class="ai-header-icon"
            size="mini"
            :aria-label="t('common.close')"
            @click="onBeforeClose"
          >
            <icon-close :size="20" />
          </a-button>
        </a-tooltip>
      </div>
    </template>
    <MsSplitBox v-model:size="splitSize" :min="196" :max="0.42" class="ai-workbench-split">
      <template #first>
        <aside class="ai-history-panel">
          <conversationList
            v-if="visible"
            ref="conversationListRef"
            v-model:value="activeConversation"
            :answering-conversation-ids="answeringConversationIds"
            @delete-conversation="handleDeleteConversation"
          />
        </aside>
      </template>
      <template #second>
        <main class="ai-conversation-panel">
          <conversation
            v-if="visible"
            ref="conversationRef"
            v-model:value="activeConversation"
            :type="props.type"
            :api-definition-id="props.apiDefinitionId"
            :module-id="props.moduleId"
            :template-id="props.templateId"
            @open-new-conversation="openNewConversation"
            @add-success="handleAddConversationSuccess"
            @answering-change="handleAnsweringChange"
            @sync-api-case="emit('syncApiCase', $event)"
            @sync-feature-case="emit('syncFeatureCase', $event)"
            @sync-success="handleSyncSuccess"
          />
        </main>
      </template>
    </MsSplitBox>
  </MsDrawer>
  <MsPersonalDrawer v-if="modelConfigVisible" v-model:visible="modelConfigVisible" default-menu="modelConfig" />
</template>

<script setup lang="ts">
  import { useRoute, useRouter } from 'vue-router';

  import MsDrawer from '@/components/pure/ms-drawer/index.vue';
  import MsSplitBox from '@/components/pure/ms-split-box/index.vue';
  import MsPersonalDrawer from '@/components/business/ms-personal-drawer/index.vue';
  import conversation from './components/conversation.vue';
  import conversationList from './components/conversationList.vue';

  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import useAIStore from '@/store/modules/setting/ai';

  import { AiCaseTransformResult, AiChatListItem } from '@/models/ai';
  import { ApiCaseDetail } from '@/models/apiTest/management';

  const props = defineProps<{
    type: 'chat' | 'case' | 'api';
    apiDefinitionId?: string | number;
    moduleId?: string | number;
    templateId?: string | number; // 功能用例保存时需要的模板 id
  }>();
  const emit = defineEmits<{
    (e: 'syncApiCase', detail: ApiCaseDetail): void;
    (e: 'syncFeatureCase', detail: AiCaseTransformResult): void;
    (e: 'syncSuccess'): void;
  }>();

  const route = useRoute();
  const router = useRouter();
  const { t } = useI18n();
  const { openModal } = useModal();
  const aiStore = useAIStore();

  const visible = defineModel<boolean>('visible', {
    default: false,
  });

  const splitSize = ref(180);
  const modelConfigVisible = ref(false);
  const activeConversation = ref<AiChatListItem | undefined>();
  const conversationListRef = ref<InstanceType<typeof conversationList>>();
  const conversationRef = ref<InstanceType<typeof conversation>>();
  const answeringConversationIds = ref<string[]>([]);
  const modeLabel = computed(() => t(`ms.ai.mode.${props.type}`));

  function openNewConversation() {
    conversationListRef.value?.openNewConversation();
  }

  function handleAddConversationSuccess(tempId: string, conversationItem: AiChatListItem) {
    conversationListRef.value?.markConversationCreated(tempId, conversationItem);
  }

  function handleSyncSuccess() {
    visible.value = false;
    emit('syncSuccess');
  }

  function handleAnsweringChange(conversationId: string, value: boolean) {
    const ids = new Set(answeringConversationIds.value);
    if (value) {
      ids.add(conversationId);
    } else {
      ids.delete(conversationId);
    }
    answeringConversationIds.value = [...ids];
  }

  function handleDeleteConversation(conversationId: string) {
    conversationRef.value?.removeConversation(conversationId);
  }

  function onBeforeClose() {
    if (answeringConversationIds.value.length > 0) {
      openModal({
        type: 'warning',
        title: t('common.tip'),
        content: t('ms.ai.answeringCloseTip'),
        okText: t('common.confirm'),
        cancelText: t('common.cancel'),
        onBeforeOk: async () => {
          conversationRef.value?.stopAllAnswers();
          visible.value = false;
          return Promise.resolve(); // 允许关闭
        },
        hideCancel: false,
      });
      return;
    }
    visible.value = false;
  }

  function handleDrawerClose() {
    const newQuery = { ...route.query };
    delete newQuery.openAi;
    router.replace({
      query: newQuery,
    });
  }

  watch(
    () => activeConversation.value?.id,
    () => {
      localStorage.setItem('activeAiConversation', JSON.stringify(activeConversation.value));
    }
  );
  watch(modelConfigVisible, (value, oldValue) => {
    if (oldValue && !value) {
      aiStore.getAISourceNameList();
    }
  });
</script>

<style lang="less" scoped>
  .ai-mode-label {
    margin-left: 10px;
    padding: 2px 8px;
    font-size: 12px;
    line-height: 20px;
    border: 1px solid var(--color-text-n8);
    border-radius: 4px;
    color: var(--color-text-3);
    background: var(--color-text-n9);
  }
  .ai-header-icon {
    padding: 0;
    width: 40px;
    min-width: 40px;
    height: 40px;
    border-radius: 6px;
    color: var(--color-text-2);
    &:hover {
      color: rgb(var(--primary-6));
      background: var(--color-text-n9);
    }
  }
  .ai-history-panel {
    @apply flex h-full flex-col overflow-hidden;

    padding: 16px 10px 12px 14px;
    border-right: 1px solid var(--color-text-n8);
    background: #f7f8fa;
  }
  .ai-conversation-panel {
    @apply h-full min-w-0 overflow-hidden;

    background: var(--color-text-fff);
  }
  :deep(.ai-workbench-split > .arco-split-trigger) {
    width: 1px;
    background: var(--color-text-n8);
  }
</style>
