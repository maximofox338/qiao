<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const items = [
  { name: 'projects', to: '/projects', icon: 'apps-o', label: 'tabbar.projects' },
  { name: 'applications', to: '/applications', icon: 'todo-list-o', label: 'tabbar.applications' },
  { name: 'contracts', to: '/contracts', icon: 'description-o', label: 'tabbar.contracts' },
  { name: 'me', to: '/me', icon: 'user-o', label: 'tabbar.me' },
]
const active = computed(() => String(route.name ?? ''))
</script>

<template>
  <van-tabbar :model-value="active" fixed placeholder safe-area-inset-bottom class="app-tabbar" data-testid="tabbar">
    <van-tabbar-item
      v-for="item in items"
      :key="item.name"
      :name="item.name"
      :to="item.to"
      :icon="item.icon"
      replace
      :data-testid="`tab-${item.name}`"
    >
      {{ $t(item.label) }}
    </van-tabbar-item>
  </van-tabbar>
</template>
