<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import StateView from '@/components/StateView.vue'
import { publicApi } from '@/api/endpoints'
import type { LegalDoc } from '@/api/types'
import { errorMessage } from '@/utils/errors'

const route = useRoute()
const { t, te, locale } = useI18n()

const doc = ref<LegalDoc | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const docName = computed(() => String(route.params.doc || ''))
const fallbackTitle = computed(() =>
  te(`legal.docs.${docName.value}`) ? t(`legal.docs.${docName.value}`) : t('legal.title'),
)
const isFallbackLocale = computed(
  () => !!doc.value?.locale && !doc.value.locale.toLowerCase().startsWith(locale.value === 'en' ? 'en' : 'zh'),
)

async function load() {
  loading.value = true
  error.value = null
  try {
    doc.value = await publicApi.legal(docName.value, locale.value)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

watch([docName, locale], load, { immediate: true })
</script>

<template>
  <div data-testid="legal-page">
    <PageHeader :title="doc?.title || fallbackTitle" back="/" />
    <div class="q-page">
      <StateView :loading="loading" :error="error" @retry="load">
        <article v-if="doc" class="q-card legal" :data-testid="`legal-doc-${docName}`">
          <p v-if="isFallbackLocale" class="q-notice q-notice--warning legal__notice">{{ t('legal.fallbackLocale') }}</p>
          <MarkdownView :source="doc.body" />
          <p class="legal__meta">{{ t('legal.version', { v: doc.version }) }}</p>
        </article>
      </StateView>
    </div>
  </div>
</template>

<style scoped>
.legal {
  padding: 20px 18px;
}
.legal__notice {
  margin: 0 0 12px;
}
.legal__meta {
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--q-border);
  font-size: 12px;
  color: var(--q-text-3);
}
</style>
