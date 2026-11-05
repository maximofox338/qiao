<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

/** Tag input: type a skill and press Enter (or comma) to add it. `data-testid` lands on the native <input>. */
const props = withDefaults(
  defineProps<{ modelValue: string[]; testid: string; max?: number; disabled?: boolean; suggestions?: string[] }>(),
  {
  max: 20,
  disabled: false,
  suggestions: () => [],
  },
)
const emit = defineEmits<{ 'update:modelValue': [v: string[]] }>()
const { t } = useI18n()

const draft = ref('')
const remaining = computed(() =>
  props.suggestions.filter((s) => !props.modelValue.some((m) => m.toLowerCase() === s.toLowerCase())).slice(0, 8),
)

function add(raw: string) {
  const parts = raw.split(',').map((s) => s.trim()).filter(Boolean)
  if (!parts.length) return
  const next = [...props.modelValue]
  for (const p of parts) {
    if (next.length >= props.max) break
    if (!next.some((s) => s.toLowerCase() === p.toLowerCase())) next.push(p.slice(0, 40))
  }
  emit('update:modelValue', next)
  draft.value = ''
}

function remove(i: number) {
  const next = [...props.modelValue]
  next.splice(i, 1)
  emit('update:modelValue', next)
}

function onKeydown(e: Event | KeyboardEvent) {
  const ke = e as KeyboardEvent
  if (ke.isComposing) return
  if (ke.key === 'Enter' || ke.key === ',') {
    ke.preventDefault()
    add(draft.value)
  } else if (ke.key === 'Backspace' && !draft.value && props.modelValue.length) {
    remove(props.modelValue.length - 1)
  }
}
</script>

<template>
  <div class="skills-input">
    <div v-if="modelValue.length" class="q-tags chosen">
      <el-tag
        v-for="(s, i) in modelValue"
        :key="s"
        :closable="!disabled"
        effect="light"
        :data-testid="`${testid}-tag-${i}`"
        @close="remove(i)"
      >
        {{ s }}
      </el-tag>
    </div>
    <el-input
      v-model="draft"
      :data-testid="testid"
      :disabled="disabled || modelValue.length >= max"
      :placeholder="t('skills.placeholder')"
      maxlength="40"
      @keydown="onKeydown"
      @blur="add(draft)"
    />
    <div v-if="remaining.length && !disabled" class="suggest">
      <span class="q-muted q-small">{{ t('skills.suggestions') }}</span>
      <button
        v-for="s in remaining"
        :key="s"
        type="button"
        class="suggest-btn"
        :data-testid="`${testid}-suggest-${s.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`"
        @click="add(s)"
      >
        + {{ s }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.skills-input {
  width: 100%;
}
.chosen {
  margin-bottom: 8px;
}
.suggest {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
}
.suggest-btn {
  font: inherit;
  font-size: 12px;
  border: 1px dashed var(--q-border);
  background: var(--q-surface);
  color: var(--q-text-2);
  border-radius: 999px;
  padding: 2px 10px;
  cursor: pointer;
}
.suggest-btn:hover {
  border-color: var(--q-accent);
  color: var(--q-accent);
}
</style>
