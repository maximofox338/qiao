<script setup lang="ts">
withDefaults(defineProps<{ size?: number; light?: boolean }>(), { size: 32, light: false })
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 64 64"
    role="img"
    aria-hidden="true"
    class="q-logo"
  >
    <rect width="64" height="64" rx="16" :fill="light ? '#ffffff' : '#2753d6'" />
    <path
      d="M10 40c7-12 15-18 22-18s15 6 22 18"
      fill="none"
      :stroke="light ? '#2753d6' : '#ffffff'"
      stroke-width="5"
      stroke-linecap="round"
    />
    <path
      d="M8 44h48M20 33v11M32 28v16M44 33v11"
      :stroke="light ? '#2753d6' : '#ffffff'"
      stroke-width="4"
      stroke-linecap="round"
    />
  </svg>
</template>
