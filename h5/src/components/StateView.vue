<script setup lang="ts">
// Loading / error / empty placeholder used by every page.
defineProps<{
  loading?: boolean
  error?: string | null
  empty?: boolean
  emptyText?: string
  emptyImage?: string
}>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="q-state" data-testid="state-loading">
    <van-loading size="24" vertical>{{ $t('common.loading') }}</van-loading>
  </div>
  <div v-else-if="error" class="q-state" data-testid="state-error">
    <van-empty image="error" :description="error">
      <van-button round size="small" type="primary" plain data-testid="state-retry" @click="$emit('retry')">
        {{ $t('common.retry') }}
      </van-button>
    </van-empty>
  </div>
  <div v-else-if="empty" class="q-state" data-testid="state-empty">
    <van-empty :image="emptyImage || 'default'" :description="emptyText">
      <slot name="action" />
    </van-empty>
  </div>
  <slot v-else />
</template>

<style scoped>
.q-state {
  padding: 48px 0;
  display: flex;
  justify-content: center;
}
</style>
