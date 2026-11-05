<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

type Kind = 'profile' | 'application' | 'contract' | 'milestone' | 'settlement'
const props = defineProps<{ kind: Kind; status: string | null | undefined }>()
const { t, te } = useI18n()

const TONES: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'default'> = {
  // profile
  DRAFT: 'default',
  NONE: 'default',
  PENDING_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  // application
  SUBMITTED: 'primary',
  SHORTLISTED: 'primary',
  SELECTED: 'success',
  HIRED: 'success',
  DECLINED: 'default',
  WITHDRAWN: 'default',
  // contract
  PENDING_SIGNATURES: 'warning',
  ACTIVE: 'primary',
  COMPLETED: 'success',
  TERMINATED: 'default',
  // milestone
  PENDING_FUNDING: 'default',
  FUNDED: 'primary',
  REVISION_REQUESTED: 'warning',
  ACCEPTED: 'success',
  DISPUTED: 'danger',
  REFUNDED: 'default',
  // settlement
  PENDING: 'warning',
  PAID: 'success',
}

const value = computed(() => props.status || 'NONE')
const label = computed(() => {
  const key = `status.${props.kind}.${value.value}`
  return te(key) ? t(key) : value.value
})
const tone = computed(() => {
  if (props.kind === 'milestone' && value.value === 'SUBMITTED') return 'warning'
  if (props.kind === 'settlement' && value.value === 'NONE') return 'default'
  return TONES[value.value] ?? 'default'
})
</script>

<template>
  <span class="q-status" :class="`q-status--${tone}`" :data-status="value">{{ label }}</span>
</template>

<style scoped>
.q-status {
  display: inline-flex;
  align-items: center;
  flex: none;
  height: 22px;
  padding: 0 8px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.q-status--default {
  background: #f2f3f5;
  color: var(--q-text-3);
}
.q-status--primary {
  background: var(--q-primary-soft);
  color: var(--q-primary);
}
.q-status--success {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.q-status--warning {
  background: var(--q-warning-soft);
  color: var(--q-warning);
}
.q-status--danger {
  background: var(--q-danger-soft);
  color: var(--q-danger);
}
</style>
