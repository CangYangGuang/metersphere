<template>
  <div class="grpc-method-list-panel flex flex-col gap-[12px]">
    <div class="font-medium">{{ t('apiTestGrpc.methodListTitle') }}</div>
    <a-empty v-if="!services.length" :description="t('apiTestGrpc.methodListEmpty')" />
    <div v-else class="flex flex-col gap-[12px]">
      <div
        v-for="service in services"
        :key="service.name"
        class="rounded-[4px] border border-[var(--color-text-n8)] p-[12px]"
      >
        <div class="mb-[8px] font-medium text-[var(--color-text-1)]">{{ service.fullName || service.name }}</div>
        <div class="flex flex-col gap-[8px]">
          <div
            v-for="method in service.methods || []"
            :key="`${service.name}-${method.name}`"
            class="flex flex-wrap items-center justify-between gap-[8px] text-[13px]"
          >
            <div class="flex min-w-0 flex-1 items-center gap-[8px]">
              <span class="truncate font-medium">{{ method.name }}</span>
              <a-tag v-if="method.methodType" size="small">{{ method.methodType }}</a-tag>
            </div>
            <a-tag v-if="isCurrentMethod(service.name, method.name)" color="arcoblue" size="small">{{
              t('apiTestGrpc.currentMethod')
            }}</a-tag>
            <a-button
              v-else
              type="outline"
              size="mini"
              class="arco-btn-outline--secondary"
              @click="emitUse(service.name, method.name)"
              >{{ t('apiTestGrpc.useMethod') }}</a-button
            >
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcRequestState, GrpcServiceInfo } from '@/models/apiTest/grpc';

  const props = defineProps<{ grpc: GrpcRequestState }>();

  const emit = defineEmits<{ (e: 'use', payload: { serviceName: string; methodName: string }): void }>();

  const { t } = useI18n();

  const services = computed<GrpcServiceInfo[]>(() => props.grpc.services || []);

  function isCurrentMethod(serviceName: string, methodName: string) {
    return props.grpc.serviceName === serviceName && props.grpc.methodName === methodName;
  }

  function emitUse(serviceName: string, methodName: string) {
    emit('use', { serviceName, methodName });
  }
</script>
