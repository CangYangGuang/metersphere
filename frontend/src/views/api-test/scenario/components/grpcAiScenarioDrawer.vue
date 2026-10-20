<template>
  <MsDrawer
    v-model:visible="visible"
    :width="1080"
    :title="t('apiScenario.grpcAi.title')"
    :closable="false"
    :ok-text="t('apiScenario.grpcAi.create')"
    :ok-loading="createLoading"
    :ok-disabled="!canCreate"
    disabled-width-drag
    @confirm="confirmCreate"
    @cancel="closeDrawer"
  >
    <a-spin :loading="generateLoading || projectContextLoading" class="w-full">
      <div class="draft-state-bar">
        <div class="flex min-w-0 items-center gap-[6px]">
          <icon-save />
          <span v-if="saveStatus === 'saving'">{{ t('apiScenario.grpcAi.saving') }}</span>
          <span v-else-if="saveStatus === 'restored'">{{ t('apiScenario.grpcAi.restored') }}</span>
          <span v-else-if="saveStatus === 'saved'">
            {{ t('apiScenario.grpcAi.savedAt', { time: savedTime }) }}
          </span>
          <span v-else>{{ t('apiScenario.grpcAi.autoSave') }}</span>
        </div>
        <a-button v-if="hasDraftContent" type="text" status="danger" size="small" @click="discardDraft">
          <template #icon><icon-delete /></template>
          {{ t('apiScenario.grpcAi.discard') }}
        </a-button>
      </div>

      <a-form :model="form" layout="vertical">
        <div class="grid grid-cols-3 gap-[12px]">
          <a-form-item :label="t('apiScenario.grpcAi.model')" required>
            <a-select v-model="form.chatModelId" :options="modelOptions" allow-search />
          </a-form-item>
          <a-form-item :label="t('apiScenario.belongModule')" required>
            <a-tree-select
              v-model="form.moduleId"
              :data="moduleTree"
              :field-names="{ title: 'name', key: 'id', children: 'children' }"
              allow-search
            />
          </a-form-item>
          <a-form-item :label="t('apiScenario.grpcAi.serverAddress')">
            <a-input
              v-model="form.serverAddress"
              allow-clear
              :placeholder="t('apiScenario.grpcAi.serverAddressAuto')"
            />
          </a-form-item>
        </div>

        <section class="project-context-panel">
          <div class="flex min-w-0 items-center gap-[8px]">
            <icon-folder />
            <span class="font-medium">{{ t('apiScenario.grpcAi.projectContext') }}</span>
            <a-tag v-if="projectDirectoryHandle" color="green" class="max-w-[360px]">
              <span class="one-line-text">{{ projectDirectoryHandle.name }}</span>
            </a-tag>
            <a-tag v-else color="gray">{{ t('apiScenario.grpcAi.notLinked') }}</a-tag>
          </div>
          <div class="flex items-center gap-[8px]">
            <span v-if="projectContextSummary" class="text-[12px] text-[var(--color-text-3)]">
              {{ projectContextSummary }}
            </span>
            <a-button type="outline" size="small" :disabled="!projectDirectorySupported" @click="linkProjectDirectory">
              <template #icon><icon-folder-add /></template>
              {{ projectDirectoryHandle ? t('apiScenario.grpcAi.changeFolder') : t('apiScenario.grpcAi.linkFolder') }}
            </a-button>
            <a-tooltip v-if="projectDirectoryHandle" :content="t('apiScenario.grpcAi.unlinkFolder')">
              <a-button type="text" size="small" @click="unlinkProjectDirectory">
                <template #icon><icon-link /></template>
              </a-button>
            </a-tooltip>
          </div>
        </section>

        <a-form-item :label="t('apiScenario.grpcAi.source')" class="!mb-[12px]">
          <a-textarea
            v-model="form.sourceText"
            :auto-size="{ minRows: 5, maxRows: 10 }"
            :placeholder="t('apiScenario.grpcAi.sourcePlaceholder')"
          />
        </a-form-item>

        <input
          ref="fileInputRef"
          class="hidden"
          type="file"
          multiple
          accept=".txt,.md,.csv,.xlsx,.docx,.png,.jpg,.jpeg"
          @change="handleFileChange"
        />
        <section
          class="attachment-panel"
          :class="{ 'attachment-panel--dragging': draggingFiles }"
          @dragenter.prevent="draggingFiles = true"
          @dragover.prevent="draggingFiles = true"
          @dragleave.prevent="draggingFiles = false"
          @drop.prevent="handleFileDrop"
        >
          <div class="attachment-toolbar">
            <div class="flex items-center gap-[8px]">
              <icon-image />
              <span class="font-medium">{{ t('apiScenario.grpcAi.attachments') }}</span>
              <a-tag v-if="files.length" size="small">
                {{ t('apiScenario.grpcAi.attachmentCount', { count: files.length }) }}
              </a-tag>
              <a-tag v-if="imageCount" size="small" color="arcoblue">
                {{ t('apiScenario.grpcAi.screenshotCount', { count: imageCount }) }}
              </a-tag>
            </div>
            <div class="flex items-center gap-[8px]">
              <a-button type="outline" size="small" :loading="pastingScreenshot" @click="pasteScreenshot">
                <template #icon><icon-paste /></template>
                {{ t('apiScenario.grpcAi.pasteScreenshot') }}
              </a-button>
              <a-button type="outline" size="small" @click="fileInputRef?.click()">
                <template #icon><icon-upload /></template>
                {{ t('apiScenario.grpcAi.addFiles') }}
              </a-button>
            </div>
          </div>
          <div v-if="files.length" class="attachment-content">
            <div v-if="imageFiles.length" class="attachment-image-grid">
              <article
                v-for="file in imageFiles"
                :key="fileKey(file)"
                class="attachment-image-card"
                tabindex="0"
                role="button"
                @click="handlePreview(file)"
                @keydown.enter="handlePreview(file)"
              >
                <div class="attachment-image-preview">
                  <img :src="filePreviewMap[fileKey(file)]" :alt="file.name" class="attachment-image" />
                  <div class="attachment-image-mask"><icon-zoom-in :size="24" /></div>
                  <a-tooltip :content="t('common.delete')">
                    <a-button
                      class="attachment-image-delete"
                      type="secondary"
                      size="mini"
                      @click.stop="removeFile(file)"
                    >
                      <template #icon><icon-close /></template>
                    </a-button>
                  </a-tooltip>
                </div>
                <div class="attachment-image-info">
                  <div class="one-line-text text-[13px]" :title="file.name">{{ file.name }}</div>
                  <div class="mt-[4px] flex items-center justify-between gap-[6px]">
                    <span class="text-[12px] text-[var(--color-text-3)]">{{ formatFileSize(file.size) }}</span>
                    <a-tag size="small" color="arcoblue">
                      {{
                        isClipboardScreenshot(file)
                          ? t('apiScenario.grpcAi.screenshotAnalysis')
                          : t('apiScenario.grpcAi.imageAnalysis')
                      }}
                    </a-tag>
                  </div>
                </div>
              </article>
            </div>
            <div v-if="documentFiles.length" class="attachment-document-list">
              <article v-for="file in documentFiles" :key="fileKey(file)" class="attachment-document-card">
                <div class="attachment-document"><icon-file :size="28" /></div>
                <div class="min-w-0 flex-1">
                  <div class="one-line-text text-[13px]" :title="file.name">{{ file.name }}</div>
                  <div class="mt-[2px] text-[12px] text-[var(--color-text-3)]">{{ formatFileSize(file.size) }}</div>
                </div>
                <a-tooltip :content="t('common.delete')">
                  <a-button type="text" size="mini" @click="removeFile(file)">
                    <template #icon><icon-close /></template>
                  </a-button>
                </a-tooltip>
              </article>
            </div>
          </div>
          <div v-else class="attachment-empty">
            <div class="attachment-empty-title">
              <icon-paste :size="18" />
              <span>{{ t('apiScenario.grpcAi.pasteScreenshotHint') }}</span>
            </div>
            <div class="mt-[4px]">{{ t('apiScenario.grpcAi.dropFiles') }}</div>
          </div>
        </section>
        <a-image-preview-group
          v-model:visible="previewVisible"
          v-model:current="previewCurrent"
          infinite
          :src-list="previewList"
        />

        <div class="mb-[16px] mt-[12px] flex justify-end">
          <a-button type="primary" :disabled="!canGenerate" @click="generateDraft(false)">
            <template #icon><svg-icon name="ai" /></template>
            {{ draft ? t('apiScenario.grpcAi.regenerate') : t('apiScenario.grpcAi.generate') }}
          </a-button>
        </div>

        <div v-if="draft" class="mb-[16px] flex items-end gap-[8px] border-t border-[var(--color-neutral-3)] pt-[16px]">
          <a-form-item :label="t('apiScenario.grpcAi.correction')" class="!mb-0 flex-1">
            <a-input v-model="form.correction" allow-clear @press-enter="generateDraft(true)" />
          </a-form-item>
          <a-button :disabled="!form.correction.trim()" @click="generateDraft(true)">
            {{ t('apiScenario.grpcAi.applyCorrection') }}
          </a-button>
        </div>
      </a-form>

      <template v-if="draft">
        <a-alert v-if="draft.protoRequired" type="warning" class="mb-[12px]">
          {{ draft.ambiguities[0] }}
        </a-alert>
        <a-alert v-for="item in draft.ambiguities" v-else :key="item" type="warning" class="mb-[8px]">
          {{ item }}
        </a-alert>
        <a-alert v-for="item in draft.warnings" :key="item" type="info" class="mb-[8px]">
          {{ item }}
        </a-alert>

        <div class="draft-summary">
          <span>{{ draft.sourceSummary }}</span>
          <span>{{ t('apiScenario.grpcAi.selectedCount', { count: selectedScenarioCount }) }}</span>
        </div>

        <section
          v-for="scenario in draft.scenarios"
          :key="scenario.clientId"
          class="scenario-section"
          :class="{ 'scenario-section--disabled': !scenario.selected }"
        >
          <div class="scenario-header">
            <a-checkbox v-model="scenario.selected" :disabled="scenario.blocked" />
            <a-input v-model="scenario.name" class="min-w-0 flex-1" />
            <a-select v-model="scenario.priority" class="w-[88px]" :options="priorityOptions" />
            <a-radio-group v-model="scenario.safety" type="button" size="small">
              <a-radio value="SAFE">SAFE</a-radio>
              <a-radio value="RISK">RISK</a-radio>
            </a-radio-group>
            <a-tag v-if="scenario.suggested" color="arcoblue">{{ t('apiScenario.grpcAi.suggestion') }}</a-tag>
          </div>
          <a-alert v-if="scenario.ambiguity" type="warning" class="mb-[8px]">{{ scenario.ambiguity }}</a-alert>
          <a-textarea v-model="scenario.description" :auto-size="{ minRows: 1, maxRows: 3 }" class="mb-[8px]" />

          <div class="case-table-head">
            <span></span>
            <span>{{ t('apiScenario.grpcAi.caseName') }}</span>
            <span>{{ t('apiScenario.grpcAi.definition') }}</span>
            <span>{{ t('apiScenario.grpcAi.serverAddress') }}</span>
            <span>{{ t('apiScenario.grpcAi.requestJson') }}</span>
          </div>
          <div
            v-for="caseItem in scenario.cases"
            :key="caseItem.clientId"
            class="case-row"
            :class="{ 'case-row--blocked': caseItem.blocked }"
          >
            <a-checkbox v-model="caseItem.selected" :disabled="caseItem.blocked" />
            <div class="min-w-0">
              <a-input v-model="caseItem.name" />
              <a-tag v-if="caseItem.suggested" size="small" color="arcoblue" class="mt-[4px]">
                {{ t('apiScenario.grpcAi.suggestion') }}
              </a-tag>
            </div>
            <a-tooltip :content="caseItem.definitionName">
              <div class="line-clamp-2 text-[var(--color-text-2)]">{{ caseItem.definitionName || '-' }}</div>
            </a-tooltip>
            <a-input v-model="caseItem.serverAddress" placeholder="host:port" @blur="validateCase(caseItem)" />
            <a-textarea
              v-model="caseItem.requestJson"
              class="request-json"
              :auto-size="{ minRows: 2, maxRows: 6 }"
              @blur="validateCase(caseItem)"
            />
            <a-alert v-if="caseItem.ambiguity" type="warning" class="case-ambiguity">
              {{ caseItem.ambiguity }}
            </a-alert>
          </div>
        </section>
      </template>
    </a-spin>
  </MsDrawer>
