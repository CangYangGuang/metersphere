<template>
  <div class="ai-history-header">
    <div>
      <div class="ai-history-title">{{ t('ms.ai.conversations') }}</div>
      <div class="ai-history-count">{{ t('ms.ai.conversationCount', { count: conversationList.length }) }}</div>
    </div>
    <a-tooltip :content="t('ms.ai.openNewConversation')">
      <a-button
        type="primary"
        shape="circle"
        size="small"
        :aria-label="t('ms.ai.openNewConversation')"
        @click="openNewConversation"
      >
        <icon-plus />
      </a-button>
    </a-tooltip>
  </div>
  <a-input
    v-model:model-value="keyword"
    allow-clear
    size="small"
    class="ai-history-search"
    :placeholder="t('ms.ai.searchConversation')"
  >
    <template #prefix><icon-search /></template>
  </a-input>
  <div class="flex-1 overflow-y-hidden">
    <a-list
      max-height="100%"
      :split="false"
      :bordered="false"
      :loading="loading"
      class="ms-ai-drawer-conversation-list"
      scrollbar
    >
      <template #empty><div /></template>
      <a-list-item
        v-for="item of filteredConversationList"
        :id="`ai-item-${item.id}`"
        :key="item.id"
        class="ms-ai-drawer-conversation-item"
        :class="activeConversation?.id === item.id ? 'ms-ai-drawer-conversation-item--active' : ''"
        @click="handleClick(item)"
      >
        <a-input
          v-if="item.isEditing"
          :id="`ai-title-${item.id}`"
          v-model:model-value="tempInputVal"
          :max-length="255"
          @blur="handleEditConfirm(item)"
          @keydown.enter="handleEditConfirm(item)"
          @keydown.esc="handleEditCancel(item)"
        ></a-input>
        <div v-else class="flex items-center justify-between gap-[8px] overflow-hidden">
          <div class="min-w-0 flex-1">
            <a-tooltip :mouse-enter-delay="300">
              <div class="one-line-text ai-history-item-title">{{ item.title }}</div>
              <template #content>
                <div>{{ item.title }}</div>
              </template>
            </a-tooltip>
            <div class="ai-history-item-time">{{ formatTime(item.createTime) }}</div>
          </div>
          <a-tooltip v-if="isAnswering(item.id)" :content="t('ms.ai.thinking')">
            <a-spin :size="14" />
          </a-tooltip>
          <MsTableMoreAction
            :list="getItemMoreAction(item)"
            trigger="click"
            @select="handleMoreActionSelect($event, item)"
          />
        </div>
      </a-list-item>
    </a-list>
  </div>
</template>

