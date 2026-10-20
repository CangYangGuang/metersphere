from __future__ import annotations
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
COMP = ROOT / "tools/grpc_ui_components"
PANEL_PATH = ROOT / "frontend/src/views/api-test/components/requestComposition/grpc/grpcPanel.vue"
PANEL = PANEL_PATH.read_text(encoding="utf-8")

def write(name: str, content: str) -> None:
    (COMP / name).write_text(content.replace("

", "
"), encoding="utf-8", newline="
")
    print("wrote", name)

def extract_script() -> str:
    return re.search(r"<script setup lang="ts">(.*)</script>", PANEL, re.S).group(1).strip()

def build_metadata() -> str:
    body = """<template>
  <div class="grpc-metadata-tab flex flex-col gap-[8px]">
    <div class="font-medium">Metadata</div>
    <div class="text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.metadataTip') }}</div>
    <httpHeader v-model:params="grpcState.metadata" layout="vertical" @change="emitChange" />
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';
  import httpHeader from '../header.vue';
  import { useI18n } from '@/hooks/useI18n';
  import type { GrpcRequestState } from '@/models/apiTest/grpc';
  const props = defineProps<{ grpc: GrpcRequestState }>();
  const emit = defineEmits<{ (e: 'update:grpc', value: GrpcRequestState): void; (e: 'change'): void }>();
  useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  function emitChange() { emit('change'); }
</script>
"""
    return body

def build_settings() -> str:
    return """<template>
  <div class="grpc-settings-tab flex flex-col gap-[16px]">
    <div class="grid grid-cols-1 gap-[16px] md:grid-cols-2">
      <a-form-item :label="t('apiTestGrpc.timeout')" class="mb-0">
        <a-input-number v-model:model-value="grpcState.timeout" :min="1000" :step="1000" class="w-full" @change="handleTimeoutChange" />
        <div class="mt-[4px] text-[12px] text-[var(--color-text-3)]">{{ t('apiTestGrpc.timeoutTip', { value: DEFAULT_GRPC_TIMEOUT }) }}</div>
      </a-form-item>
      <a-form-item :label="t('apiTestGrpc.requestTls')" class="mb-0">
        <a-switch v-model:model-value="useTls" size="small" @change="emitChange">
          <template #checked>{{ t('apiTestGrpc.reflectionUseTls') }}</template>
          <template #unchecked>{{ t('apiTestGrpc.reflectionPlaintext') }}</template>
        </a-switch>
      </a-form-item>
    </div>
    <a-form-item :label="t('apiTestGrpc.authority')" class="mb-0">
      <a-input v-model:model-value="grpcState.authority" :max-length="255" :placeholder="t('apiTestGrpc.authorityPlaceholder')" allow-clear @change="emitChange" />
    </a-form-item>
  </div>
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';
  import { useI18n } from '@/hooks/useI18n';
  import type { GrpcRequestState } from '@/models/apiTest/grpc';
  import { DEFAULT_GRPC_TIMEOUT } from './constants';
  const props = defineProps<{ grpc: GrpcRequestState }>();
  const emit = defineEmits<{ (e: 'update:grpc', value: GrpcRequestState): void; (e: 'change'): void }>();
  useI18n();
  const grpcState = useVModel(props, 'grpc', emit);
  const useTls = computed({ get: () => grpcState.value.useTls === true, set: (v: boolean) => { grpcState.value.useTls = v; } });
  function emitChange() { emit('change'); }
  function handleTimeoutChange(value: number | undefined) { if (!value) grpcState.value.timeout = DEFAULT_GRPC_TIMEOUT; emitChange(); }
</script>
"""

def build_message() -> str:
    return PANEL.replace("grpc-panel", "grpc-message-tab", 1).split("<div class="flex flex-col gap-[8px]">
      <div class="font-medium">Metadata</div>")[0] + "</div>
</template>"

def build_service_definition() -> str:
    return (Path(__file__).parent / '_svc.vue').read_text(encoding='utf-8')

def build_response() -> str:
    return (Path(__file__).parent / '_resp.vue').read_text(encoding='utf-8')

def main():
    write('grpcMessageTab.vue', build_message())
    write('grpcMetadataTab.vue', build_metadata())
    write('grpcSettingsTab.vue', build_settings())
    write('grpcServiceDefinitionTab.vue', build_service_definition())
    write('grpcResponsePanel.vue', build_response())
    print('done')

if __name__ == "__main__":
    main()
