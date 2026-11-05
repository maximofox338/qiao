<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import BrandLogo from '@/components/BrandLogo.vue'
import { authApi, publicApi, type VerifyOtpBody } from '@/api/endpoints'
import { useAuthStore } from '@/stores/auth'
import { useLoginDraft } from '@/stores/login'
import { currentLocale } from '@/i18n'
import { errorMessage, isCode } from '@/utils/errors'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const draft = useLoginDraft()

const sending = ref(false)
const submitting = ref(false)
const now = ref(Date.now())
let timer: number | undefined

onMounted(() => {
  timer = window.setInterval(() => (now.value = Date.now()), 1000)
})
onBeforeUnmount(() => window.clearInterval(timer))

const RESEND_SECONDS = 60
const countdown = computed(() =>
  draft.sentAt ? Math.max(0, RESEND_SECONDS - Math.floor((now.value - draft.sentAt) / 1000)) : 0,
)
const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(draft.email.trim()))
const codeValid = computed(() => /^\d{6}$/.test(draft.code.trim()))

async function sendCode() {
  if (!emailValid.value) {
    showFailToast(t('login.invalidEmail'))
    return
  }
  if (countdown.value > 0) return
  sending.value = true
  try {
    await authApi.requestOtp(draft.email.trim(), currentLocale())
    draft.step = 'code'
    draft.sentAt = Date.now()
    now.value = Date.now()
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    sending.value = false
  }
}

function changeEmail() {
  draft.step = 'email'
  draft.code = ''
  draft.sentAt = 0
  draft.newUser = false
}

async function termsVersions(): Promise<{ termsVersion?: string; privacyVersion?: string }> {
  const loc = currentLocale()
  const [terms, privacy] = await Promise.allSettled([publicApi.legal('terms', loc), publicApi.legal('privacy', loc)])
  return {
    termsVersion: terms.status === 'fulfilled' ? terms.value?.version : undefined,
    privacyVersion: privacy.status === 'fulfilled' ? privacy.value?.version : undefined,
  }
}

function safeRedirect(): string | null {
  const r = route.query.redirect
  return typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : null
}

