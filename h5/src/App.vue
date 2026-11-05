<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppTabbar from '@/components/AppTabbar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--tab': route.meta.tab }">
    <router-view />
    <AppTabbar v-if="route.meta.tab" />
    <ConfirmDialog />
  </div>
</template>
