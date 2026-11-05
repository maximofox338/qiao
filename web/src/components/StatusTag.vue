<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ status?: string | null; testid?: string; size?: 'small' | 'default' | 'large' }>()
const { t, te } = useI18n()

const TYPES: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
  APPROVED: 'success',
  OPEN: 'success',
  ACTIVE: 'success',
  COMPLETED: 'success',
  ACCEPTED: 'success',
  PAID: 'success',
  HIRED: 'success',
  CLEAR: 'success',
  SENT: 'success',
  OK: 'success',
  FUNDED: 'primary',
  IN_CONTRACT: 'primary',
  SELECTED: 'primary',
  EXPORTED: 'primary',
  PENDING_REVIEW: 'warning',
  SUBMITTED: 'warning',
  CHANGES_REQUESTED: 'warning',
  PENDING_SIGNATURES: 'warning',
  PENDING_FUNDING: 'warning',
  REVISION_REQUESTED: 'warning',
  PENDING: 'warning',
  SHORTLISTED: 'warning',
  WARN: 'warning',
  REJECTED: 'danger',
  BLOCKED: 'danger',
  DISPUTED: 'danger',
  FAILED: 'danger',
  HIT: 'danger',
  CRITICAL: 'danger',
  LIMIT: 'danger',
  TERMINATED: 'danger',
  DISABLED: 'danger',
  DECLINED: 'info',
  DRAFT: 'info',
  CANCELLED: 'info',
  WITHDRAWN: 'info',
  REFUNDED: 'info',
  LOGGED: 'info',
  NONE: 'info',
}

const type = computed(() => (props.status ? (TYPES[props.status] ?? 'info') : 'info'))
const label = computed(() => {
  if (!props.status) return t('status.NONE')
  return te(`status.${props.status}`) ? t(`status.${props.status}`) : props.status
})
</script>

<template>
  <el-tag :type="type" :size="size ?? 'default'" effect="light" disable-transitions :data-testid="testid" :data-status="status ?? ''">
    {{ label }}
  </el-tag>
</template>