async function submit() {
  if (!codeValid.value) {
    showFailToast(t('login.invalidCode'))
    return
  }
  submitting.value = true
  try {
    // We cannot know beforehand whether the email is registered. Always send role=ENGINEER (ignored for
    // existing users); send acceptTerms only when the box is ticked. A new user without it gets
    // TERMS_REQUIRED / AUTH_ROLE_REQUIRED and is asked to tick it (see docs/DECISIONS.md).
    const body: VerifyOtpBody = {
      email: draft.email.trim(),
      code: draft.code.trim(),
      role: 'ENGINEER',
      locale: currentLocale(),
    }
    if (draft.acceptTerms) {
      body.acceptTerms = true
      const v = await termsVersions()
      if (v.termsVersion) body.termsVersion = v.termsVersion
      if (v.privacyVersion) body.privacyVersion = v.privacyVersion
    }
    const res = await authApi.verifyOtp(body)
    if (res.user?.role && res.user.role !== 'ENGINEER') {
      showFailToast(t('login.notEngineer'))
      return
    }
    auth.setSession(res.token)
    const me = await auth.loadMe()
    if (me.role !== 'ENGINEER') {
      auth.clear()
      showFailToast(t('login.notEngineer'))
      return
    }
    // Separate cross-border consent, recorded right after registration (only if not already given).
    if (draft.acceptCrossBorder && !me.consents?.CROSS_BORDER_PROCESSING) {
      try {
        await auth.setConsent('CROSS_BORDER_PROCESSING', true)
      } catch (e) {
        showFailToast(errorMessage(e))
      }
    }
    showSuccessToast(t('login.success'))
    draft.reset()
    const status = auth.me?.engineerProfileStatus
    router.replace(safeRedirect() ?? (status && status !== 'DRAFT' ? '/projects' : '/profile'))
  } catch (e) {
    if (isCode(e, 'TERMS_REQUIRED') || isCode(e, 'AUTH_ROLE_REQUIRED')) {
      draft.newUser = true
    } else {
      showFailToast(errorMessage(e))
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login" data-testid="login-page">
    <div class="login__top">
      <router-link to="/" class="login__home" data-testid="login-home">
        <BrandLogo :size="40" />
      </router-link>
      <h1 class="login__title">{{ t('login.title') }}</h1>
      <p class="login__subtitle">{{ t('login.subtitle') }}</p>
    </div>

    <div class="login__form">
      <div class="login__field">
        <label class="login__label" for="login-email-input">{{ t('login.email') }}</label>
        <van-field
          id="login-email-input"
          v-model="draft.email"
          v-testid="'login-email'"
          type="email"
          name="email"
          autocomplete="email"
          inputmode="email"
          enterkeyhint="send"
          :placeholder="t('login.emailPlaceholder')"
          :readonly="draft.step === 'code'"
          clearable
          class="login__input"
          @keyup.enter="draft.step === 'email' && sendCode()"
        >
          <template v-if="draft.step === 'code'" #button>
            <button type="button" class="login__link-btn" data-testid="login-change-email" @click="changeEmail">
              {{ t('login.changeEmail') }}
            </button>
          </template>
        </van-field>
      </div>

      <template v-if="draft.step === 'email'">
        <van-button
          block
          round
          type="primary"
          size="large"
          :loading="sending"
          :disabled="!draft.email"
          data-testid="login-send-code"
          @click="sendCode"
        >
          {{ t('login.sendCode') }}
        </van-button>
      </template>

      <template v-else>
        <p class="login__sent" data-testid="login-code-sent">{{ t('login.codeSent', { email: draft.email }) }}</p>
        <div class="login__field">
          <label class="login__label" for="login-code-input">{{ t('login.code') }}</label>
          <van-field
            id="login-code-input"
            v-model="draft.code"
            v-testid="'login-code'"
            type="digit"
            name="code"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            enterkeyhint="done"
            :placeholder="t('login.codePlaceholder')"
            class="login__input login__input--code"
            @keyup.enter="submit"
          >
            <template #button>
              <button
                type="button"
                class="login__link-btn"
                :disabled="countdown > 0 || sending"
                data-testid="login-resend"
                @click="sendCode"
              >
                {{ countdown > 0 ? t('login.resendIn', { s: countdown }) : t('login.resend') }}
              </button>
            </template>
          </van-field>
        </div>
      </template>

      <div class="agree" :class="{ 'agree--alert': draft.newUser && !draft.acceptTerms }" data-testid="login-agreements">
        <p class="agree__title">{{ t('login.newUserTitle') }}</p>
        <div v-if="draft.newUser" class="q-notice q-notice--warning agree__alert" data-testid="login-new-user-notice">
          <van-icon name="info-o" />
          <span>{{ t('login.newUserNeedTerms') }}</span>
        </div>

        <div class="agree__item">
          <div class="agree__line">
            <van-checkbox
              v-model="draft.acceptTerms"
              shape="square"
              icon-size="16px"
              class="agree__box"
              data-testid="login-accept-terms"
            >
              {{ t('login.acceptTerms') }}
            </van-checkbox>
            <i18n-t keypath="login.acceptTermsDocs" tag="span" class="agree__docs" scope="global">
              <template #terms>
                <router-link to="/legal/terms" data-testid="login-link-terms">{{ t('login.termsLink') }}</router-link>
              </template>
              <template #privacy>
                <router-link to="/legal/privacy" data-testid="login-link-privacy">{{ t('login.privacyLink') }}</router-link>
              </template>
            </i18n-t>
          </div>
          <p class="agree__explain">
            <span class="agree__tag agree__tag--required">{{ t('login.requiredTag') }}</span>
          </p>
        </div>

        <div class="agree__item">
          <div class="agree__line">
            <van-checkbox
              v-model="draft.acceptCrossBorder"
              shape="square"
              icon-size="16px"
              class="agree__box"
              data-testid="login-accept-crossborder"
            >
              {{ t('login.acceptCrossBorder') }}
            </van-checkbox>
          </div>
          <p class="agree__explain" data-testid="login-crossborder-explain">
            <span class="agree__tag">{{ t('login.optionalTag') }}</span>
            {{ t('login.crossBorderExplain') }}
            <router-link to="/legal/cross-border" data-testid="login-link-crossborder">
              {{ t('login.crossBorderLink') }}
            </router-link>
          </p>
        </div>
      </div>

      <van-button
        v-if="draft.step === 'code'"
        block
        round
        type="primary"
        size="large"
        :loading="submitting"
        :disabled="!draft.code"
        data-testid="login-submit"
        @click="submit"
      >
        {{ t('login.submit') }}
      </van-button>

      <p v-if="draft.step === 'code'" class="login__help">{{ t('login.codeHelp') }}</p>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  background: #fff;
  padding: calc(40px + env(safe-area-inset-top)) 24px 32px;
}
.login__home {
  display: inline-block;
  margin-bottom: 20px;
}
.login__title {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 700;
}
.login__subtitle {
  margin: 0 0 28px;
  color: var(--q-text-3);
  font-size: 14px;
  line-height: 1.6;
}
.login__field {
  margin-bottom: 16px;
}
.login__label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--q-text-2);
}
.login__input {
  padding: 12px 14px;
  border-radius: 10px;
  background: #f5f6f8;
  font-size: 16px;
}
.login__input--code :deep(input) {
  letter-spacing: 4px;
  font-variant-numeric: tabular-nums;
}
.login__input::after {
  display: none;
}
.login__link-btn {
  border: 0;
  background: none;
  padding: 0 0 0 8px;
  color: var(--q-primary);
  font-size: 14px;
  white-space: nowrap;
  cursor: pointer;
}
.login__link-btn:disabled {
  color: var(--q-text-3);
  cursor: default;
}
.login__sent {
  margin: -6px 0 16px;
  font-size: 12px;
  color: var(--q-text-3);
}
.agree {
  margin: 20px 0;
  padding: 14px;
  border-radius: 10px;
  border: 1px solid var(--q-border);
  background: #fafbfc;
  transition: border-color 0.2s;
}
.agree--alert {
  border-color: #f5c08f;
}
.agree__title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--q-text);
}
.agree__alert {
  margin-bottom: 10px;
}
.agree__item + .agree__item {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--q-border);
}
.agree__line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 4px;
  font-size: 13px;
  line-height: 1.6;
}
.agree__box {
  display: inline-flex;
  align-items: flex-start;
}
.agree__box :deep(.van-checkbox__label) {
  color: var(--q-text-2);
  font-size: 13px;
  line-height: 1.6;
  margin-left: 6px;
}
.agree__box :deep(.van-checkbox__icon) {
  margin-top: 2px;
}
.agree__docs a {
  font-size: 13px;
}
.agree__tag {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 5px;
  margin-right: 4px;
  vertical-align: 1px;
  border-radius: 3px;
  background: #f2f3f5;
  color: var(--q-text-3);
  font-size: 11px;
}
.agree__tag--required {
  background: var(--q-danger-soft);
  color: var(--q-danger);
}
.agree__explain {
  margin: 6px 0 0 22px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--q-text-3);
}
.login__help {
  margin: 14px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--q-text-3);
}
</style>
