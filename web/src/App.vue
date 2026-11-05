<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import en from 'element-plus/es/locale/lang/en'
import es from 'element-plus/es/locale/lang/es'

const { t, locale } = useI18n()
const route = useRoute()

const elLocale = computed(() => (locale.value === 'es' ? es : en))

watchEffect(() => {
  const key = route.meta.titleKey
  const base = route.matched.some((r) => r.meta.area === 'admin') ? t('admin.brand') : t('app.name')
  document.title = key ? `${t(key)} · ${base}` : `${base} · ${t('app.tagline')}`
})
</script>

<template>
  <el-config-provider :locale="elLocale">
    <router-view />
  </el-config-provider>
</template>
