<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi, publicApi } from '@/api/user'
import { isApiError } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { currentLocale, setLocale } from '@/i18n'
import { errorText } from '@/utils/errors'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const user = useUserStore()

type Step = 'email' | 'code'
const step = ref<Step>('email')
const email = ref('')
const code = ref('')
const isNew = ref(false)
const acceptTerms = ref(false)
const loading = ref(false)
const error = ref('')
const emailError = ref('')
const codeError = ref('')
const termsError = ref('')
const notClient = ref(route.query.notClient === '1')
const cooldown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const codeValid = computed(() => /^\d{6}$/.test(code.value))

function startCooldown() {
  cooldown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

async function sendCode() {
  error.value = ''
  emailError.value = ''
  email.value = email.value.trim().toLowerCase()
  if (!EMAIL_RE.test(email.value)) {
    emailError.value = t('fieldErrors.EMAIL')
    return
  }
  loading.value = true
  try {
    await authApi.requestOtp(email.value, currentLocale())
    step.value = 'code'
    code.value = ''
    startCooldown()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}

async function legalVersions(): Promise<{ termsVersion?: string; privacyVersion?: string }> {
  try {
    const [terms, privacy] = await Promise.all([publicApi.legal('terms', currentLocale()), publicApi.legal('privacy', currentLocale())])
    return { termsVersion: terms?.version, privacyVersion: privacy?.version }
  } catch {
    return {}
  }
}

async function submit() {
  error.value = ''
  codeError.value = ''
  termsError.value = ''
  code.value = code.value.replace(/\D/g, '')
  if (!codeValid.value) {
    codeError.value = t('login.codeInvalid')
    return
  }
  if (isNew.value && !acceptTerms.value) {
    termsError.value = t('login.termsRequired')
    return
  }
  loading.value = true
  try {
    const extra = isNew.value ? { acceptTerms: true, ...(await legalVersions()) } : {}
    const res = await authApi.verifyOtp({ email: email.value, code: code.value, role: 'CLIENT', locale: currentLocale(), ...extra })
    if (res.user && res.user.role !== 'CLIENT') {
      notClient.value = true
      return
    }
    user.setSession(res.token, res.user ?? null)
    if (res.user?.locale === 'en' || res.user?.locale === 'es') setLocale(res.user.locale, 'client')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : '/client'
    await router.replace(redirect)
  } catch (e) {
    if (isApiError(e, 'TERMS_REQUIRED') || isApiError(e, 'AUTH_ROLE_REQUIRED')) {
      // New account: the same code is submitted again together with the acceptance.
      isNew.value = true
      if (acceptTerms.value) termsError.value = errorText(e)
    } else if (isApiError(e, 'OTP_INVALID') || isApiError(e, 'OTP_EXPIRED')) {
      codeError.value = errorText(e)
    } else {
      error.value = errorText(e)
    }
  } finally {
    loading.value = false
  }
}

function changeEmail() {
  step.value = 'email'
  code.value = ''
  isNew.value = false
  acceptTerms.value = false
  error.value = ''
  codeError.value = ''
}
</script>

<template>
  <div class="q-container q-page login-page">
    <div class="login-card q-card">
      <h1 data-testid="login-title">{{ isNew ? t('login.newTitle') : t('login.title') }}</h1>
      <p class="q-muted">{{ isNew ? t('login.newSubtitle') : t('login.subtitle') }}</p>

      <el-alert v-if="route.query.expired" type="info" :closable="false" show-icon :title="t('login.expired')" data-testid="login-expired" />
      <el-alert v-if="notClient" type="warning" :closable="false" show-icon data-testid="login-not-client" :title="t('login.notClientTitle')">
        <template #default>
          {{ t('login.notClientText') }}
          <a href="/m/" data-testid="login-engineer-app-link">{{ t('login.engineerApp') }}</a>
        </template>
      </el-alert>
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" data-testid="login-error" />

      <el-form v-if="step === 'email'" label-position="top" @submit.prevent="sendCode">
        <el-form-item :label="t('login.email')" :error="emailError">
          <el-input
            v-model="email"
            type="email"
            autocomplete="email"
            inputmode="email"
            size="large"
            :placeholder="t('login.emailPlaceholder')"
            data-testid="login-email"
            autofocus
          />
        </el-form-item>
        <el-button type="primary" size="large" class="full" native-type="submit" :loading="loading" data-testid="login-send-code">
          {{ t('login.sendCode') }}
        </el-button>
      </el-form>

      <el-form v-else label-position="top" @submit.prevent="submit">
        <p class="sent-to" data-testid="login-code-sent">
          {{ t('login.codeSent', { email }) }}
          <el-button link type="primary" data-testid="login-change-email" @click="changeEmail">{{ t('login.changeEmail') }}</el-button>
        </p>
        <el-form-item :label="t('login.code')" :error="codeError">
          <el-input
            v-model="code"
            size="large"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
            class="code-input"
            :placeholder="t('login.codePlaceholder')"
            data-testid="login-code"
            autofocus
          />
        </el-form-item>

        <div v-if="isNew" class="terms" data-testid="login-new-account">
          <el-alert type="info" :closable="false" :title="t('login.newAccountNotice')" />
          <div class="terms-row">
            <!-- The links live outside the checkbox's own <label> so clicking them never toggles it. -->
            <el-checkbox
              v-model="acceptTerms"
              id="login-accept-terms-input"
              aria-labelledby="login-accept-terms-label"
              data-testid="login-accept-terms"
            />
            <i18n-t id="login-accept-terms-label" keypath="login.acceptTerms" tag="label" for="login-accept-terms-input" class="terms-label">
              <template #terms>
                <a href="/legal/terms" target="_blank" rel="noopener" data-testid="login-terms-link">{{ t('legal.docs.terms') }}</a>
              </template>
              <template #privacy>
                <a href="/legal/privacy" target="_blank" rel="noopener" data-testid="login-privacy-link">{{ t('legal.docs.privacy') }}</a>
              </template>
            </i18n-t>
          </div>
          <div v-if="termsError" class="field-error" data-testid="login-terms-error">{{ termsError }}</div>
        </div>

        <el-button type="primary" size="large" class="full" native-type="submit" :loading="loading" data-testid="login-submit">
          {{ isNew ? t('login.createAccount') : t('login.signIn') }}
        </el-button>
        <div class="resend">
          <el-button link :disabled="cooldown > 0 || loading" data-testid="login-resend" @click="sendCode">
            {{ cooldown > 0 ? t('login.resendIn', { s: cooldown }) : t('login.resend') }}
          </el-button>
        </div>
      </el-form>

      <p class="q-muted q-small engineer-note">
        {{ t('login.engineerNote') }}
        <a href="/m/" data-testid="login-engineer-link">{{ t('login.engineerApp') }}</a>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
}
.login-card {
  width: 100%;
  max-width: 440px;
  padding: 28px;
  margin-top: 24px;
}
.full {
  width: 100%;
}
.sent-to {
  color: var(--q-text-2);
  overflow-wrap: anywhere;
}
.code-input :deep(input) {
  letter-spacing: 0.4em;
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}
.terms {
  margin-bottom: 16px;
}
.terms-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.terms-row :deep(.el-checkbox) {
  height: 22px;
  margin-right: 0;
}
.terms-label {
  line-height: 1.5;
  color: var(--q-text-2);
  cursor: pointer;
  font-size: 14px;
}
.resend {
  text-align: center;
  margin-top: 12px;
}
.engineer-note {
  margin: 20px 0 0;
  padding-top: 16px;
  border-top: 1px solid var(--q-border);
}
@media (max-width: 640px) {
  .login-card {
    padding: 20px;
    margin-top: 0;
  }
}
</style>
