<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ source?: string | null; testid?: string }>()
// Safe: renderMarkdown escapes raw HTML and filters link protocols.
const html = computed(() => renderMarkdown(props.source))
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html -->
  <div class="q-markdown" :data-testid="testid" v-html="html" />
</template>
