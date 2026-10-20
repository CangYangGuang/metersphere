<template>
  <!-- TODO:接口请求超过5S以上可展示取消请求按钮，避免用户过长等待 -->
  <MsCard :loading="loading" show-full-screen simple no-content-padding>
    <MsSplitBox :size="300" :max="0.5">
      <template #first>
        <moduleTree
          ref="moduleTreeRef"
          :active-node-id="activeDebug.id"
          @init="(val) => (folderTree = val)"
          @new-api="addDebugTab"
          @new-module-request="addModuleRequestTab"
          @click-api-node="openApiTab"
          @update-api-node="handleApiUpdateFromModuleTree"
          @delete-finish="handleDeleteFinish"
        />
      </template>
      <template #second>
        <div class="flex h-full flex-col">
          <div class="border-b border-[var(--color-text-n8)] p-[12px_18px]">
            <MsEditableTab
              v-model:active-tab="activeDebug"
              v-model:tabs="debugTabs"
              :readonly="!hasAnyPermission(['PROJECT_API_DEBUG:READ+ADD'])"
              at-least-one
              @add="addDebugTab"
              @close="handleDebugTabClose"
            >
              <template #label="{ tab }">
                <apiMethodName :method="tab.protocol === 'HTTP' ? tab.method : tab.protocol" class="mr-[4px]" />
                <a-tooltip :content="tab.name || tab.label" :mouse-enter-delay="500">
                  <div class="one-line-text max-w-[144px]">
                    {{ tab.name || tab.label }}
                  </div>
                </a-tooltip>
              </template>
            </MsEditableTab>
          </div>
          <div class="flex-1 overflow-hidden">
            <requestComposition
              v-model:detail-loading="loading"
              v-model:request="activeDebug"
              :module-tree="folderTree"
              :protocol-key="ProtocolKeyEnum.API_DEBUG_NEW_PROTOCOL"
              :create-api="addDebug"
              :update-api="updateDebug"
              :execute-api="executeDebug"
              :local-execute-api="localExecuteApiDebug"
              :upload-temp-file-api="uploadTempFile"
              :file-save-as-source-id="activeDebug.id"
              :file-save-as-api="transferFile"
              :file-module-options-api="getTransferOptions"
              hide-json-schema
              :permission-map="{
                execute: 'PROJECT_API_DEBUG:READ+EXECUTE',
                update: 'PROJECT_API_DEBUG:READ+UPDATE',
                create: 'PROJECT_API_DEBUG:READ+ADD',
                saveASApi: 'PROJECT_API_DEFINITION:READ+ADD',
              }"
              @import="importDialogVisible = true"
              @add-done="handleDebugAddDone"
              @definition-saved="openSavedDefinition"
            />
          </div>
        </div>
      </template>
    </MsSplitBox>
  </MsCard>
  <importCurlDialog v-model:visible="importDialogVisible" @done="handleImportCurlDone" />
</template>

