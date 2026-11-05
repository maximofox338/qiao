<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AREA_LOCALES, setLocale, type AppLocale, type LocaleArea } from '@/i18n'

const props = withDefaults(defineProps<{ area?: LocaleArea; testidPrefix?: string }>(), {
  area: 'client',
  testidPrefix: 'locale',
})
const emit = defineEmits<{ change: [locale: AppLocale] }>()

const { locale, t } = useI18n()
const options = computed(() => AREA_LOCALES[props.area])

function choose(l: AppLocale) {
  if (l === locale.value) return
  setLocale(l, props.area)
  emit('change', l)
}
</script>

<template>
  <div class="locale-switch" role="group" :aria-label="t('common.language')" :data-testid="`${testidPrefix}-switcher`">
    <button
      v-for="l in options"
      :key="l"
      type="button"
      class="locale-btn"
      :class="{ active: locale === l }"
      :aria-pressed="locale === l"
      :title="t(`locales.${l}`)"
      :data-testid="`${testidPrefix}-${l}`"
      @click="choose(l)"
    >
      {{ l.toUpperCase() }}
    </button>
  </div>
</template>

<style scoped>
.locale-switch {
  display: inline-flex;
  border: 1px solid var(--q-border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--q-surface);
  flex-shrink: 0;
}
.locale-btn {
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  padding: 4px 9px;
  border: 0;
  background: transparent;
  color: var(--q-text-3);
  cursor: pointer;
}
.locale-btn + .locale-btn {
  border-left: 1px solid var(--q-border);
}
.locale-btn.active {
  background: var(--q-accent-soft);
  color: var(--q-accent);
}
.locale-btn:focus-visible {
  outline: 2px solid var(--q-accent);
  outline-offset: -2px;
}
</style>
