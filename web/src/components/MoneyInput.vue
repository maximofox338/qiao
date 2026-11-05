<script setup lang="ts">
import { ref, watch } from 'vue'

/**
 * USD amount input built on ElInput so that `data-testid` and other attributes land on the native <input>
 * (Playwright `fill()` works directly). Emits a number with 2 decimals, or null when empty.
 */
defineOptions({ inheritAttrs: false })
const props = defineProps<{ modelValue: number | null | undefined; placeholder?: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: number | null] }>()

const text = ref(format(props.modelValue))

function format(v: number | null | undefined): string {
  return v === null || v === undefined || Number.isNaN(v) ? '' : String(v)
}

function parse(s: string): number | null {
  const cleaned = s.replace(/[^\d.,-]/g, '').replace(/,(?=\d{3}(\D|$))/g, '').replace(',', '.')
  if (!cleaned) return null
  const n = Number(cleaned)
  return Number.isFinite(n) ? Math.round(n * 100) / 100 : null
}

watch(
  () => props.modelValue,
  (v) => {
    if (parse(text.value) !== (v ?? null)) text.value = format(v)
  },
)

function onInput(v: string) {
  text.value = v
  emit('update:modelValue', parse(v))
}

function onBlur() {
  const n = parse(text.value)
  text.value = n === null ? '' : n.toFixed(2)
}
</script>

<template>
  <el-input
    v-bind="$attrs"
    :model-value="text"
    inputmode="decimal"
    autocomplete="off"
    :placeholder="placeholder ?? '0.00'"
    :disabled="disabled"
    class="money-input"
    @update:model-value="onInput"
    @blur="onBlur"
  >
    <template #prefix>US$</template>
  </el-input>
</template>

<style scoped>
.money-input :deep(input) {
  font-variant-numeric: tabular-nums;
}
</style>
