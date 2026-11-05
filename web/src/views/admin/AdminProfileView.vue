<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import QRCode from 'qrcode'
import { adminApi } from '@/api/admin'
import type { TotpSetup } from '@/api/types'
import { useAdminStore } from '@/stores/admin'
import { errorText, notifySuccess } from '@/utils/errors'

const { t } = useI18n()
const admin = useAdminStore()

const enabled = computed(() => !!admin.admin?.totpEnabled)
const setup = ref<TotpSetup | null>(null)
const qr = ref('')
const code = ref('')
const busy = ref(false)
const error = ref('')

onMounted(() => admin.loadMe(true).catch(() => undefined))

const validCode = computed(() => /^\d{6}$/.test(code.value.trim()))

async function startSetup() {
  busy.value = true
  error.value = ''
  try {
    setup.value = await adminApi.totpSetup()
    // The QR code is drawn in the browser by the bundled `qrcode` library: the secret never leaves the page.
    qr.value = await QRCode.toDataURL(setup.value.otpauthUri, { margin: 1, width: 220, errorCorrectionLevel: 'M' })
    code.value = ''
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

async function confirm(action: 'enable' | 'disable') {
  if (!validCode.value) {
    error.value = t('admin.profile.codeFormat')
    return
  }
  busy.value = true
  error.value = ''
  try {
    if (action === 'enable') await adminApi.totpEnable(code.value.trim())
    else await adminApi.totpDisable(code.value.trim())
    await admin.loadMe(true)
    setup.value = null
    qr.value = ''
    code.value = ''
    notifySuccess(action === 'enable' ? t('admin.profile.enabled') : t('admin.profile.disabled'))
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

function cancelSetup() {
  setup.value = null
  qr.value = ''
  code.value = ''
  error.value = ''
}
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-profile-title">{{ t('admin.profile.title') }}</h1>
        <p class="q-sub" data-testid="admin-profile-email">{{ admin.admin?.email }}</p>
      </div>
    </div>

    <section class="q-card totp">
      <div class="q-card-head">
        <h2>{{ t('admin.profile.totpTitle') }}</h2>
        <el-tag :type="enabled ? 'success' : 'info'" data-testid="admin-totp-status" :data-status="enabled ? 'ENABLED' : 'DISABLED'">
          {{ enabled ? t('admin.profile.statusOn') : t('admin.profile.statusOff') }}
        </el-tag>
      </div>
      <p class="q-muted">{{ t('admin.profile.totpIntro') }}</p>
      <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon data-testid="admin-totp-error" class="err" />

      <template v-if="!enabled && !setup">
        <el-button type="primary" :loading="busy" data-testid="admin-totp-setup" @click="startSetup">
          {{ t('admin.profile.setup') }}
        </el-button>
      </template>

      <div v-if="!enabled && setup" class="setup" data-testid="admin-totp-setup-panel">
        <ol class="steps">
          <li>{{ t('admin.profile.step1') }}</li>
          <li>{{ t('admin.profile.step2') }}</li>
        </ol>
        <div class="qr-row">
          <img v-if="qr" :src="qr" :alt="t('admin.profile.qrAlt')" width="220" height="220" data-testid="admin-totp-qr" />
          <div class="manual">
            <div class="q-muted q-small">{{ t('admin.profile.manual') }}</div>
            <code class="secret" data-testid="admin-totp-secret">{{ setup.secret }}</code>
            <div class="q-muted q-small">{{ t('admin.profile.account', { account: setup.account ?? admin.admin?.email ?? '' }) }}</div>
            <input type="hidden" :value="setup.otpauthUri" data-testid="admin-totp-uri" />
          </div>
        </div>
        <el-form class="code-form" @submit.prevent="confirm('enable')">
          <el-form-item :label="t('admin.profile.code')">
            <el-input v-model="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" data-testid="admin-totp-code" />
          </el-form-item>
          <div class="actions">
            <el-button data-testid="admin-totp-cancel" @click="cancelSetup">{{ t('common.cancel') }}</el-button>
            <el-button type="primary" native-type="submit" :disabled="!validCode" :loading="busy" data-testid="admin-totp-enable">
              {{ t('admin.profile.enable') }}
            </el-button>
          </div>
        </el-form>
      </div>

      <el-form v-if="enabled" class="code-form" data-testid="admin-totp-disable-form" @submit.prevent="confirm('disable')">
        <p class="q-small">{{ t('admin.profile.disableHint') }}</p>
        <el-form-item :label="t('admin.profile.code')">
          <el-input v-model="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" data-testid="admin-totp-disable-code" />
        </el-form-item>
        <el-button type="danger" plain native-type="submit" :disabled="!validCode" :loading="busy" data-testid="admin-totp-disable">
          {{ t('admin.profile.disable') }}
        </el-button>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.totp {
  max-width: 720px;
}
.err {
  margin-bottom: 12px;
}
.steps {
  padding-left: 1.2em;
  margin: 0 0 12px;
}
.qr-row {
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
}
.qr-row img {
  border: 1px solid var(--q-border);
  border-radius: 8px;
  background: #fff;
}
.manual {
  min-width: 0;
}
.secret {
  display: block;
  margin: 4px 0;
  font-size: 15px;
  letter-spacing: 0.08em;
  overflow-wrap: anywhere;
}
.code-form {
  margin-top: 16px;
  max-width: 320px;
}
.actions {
  display: flex;
  gap: 8px;
}
</style>