</template>

<script setup lang="ts">
  import { Message, Modal } from '@arco-design/web-vue';
  import { cloneDeep } from 'lodash-es';
  import localforage from 'localforage';

  import MsDrawer from '@/components/pure/ms-drawer/index.vue';

  import { createGrpcAiScenarios, generateGrpcAiScenarioDraft } from '@/api/modules/api-test/scenario';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import useAIStore from '@/store/modules/setting/ai';
  import useUserStore from '@/store/modules/user';
  import { getGenerateId } from '@/utils';

  import type { GrpcAiCaseDraft, GrpcAiScenarioDraft } from '@/models/apiTest/scenario';
  import type { ModuleTreeNode } from '@/models/common';

  import {
    buildGrpcAiProjectContext,
    ensureProjectDirectoryPermission,
    type ProjectDirectoryHandle,
  } from './grpcAiProjectContext';

  // Vue setup lifecycle callbacks reference function declarations defined later in this cohesive workflow.
  /* eslint-disable no-use-before-define */

  interface GrpcAiFormState {
    chatModelId: string;
    moduleId: string;
    sourceText: string;
    serverAddress: string;
    correction: string;
  }

  interface PersistedGrpcAiState {
    version: 1;
    savedAt: number;
    form: GrpcAiFormState;
    files: File[];
    draft?: GrpcAiScenarioDraft;
    conversationId: string;
  }

  type SaveStatus = 'idle' | 'saving' | 'saved' | 'restored';

  const props = defineProps<{
    moduleTree: ModuleTreeNode[];
    activeModule: string;
  }>();
  const emit = defineEmits<{
    (event: 'created', scenarioIds: string[]): void;
  }>();

  const visible = defineModel<boolean>('visible', { default: false });
  const { t } = useI18n();
  const appStore = useAppStore();
  const aiStore = useAIStore();
  const userStore = useUserStore();

  const emptyForm = (): GrpcAiFormState => ({
    chatModelId: '',
    moduleId: 'root',
    sourceText: '',
    serverAddress: '',
    correction: '',
  });
  const form = reactive<GrpcAiFormState>(emptyForm());
  const files = ref<File[]>([]);
  const draft = ref<GrpcAiScenarioDraft>();
  const generateLoading = ref(false);
  const createLoading = ref(false);
  const projectContextLoading = ref(false);
  const pastingScreenshot = ref(false);
  const conversationId = ref(getGenerateId());
  const fileInputRef = ref<HTMLInputElement>();
  const draggingFiles = ref(false);
  const filePreviewMap = ref<Record<string, string>>({});
  const previewVisible = ref(false);
  const previewCurrent = ref(0);
  const saveStatus = ref<SaveStatus>('idle');
  const savedAt = ref(0);
  const suppressAutoSave = ref(false);
  const restoredStorageKey = ref('');
  const projectDirectoryHandle = shallowRef<ProjectDirectoryHandle>();
  const projectContextSummary = ref('');
  let saveTimer: ReturnType<typeof setTimeout> | undefined;

  const priorityOptions = ['P0', 'P1', 'P2', 'P3'].map((value) => ({ label: value, value }));
  const modelOptions = computed(() => aiStore.aiSourceNameList.map((item) => ({ label: item.name, value: item.id })));
  const currentProjectName = computed(
    () => appStore.projectList.find((item) => item.id === appStore.currentProjectId)?.name || appStore.currentProjectId
  );
  const storageScope = computed(
    () => `${userStore.id || 'anonymous'}:${appStore.currentOrgId}:${appStore.currentProjectId}`
  );
  const draftStorageKey = computed(() => `grpc-ai-scenario-draft:${storageScope.value}`);
  const projectDirectoryStorageKey = computed(() => `grpc-ai-project-directory:${storageScope.value}`);
  const projectDirectorySupported = computed(
    () => typeof (window as Window & { showDirectoryPicker?: unknown }).showDirectoryPicker === 'function'
  );
  const selectedScenarioCount = computed(
    () => draft.value?.scenarios.filter((scenario) => scenario.selected && !scenario.blocked).length || 0
  );
  const imageCount = computed(
    () =>
      files.value.filter((file) => ['png', 'jpg', 'jpeg'].includes(file.name.split('.').pop()?.toLowerCase() || ''))
        .length
  );
  const imageFiles = computed(() => files.value.filter(isImageFile));
  const documentFiles = computed(() => files.value.filter((file) => !isImageFile(file)));
  const previewList = computed(() =>
    imageFiles.value.map((file) => filePreviewMap.value[fileKey(file)]).filter((url): url is string => !!url)
  );
  const hasDraftContent = computed(
    () =>
      !!form.sourceText.trim() ||
      !!form.serverAddress.trim() ||
      !!form.correction.trim() ||
      files.value.length > 0 ||
      !!draft.value
  );
  const canGenerate = computed(
    () => !!form.chatModelId && !!form.moduleId && (hasDraftContent.value || !!projectDirectoryHandle.value)
  );
  const canCreate = computed(
    () =>
      !!draft.value &&
      !draft.value.protoRequired &&
      draft.value.scenarios.some(
        (scenario) =>
          scenario.selected &&
          !scenario.blocked &&
          scenario.cases.some((caseItem) => caseItem.selected && !caseItem.blocked)
      )
  );
  const savedTime = computed(() => (savedAt.value ? new Date(savedAt.value).toLocaleTimeString() : ''));

  watch(visible, async (value) => {
    if (!value) {
      window.removeEventListener('paste', handlePaste, true);
      return;
    }
    window.addEventListener('paste', handlePaste, true);
    const restored = await restoreDraft();
    await loadProjectDirectory();
    if (!restored) {
      form.moduleId = props.activeModule && props.activeModule !== 'all' ? props.activeModule : 'root';
    }
    if (!aiStore.aiSourceNameList.length) {
      await aiStore.getAISourceNameList();
    }
    form.chatModelId ||= aiStore.aiSourceNameList[0]?.id || '';
  });

  watch(
    [() => ({ ...form }), draft, files],
    () => {
      scheduleDraftSave();
    },
    { deep: true }
  );

  watch(
    files,
    (value) => {
      Object.values(filePreviewMap.value).forEach(URL.revokeObjectURL);
      filePreviewMap.value = Object.fromEntries(
        value.filter(isImageFile).map((file) => [fileKey(file), URL.createObjectURL(file)])
      );
    },
    { immediate: true }
  );

  function fileKey(file: File) {
    return `${file.name}:${file.size}:${file.lastModified}`;
  }

  function isImageFile(file: File) {
    return ['png', 'jpg', 'jpeg'].includes(file.name.split('.').pop()?.toLowerCase() || '');
  }

  function isClipboardScreenshot(file: File) {
    return file.name.startsWith('clipboard-screenshot-');
  }

  function formatFileSize(size: number) {
    return size < 1024 * 1024 ? `${Math.max(1, Math.round(size / 1024))} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`;
  }

  function handlePreview(file: File) {
    const previewUrl = filePreviewMap.value[fileKey(file)];
    const index = previewList.value.indexOf(previewUrl);
    if (index < 0) return;
    previewCurrent.value = index;
    previewVisible.value = true;
  }

  function addFiles(nextFiles: File[]) {
    const allowed = new Set(['txt', 'md', 'csv', 'xlsx', 'docx', 'png', 'jpg', 'jpeg']);
    if (nextFiles.some((file) => !allowed.has(file.name.split('.').pop()?.toLowerCase() || ''))) {
      Message.error(t('apiScenario.grpcAi.fileFormatError'));
      return 0;
    }
    const uniqueFiles = [...files.value];
    const previousCount = uniqueFiles.length;
    nextFiles.forEach((file) => {
      if (!uniqueFiles.some((item) => fileKey(item) === fileKey(file))) uniqueFiles.push(file);
    });
    if (uniqueFiles.length > 20 || uniqueFiles.some((file) => file.size > 10 * 1024 * 1024)) {
      Message.error(t('apiScenario.grpcAi.fileLimitError'));
      return 0;
    }
    if (uniqueFiles.filter(isImageFile).length > 20) {
      Message.error(t('apiScenario.grpcAi.imageLimitError'));
      return 0;
    }
    files.value = uniqueFiles;
    return uniqueFiles.length - previousCount;
  }

  function createClipboardImageFile(blob: Blob, index = 0) {
    const extension = blob.type === 'image/jpeg' ? 'jpg' : 'png';
    const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, '');
    return new File([blob], `clipboard-screenshot-${timestamp}-${index + 1}.${extension}`, {
      type: blob.type || `image/${extension}`,
      lastModified: Date.now() + index,
    });
  }

  function addClipboardImages(blobs: Blob[]) {
    if (!blobs.length) {
      Message.warning(t('apiScenario.grpcAi.clipboardNoImage'));
      return;
    }
    const added = addFiles(blobs.map(createClipboardImageFile));
    if (added) Message.success(t('apiScenario.grpcAi.screenshotPasted', { count: added }));
  }

  function handlePaste(event: ClipboardEvent) {
    if (!visible.value) return;
    const clipboardImageFiles = Array.from(event.clipboardData?.items || [])
      .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
      .map((item) => item.getAsFile())
      .filter((file): file is File => !!file);
    if (!clipboardImageFiles.length) return;
    event.preventDefault();
    addClipboardImages(clipboardImageFiles);
  }

  async function pasteScreenshot() {
    if (!navigator.clipboard?.read) {
      Message.warning(t('apiScenario.grpcAi.clipboardUnsupported'));
      return;
    }
    try {
      pastingScreenshot.value = true;
      const clipboardItems = await navigator.clipboard.read();
      const imageBlobs = (
        await Promise.all(
          clipboardItems.map((item) => {
            const imageType = item.types.find((type) => type.startsWith('image/'));
            return imageType ? item.getType(imageType) : undefined;
          })
        )
      ).filter((blob): blob is Blob => !!blob);
      addClipboardImages(imageBlobs);
    } catch (error) {
      const errorName = (error as DOMException)?.name;
      Message.warning(
        t(
          errorName === 'NotAllowedError'
            ? 'apiScenario.grpcAi.clipboardPermissionDenied'
            : 'apiScenario.grpcAi.clipboardReadFailed'
        )
      );
    } finally {
      pastingScreenshot.value = false;
    }
  }

  function handleFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    addFiles(Array.from(input.files || []));
    input.value = '';
  }

  function handleFileDrop(event: DragEvent) {
    draggingFiles.value = false;
    addFiles(Array.from(event.dataTransfer?.files || []));
  }

  function removeFile(file: File) {
    files.value = files.value.filter((item) => fileKey(item) !== fileKey(file));
  }

  async function linkProjectDirectory() {
    const picker = (
      window as Window & {
        showDirectoryPicker?: (options?: { mode?: 'read' }) => Promise<ProjectDirectoryHandle>;
      }
    ).showDirectoryPicker;
    if (!picker) {
      Message.warning(t('apiScenario.grpcAi.folderUnsupported'));
      return;
    }
    try {
      const handle = await picker.call(window, { mode: 'read' });
      projectDirectoryHandle.value = handle;
      projectContextSummary.value = '';
      await localforage.setItem(projectDirectoryStorageKey.value, handle);
      Message.success(t('apiScenario.grpcAi.folderLinked', { name: handle.name }));
    } catch (error) {
      if ((error as DOMException)?.name !== 'AbortError') throw error;
    }
  }

  async function loadProjectDirectory() {
    const handle = await localforage.getItem<ProjectDirectoryHandle>(projectDirectoryStorageKey.value);
    projectDirectoryHandle.value = handle || undefined;
  }

  async function unlinkProjectDirectory() {
    projectDirectoryHandle.value = undefined;
    projectContextSummary.value = '';
    await localforage.removeItem(projectDirectoryStorageKey.value);
  }

  async function resolveProjectContext(correction: boolean) {
    const handle = projectDirectoryHandle.value;
    if (!handle) return '';
    const permitted = await ensureProjectDirectoryPermission(handle, true);
    if (!permitted) {
      Message.warning(t('apiScenario.grpcAi.folderPermissionRequired'));
      return '';
    }
    projectContextLoading.value = true;
    try {
      const query = [form.sourceText, correction ? form.correction : '', draft.value?.sourceSummary || '']
        .filter(Boolean)
        .join('\n');
      const result = await buildGrpcAiProjectContext(handle, query);
      projectContextSummary.value = t('apiScenario.grpcAi.contextIndexed', {
        discovered: result.discoveredFileCount,
        selected: result.selectedFiles.length,
      });
      return result.text;
    } finally {
      projectContextLoading.value = false;
    }
  }

  async function generateDraft(correction: boolean) {
    if (!canGenerate.value) return;
    try {
      generateLoading.value = true;
      const projectContext = await resolveProjectContext(correction);
      const response = await generateGrpcAiScenarioDraft(
        {
          prompt: correction ? form.correction : form.sourceText || t('apiScenario.grpcAi.generate'),
          chatModelId: form.chatModelId,
          conversationId: conversationId.value,
          organizationId: appStore.currentOrgId,
          projectId: appStore.currentProjectId,
          projectName: currentProjectName.value,
          moduleId: form.moduleId,
          sourceText: form.sourceText,
          projectContext,
          correction: correction ? form.correction : '',
          serverAddress: form.serverAddress,
          currentDraft: correction ? draft.value : undefined,
        },
        files.value
      );
      draft.value = response.data;
      form.correction = '';
      await persistDraft();
    } finally {
      generateLoading.value = false;
    }
  }

  function validateCase(caseItem: GrpcAiCaseDraft) {
    if (!caseItem.apiDefinitionId) return;
    if (!caseItem.serverAddress?.trim()) {
      caseItem.blocked = true;
      caseItem.ambiguity = t('apiScenario.grpcAi.addressRequired');
      return;
    }
    try {
      const value = JSON.parse(caseItem.requestJson);
      if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('not object');
      caseItem.blocked = false;
      caseItem.ambiguity = '';
    } catch (error) {
      caseItem.blocked = true;
      caseItem.ambiguity = t('apiScenario.grpcAi.jsonInvalid');
    }
  }

  function normalizeStoredFile(value: File | Blob) {
    if (value instanceof File) return value;
    if (value instanceof Blob) {
      const stored = value as Blob & { name?: string; lastModified?: number };
      return new File([stored], stored.name || `attachment-${getGenerateId()}`, {
        type: stored.type,
        lastModified: stored.lastModified || Date.now(),
      });
    }
    return undefined;
  }

  async function restoreDraft() {
    if (restoredStorageKey.value === draftStorageKey.value) return hasDraftContent.value;
    restoredStorageKey.value = draftStorageKey.value;
    const stored = await localforage.getItem<PersistedGrpcAiState>(draftStorageKey.value);
    if (!stored || stored.version !== 1) return false;
    suppressAutoSave.value = true;
    Object.assign(form, stored.form);
    files.value = (stored.files || []).map(normalizeStoredFile).filter((file): file is File => !!file);
    draft.value = stored.draft ? cloneDeep(stored.draft) : undefined;
    conversationId.value = stored.conversationId || getGenerateId();
    savedAt.value = stored.savedAt;
    saveStatus.value = 'restored';
    await nextTick();
    suppressAutoSave.value = false;
    return true;
  }

  function scheduleDraftSave() {
    if (suppressAutoSave.value) return;
    if (saveTimer) clearTimeout(saveTimer);
    saveStatus.value = 'saving';
    saveTimer = setTimeout(() => {
      persistDraft().catch(() => undefined);
    }, 500);
  }

  async function persistDraft() {
    if (suppressAutoSave.value) return;
    if (!hasDraftContent.value) {
      await localforage.removeItem(draftStorageKey.value);
      saveStatus.value = 'idle';
      return;
    }
    const timestamp = Date.now();
    await localforage.setItem<PersistedGrpcAiState>(draftStorageKey.value, {
      version: 1,
      savedAt: timestamp,
      form: { ...toRaw(form) },
      files: [...files.value],
      draft: draft.value ? cloneDeep(toRaw(draft.value)) : undefined,
      conversationId: conversationId.value,
    });
    savedAt.value = timestamp;
    saveStatus.value = 'saved';
  }

  async function clearPersistedDraft() {
    if (saveTimer) clearTimeout(saveTimer);
    suppressAutoSave.value = true;
    const currentModel = form.chatModelId;
    Object.assign(form, emptyForm(), {
      chatModelId: currentModel,
      moduleId: props.activeModule && props.activeModule !== 'all' ? props.activeModule : 'root',
    });
    files.value = [];
    draft.value = undefined;
    conversationId.value = getGenerateId();
    savedAt.value = 0;
    saveStatus.value = 'idle';
    await localforage.removeItem(draftStorageKey.value);
    await nextTick();
    suppressAutoSave.value = false;
  }

  function discardDraft() {
    Modal.confirm({
      title: t('apiScenario.grpcAi.discardTitle'),
      content: t('apiScenario.grpcAi.discardContent'),
      okText: t('apiScenario.grpcAi.discard'),
      cancelText: t('common.cancel'),
      onOk: clearPersistedDraft,
    });
  }

  function closeDrawer() {
    if (saveTimer) clearTimeout(saveTimer);
    persistDraft().catch(() => undefined);
    visible.value = false;
  }

  async function confirmCreate() {
    if (!draft.value || !canCreate.value) return;
    draft.value.scenarios.forEach((scenario) => scenario.cases.forEach(validateCase));
    if (!canCreate.value) return;
    try {
      createLoading.value = true;
      const result = await createGrpcAiScenarios({
        projectId: appStore.currentProjectId,
        moduleId: form.moduleId,
        draft: draft.value,
      });
      Message.success(
        t('apiScenario.grpcAi.createResult', {
          scenario: result.createdScenarioCount + result.updatedScenarioCount,
          caseCount: result.createdCaseCount + result.updatedCaseCount,
          skipped: result.skippedCount,
        })
      );
      emit('created', result.scenarioIds);
      await clearPersistedDraft();
      visible.value = false;
    } finally {
      createLoading.value = false;
    }
  }

  onBeforeUnmount(() => {
    if (saveTimer) clearTimeout(saveTimer);
    window.removeEventListener('paste', handlePaste, true);
    persistDraft().catch(() => undefined);
    Object.values(filePreviewMap.value).forEach(URL.revokeObjectURL);
  });