<script lang="ts" setup>
  /**
   * @description 接口测试-接口调试
   */
  import { useRoute, useRouter } from 'vue-router';
  import { cloneDeep } from 'lodash-es';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsEditableTab from '@/components/pure/ms-editable-tab/index.vue';
  import { TabItem } from '@/components/pure/ms-editable-tab/types';
  import MsSplitBox from '@/components/pure/ms-split-box/index.vue';
  import moduleTree from './components/moduleTree.vue';
  import apiMethodName from '@/views/api-test/components/apiMethodName.vue';
  import importCurlDialog from '@/views/api-test/components/importCurlDialog.vue';
  import requestComposition, { RequestParam } from '@/views/api-test/components/requestComposition/index.vue';

  import { localExecuteApiDebug } from '@/api/modules/api-test/common';
  import {
    addDebug,
    executeDebug,
    getDebugDetail,
    getTransferOptions,
    transferFile,
    updateDebug,
    uploadTempFile,
  } from '@/api/modules/api-test/debug';
  import { useI18n } from '@/hooks/useI18n';
  import useLeaveTabUnSaveCheck from '@/hooks/useLeaveTabUnSaveCheck';
  import useRequestCompositionStore from '@/store/modules/api/requestComposition';
  import useAppStore from '@/store/modules/app';
  import { hasAnyPermission } from '@/utils/permission';

  import { CurlParseResult } from '@/models/apiTest/common';
  import type { GrpcCaseDebugRequest, GrpcRequestState } from '@/models/apiTest/grpc';
  import { ModuleTreeNode } from '@/models/common';
  import {
    ProtocolKeyEnum,
    RequestAuthType,
    RequestComposition,
    RequestContentTypeEnum,
    RequestMethods,
    RequestParamsType,
    ResponseComposition,
  } from '@/enums/apiEnum';
  import { ApiTestRouteEnum } from '@/enums/routeEnum';

  import { defaultBodyParams, defaultResponse } from '../components/config';
  import { createDefaultGrpcState, mapGrpcDebugRequestToState } from '../components/requestComposition/grpc/constants';
  import { parseCurlBody, parseRequestBodyFiles } from '../components/utils';

  defineOptions({
    name: ApiTestRouteEnum.API_TEST_DEBUG_MANAGEMENT,
  });

  const route = useRoute();
  const router = useRouter();
  const { t } = useI18n();
  const appStore = useAppStore();
  const requestCompositionStore = useRequestCompositionStore();

  const moduleTreeRef = ref<InstanceType<typeof moduleTree>>();
  const folderTree = ref<ModuleTreeNode[]>([]);
  const importDialogVisible = ref(false);
  const loading = ref(false);

  async function handleDebugAddDone() {
    await moduleTreeRef.value?.initModules();
    moduleTreeRef.value?.initModuleCount();
  }

  function openSavedDefinition(id?: string) {
    router.push({
      name: ApiTestRouteEnum.API_TEST_MANAGEMENT,
      query: id ? { id } : undefined,
    });
  }

  const initDefaultId = `debug-${Date.now()}`;
  const localProtocol = localStorage.getItem(ProtocolKeyEnum.API_DEBUG_NEW_PROTOCOL);
  const defaultDebugParams: RequestParam = {
    type: 'api',
    id: initDefaultId,
    moduleId: 'root',
    protocol: localProtocol || 'HTTP',
    url: '',
    activeTab: RequestComposition.HEADER,
    label: t('apiTestDebug.newApi'),
    closable: true,
    method: RequestMethods.GET,
    unSaved: false,
    headers: [],
    body: {
      ...cloneDeep(defaultBodyParams),
      jsonBody: {
        jsonValue: '',
        enableJsonSchema: false,
        jsonSchemaTableData: [],
        jsonSchemaTableSelectedRowKeys: [],
      },
    },
    query: [],
    rest: [],
    polymorphicName: '',
    name: '',
    path: '',
    projectId: '',
    uploadFileIds: [],
    linkFileIds: [],
    authConfig: {
      authType: RequestAuthType.NONE,
      basicAuth: {
        userName: '',
        password: '',
      },
      digestAuth: {
        userName: '',
        password: '',
      },
    },
    children: [
      {
        polymorphicName: 'MsCommonElement', // 协议多态名称，写死MsCommonElement
        assertionConfig: {
          enableGlobal: false,
          assertions: [],
        },
        postProcessorConfig: {
          enableGlobal: false,
          processors: [],
        },
        preProcessorConfig: {
          enableGlobal: false,
          processors: [],
        },
      },
    ],
    otherConfig: {
      connectTimeout: 60000,
      responseTimeout: 60000,
      certificateAlias: '',
      followRedirects: true,
      autoRedirects: false,
    },
    responseActiveTab: ResponseComposition.BODY,
    response: cloneDeep(defaultResponse),
    isNew: true,
    executeLoading: false,
    errorMessageInfo: {},
    grpc: createDefaultGrpcState(),
  };
  const debugTabs = ref<RequestParam[]>([cloneDeep(defaultDebugParams)]);
  const activeDebug = ref<RequestParam>(debugTabs.value[0]);
  const workspaceProjectId = ref(appStore.currentProjectId);

  watch(
    () => appStore.currentProjectId,
    async (projectId) => {
      if (!projectId || projectId === workspaceProjectId.value) {
        return;
      }
      workspaceProjectId.value = projectId;
      debugTabs.value.forEach((item) => requestCompositionStore.removePluginFormMapItem(item.id));
      const nextDefaultDebug = {
        ...cloneDeep(defaultDebugParams),
        id: `debug-${Date.now()}`,
      };
      debugTabs.value = [nextDefaultDebug];
      activeDebug.value = nextDefaultDebug;
      folderTree.value = [];
      await nextTick();
      await handleDebugAddDone();
    }
  );

  function handleActiveDebugChange() {
    if (!loading.value) {
      // 如果是因为加载详情触发的change则不需要标记为未保存
      activeDebug.value.unSaved = true;
    }
  }

  function addDebugTab(defaultProps?: Partial<TabItem>) {
    const id = `debug-${Date.now()}`;
    const protocol = localStorage.getItem(ProtocolKeyEnum.API_DEBUG_NEW_PROTOCOL);
    const nextProtocol =
      defaultProps?.protocol ||
      (activeDebug.value.protocol === 'GRPC'
        ? 'GRPC'
        : protocol || activeDebug.value.protocol || defaultDebugParams.protocol);
    let inheritedGrpc: GrpcRequestState | undefined;
    if (
      !defaultProps?.id &&
      nextProtocol === 'GRPC' &&
      activeDebug.value.protocol === 'GRPC' &&
      activeDebug.value.grpc
    ) {
      const sourceGrpc = activeDebug.value.grpc;
      const nextGrpc = createDefaultGrpcState();
      Object.assign(nextGrpc, {
        // 复用当前请求已解析的 Proto bundle，避免每个新请求重复上传文件。
        protoContent: sourceGrpc.protoContent,
        fileName: sourceGrpc.fileName,
        protoFiles: cloneDeep(sourceGrpc.protoFiles),
        mainFileName: sourceGrpc.mainFileName,
        packageName: sourceGrpc.packageName,
        descriptorContent: sourceGrpc.descriptorContent,
        services: cloneDeep(sourceGrpc.services),
        uploadedMainProtos: cloneDeep(sourceGrpc.uploadedMainProtos),
        reflectionDiscovered: sourceGrpc.reflectionDiscovered,
        reflectionServerAddress: sourceGrpc.reflectionServerAddress,
        reflectionUseTls: sourceGrpc.reflectionUseTls,
        reflectionDiscoveredAt: sourceGrpc.reflectionDiscoveredAt,
        serverAddress: sourceGrpc.serverAddress,
        timeout: sourceGrpc.timeout,
        useTls: sourceGrpc.useTls,
        verifyServerCertificate: sourceGrpc.verifyServerCertificate,
        serverNameOverride: sourceGrpc.serverNameOverride,
        authority: sourceGrpc.authority,
        includeDefaultValues: sourceGrpc.includeDefaultValues,
        maxResponseSizeMb: sourceGrpc.maxResponseSizeMb,
        connectTimeout: sourceGrpc.connectTimeout,
      });
      inheritedGrpc = nextGrpc;
    }
    debugTabs.value.push({
      ...cloneDeep(defaultDebugParams),
      id,
      isNew: !defaultProps?.id, // 新开的tab标记为前端新增的调试，因为此时都已经有id了；但是如果是查看打开的会有携带id
      protocol: nextProtocol, // 新开的tab默认使用当前激活的tab的协议
      ...(inheritedGrpc
        ? {
            activeTab: RequestComposition.GRPC_MESSAGE,
            grpc: inheritedGrpc,
          }
        : {}),
      ...defaultProps,
    });
    activeDebug.value = debugTabs.value[debugTabs.value.length - 1];
  }

  function addModuleRequestTab(moduleId: string | number) {
    addDebugTab({
      moduleId: String(moduleId),
      label: t('apiTestDebug.newApi'),
    });
  }

  async function openApiTab(apiInfo: ModuleTreeNode | string) {
    const id = typeof apiInfo === 'string' ? apiInfo : apiInfo.id;
    const isLoadedTabIndex = debugTabs.value.findIndex((e) => e.id === id);
    if (isLoadedTabIndex > -1) {
      // 如果点击的请求在tab中已经存在，则直接切换到该tab
      activeDebug.value = debugTabs.value[isLoadedTabIndex];
      return;
    }
    try {
      loading.value = true;
      const res = await getDebugDetail(id);
      let parseRequestBodyResult;
      if (res.protocol === 'HTTP') {
        parseRequestBodyResult = parseRequestBodyFiles(res.request.body); // 解析请求体中的文件，将详情中的文件 id 集合收集，更新时以判断文件是否删除以及是否新上传的文件
        addDebugTab({
          ...res,
          response: cloneDeep(defaultResponse),
          ...res.request,
          url: res.path,
          label: res.name,
          name: res.name, // request里面还有个name但是是null
          moduleId: res.moduleId, // request里面还有个moduleId但是是null
          ...parseRequestBodyResult,
        });
      } else if (res.protocol === 'GRPC') {
        addDebugTab({
          ...res,
          response: cloneDeep(defaultResponse),
          url: res.path,
          label: res.name,
          name: res.name,
          moduleId: res.moduleId,
          grpc: mapGrpcDebugRequestToState(res.request as unknown as GrpcCaseDebugRequest),
        });
      } else {
        addDebugTab({
          ...res,
          response: cloneDeep(defaultResponse),
          ...res.request,
          url: res.path,
          label: res.name,
          name: res.name,
          moduleId: res.moduleId,
        });
      }
      nextTick(() => {
        // 等待内容渲染出来再隐藏loading
        loading.value = false;
      });
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
      loading.value = false;
    }
  }

  function handleImportCurlDone(res: CurlParseResult) {
    const { url, method, headers, queryParams, bodyType, body } = res;
    const requestBody = parseCurlBody(bodyType, body);
    addDebugTab({
      url,
      method: method?.toUpperCase() || RequestMethods.GET,
      headers:
        Object.keys(headers)?.map((e) => ({
          contentType: RequestContentTypeEnum.TEXT,
          description: '',
          enable: true,
          key: e,
          value: headers[e],
        })) || [],
      query:
        Object.keys(queryParams)?.map((e) => ({
          paramType: RequestParamsType.STRING,
          description: '',
          required: false,
          maxLength: undefined,
          minLength: undefined,
          encode: false,
          enable: true,
          key: e,
          value: queryParams[e],
        })) || [],
      body: requestBody,
    });
    importDialogVisible.value = false;
    nextTick(() => {
      handleActiveDebugChange();
    });
  }

  /**
   * 同步模块树的接口信息更新操作
   */
  function handleApiUpdateFromModuleTree(newInfo: { id: string; name: string; moduleId?: string; [key: string]: any }) {
    debugTabs.value = debugTabs.value.map((item) => {
      if (item.id === newInfo.id) {
        item.label = newInfo.name;
        item.name = newInfo.name;
        if (newInfo.moduleId) {
          item.moduleId = newInfo.moduleId;
        }
      }
      return item;
    });
    if (activeDebug.value.id === newInfo.id) {
      activeDebug.value.label = newInfo.name;
      activeDebug.value.name = newInfo.name;
      if (newInfo.moduleId) {
        activeDebug.value.moduleId = newInfo.moduleId;
      }
    }
  }

  /**
   * 同步模块树的接口信息删除操作
   * @param id 接口 id
   * @param isModule 是否是删除模块
   */
  function handleDeleteFinish(node: ModuleTreeNode) {
    if (node.type === 'MODULE') {
      // 删除整个模块
      debugTabs.value = debugTabs.value.filter((item) => {
        if (activeDebug.value.id === item.id) {
          // 删除的是当前激活的 tab, 切换到第一个 tab
          [activeDebug.value] = debugTabs.value;
        }
        return item.moduleId !== node.id || item.id === 'all';
      });
    } else {
      // 删除单个 api
      debugTabs.value = debugTabs.value.filter((item) => item.id !== node.id);
      if (activeDebug.value.id === node.id) {
        [activeDebug.value] = debugTabs.value;
      }
    }
    if (debugTabs.value.length === 0) {
      addDebugTab();
    }
  }

  function handleDebugTabClose(item: TabItem) {
    requestCompositionStore.removePluginFormMapItem(item.id);
    const closingIndex = debugTabs.value.findIndex((e) => e.id === item.id);
    if (closingIndex > -1) {
      debugTabs.value.splice(closingIndex, 1);
    }
  }

  onMounted(() => {
    if (route.query.id) {
      openApiTab(route.query.id as string);
    }
  });

  useLeaveTabUnSaveCheck(
    debugTabs.value,
    ['PROJECT_API_DEBUG:READ+ADD', 'PROJECT_API_DEBUG:READ+UPDATE'],
    [
      ApiTestRouteEnum.API_TEST_DEBUG_MANAGEMENT,
      ApiTestRouteEnum.API_TEST_MANAGEMENT,
      ApiTestRouteEnum.API_TEST_MANAGEMENT_RECYCLE,
      ApiTestRouteEnum.API_TEST_SCENARIO,
      ApiTestRouteEnum.API_TEST_SCENARIO_RECYCLE,
      ApiTestRouteEnum.API_TEST_REPORT,
    ]
  );
</script>

<style lang="less" scoped></style>
