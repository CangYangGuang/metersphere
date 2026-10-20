<template>
  <a-spin
    :loading="loading"
    class="ai-conversation-shell"
    @dragenter.prevent="handleDragEnter"
    @dragover.prevent="handleDragOver"
    @dragleave.prevent="handleDragLeave"
    @drop.prevent="handleDrop"
  >
    <div v-if="draggingImages" class="ai-drop-overlay">
      <div class="ai-drop-content">
        <icon-image :size="28" />
        <span>{{ t('ms.ai.dropImages') }}</span>
      </div>
    </div>
    <header class="ai-conversation-header">
      <div class="ai-context-mark">
        <svg-icon width="28px" height="28px" name="ai" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="ai-context-label">{{ contextLabel }}</div>
        <template v-if="title">
          <a-input
            v-if="editTitle"
            ref="titleInputRef"
            v-model:model-value="tempTitle"
            size="large"
            class="conversation-title"
            :max-length="255"
            @keydown.enter="handleSaveTitle"
            @keydown.esc="editTitle = false"
          />
          <div v-else class="one-line-text conversation-title-display" @click="handleEditTitle">
            {{ title }}
          </div>
        </template>
        <div v-else class="conversation-title-display">{{ t('ms.ai.newConversation') }}</div>
      </div>
      <span class="ai-ready-indicator">{{ t('ms.ai.ready') }}</span>
    </header>
    <div class="ai-message-viewport flex-1 overflow-hidden">
      <a-checkbox-group
        v-if="conversationItems.length"
        v-model:model-value="checkedCases"
        class="ai-message-group flex h-full w-full overflow-hidden"
      >
        <BubbleList
          ref="bubbleListRef"
          :list="conversationItems"
          :btn-loading="answering"
          max-height="100%"
          class="ai-message-list h-full w-full"
        >
          <!-- 自定义 loading -->
          <template #loading>
            <div class="loading-wrapper">
              <a-spin dot />
            </div>
          </template>
          <!-- 自定义头像 -->
          <template #avatar="{ item }">
            <div class="avatar-wrapper">
              <svg-icon v-if="item.type === AiChatContentRoleTypeEnum.ASSISTANT" width="40px" height="40px" name="ai" />
              <MsAvatar v-else is-user />
            </div>
          </template>

          <!-- 自定义气泡内容 -->
          <template #content="{ item }">
            <!-- <div v-if="item.isEdit" class="flex w-full flex-col gap-[8px]">
            <a-textarea v-model:model-value="tempInput"></a-textarea>
            <div class="flex items-center justify-end gap-[8px]">
              <a-button type="secondary" @click="cancelInput(item)">{{ t('common.cancel') }}</a-button>
              <a-button type="primary" @click="sendInput(item)">{{ t('ms.ai.send') }}</a-button>
            </div>
          </div> -->
            <a-trigger position="left" auto-fit-position :popup-translate="[-8, 0]" popup-container=".content-wrapper">
              <div
                class="content-wrapper"
                :class="item.type === AiChatContentRoleTypeEnum.ASSISTANT ? 'content-wrapper--assistant' : ''"
              >
                <Bubble
                  class="content-text"
                  :class="item.type === AiChatContentRoleTypeEnum.ASSISTANT ? 'content-text--assistant' : ''"
                >
                  <template #content>
                    <div
                      v-if="item.attachments?.length"
                      class="ai-message-attachments"
                      :class="item.type === AiChatContentRoleTypeEnum.USER ? 'ai-message-attachments--user' : ''"
                    >
                      <a-image
                        v-for="attachment in item.attachments"
                        :key="attachment.id"
                        :src="attachmentSrc(attachment)"
                        :alt="attachment.name"
                        fit="cover"
                        width="132"
                        height="96"
                        class="ai-message-image"
                      />
                    </div>
                    <a-collapse
                      v-if="
                        item.type === AiChatContentRoleTypeEnum.ASSISTANT &&
                        (item.content.includes('featureCaseStart') || item.content.includes('apiCaseStart'))
                      "
                      :default-active-key="[item.id]"
                      expand-icon-position="right"
                      show-expand-icon
                      :bordered="false"
                      class="flex w-full flex-col"
                    >
                      <template #expand-icon="{ active }">
                        <icon-up v-if="active" />
                        <icon-down v-else />
                      </template>
                      <div
                        v-for="(caseItem, idx) in item.content
                          .split(/featureCaseEnd|apiCaseEnd/)
                          .filter((e) => e.trim())"
                        :key="caseItem + idx"
                        class="case-collapse-item flex w-full border-b border-[var(--color-text-n8)] pb-[16px]"
                      >
                        <a-checkbox
                          v-if="
                            (props.type === 'case' && item.content.includes('featureCaseStart')) ||
                            (props.type === 'api' && item.content.includes('apiCaseStart'))
                          "
                          :value="caseItem"
                          class="mt-[4px] h-[16px] items-start"
                        />
                        <a-collapse-item
                          :key="
                            item.content.split(/featureCaseEnd|apiCaseEnd/).filter((e) => e.trim()).length > 1
                              ? caseItem + idx
                              : item.id
                          "
                          class="flex-1 overflow-hidden"
                          :style="{
                            background: 'transparent',
                          }"
                        >
                          <template #header>
                            <h3 class="text-[1.25em] font-semibold">
                              {{ caseItem.split(/caseExpand/)[0].replace(/featureCaseStart|apiCaseStart|[#\s]/g, '') }}
                            </h3>
                          </template>
                          <div class="flex">
                            <Typewriter
                              :content="
                                caseItem
                                  .split(/caseExpand/)[1]
                                  ?.replace(/featureCaseStart|featureCaseEnd|apiCaseStart|apiCaseEnd/g, '')
                              "
                              :is-markdown="true"
                            />
                          </div>
                        </a-collapse-item>
                      </div>
                    </a-collapse>
                    <Typewriter
                      v-else
                      :content="item.content"
                      :is-markdown="item.type === AiChatContentRoleTypeEnum.ASSISTANT"
                    />
                  </template>
                </Bubble>
              </div>
              <template v-if="item.type === AiChatContentRoleTypeEnum.USER" #content>
                <div class="flex items-center gap-[8px]">
                  <a-tooltip :mouse-enter-delay="300">
                    <MsButton type="icon" status="default" class="!mr-0" @click="handleCopy(item)">
                      <MsIcon type="icon-icon_copy_outlined" class="text-[var(--color-text-4)]" />
                    </MsButton>
                    <template #content>
                      <span>{{ t('common.copy') }}</span>
                    </template>
                  </a-tooltip>
                  <a-tooltip :mouse-enter-delay="300">
                    <MsButton type="icon" status="default" class="!mr-0" @click="handleEdit(item)">
                      <MsIcon type="icon-icon_edit_outlined" class="text-[var(--color-text-4)]" />
                    </MsButton>
                    <template #content>
                      <span>{{ t('common.edit') }}</span>
                    </template>
                  </a-tooltip>
                </div>
              </template>
            </a-trigger>
          </template>

          <!-- 自定义底部 -->
          <template #footer="{ item }">
            <div v-if="!answering && item.type === AiChatContentRoleTypeEnum.ASSISTANT" class="footer-wrapper">
              <div class="footer-container">
                <a-tooltip :mouse-enter-delay="300">
                  <MsButton type="icon" status="default" class="!mr-0" @click="handleCopy(item)">
                    <MsIcon type="icon-icon_copy_outlined" class="text-[var(--color-text-4)]" />
                  </MsButton>
                  <template #content>
                    <span>{{ t('common.copy') }}</span>
                  </template>
                </a-tooltip>
                <a-tooltip :mouse-enter-delay="300">
                  <MsButton type="icon" status="default" class="!mr-0" @click="handleReset(item)">
                    <MsIcon type="icon-icon_reset_outlined" class="text-[var(--color-text-4)]" />
                  </MsButton>
                  <template #content>
                    <span>{{ t('ms.ai.regenerate') }}</span>
                  </template>
                </a-tooltip>
              </div>
            </div>
          </template>
        </BubbleList>
      </a-checkbox-group>
      <div v-else class="ai-empty-workspace">
        <div class="ai-empty-icon"><svg-icon width="44px" height="44px" name="ai" /></div>
        <h3>{{ emptyTitle }}</h3>
        <div class="ai-quick-grid">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            type="button"
            class="ai-quick-command"
            @click="applyPrompt(prompt)"
          >
            <span>{{ prompt }}</span>
            <icon-arrow-right />
          </button>
        </div>
      </div>
    </div>
    <div class="ai-conversation-actions flex flex-wrap items-center gap-[8px] py-[12px]">
      <div class="flex flex-wrap items-center gap-[12px]">
        <template v-if="props.type === 'chat'">
          <MsAiButton
            v-permission="['FUNCTIONAL_CASE:READ+ADD']"
            :text="t('ms.ai.generateFeatureCase')"
            no-icon
            @click="jump('case')"
          />
          <MsAiButton
            v-permission="['PROJECT_API_DEFINITION_CASE:READ+ADD']"
            :text="t('ms.ai.generateApiCase')"
            no-icon
            @click="jump('api')"
          />
        </template>
        <a-button v-if="checkedCases.length > 0" type="outline" @click="handleSync()">
          <template #icon>
            <MsIcon type="icon-icon_synchronous" />
          </template>
          {{ t('ms.ai.caseSync') }}
        </a-button>
      </div>
    </div>
    <div class="ai-composer-shell">
      <input
        ref="fileInputRef"
        type="file"
        accept="image/png,image/jpeg,image/webp,image/gif"
        multiple
        class="hidden"
        @change="handleFileInput"
      />
      <div v-if="draftAttachments.length" class="ai-draft-attachments">
        <div v-for="attachment in draftAttachments" :key="attachment.id" class="ai-draft-image-wrap">
          <a-image
            :src="attachmentSrc(attachment)"
            :alt="attachment.name"
            fit="cover"
            width="72"
            height="72"
            class="ai-draft-image"
          />
          <a-tooltip :content="t('ms.ai.removeImage')">
            <button
              type="button"
              class="ai-remove-image"
              :aria-label="t('ms.ai.removeImage')"
              @click="removeDraftAttachment(attachment.id)"
            >
              <icon-close :size="14" />
            </button>
          </a-tooltip>
        </div>
      </div>
      <Sender
        ref="senderRef"
        v-model="senderValue"
        variant="updown"
        :auto-size="{ minRows: 2, maxRows: 5 }"
        :submit-btn-disabled="!canSubmit"
        :placeholder="sendPlaceholder"
        class="ai-command-sender"
        @paste-file="handlePasteFile"
        @submit="handleSubmit"
      >
        <template #action-list>
          <div class="ai-sender-toolbar">
            <div class="ai-sender-toolbar__left">
              <a-tooltip :content="t('ms.ai.addImage')" position="tr">
                <a-button
                  type="text"
                  shape="circle"
                  class="ai-composer-icon"
                  :aria-label="t('ms.ai.addImage')"
                  @click="openFilePicker"
                >
                  <icon-image :size="19" />
                </a-button>
              </a-tooltip>
              <MsSelect
                v-model:model-value="model"
                :options="models"
                option-tooltip-position="left"
                full-tooltip-position="left"
                size="mini"
                class="ai-model-select"
              ></MsSelect>
              <span class="ai-vision-label">{{ t('ms.ai.imageSupported') }}</span>
            </div>
            <div class="ai-sender-toolbar__right">
              <a-tooltip v-if="props.type !== 'chat'" :content="t('common.setting')" position="tr">
                <a-button
                  type="text"
                  shape="circle"
                  class="ai-composer-icon"
                  :aria-label="t('common.setting')"
                  @click="configModalVisible = true"
                >
                  <MsIcon type="icon-icon-setting" :size="19" />
                </a-button>
              </a-tooltip>
              <a-tooltip v-if="answering" :content="t('ms.ai.stop')" position="tr">
                <a-button
                  type="primary"
                  shape="circle"
                  class="ai-send-button"
                  :aria-label="t('ms.ai.stop')"
                  @click="handleSendClick"
                >
                  <MsIcon type="icon-icon_stop_outlined" :size="18" />
                </a-button>
              </a-tooltip>
              <a-tooltip v-else :content="t('ms.ai.send')" position="tr">
                <a-button
                  type="primary"
                  shape="circle"
                  class="ai-send-button"
                  :aria-label="t('ms.ai.send')"
                  :disabled="!canSubmit"
                  @click="handleSendClick"
                >
                  <MsIcon type="icon-icon_release" :size="18" />
                </a-button>
              </a-tooltip>
            </div>
          </div>
        </template>
      </Sender>
    </div>
  </a-spin>
  <caseConfigModal v-if="props.type === 'case'" v-model:visible="configModalVisible" />
  <caseModuleSelectModal
    v-if="props.type === 'case'"
    v-model:visible="caseModuleSelectModalVisible"
    :prompt="checkedCases.join('')"
    :conversation-id="activeConversation?.id || ''"
    :model="model"
    :template-id="props.templateId || ''"
    @sync-success="emit('syncSuccess')"
  />
  <apiConfigModal v-if="props.type === 'api'" v-model:visible="configModalVisible" />
  <apiSelectModal v-if="props.type === 'chat'" v-model:visible="apiSelectModalVisible" />
</template>

<script setup lang="ts">
  import { useClipboard } from '@vueuse/core';
  import { Message } from '@arco-design/web-vue';
  import { Bubble, BubbleList, Sender, Typewriter } from 'vue-element-plus-x';

  import MsAiButton from '@/components/pure/ms-ai-button/index.vue';
  import MsAvatar from '@/components/pure/ms-avatar/index.vue';
  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsIcon from '@/components/pure/ms-icon-font/index.vue';
  import MsSelect from '@/components/business/ms-select';

  import { addAiChat, aiChat, getAiChatDetail, updateAiChatTitle } from '@/api/modules/ai';
  import { apiAiCaseBatchSave, apiAiChat, apiAiTransform } from '@/api/modules/api-test/management';
  import { caseAiChat, caseAiTransform } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useOpenNewPage from '@/hooks/useOpenNewPage';
  import useAppStore from '@/store/modules/app';
  import useAIStore from '@/store/modules/setting/ai';
  import { getGenerateId } from '@/utils';

  import { AiCaseTransformResult, AiChatContentItem, AiChatListItem, AiImageAttachment } from '@/models/ai';
  import { ApiCaseDetail } from '@/models/apiTest/management';
  import { AiChatContentRoleTypeEnum } from '@/enums/aiEnums';
  import { CaseManagementRouteEnum } from '@/enums/routeEnum';

  const apiConfigModal = defineAsyncComponent(() => import('./apiConfigModal.vue'));
  const apiSelectModal = defineAsyncComponent(() => import('./apiSelectModal.vue'));
  const caseConfigModal = defineAsyncComponent(() => import('./caseConfigModal.vue'));
  const caseModuleSelectModal = defineAsyncComponent(() => import('./caseModuleSelect.vue'));

  const props = withDefaults(
    defineProps<{
      type: 'case' | 'api' | 'chat';
      apiDefinitionId?: string | number;
      moduleId?: string | number; // 用例保存时的模块
      templateId?: string | number; // 功能用例保存时需要的模板 id
    }>(),
    {
      type: 'chat',
    }
  );
  const emit = defineEmits<{
    (e: 'openNewConversation'): void;
    (e: 'addSuccess', tempId: string, conversationItem: AiChatListItem): void;
    (e: 'answeringChange', conversationId: string, value: boolean): void;
    (e: 'syncSuccess'): void;
    (e: 'syncApiCase', detail: ApiCaseDetail): void;
    (e: 'syncFeatureCase', detail: AiCaseTransformResult): void;
  }>();

  const { t } = useI18n();
  const { openNewPage } = useOpenNewPage();
  const { copy, isSupported } = useClipboard({ legacy: true });
  const appStore = useAppStore();
  const aiStore = useAIStore();

  const activeConversation = defineModel<AiChatListItem | undefined>('value', {
    required: true,
  });

  interface ConversationRuntime {
    items: AiChatContentItem[];
    senderValue: string;
    draftAttachments: AiImageAttachment[];
    checkedCases: string[];
    answering: boolean;
    loading: boolean;
    initialized: boolean;
    answerController?: AbortController;
  }

  const detachedConversationId = `detached-${getGenerateId()}`;
  const conversationRuntimes = reactive(new Map<string, ConversationRuntime>());

  function createRuntime(): ConversationRuntime {
    return {
      items: [],
      senderValue: '',
      draftAttachments: [],
      checkedCases: [],
      answering: false,
      loading: false,
      initialized: false,
    };
  }

  function getRuntime(conversationId = activeConversation.value?.id || detachedConversationId) {
    if (!conversationRuntimes.has(conversationId)) {
      conversationRuntimes.set(conversationId, createRuntime());
    }
    return conversationRuntimes.get(conversationId) as ConversationRuntime;
  }

  const currentRuntime = computed(() => getRuntime());
  const answering = computed(() => currentRuntime.value.answering);
  const loading = computed({
    get: () => currentRuntime.value.loading,
    set: (value: boolean) => {
      currentRuntime.value.loading = value;
    },
  });
  const title = computed(() => activeConversation.value?.title || '');
  const editTitle = ref(false);
  const titleInputRef = ref<InstanceType<typeof HTMLInputElement>>();
  const tempTitle = ref('');

  function handleEditTitle() {
    if (activeConversation.value?.isNew) {
      return;
    }
    tempTitle.value = title.value;
    editTitle.value = true;
    nextTick(() => {
      titleInputRef.value?.focus();
    });
  }

  async function handleSaveTitle() {
    try {
      if (!tempTitle.value.trim()) {
        editTitle.value = false;
        return;
      }
      await updateAiChatTitle({
        id: activeConversation.value?.id || '',
        title: tempTitle.value.trim(),
      });
      if (activeConversation.value) {
        activeConversation.value.title = tempTitle.value.trim();
      }
      editTitle.value = false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('Error saving title:', error);
    }
  }

  const senderRef = ref<InstanceType<typeof Sender>>();
  const fileInputRef = ref<HTMLInputElement>();
  const senderValue = computed({
    get: () => currentRuntime.value.senderValue,
    set: (value: string) => {
      currentRuntime.value.senderValue = value;
    },
  });
  const draftAttachments = computed({
    get: () => currentRuntime.value.draftAttachments,
    set: (value: AiImageAttachment[]) => {
      currentRuntime.value.draftAttachments = value;
    },
  });
  const draggingImages = ref(false);
  const canSubmit = computed(
    () => !loading.value && !answering.value && (!!senderValue.value.trim() || draftAttachments.value.length > 0)
  );
  const supportedImageTypes = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif']);
  const maxImageCount = 4;
  const maxImageBytes = 5 * 1024 * 1024;
  const maxTotalImageBytes = 10 * 1024 * 1024;
  let dragDepth = 0;

  function attachmentSrc(attachment: AiImageAttachment) {
    return `data:${attachment.mimeType};base64,${attachment.data}`;
  }

  function readImageAsBase64(file: File) {
    return new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || '').split(',')[1] || '');
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(file);
    });
  }

  async function addImageFiles(files: File[]) {
    if (!files.length) return;
    if (draftAttachments.value.length + files.length > maxImageCount) {
      Message.error(t('ms.ai.imageCountLimit', { count: maxImageCount }));
      return;
    }
    if (files.some((file) => !supportedImageTypes.has(file.type.toLowerCase()))) {
      Message.error(t('ms.ai.imageFormatError'));
      return;
    }
    if (files.some((file) => file.size === 0 || file.size > maxImageBytes)) {
      Message.error(t('ms.ai.imageSizeLimit'));
      return;
    }
    const totalBytes =
      draftAttachments.value.reduce((total, attachment) => total + attachment.size, 0) +
      files.reduce((total, file) => total + file.size, 0);
    if (totalBytes > maxTotalImageBytes) {
      Message.error(t('ms.ai.imageTotalSizeLimit'));
      return;
    }

    try {
      const attachments = await Promise.all(
        files.map(async (file) => ({
          id: getGenerateId(),
          name: file.name || `image-${Date.now()}`,
          mimeType: file.type.toLowerCase(),
          data: await readImageAsBase64(file),
          size: file.size,
        }))
      );
      draftAttachments.value.push(...attachments);
      nextTick(() => senderRef.value?.focus());
    } catch (error) {
      Message.error(t('ms.ai.imageReadError'));
    }
  }

  function openFilePicker() {
    fileInputRef.value?.click();
  }

  function handleFileInput(event: Event) {
    const input = event.target as HTMLInputElement;
    addImageFiles(Array.from(input.files || []));
    input.value = '';
  }

  function handlePasteFile(_firstFile: File, fileList: FileList) {
    addImageFiles(Array.from(fileList));
  }

  function removeDraftAttachment(id: string) {
    draftAttachments.value = draftAttachments.value.filter((attachment) => attachment.id !== id);
  }

  function isFileDrag(event: DragEvent) {
    return Array.from(event.dataTransfer?.types || []).includes('Files');
  }

  function handleDragEnter(event: DragEvent) {
    if (!isFileDrag(event)) return;
    dragDepth += 1;
    draggingImages.value = true;
  }

  function handleDragOver(event: DragEvent) {
    if (event.dataTransfer && isFileDrag(event)) {
      event.dataTransfer.dropEffect = 'copy';
    }
  }

  function handleDragLeave(event: DragEvent) {
    if (!isFileDrag(event)) return;
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) draggingImages.value = false;
  }

  function handleDrop(event: DragEvent) {
    dragDepth = 0;
    draggingImages.value = false;
    addImageFiles(Array.from(event.dataTransfer?.files || []));
  }
  const model = ref(aiStore.aiSourceNameList[0]?.id || ''); // 默认选择第一个模型
  const models = computed(() =>
    aiStore.aiSourceNameList.map((item) => ({
      label: item.name,
      value: item.id,
    }))
  );

  watch(
    () => models.value,
    (vals) => {
      model.value =
        vals.find((item) => item.value === localStorage.getItem('aiChatModel'))?.value || vals[0]?.value || '';
    },
    { immediate: true }
  );

  watch(model, (newModel) => {
    localStorage.setItem('aiChatModel', newModel);
  });

  const configModalVisible = ref(false);
  const bubbleListRef = ref<any>();
  const conversationItems = computed({
    get: () => currentRuntime.value.items,
    set: (value: AiChatContentItem[]) => {
      currentRuntime.value.items = value;
    },
  });
  const checkedCases = computed({
    get: () => currentRuntime.value.checkedCases,
    set: (value: string[]) => {
      currentRuntime.value.checkedCases = value;
    },
  });
  const contextLabel = computed(() => t(`ms.ai.context.${props.type}`));
  const emptyTitle = computed(() => t(`ms.ai.empty.${props.type}`));
  const quickPrompts = computed(() => {
    if (props.type === 'api') {
      return [t('ms.ai.prompt.apiBoundary'), t('ms.ai.prompt.apiNegative'), t('ms.ai.prompt.apiAssertions')];
    }
    if (props.type === 'case') {
      return [t('ms.ai.prompt.featureFlow'), t('ms.ai.prompt.featureBoundary'), t('ms.ai.prompt.featureRisk')];
    }
    return [t('ms.ai.prompt.analyzeApi'), t('ms.ai.prompt.designScenario'), t('ms.ai.prompt.reviewFailure')];
  });
  const sendPlaceholder = computed(() => {
    if (props.type === 'case') {
      return t('ms.ai.casePlaceholder');
    }
    if (props.type === 'api') {
      return t('ms.ai.apiCasePlaceholder');
    }
    return t('ms.ai.sendTip');
  });

  function handleCopy(item: AiChatContentItem) {
    if (isSupported) {
      copy(item.content || '');
      Message.success(t('common.copySuccess'));
    } else {
      Message.error(t('common.copyNotSupport'));
    }
  }

  function applyPrompt(prompt: string) {
    senderValue.value = prompt;
    nextTick(() => senderRef.value?.focus());
  }

  const apiSelectModalVisible = ref(false);
  const caseModuleSelectModalVisible = ref(false);
  function jump(type: 'case' | 'api') {
    if (type === 'api') {
      apiSelectModalVisible.value = true;
    } else {
      openNewPage(CaseManagementRouteEnum.CASE_MANAGEMENT, {
        openAi: 'Y',
      });
    }
  }

  function handleEdit(item: AiChatContentItem) {
    senderValue.value = item.content || '';
    draftAttachments.value = (item.attachments || []).map((attachment) => ({ ...attachment }));
    senderRef.value?.focus();
  }

  async function addChat(prompt: string, tempId: string, runtime: ConversationRuntime, signal: AbortSignal) {
    const addRes = await addAiChat(
      {
        prompt,
        chatModelId: model.value,
        conversationId: tempId,
        organizationId: appStore.currentOrgId || '',
      },
      signal
    );
    const conversationItem: AiChatListItem = {
      id: addRes.id,
      title: addRes.title,
      isNew: false,
      createTime: addRes.createTime,
      createUser: addRes.createUser,
    };
    conversationRuntimes.delete(tempId);
    conversationRuntimes.set(addRes.id, runtime);
    emit('answeringChange', tempId, false);
    emit('answeringChange', addRes.id, true);
    emit('addSuccess', tempId, conversationItem);
    return addRes.id;
  }

  function updateAssistant(runtime: ConversationRuntime, assistantId: string, content: string) {
    const assistantIndex = runtime.items.findIndex((item) => item.id === assistantId);
    if (assistantIndex < 0) return;
    runtime.items[assistantIndex] = {
      ...runtime.items[assistantIndex],
      content,
      loading: false,
    };
  }

  async function handleSubmit() {
    if (!canSubmit.value) return;
    const prompt = senderValue.value.trim() || t('ms.ai.imageAnalysisPrompt');
    const attachments = draftAttachments.value.map((attachment) => ({ ...attachment }));
    let submittedConversation = activeConversation.value;
    const sourceRuntime = currentRuntime.value;
    if (!submittedConversation) {
      emit('openNewConversation');
      submittedConversation = activeConversation.value;
      if (submittedConversation) {
        conversationRuntimes.delete(detachedConversationId);
        conversationRuntimes.set(submittedConversation.id, sourceRuntime);
      }
    }
    if (!submittedConversation) return;

    const tempId = submittedConversation.id;
    const runtime = getRuntime(tempId);
    const assistantId = getGenerateId();
    runtime.items.push({
      id: getGenerateId(),
      conversationId: tempId,
      type: AiChatContentRoleTypeEnum.USER,
      isEdit: false,
      timestamp: new Date().getTime(),
      content: prompt,
      attachments,
      placement: 'end',
    });
    runtime.items.push({
      id: assistantId,
      conversationId: tempId,
      type: AiChatContentRoleTypeEnum.ASSISTANT,
      isEdit: false,
      timestamp: new Date().getTime(),
      content: t('ms.ai.thinking'),
      loading: true,
      placement: 'start',
      checkedCases: [],
    });
    runtime.senderValue = '';
    runtime.draftAttachments = [];
    nextTick(() => {
      bubbleListRef.value?.scrollToBottom();
    });
    const controller = markRaw(new AbortController());
    runtime.answerController = controller;
    runtime.answering = true;
    emit('answeringChange', tempId, true);
    let conversationId = tempId;
    try {
      let res = '';
      if (submittedConversation.isNew) {
        conversationId = await addChat(prompt, tempId, runtime, controller.signal);
      }
      if (props.type === 'case') {
        res = await caseAiChat(
          {
            prompt,
            attachments,
            chatModelId: model.value,
            conversationId,
            organizationId: appStore.currentOrgId || '',
          },
          controller.signal
        );
      } else if (props.type === 'api') {
        res = await apiAiChat(
          {
            prompt,
            attachments,
            chatModelId: model.value,
            conversationId,
            organizationId: appStore.currentOrgId || '',
            apiDefinitionId: props.apiDefinitionId || '',
          },
          controller.signal
        );
      } else {
        res = await aiChat(
          {
            prompt,
            attachments,
            chatModelId: model.value,
            conversationId,
            organizationId: appStore.currentOrgId || '',
          },
          controller.signal
        );
      }
      updateAssistant(runtime, assistantId, res);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('Error submitting:', error);
      if ((error as any).code === 'ERR_CANCELED' || controller.signal.aborted) {
        updateAssistant(runtime, assistantId, t('ms.ai.hasStopped'));
      } else {
        updateAssistant(runtime, assistantId, t('ms.ai.failed'));
      }
    } finally {
      runtime.answering = false;
      runtime.answerController = undefined;
      emit('answeringChange', tempId, false);
      emit('answeringChange', conversationId, false);
    }
  }

  function handleReset(item: AiChatContentItem) {
    const lastUserAsk = conversationItems.value[conversationItems.value.findIndex((e) => e.id === item.id) - 1];

    if (lastUserAsk) {
      senderValue.value = lastUserAsk.content || '';
      draftAttachments.value = (lastUserAsk.attachments || []).map((attachment) => ({ ...attachment }));
      handleSubmit();
    }
  }

  async function syncSingleApiCase() {
    try {
      loading.value = true;
      const res = await apiAiTransform({
        prompt: checkedCases.value[0],
        chatModelId: model.value,
        conversationId: activeConversation.value?.id || '',
        organizationId: appStore.currentOrgId || '',
        apiDefinitionId: props.apiDefinitionId || '',
      });
      emit('syncApiCase', res);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('Error syncing single API case:', error);
    } finally {
      loading.value = false;
    }
  }

  async function syncSingleFeatureCase() {
    try {
      loading.value = true;
      const res = await caseAiTransform({
        prompt: checkedCases.value[0],
        chatModelId: model.value,
        conversationId: activeConversation.value?.id || '',
        organizationId: appStore.currentOrgId || '',
      });
      emit('syncFeatureCase', res);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('Error syncing single feature case:', error);
    } finally {
      loading.value = false;
    }
  }

  async function batchSaveAiCase() {
    try {
      loading.value = true;
      if (props.type === 'api') {
        const res = await apiAiCaseBatchSave({
          prompt: checkedCases.value.join('apiCaseEnd'),
          chatModelId: model.value,
          conversationId: activeConversation.value?.id || '',
          organizationId: appStore.currentOrgId || '',
          apiDefinitionId: props.apiDefinitionId || '',
          projectId: appStore.currentProjectId || '',
        });
        if (res.errorCount > 0) {
          Message.success(
            t('ms.ai.errorSyncTip', {
              successCount: res.successCount,
              errorCount: res.errorCount,
              errorDetail: res.errorDetail,
            })
          );
        } else {
          Message.success(t('ms.ai.caseSyncSuccess'));
        }
        checkedCases.value = [];
      } else if (props.type === 'case') {
        caseModuleSelectModalVisible.value = true;
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('Error batch saving AI case:', error);
    } finally {
      loading.value = false;
    }
  }

  function handleSync() {
    if (checkedCases.value.length === 1) {
      if (props.type === 'api') {
        syncSingleApiCase();
      } else if (props.type === 'case') {
        syncSingleFeatureCase();
      }
    } else {
      batchSaveAiCase();
    }
  }

  function stopAnswer(conversationId = activeConversation.value?.id) {
    if (!conversationId) return;
    const runtime = conversationRuntimes.get(conversationId);
    if (!runtime?.answering) return;
    runtime.answering = false;
    runtime.answerController?.abort();
    emit('answeringChange', conversationId, false);
  }

  function handleSendClick() {
    if (answering.value) {
      stopAnswer(activeConversation.value?.id);
      return;
    }
    if (canSubmit.value) {
      handleSubmit();
    }
  }

  async function initDetail() {
    const conversation = activeConversation.value;
    if (!conversation) return;
    const runtime = getRuntime(conversation.id);
    if (conversation.isNew) {
      runtime.initialized = true;
      return;
    }
    if (runtime.initialized || runtime.loading || runtime.answering) return;
    try {
      runtime.loading = true;
      const res = await getAiChatDetail(conversation.id);
      runtime.items = res.map((e) => ({
        ...e,
        attachments: e.attachments || [],
        checkedCases: [],
        placement: e.type === AiChatContentRoleTypeEnum.ASSISTANT ? 'start' : 'end',
      }));
      runtime.checkedCases = [];
      runtime.initialized = true;
      editTitle.value = false;
      if (activeConversation.value?.id === conversation.id) {
        nextTick(() => {
          bubbleListRef.value?.scrollToBottom();
        });
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('Error initializing conversation detail:', error);
      runtime.items = [];
      runtime.checkedCases = [];
    } finally {
      runtime.loading = false;
    }
  }

  watch(
    () => activeConversation.value?.id,
    () => {
      editTitle.value = false;
      initDetail();
    },
    {
      immediate: true,
    }
  );

  function stopAllAnswers() {
    conversationRuntimes.forEach((runtime, conversationId) => {
      if (runtime.answering) {
        stopAnswer(conversationId);
      }
    });
  }

  function removeConversation(conversationId: string) {
    stopAnswer(conversationId);
    conversationRuntimes.delete(conversationId);
  }

  onBeforeUnmount(stopAllAnswers);

  defineExpose({
    removeConversation,
    stopAllAnswers,
  });
</script>

<style lang="less" scoped>
  .ai-conversation-shell {
    @apply relative flex h-full flex-col overflow-hidden;

    padding: 0 20px 18px;
  }
  .ai-drop-overlay {
    @apply absolute inset-0 flex items-center justify-center;

    z-index: 20;
    border: 2px dashed rgb(var(--primary-5));
    background: rgb(255 255 255 / 90%);
  }
  .ai-drop-content {
    @apply flex flex-col items-center justify-center;

    gap: 10px;
    font-size: 14px;
    font-weight: 500;
    color: rgb(var(--primary-6));
  }
  .ai-conversation-header {
    @apply flex items-center;

    gap: 10px;
    min-height: 68px;
    border-bottom: 1px solid var(--color-text-n8);
  }
  .ai-message-viewport,
  .ai-message-group,
  :deep(.ai-message-list.el-bubble-list-wrapper),
  :deep(.ai-message-list .el-bubble-list) {
    min-height: 0;
  }
  .ai-message-group,
  :deep(.ai-message-list.el-bubble-list-wrapper),
  :deep(.ai-message-list .el-bubble-list) {
    height: 100%;
  }
  :deep(.ai-message-list.el-bubble-list-wrapper) {
    overflow: hidden;
  }
  :deep(.ai-message-list .el-bubble-list) {
    overflow-y: auto;
    max-height: none !important;
    overscroll-behavior: contain;
  }
  .ai-context-mark {
    @apply flex shrink-0 items-center justify-center;

    width: 36px;
    height: 36px;
    border: 1px solid rgb(var(--primary-2));
    border-radius: 6px;
    background: rgb(var(--primary-1));
  }
  .ai-context-label {
    font-size: 11px;
    line-height: 16px;
    color: var(--color-text-4);
  }
  .conversation-title-display {
    max-width: 100%;
    cursor: pointer;
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
    color: var(--color-text-1);
  }
  .ai-ready-indicator {
    @apply shrink-0;

    padding: 2px 7px;
    font-size: 11px;
    line-height: 18px;
    border: 1px solid #b7dfca;
    border-radius: 4px;
    color: #197044;
    background: #f0faf5;
  }
  .ai-empty-workspace {
    @apply flex h-full flex-col items-center justify-center;

    margin: 0 auto;
    width: min(100%, 420px);
    min-height: 300px;
    h3 {
      margin: 14px 0 18px;
      font-size: 16px;
      font-weight: 600;
      line-height: 24px;
      color: var(--color-text-1);
    }
  }
  .ai-empty-icon {
    @apply flex items-center justify-center;

    width: 64px;
    height: 64px;
    border: 1px solid var(--color-text-n8);
    border-radius: 8px;
    background: #f7f8fa;
  }
  .ai-quick-grid {
    @apply grid w-full;

    gap: 8px;
  }
  .ai-quick-command {
    @apply flex w-full items-center justify-between text-left;

    gap: 12px;
    padding: 10px 12px;
    min-height: 42px;
    font-size: 13px;
    line-height: 20px;
    border: 1px solid var(--color-text-n8);
    border-radius: 6px;
    color: var(--color-text-2);
    background: var(--color-text-fff);
    transition: border-color 0.15s ease, background-color 0.15s ease;
    &:hover {
      border-color: rgb(var(--primary-4));
      color: rgb(var(--primary-6));
      background: rgb(var(--primary-1));
    }
  }
  .ai-composer-shell {
    overflow: hidden;
    border: 1px solid var(--color-text-n7);
    border-radius: 8px;
    background: var(--color-text-fff);
    box-shadow: 0 8px 24px rgb(30 34 42 / 10%);
    transition: border-color 0.15s ease, box-shadow 0.15s ease;
    &:focus-within {
      border-color: rgb(var(--primary-6));
      box-shadow: 0 8px 24px rgb(30 34 42 / 12%), 0 0 0 1px rgb(var(--primary-2));
    }
  }
  .ai-command-sender {
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }
  .ai-draft-attachments {
    @apply flex flex-wrap;

    gap: 8px;
    padding: 12px 12px 0;
  }
  .ai-draft-image-wrap {
    @apply relative shrink-0;

    width: 72px;
    height: 72px;
  }
  .ai-draft-image,
  .ai-message-image {
    overflow: hidden;
    border: 1px solid var(--color-text-n8);
    border-radius: 6px;
    background: var(--color-text-n9);
  }
  .ai-remove-image {
    @apply absolute flex items-center justify-center;

    top: -6px;
    right: -6px;
    z-index: 2;
    padding: 0;
    width: 24px;
    height: 24px;
    border: 1px solid var(--color-text-n8);
    border-radius: 50%;
    color: var(--color-text-2);
    background: var(--color-text-fff);
    box-shadow: 0 2px 6px rgb(30 34 42 / 16%);
    cursor: pointer;
    &:hover {
      border-color: rgb(var(--danger-3));
      color: rgb(var(--danger-6));
    }
  }
  .ai-sender-toolbar {
    @apply flex min-w-0 flex-1 items-center justify-between;

    gap: 12px;
    width: 100%;
  }
  .ai-sender-toolbar__left,
  .ai-sender-toolbar__right {
    @apply flex min-w-0 items-center;

    gap: 6px;
  }
  .ai-sender-toolbar__right {
    @apply shrink-0;
  }
  .ai-model-select {
    width: 140px !important;
  }
  .ai-vision-label {
    @apply shrink-0;

    font-size: 11px;
    line-height: 18px;
    color: var(--color-text-4);
  }
  .ai-composer-icon,
  .ai-send-button {
    padding: 0;
    width: 34px;
    min-width: 34px;
    height: 34px;
  }
  .ai-send-button {
    border-color: #202124;
    color: #ffffff;
    background: #202124;
    &:not(.arco-btn-disabled):hover {
      border-color: #34363a;
      color: #ffffff;
      background: #34363a;
    }
  }
  :deep(.ai-command-sender.el-sender) {
    border: 0;
    box-shadow: none;
    &::after {
      display: none;
    }
  }
  :deep(.ai-command-sender .el-sender-updown-wrap) {
    width: 100%;
  }
  :deep(.ai-command-sender .el-sender-action-list) {
    width: 100%;
  }
  :deep(.el-bubble) {
    gap: 8px;
    .el-bubble-content-filled {
      padding: 0;
      width: 100%;
      max-width: 100%;
      background: var(--color-text-fff);
    }
    .el-bubble-footer {
      margin-top: 4px;
    }
  }
  :deep(.el-bubble-end) {
    .el-bubble-content-filled {
      @apply flex justify-end;
    }
  }
  .conversation-title {
    padding: 0;
    width: 100%;
    :deep(.arco-input) {
      padding: 0;
      font-size: 14px;
      font-weight: 600;
      line-height: 22px;
    }
  }
  .avatar-wrapper {
    @apply overflow-hidden;

    width: 30px;
    height: 30px;
    border-radius: 6px;
    background-color: var(--color-text-n9);
    img {
      width: 100%;
      height: 100%;
    }
  }
  .header-wrapper {
    .header-name {
      font-size: 14px;
      color: #979797;
    }
  }
  .content-wrapper {
    @apply relative flex;

    max-width: 84%;
    .content-text {
      @apply w-full;

      padding: 12px;
      min-height: 48px;
      font-size: 14px;
      border: 1px solid rgb(var(--link-2));
      border-radius: 6px;
      color: var(--color-text-1);
      background: rgb(var(--link-1));
      :deep(.typer-content) {
        color: var(--color-text-1);
        background: rgb(var(--link-1));
      }
      .case-collapse-item {
        &:not(:last-child) {
          margin-bottom: 24px;
        }
      }
      :deep(.arco-collapse-item-icon-right) {
        right: 0;
      }
      :deep(.arco-collapse-item-header-title) {
        width: calc(100% - 20px);
      }
      :deep(.arco-collapse-item-content) {
        margin-top: 16px;
        padding: 0;
      }
    }
    :deep(.markdown-body) {
      color: var(--color-text-1) !important;
    }
  }
  .content-wrapper--assistant {
    width: 100%;
    max-width: 100%;
    .content-text--assistant {
      padding: 4px 0 12px;
      min-height: 0;
      border: 0;
      border-radius: 0;
      background: transparent;
      :deep(.typer-content) {
        background: transparent;
      }
    }
  }
  .ai-message-attachments {
    @apply flex flex-wrap;

    gap: 8px;
    margin-bottom: 10px;
  }
  .ai-message-attachments--user {
    justify-content: flex-end;
  }
  :deep(.el-bubble-loading-wrap) {
    @apply !justify-start;
  }
  .loading-wrapper {
    padding: 8px;
    min-height: 48px;
    font-size: 12px;
    color: var(--color-text-1);
    :deep(.arco-dot-loading) {
      width: 48px;
      .arco-dot-loading-item {
        width: 6px;
        height: 6px;
      }
    }
  }
  .footer-wrapper {
    display: flex;
    align-items: center;
  }
  .footer-container {
    @apply flex items-center;

    gap: 8px;
  }
  .ai-conversation-actions {
    min-width: 0;
    min-height: 0;
  }
</style>
