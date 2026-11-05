<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = withDefaults(defineProps<{ title: string; back?: boolean | string }>(), { back: false })
const router = useRouter()

function goBack() {
  // Go back inside the app when there is history, otherwise to the given fallback.
  const state = window.history.state as { back?: string | null } | null
  if (state?.back) router.back()
  else router.replace(typeof props.back === 'string' ? props.back : '/projects')
}
</script>

<template>
  <van-nav-bar
    :title="title"
    :left-arrow="!!back"
    fixed
    placeholder
    safe-area-inset-top
    class="q-header"
    data-testid="page-header"
    @click-left="back && goBack()"
  >
    <template v-if="back" #left>
      <van-icon name="arrow-left" size="20" data-testid="nav-back" />
    </template>
    <template #right><slot name="right" /></template>
  </van-nav-bar>
</template>

<style scoped>
.q-header :deep(.van-nav-bar__title) {
  font-weight: 600;
}
</style>
