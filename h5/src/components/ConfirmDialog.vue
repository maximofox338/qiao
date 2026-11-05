<script setup lang="ts">
import { useConfirmStore } from '@/stores/confirm'

const confirm = useConfirmStore()
</script>

<template>
  <van-dialog
    :show="confirm.visible"
    :title="confirm.options.title"
    :show-confirm-button="false"
    :close-on-click-overlay="false"
    class="q-confirm"
    data-testid="confirm-dialog"
    @update:show="(v: boolean) => !v && confirm.close(false)"
  >
    <div class="q-confirm__message">{{ confirm.options.message }}</div>
    <template #footer>
      <div class="q-confirm__footer">
        <button
          v-if="confirm.options.showCancel"
          type="button"
          class="q-confirm__btn"
          data-testid="confirm-cancel"
          @click="confirm.close(false)"
        >
          {{ confirm.options.cancelText || $t('common.cancel') }}
        </button>
        <button
          type="button"
          class="q-confirm__btn q-confirm__btn--ok"
          :class="{ 'is-danger': confirm.options.danger }"
          data-testid="confirm-ok"
          @click="confirm.close(true)"
        >
          {{ confirm.options.confirmText || $t('common.confirm') }}
        </button>
      </div>
    </template>
  </van-dialog>
</template>

<style scoped>
.q-confirm__message {
  padding: 8px 24px 20px;
  color: var(--q-text-2);
  font-size: 14px;
  line-height: 1.6;
  text-align: center;
  white-space: pre-line;
}
.q-confirm__footer {
  display: flex;
  border-top: 1px solid var(--q-border);
}
.q-confirm__btn {
  flex: 1;
  height: 48px;
  border: 0;
  background: none;
  font-size: 16px;
  color: var(--q-text);
  cursor: pointer;
}
.q-confirm__btn + .q-confirm__btn {
  border-left: 1px solid var(--q-border);
}
.q-confirm__btn--ok {
  color: var(--q-primary);
  font-weight: 600;
}
.q-confirm__btn--ok.is-danger {
  color: var(--q-danger);
}
.q-confirm__btn:active {
  background: var(--q-bg);
}
</style>
