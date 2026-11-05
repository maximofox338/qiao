<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>

<template>
  <div class="q-container q-page">
    <el-result icon="info" :title="t('notFound.title')" :sub-title="t('notFound.text')" data-testid="not-found">
      <template #extra>
        <router-link to="/" data-testid="not-found-home">{{ t('notFound.home') }}</router-link>
      </template>
    </el-result>
  </div>
</template>
