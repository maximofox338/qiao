<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ source: string | null | undefined }>()
// renderMarkdown escapes raw HTML and drops images, so v-html is safe here.
const html = computed(() => renderMarkdown(props.source))
</script>

<template>
  <div class="q-markdown" v-html="html" />
</template>
