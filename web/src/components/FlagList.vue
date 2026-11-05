<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectFlags } from '@/api/types'
import { flagKeywords } from '@/utils/compliance'

const props = defineProps<{ flags?: ProjectFlags; testid?: string }>()
const keywords = computed(() => flagKeywords(props.flags))
</script>

<template>
  <div v-if="keywords.length" class="q-tags" :data-testid="testid">
    <el-tag v-for="k in keywords" :key="k" type="danger" effect="light" size="small">{{ k }}</el-tag>
  </div>
</template>
