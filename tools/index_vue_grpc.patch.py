"""Patch requestComposition/index.vue for gRPC UI Phase A+B."""
from __future__ import annotations
import re
from pathlib import Path

def apply(ROOT: Path) -> None:
    p = ROOT / "frontend/src/views/api-test/components/requestComposition/index.vue"
    text = p.read_text(encoding="utf-8")
    if "grpcMessageTab" in text and "grpcDebugContentTabs" in text:
        print("index.vue already patched")
        return

    old_input = '            <a-input\n              v-model:model-value="grpcState.serverAddress"\n              :max-length="255"\n              :placeholder="t(\'apiTestGrpc.serverAddressPlaceholder\')"\n              allow-clear\n              class="flex-1 hover:z-10"\n              @change="handleActiveDebugChange"\n            />\n          </a-input-group>'
    new_input = '            <a-input\n              v-model:model-value="grpcState.serverAddress"\n              :max-length="255"\n              :placeholder="t(\'apiTestGrpc.serverAddressPlaceholder\')"\n              allow-clear\n              class="flex-1 hover:z-10"\n              @change="handleActiveDebugChange"\n            />\n            <grpcMethodSelect :grpc="grpcState" class="min-w-[220px] hover:z-10" @select="handleGrpcMethodSelect" />\n          </a-input-group>'
    if old_input not in text: raise SystemExit("grpc input anchor missing")
    text = text.replace(old_input, new_input, 1)
    text = text.replace('<div :class="`request-content-and-response ${activeLayout}`">', '<div :class="`request-content-and-response ${isGrpcProtocol ? \'vertical\' : activeLayout}`">', 1)
    old_panel = '            <grpcPanel\n              v-if="isGrpcProtocol"\n              v-show="requestVModel.activeTab === RequestComposition.GRPC"\n              v-model:grpc="grpcState"\n              :grpc-definition="requestVModel.grpcDefinition"\n              :grpc-case-config="requestVModel.grpcCaseConfig"\n              :is-case="props.isCase"\n              @change="handleActiveDebugChange"\n            />'
    new_panel = '            <grpcMessageTab\n              v-if="isGrpcProtocol"\n              v-show="isActiveGrpcTab(RequestComposition.GRPC_MESSAGE)"\n              v-model:grpc="grpcState"\n              :is-case="props.isCase"\n              @change="handleActiveDebugChange"\n            />\n            <grpcMetadataTab\n              v-if="isGrpcProtocol"\n              v-show="isActiveGrpcTab(RequestComposition.GRPC_METADATA)"\n              v-model:grpc="grpcState"\n              @change="handleActiveDebugChange"\n            />\n            <grpcServiceDefinitionTab\n              v-if="isGrpcProtocol && !props.isCase"\n              v-show="isActiveGrpcTab(RequestComposition.GRPC_SERVICE_DEFINITION)"\n              v-model:grpc="grpcState"\n              :grpc-definition="requestVModel.grpcDefinition"\n              @change="handleActiveDebugChange"\n            />\n            <grpcSettingsTab\n              v-if="isGrpcProtocol"\n              v-show="isActiveGrpcTab(RequestComposition.GRPC_SETTINGS)"\n              v-model:grpc="grpcState"\n              @change="handleActiveDebugChange"\n            />'
    if old_panel not in text: raise SystemExit("grpcPanel anchor missing")
    text = text.replace(old_panel, new_panel, 1)
    text = text.replace("  import grpcPanel from '@/views/api-test/components/requestComposition/grpc/grpcPanel.vue';\n", "  import grpcMessageTab from '@/views/api-test/components/requestComposition/grpc/grpcMessageTab.vue';\n  import grpcMetadataTab from '@/views/api-test/components/requestComposition/grpc/grpcMetadataTab.vue';\n  import grpcMethodSelect from '@/views/api-test/components/requestComposition/grpc/grpcMethodSelect.vue';\n  import grpcServiceDefinitionTab from '@/views/api-test/components/requestComposition/grpc/grpcServiceDefinitionTab.vue';\n  import grpcSettingsTab from '@/views/api-test/components/requestComposition/grpc/grpcSettingsTab.vue';\n", 1)
    text = text.replace('    buildGrpcCaseConfigFromState,\n', '    applyGrpcMethodSelection,\n    buildGrpcCaseConfigFromState,\n', 1)
    text = text.replace('    formatGrpcDebugSummary,\n    validateRequestJson,', '    formatGrpcDebugSummary,\n    normalizeGrpcActiveTab,\n    validateRequestJson,', 1)
    old_tabs = "  const grpcContentTab = [\n    {\n      value: RequestComposition.GRPC,\n      label: 'gRPC',\n    },\n  ];\n  const grpcCaseContentTab = [\n    {\n      value: RequestComposition.GRPC,\n      label: 'gRPC',\n    },"
    new_tabs = "  const grpcDebugContentTabs = [\n    { value: RequestComposition.GRPC_MESSAGE, label: 'Message' },\n    { value: RequestComposition.GRPC_METADATA, label: 'Metadata' },\n    { value: RequestComposition.GRPC_SERVICE_DEFINITION, label: t('apiTestGrpc.serviceDefinitionTab') },\n    { value: RequestComposition.GRPC_SETTINGS, label: t('apiTestDebug.setting') },\n  ];\n  const grpcCaseContentTab = [\n    { value: RequestComposition.GRPC_MESSAGE, label: 'Message' },\n    { value: RequestComposition.GRPC_METADATA, label: 'Metadata' },\n    { value: RequestComposition.GRPC_SETTINGS, label: t('apiTestDebug.setting') },"
    if old_tabs not in text: raise SystemExit("tabs anchor missing")
    text = text.replace(old_tabs, new_tabs, 1)
    text = text.replace('grpcContentTab', 'grpcDebugContentTabs')
    if 'case RequestComposition.GRPC_METADATA' not in text:
        text = text.replace('      case RequestComposition.AUTH:', "      case RequestComposition.GRPC_METADATA: {\n        const grpcMeta = ensureGrpcState();\n        const metaNum = filterKeyValParams(grpcMeta.metadata || [], defaultHeaderParamsItem).validParams.length;\n        return metaNum > 0 ? `${metaNum}` : '';\n      }\n      case RequestComposition.AUTH:", 1)
    if 'function isActiveGrpcTab' not in text:
        text = text.replace('  function getTabBadge(tabKey: RequestComposition) {', '  function isActiveGrpcTab(tab: RequestComposition) {\n    return requestVModel.value.activeTab === tab;\n  }\n\n  function handleGrpcMethodSelect(payload: { serviceName: string; methodName: string }) {\n    const grpc = ensureGrpcState();\n    applyGrpcMethodSelection(grpc, payload.serviceName, payload.methodName);\n    handleActiveDebugChange();\n  }\n\n  function getTabBadge(tabKey: RequestComposition) {', 1)
    text = re.sub(r'RequestComposition\.GRPC(?!_)', 'RequestComposition.GRPC_MESSAGE', text)
    if 'normalizeGrpcActiveTab(requestVModel.value.activeTab)' not in text:
        text = text.replace('  watch(\n    () => requestVModel.value.id,', "  watch(\n    () => [requestVModel.value.protocol, requestVModel.value.activeTab],\n    () => {\n      if (requestVModel.value.protocol === 'GRPC') {\n        const normalized = normalizeGrpcActiveTab(requestVModel.value.activeTab);\n        if (normalized !== requestVModel.value.activeTab) {\n          requestVModel.value.activeTab = normalized;\n        }\n      }\n    },\n    { immediate: true }\n  );\n\n  watch(\n    () => requestVModel.value.id,", 1)
    p.write_text(text, encoding="utf-8", newline=chr(10))
    print("patched index.vue")
