<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    description?: string
    label?: string
    confirmText?: string
    confirmType?: 'primary' | 'danger' | 'warning'
    required?: boolean
    loading?: boolean
    error?: string
    testidPrefix: string
  }>(),
  { required: true, confirmType: 'primary', description: '', label: '', confirmText: '', error: '' },
)
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; confirm: [reason: string] }>()
const { t } = useI18n()

const reason = ref('')
const touched = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reason.value = ''
      touched.value = false
    }
  },
)

function confirm() {
  touched.value = true
  if (props.required && !reason.value.trim()) return
  emit('confirm', reason.value.trim())
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="520px"
    append-to-body
    :data-testid="`${testidPrefix}-dialog`"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p v-if="description" class="q-muted">{{ description }}</p>
    <el-form label-position="top" @submit.prevent="confirm">
      <el-form-item
        :label="label || t('common.reason')"
        :required="required"
        :error="touched && required && !reason.trim() ? t('fieldErrors.REQUIRED') : error"
      >
        <el-input
          v-model="reason"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          :data-testid="`${testidPrefix}-input`"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :data-testid="`${testidPrefix}-cancel`" @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button :type="confirmType" :loading="loading" :data-testid="`${testidPrefix}-submit`" @click="confirm">
        {{ confirmText || t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>
