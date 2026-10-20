<template>
  <a-select
    :model-value="selectValue"
    :placeholder="t('apiTestGrpc.selectMethodPlaceholder')"
    allow-search
    allow-clear
    class="grpc-method-select min-w-[220px] flex-1 hover:z-10"
    :disabled="!groupedOptions.length"
    @update:model-value="handleSelect"
  >
    <a-optgroup v-for="group in groupedOptions" :key="group.serviceName" :label="group.label">
      <a-option v-for="item in group.methods" :key="item.value" :value="item.value">
        <div class="flex w-full items-center justify-between gap-[8px]">
          <span class="truncate">{{ item.methodName }}</span>
          <a-tag v-if="item.methodType" size="small" class="shrink-0">{{ item.methodType }}</a-tag>
        </div>
      </a-option>
    </a-optgroup>
  </a-select>
</template>

<script setup lang="ts">
  import { useI18n } from '@/hooks/useI18n';

  import type { GrpcRequestState } from '@/models/apiTest/grpc';

  import { buildGrpcMethodSelectOptions, resolveGrpcMethodSelectValue } from './utils';

  const props = defineProps<{
    grpc: GrpcRequestState;
  }>();

  const emit = defineEmits<{
    (e: 'select', payload: { serviceName: string; methodName: string }): void;
  }>();

  const { t } = useI18n();

  const selectValue = computed(() => resolveGrpcMethodSelectValue(props.grpc));

  const groupedOptions = computed(() => {
    const map = new Map<
      string,
      {
        serviceName: string;
        label: string;
        methods: ReturnType<typeof buildGrpcMethodSelectOptions>;
      }
    >();
    buildGrpcMethodSelectOptions(props.grpc).forEach((item) => {
      if (!map.has(item.serviceName)) {
        map.set(item.serviceName, {
          serviceName: item.serviceName,
          label: item.serviceLabel,
          methods: [],
        });
      }
      map.get(item.serviceName)!.methods.push(item);
    });
    return Array.from(map.values());
  });

  function handleSelect(value: string | undefined) {
    if (!value) {
      return;
    }
    const [serviceName, methodName] = value.split('::');
    if (!serviceName || !methodName) {
      return;
    }
    emit('select', { serviceName, methodName });
  }
</script>
