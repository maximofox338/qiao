<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import MarkdownView from '@/components/MarkdownView.vue'
import { publicApi } from '@/api/user'
import type { LegalText } from '@/api/types'
import { errorText } from '@/utils/errors'

const DOCS = ['terms', 'privacy', 'client-declarations', 'engineer-declaration', 'cross-border']

const { t, locale } = useI18n()
const route = useRoute()
const doc = computed(() => String(route.params.doc ?? ''))
const known = computed(() => DOCS.includes(doc.value))
const text = ref<LegalText | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  text.value = null
  error.value = ''
  if (!known.value) return
  loading.value = true
  try {
    text.value = await publicApi.legal(doc.value, locale.value)
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}

watch([doc, locale], load, { immediate: true })
</script>

<template>
  <div class="q-container q-page legal">
    <nav class="legal-nav q-small" :aria-label="t('footer.legal')">
      <router-link v-for="d in ['terms', 'privacy', 'client-declarations']" :key="d" :to="`/legal/${d}`" :data-testid="`legal-nav-${d}`">
        {{ t(`legal.docs.${d}`) }}
      </router-link>
    </nav>
    <el-empty v-if="!known" :description="t('legal.unknown')" data-testid="legal-unknown" />
    <div v-else class="q-card legal-card" v-loading="loading">
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon data-testid="legal-error" />
      <template v-if="text">
        <h1 data-testid="legal-title">{{ text.title || t(`legal.docs.${doc}`) }}</h1>
        <p class="q-muted q-small" data-testid="legal-version">{{ t('legal.version', { version: text.version }) }}</p>
        <MarkdownView :source="text.body" testid="legal-body" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.legal {
  max-width: 820px;
}
.legal-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}
.legal-card {
  min-height: 200px;
  padding: 28px;
}
@media (max-width: 640px) {
  .legal-card {
    padding: 18px;
  }
}
</style>
