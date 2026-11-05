<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LocaleSwitch from '@/components/LocaleSwitch.vue'
import { adminApi } from '@/api/admin'
import { isApiError } from '@/api/http'
import { useAdminStore } from '@/stores/admin'
import { errorText } from '@/utils/errors'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const admin = useAdminStore()

const form = reactive({ email: '', password: '', totp: '' })
const errors = ref<Record<string, string>>({})
const error = ref('')
const loading = ref(false)
const showTotp = ref(false)

async function submit() {
  error.value = ''
  const e: Record<string, string> = {}
  if (!form.email.trim()) e.email = t('fieldErrors.REQUIRED')
  if (!form.password) e.password = t('fieldErrors.REQUIRED')
  if (form.totp && !/^\d{6}$/.test(form.totp.trim())) e.totp = t('admin.login.totpInvalid')
  errors.value = e
  if (Object.keys(e).length) return
  loading.value = true
  try {
    const res = await adminApi.login(form.email.trim().toLowerCase(), form.password, form.totp.trim() || undefined)
    admin.setSession(res.token, res.admin)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/admin') ? route.query.redirect : '/admin'
    await router.replace(redirect)
  } catch (err) {
    if (isApiError(err) && /TOTP/.test(err.code)) showTotp.value = true
    error.value = errorText(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <div class="box">
      <div class="top">
        <div class="brand">
          <span class="logo" aria-hidden="true">桥</span>
          <span>{{ t('admin.brand') }}</span>
        </div>
        <LocaleSwitch area="admin" testid-prefix="admin-locale" />
      </div>
      <h1 data-testid="admin-login-title">{{ t('admin.login.title') }}</h1>
      <p class="q-muted">{{ t('admin.login.subtitle') }}</p>
      <el-alert v-if="route.query.expired" type="info" :closable="false" show-icon :title="t('login.expired')" />
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" data-testid="admin-login-error" />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item :label="t('admin.login.email')" :error="errors.email">
          <el-input v-model="form.email" type="email" autocomplete="username" data-testid="admin-login-email" />
        </el-form-item>
        <el-form-item :label="t('admin.login.password')" :error="errors.password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" data-testid="admin-login-password" />
        </el-form-item>
        <el-form-item :error="errors.totp">
          <template #label>
            <span>{{ t('admin.login.totp') }}</span>
            <span class="q-muted optional">{{ t('common.optional') }}</span>
          </template>
          <el-input
            v-model="form.totp"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
            :placeholder="t('admin.login.totpPlaceholder')"
            :class="{ highlight: showTotp }"
            data-testid="admin-login-totp"
          />
        </el-form-item>
        <el-button type="primary" native-type="submit" class="full" size="large" :loading="loading" data-testid="admin-login-submit">
          {{ t('admin.login.submit') }}
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 10vh 16px 32px;
  background: var(--q-bg);
}
.box {
  width: 100%;
  max-width: 400px;
  background: var(--q-surface);
  border: 1px solid var(--q-border);
  border-radius: 12px;
  box-shadow: var(--q-shadow);
  padding: 28px;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
}
.logo {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: var(--q-accent);
  color: #fff;
  display: grid;
  place-items: center;
}
h1 {
  font-size: 1.4rem;
}
.optional {
  font-weight: 400;
  margin-left: 6px;
  font-size: 12px;
}
.highlight :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-warning) inset;
}
.full {
  width: 100%;
  margin-top: 8px;
}
@media (max-width: 640px) {
  .box {
    padding: 20px;
  }
}
</style>
