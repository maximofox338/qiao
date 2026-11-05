<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Download, Delete, SwitchButton } from '@element-plus/icons-vue'
import { meApi } from '@/api/user'
import { downloadFile } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { setLocale, type AppLocale } from '@/i18n'
import { errorText, notifyError, notifySuccess } from '@/utils/errors'

const { t, locale } = useI18n()
const router = useRouter()
const user = useUserStore()

const savingLocale = ref(false)
const exporting = ref(false)
const deleteOpen = ref(false)
const understand = ref(false)
const deleting = ref(false)
const deleteError = ref('')

async function changeLocale(v: string | number | boolean | undefined) {
  const l = v as AppLocale
  setLocale(l, 'client')
  savingLocale.value = true
  try {
    await meApi.update(l)
    notifySuccess(t('account.localeSaved'))
  } catch (e) {
    notifyError(e)
  } finally {
    savingLocale.value = false
  }
}

async function exportData() {
  exporting.value = true
  try {
    await downloadFile('/api/me/export', 'qiao-my-data.json')
  } catch (e) {
    notifyError(e)
  } finally {
    exporting.value = false
  }
}

function openDelete() {
  understand.value = false
  deleteError.value = ''
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!understand.value) return
  deleting.value = true
  deleteError.value = ''
  try {
    await meApi.delete()
    deleteOpen.value = false
    user.clear()
    notifySuccess(t('account.deleted'))
    await router.replace({ name: 'landing' })
  } catch (e) {
    deleteError.value = errorText(e)
  } finally {
    deleting.value = false
  }
}

async function logout() {
  await user.logout()
  await router.push({ name: 'landing' })
}
</script>

<template>
  <div class="q-container q-page account">
    <div class="q-page-head">
      <div>
        <h1 data-testid="account-title">{{ t('account.title') }}</h1>
        <p class="q-sub" data-testid="account-email">{{ user.me?.email }}</p>
      </div>
      <el-button :icon="SwitchButton" data-testid="account-logout" @click="logout">{{ t('nav.logout') }}</el-button>
    </div>

    <section class="q-card">
      <h2>{{ t('account.language') }}</h2>
      <p class="q-muted q-small">{{ t('account.languageHint') }}</p>
      <el-radio-group :model-value="locale" :disabled="savingLocale" data-testid="account-locale" @change="changeLocale">
        <el-radio-button value="en" data-testid="account-locale-en">{{ t('locales.en') }}</el-radio-button>
        <el-radio-button value="es" data-testid="account-locale-es">{{ t('locales.es') }}</el-radio-button>
      </el-radio-group>
    </section>

    <section class="q-card">
      <h2>{{ t('account.exportTitle') }}</h2>
      <p class="q-muted">{{ t('account.exportText') }}</p>
      <el-button :icon="Download" :loading="exporting" data-testid="account-export" @click="exportData">{{ t('account.export') }}</el-button>
    </section>

    <section class="q-card danger-zone">
      <h2>{{ t('account.deleteTitle') }}</h2>
      <p class="q-muted">{{ t('account.deleteText') }}</p>
      <el-button type="danger" plain :icon="Delete" data-testid="account-delete" @click="openDelete">{{ t('account.delete') }}</el-button>
    </section>

    <el-dialog v-model="deleteOpen" :title="t('account.deleteConfirmTitle')" width="480px" append-to-body data-testid="account-delete-dialog">
      <p>{{ t('account.deleteConfirmText') }}</p>
      <el-checkbox v-model="understand" class="understand" data-testid="account-delete-understand">{{ t('account.deleteUnderstand') }}</el-checkbox>
      <el-alert v-if="deleteError" type="error" :closable="false" show-icon :title="deleteError" data-testid="account-delete-error" />
      <template #footer>
        <el-button data-testid="account-delete-cancel" @click="deleteOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" :disabled="!understand" :loading="deleting" data-testid="account-delete-confirm" @click="confirmDelete">
          {{ t('account.deleteConfirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.account {
  max-width: 760px;
}
.danger-zone {
  border-color: #f5c2c0;
}
.understand {
  height: auto;
  white-space: normal;
  align-items: flex-start;
  margin-bottom: 12px;
}
.understand :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.5;
}
.understand :deep(.el-checkbox__input) {
  margin-top: 3px;
}
</style>