</script>

<style scoped lang="less">
  .draft-state-bar,
  .project-context-panel,
  .attachment-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .draft-state-bar {
    margin-bottom: 14px;
    padding: 4px 8px 4px 12px;
    min-height: 36px;
    border: 1px solid var(--color-neutral-3);
    border-radius: 6px;
    color: var(--color-text-2);
    background: var(--color-fill-1);
  }
  .project-context-panel {
    margin-bottom: 16px;
    padding: 8px 12px;
    min-height: 48px;
    border: 1px solid rgb(var(--primary-2));
    border-radius: 6px;
    background: rgb(var(--primary-1));
  }
  .attachment-panel {
    padding: 12px;
    border: 1px dashed var(--color-neutral-4);
    border-radius: 6px;
    background: var(--color-fill-1);
    transition: border-color 0.2s, background-color 0.2s;
  }
  .attachment-panel--dragging {
    border-color: rgb(var(--primary-5));
    background: rgb(var(--primary-1));
  }
  .attachment-content {
    margin-top: 12px;
  }
  .attachment-image-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 10px;
  }
  .attachment-image-card {
    overflow: hidden;
    min-width: 0;
    border: 1px solid var(--color-neutral-3);
    border-radius: 6px;
    background: var(--color-bg-2);
    transition: border-color 0.2s, box-shadow 0.2s;
    cursor: zoom-in;
  }
  .attachment-image-card:hover,
  .attachment-image-card:focus-visible {
    border-color: rgb(var(--primary-5));
    outline: none;
    box-shadow: 0 0 0 2px rgb(var(--primary-1));
  }
  .attachment-image-preview {
    position: relative;
    overflow: hidden;
    width: 100%;
    background: var(--color-fill-2);
    aspect-ratio: 16 / 10;
  }
  .attachment-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .attachment-image-mask {
    position: absolute;
    display: flex;
    justify-content: center;
    align-items: center;
    color: #ffffff;
    background: rgb(0 0 0 / 38%);
    opacity: 0;
    transition: opacity 0.2s;
    inset: 0;
  }
  .attachment-image-card:hover .attachment-image-mask,
  .attachment-image-card:focus-visible .attachment-image-mask {
    opacity: 1;
  }
  .attachment-image-delete {
    position: absolute;
    top: 6px;
    right: 6px;
    z-index: 1;
  }
  .attachment-image-info {
    padding: 8px 10px 10px;
  }
  .attachment-document-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin-top: 10px;
  }
  .attachment-document-card {
    display: flex;
    align-items: center;
    padding: 8px;
    min-width: 0;
    min-height: 64px;
    border: 1px solid var(--color-neutral-3);
    border-radius: 6px;
    background: var(--color-bg-2);
    gap: 10px;
  }
  .attachment-document {
    flex: none;
    width: 48px;
    height: 48px;
    border-radius: 4px;
  }
  .attachment-document {
    display: flex;
    justify-content: center;
    align-items: center;
    color: rgb(var(--primary-5));
    background: var(--color-fill-2);
  }
  .attachment-empty {
    padding: 18px 8px 6px;
    text-align: center;
    color: var(--color-text-3);
  }
  .attachment-empty-title {
    display: flex;
    justify-content: center;
    align-items: center;
    font-weight: 500;
    color: rgb(var(--primary-6));
    gap: 6px;
  }
  .draft-summary {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    padding: 8px 0 12px;
    color: var(--color-text-2);
  }
  .scenario-section {
    padding: 12px 0;
    border-top: 1px solid var(--color-neutral-3);
  }
  .scenario-section--disabled,
  .case-row--blocked {
    opacity: 0.68;
  }
  .scenario-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
  .case-table-head,
  .case-row {
    display: grid;
    grid-template-columns: 28px minmax(150px, 0.9fr) minmax(170px, 1fr) minmax(150px, 0.8fr) minmax(260px, 1.4fr);
    gap: 8px;
    align-items: start;
  }
  .case-table-head {
    padding: 6px 0;
    font-size: 12px;
    color: var(--color-text-3);
  }
  .case-row {
    padding: 8px 0;
    border-top: 1px solid var(--color-neutral-2);
  }
  .case-ambiguity {
    grid-column: 2 / -1;
  }
  .request-json {
    font-size: 12px;
    font-family: Consolas, monospace;
  }
</style>