<script setup lang="ts">
  import { Message } from '@arco-design/web-vue';

  import MsTableMoreAction from '@/components/pure/ms-table-more-action/index.vue';
  import { ActionsItem } from '@/components/pure/ms-table-more-action/types';

  import { deleteAiChat, getAiChatList, updateAiChatTitle } from '@/api/modules/ai';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import { characterLimit, getGenerateId } from '@/utils';

  import { AiChatListItem } from '@/models/ai';

  const props = defineProps<{
    answeringConversationIds?: string[];
  }>();
  const emit = defineEmits<{
    (e: 'deleteConversation', conversationId: string): void;
  }>();

  const { t } = useI18n();
  const { openModal } = useModal();

  const activeConversation = defineModel<AiChatListItem | undefined>('value', {
    required: true,
  });

  const loading = ref<boolean>(false);
  const conversationList = ref<AiChatListItem[]>([]);
  const keyword = ref('');
  const filteredConversationList = computed(() => {
    const value = keyword.value.trim().toLowerCase();
    if (!value) return conversationList.value;
    return conversationList.value.filter((item) => item.title?.toLowerCase().includes(value));
  });

  function formatTime(timestamp?: number) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const today = new Date();
    if (date.toDateString() === today.toDateString()) {
      return new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' }).format(date);
    }
    return new Intl.DateTimeFormat(undefined, { month: '2-digit', day: '2-digit' }).format(date);
  }

  const itemMoreActions: ActionsItem[] = [
    {
      label: 'common.rename',
      eventTag: 'rename',
    },
    {
      label: 'project.fileManagement.delete',
      eventTag: 'delete',
      danger: true,
    },
  ];

  function getItemMoreAction(item: AiChatListItem) {
    if (item.isNew) {
      return itemMoreActions.filter((action) => action.eventTag !== 'rename');
    }
    return itemMoreActions;
  }

  function handleClick(item: AiChatListItem) {
    activeConversation.value = item;
  }

  function isAnswering(conversationId: string) {
    return props.answeringConversationIds?.includes(conversationId) || false;
  }

  function openNewConversation() {
    const conversationItem: AiChatListItem = {
      id: getGenerateId(),
      title: t('ms.ai.newConversation'),
      isNew: true,
      createTime: new Date().getTime(),
      createUser: '',
    };
    conversationList.value.unshift(conversationItem);
    activeConversation.value = conversationItem;
    nextTick(() => {
      const itemElement = document.querySelector(`#ai-item-${activeConversation.value?.id}`) as HTMLInputElement;
      if (itemElement) {
        itemElement.scrollIntoView({
          behavior: 'smooth',
        });
      }
    });
  }

  function markConversationCreated(tempId: string, conversationItem: AiChatListItem) {
    const localItem = conversationList.value.find((item) => item.id === tempId);
    if (localItem) {
      const wasActive = activeConversation.value?.id === tempId;
      Object.assign(localItem, conversationItem, { isNew: false });
      if (wasActive) {
        activeConversation.value = localItem;
      }
      return;
    }
    conversationList.value.unshift(conversationItem);
    activeConversation.value = conversationItem;
  }

  const tempInputVal = ref<string>('');

  function handleEditConfirm(item: AiChatListItem) {
    // setTimeout 解决中文输入法输入英文回车时英文未被填充的问题
    setTimeout(async () => {
      if (!tempInputVal.value.trim()) {
        item.isEditing = false;
        return;
      }
      try {
        await updateAiChatTitle({
          id: item.id,
          title: tempInputVal.value.trim(),
        });
        item.title = tempInputVal.value.trim();
        tempInputVal.value = '';
        item.isEditing = false;
      } catch (error) {
        // eslint-disable-next-line no-console
        console.log(error);
      }
    });
  }

  function handleEditCancel(item: AiChatListItem) {
    item.isEditing = false;
    tempInputVal.value = '';
  }

  const handleMoreActionSelect = (event: ActionsItem, item: AiChatListItem) => {
    if (event.eventTag === 'rename') {
      tempInputVal.value = item.title;
      item.isEditing = true;
      nextTick(() => {
        const inputElement = document.querySelector(`#ai-title-${item.id}`) as HTMLInputElement;
        if (inputElement) {
          (inputElement.querySelector('.arco-input') as HTMLInputElement)?.focus();
        }
      });
    } else if (event.eventTag === 'delete') {
      openModal({
        type: 'error',
        title: t('common.deleteConfirmTitle', { name: characterLimit(item.title) }),
        content: t('ms.ai.deleteConversationTip'),
        okText: t('common.confirmDelete'),
        cancelText: t('common.cancel'),
        okButtonProps: {
          status: 'danger',
        },
        onBeforeOk: async () => {
          try {
            if (!item.isNew) {
              await deleteAiChat(item.id);
            }
            conversationList.value = conversationList.value.filter((i: AiChatListItem) => i.id !== item.id);
            if (activeConversation.value?.id === item.id) {
              [activeConversation.value] = conversationList.value;
            }
            emit('deleteConversation', item.id);
            Message.success(t('common.deleteSuccess'));
          } catch (error) {
            // eslint-disable-next-line no-console
            console.log(error);
          }
        },
        hideCancel: false,
      });
    }
  };

  async function initList(setDefaultActive = true) {
    try {
      loading.value = true;
      conversationList.value = await getAiChatList();
      const localConversation = JSON.parse(localStorage.getItem('activeAiConversation') || '""');
      if (typeof localConversation === 'object' && localConversation?.id) {
        const found = conversationList.value.find((item: AiChatListItem) => item.id === localConversation.id);
        activeConversation.value = found || (setDefaultActive ? conversationList.value[0] : activeConversation.value);
      } else if (conversationList.value.length > 0 && setDefaultActive) {
        [activeConversation.value] = conversationList.value;
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }

  onBeforeMount(() => {
    initList();
  });

  defineExpose({
    openNewConversation,
    initList,
    markConversationCreated,
  });
</script>

<style lang="less" scoped>
  .ai-history-header {
    @apply flex items-center justify-between;

    gap: 8px;
    margin-bottom: 12px;
  }
  .ai-history-title {
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
    color: var(--color-text-1);
  }
  .ai-history-count {
    font-size: 12px;
    line-height: 18px;
    color: var(--color-text-4);
  }
  .ai-history-search {
    margin-bottom: 12px;
    border-radius: 4px;
    background: var(--color-text-fff);
  }
  .arco-list-wrapper {
    @apply h-full;
    :deep(.arco-scrollbar) {
      @apply h-full;
      .arco-list-content-wrapper {
        padding-right: 12px;
        .arco-list-content {
          @apply flex flex-col;

          gap: 4px;
          padding: 1px 0 0 1px;
        }
      }
      .arco-scrollbar-track-direction-vertical {
        right: 0;
      }
    }
  }
  .ms-ai-drawer-conversation-item {
    @apply flex cursor-pointer items-center;

    padding: 10px !important;
    border: 1px solid transparent;
    border-radius: 4px;
    &:hover {
      border: 1px solid rgb(var(--primary-7));
    }
    &--active {
      border-color: rgb(var(--primary-3));
      color: rgb(var(--primary-6));
      background: var(--color-text-fff);
    }
    :deep(.arco-list-item-main) {
      @apply overflow-hidden;
    }
    :deep(.ms-more-action-trigger-content) {
      @apply invisible;
    }
    &:hover {
      :deep(.ms-more-action-trigger-content) {
        @apply visible;
      }
    }
  }
  .ai-history-item-title {
    font-size: 13px;
    font-weight: 500;
    line-height: 20px;
  }
  .ai-history-item-time {
    margin-top: 2px;
    font-size: 11px;
    line-height: 16px;
    color: var(--color-text-4);
  }
</style>
