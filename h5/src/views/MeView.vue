<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { engineerApi, meApi, teamApi } from '@/api/endpoints'
import type { ConsentRecord, ConsentType } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useConfirmStore } from '@/stores/confirm'
import { applyLocale, currentLocale, type AppLocale } from '@/i18n'
import { formatDate } from '@/utils/format'
import { errorMessage, isCode } from '@/utils/errors'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const confirm = useConfirmStore()

const showLanguage = ref(false)
const toggling = ref<string | null>(null)
const exporting = ref(false)
const deleting = ref(false)
const projectTitles = ref<Record<string, string>>({})
const invitationCount = ref(0)

const LANGS: { value: AppLocale; label: string }[] = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en', label: 'English' },
]
const FIXED: ConsentType[] = ['TERMS', 'PRIVACY']
const TOGGLES: ConsentType[] = ['CROSS_BORDER_PROCESSING', 'SHARE_PROFILE_PSEUDONYMIZED']
const LEGAL_DOCS = ['terms', 'privacy', 'cross-border', 'engineer-declaration']

const me = computed(() => auth.me)
const profileStatus = computed(() => me.value?.engineerProfileStatus ?? 'NONE')
const avatar = computed(() => (me.value?.email?.[0] ?? 'Q').toUpperCase())
const langLabel = computed(() => LANGS.find((l) => l.value === currentLocale())?.label ?? '')
const cvConsents = computed(() =>
  auth.consents.filter((c) => c.type === 'SHARE_FULL_CV' && c.scopeRef !== null && c.scopeRef !== undefined),
)

onMounted(async () => {
  auth.loadMe().catch(() => undefined)
  teamApi
    .invitations()
    .then((list) => (invitationCount.value = list.length))
    .catch(() => undefined)
  try {
    await auth.loadConsents()
  } catch {
    /* fall back to me.consents */
  }
  if (cvConsents.value.length) {
    try {
      const apps = await engineerApi.applications()
      const map: Record<string, string> = {}
      for (const a of apps) if (a.projectTitle) map[String(a.projectId)] = a.projectTitle
      projectTitles.value = map
    } catch {
      /* titles are optional */
    }
  }
})

function record(type: ConsentType): ConsentRecord | undefined {
  return auth.consents.find((c) => c.type === type && (c.scopeRef === null || c.scopeRef === undefined))
}

function statusText(row: ConsentRecord | undefined, granted: boolean) {
  if (!granted) return row ? t('me.consentWithdrawn') : t('me.consentNotGranted')
  const date = formatDate(row?.createdAt)
  if (row?.version) return t('me.consentGrantedVersion', { v: row.version, date })
  return date ? t('me.consentGranted', { date }) : t('me.granted')
}

async function toggle(type: ConsentType, value: boolean, scopeRef?: string | number) {
  if (!value) {
    const key = type === 'SHARE_FULL_CV' ? 'SHARE_FULL_CV' : type
    const ok = await confirm.open({
      title: t('me.withdrawTitle'),
      message: t(`me.withdrawConfirm.${key}`),
      danger: true,
    })
    if (!ok) return
  }
  toggling.value = `${type}-${scopeRef ?? ''}`
  try {
    await auth.setConsent(type, value, scopeRef)
    showSuccessToast(value ? t('me.granted') : t('me.withdrawn'))
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    toggling.value = null
  }
}

async function pickLanguage(locale: AppLocale) {
  showLanguage.value = false
  if (locale === currentLocale()) return
  applyLocale(locale)
  if (auth.isLoggedIn) meApi.setLocale(locale).catch(() => undefined)
}

async function exportData() {
  exporting.value = true
  try {
    const { blob, filename } = await meApi.exportData()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 10000)
    showSuccessToast(t('me.exported'))
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    exporting.value = false
  }
}

async function deleteAccount() {
  const ok = await confirm.open({
    title: t('me.deleteTitle'),
    message: t('me.deleteConfirm'),
    confirmText: t('me.delete'),
    danger: true,
  })
  if (!ok) return
  deleting.value = true
  try {
    await meApi.deleteAccount()
    auth.clear()
    showSuccessToast(t('me.deleted'))
    router.replace('/')
  } catch (e) {
    if (isCode(e, 'ACCOUNT_HAS_ACTIVE_CONTRACTS')) {
      await confirm.open({ title: t('me.deleteTitle'), message: t('me.deleteBlocked'), showCancel: false })
    } else {
      showFailToast(errorMessage(e))
    }
  } finally {
    deleting.value = false
  }
}

async function logout() {
  const ok = await confirm.open({ title: t('me.logout'), message: t('me.logoutConfirm') })
  if (!ok) return
  await auth.logout()
  showSuccessToast(t('me.loggedOut'))
  router.replace('/login')
}
</script>

<template>
  <div class="me" data-testid="me-page">
    <PageHeader :title="t('me.title')" />

    <section class="me-head">
      <div class="me-head__avatar">{{ avatar }}</div>
      <div class="me-head__info">
        <div class="me-head__email" data-testid="me-email">{{ me?.email }}</div>
        <div class="q-row me-head__meta">
          <StatusTag kind="profile" :status="profileStatus" data-testid="me-profile-status" />
        </div>
      </div>
    </section>

    <van-cell-group inset class="me-group">
      <van-cell
        :title="t('me.profile')"
        icon="contact-o"
        is-link
        to="/profile"
        :value="t(`status.profile.${profileStatus}`)"
        data-testid="me-profile"
      />
      <van-cell :title="t('me.earnings')" icon="balance-o" is-link to="/earnings" data-testid="me-earnings" />
      <van-cell
        :title="t('me.teams')"
        icon="friends-o"
        is-link
        to="/teams"
        :value="invitationCount ? t('me.teamsInvites', { n: invitationCount }) : ''"
        data-testid="me-teams"
      />
      <van-cell
        :title="t('me.language')"
        icon="exchange"
        is-link
        :value="langLabel"
        data-testid="me-language"
        @click="showLanguage = true"
      />
    </van-cell-group>

    <h2 class="q-group-title">{{ t('me.consents') }}</h2>
    <van-cell-group inset class="me-group" data-testid="me-consents">
      <van-cell v-for="type in FIXED" :key="type" :data-testid="`consent-${type}`" class="consent">
        <template #title>
          <div class="consent__name">{{ t(`me.consentName.${type}`) }}</div>
          <div class="consent__desc">{{ t(`me.consentDesc.${type}`) }}</div>
          <div class="consent__state">{{ statusText(record(type), auth.hasConsent(type)) }}</div>
          <div class="consent__desc">{{ t('me.consentFixed') }}</div>
        </template>
      </van-cell>
      <van-cell v-for="type in TOGGLES" :key="type" :data-testid="`consent-${type}`" class="consent" center>
        <template #title>
          <div class="consent__name">{{ t(`me.consentName.${type}`) }}</div>
          <div class="consent__desc">{{ t(`me.consentDesc.${type}`) }}</div>
          <div class="consent__state" :class="{ 'is-off': !auth.hasConsent(type) }">
            {{ statusText(record(type), auth.hasConsent(type)) }}
          </div>
        </template>
        <template #right-icon>
          <van-switch
            :model-value="auth.hasConsent(type)"
            size="22px"
            :loading="toggling === `${type}-`"
            :data-testid="`consent-toggle-${type}`"
            @update:model-value="(v: boolean) => toggle(type, v)"
          />
        </template>
      </van-cell>
      <van-cell
        v-for="c in cvConsents"
        :key="`cv-${c.scopeRef}`"
        :data-testid="`consent-SHARE_FULL_CV-${c.scopeRef}`"
        class="consent"
        center
      >
        <template #title>
          <div class="consent__name">
            {{ t('me.consentName.SHARE_FULL_CV') }} ·
            {{ projectTitles[String(c.scopeRef)] || t('common.projectRef', { id: c.scopeRef }) }}
          </div>
          <div class="consent__desc">{{ t('me.consentDesc.SHARE_FULL_CV') }}</div>
          <div class="consent__state" :class="{ 'is-off': !c.granted }">{{ statusText(c, c.granted) }}</div>
        </template>
        <template #right-icon>
          <van-switch
            :model-value="c.granted"
            size="22px"
            :loading="toggling === `SHARE_FULL_CV-${c.scopeRef}`"
            :data-testid="`consent-toggle-SHARE_FULL_CV-${c.scopeRef}`"
            @update:model-value="(v: boolean) => toggle('SHARE_FULL_CV', v, c.scopeRef ?? undefined)"
          />
        </template>
      </van-cell>
    </van-cell-group>

    <h2 class="q-group-title">{{ t('me.legal') }}</h2>
    <van-cell-group inset class="me-group">
      <van-cell
        v-for="doc in LEGAL_DOCS"
        :key="doc"
        :title="t(`legal.docs.${doc}`)"
        is-link
        :to="`/legal/${doc}`"
        :data-testid="`me-legal-${doc}`"
      />
    </van-cell-group>

    <h2 class="q-group-title">{{ t('me.data') }}</h2>
    <van-cell-group inset class="me-group">
      <van-cell
        :title="t('me.export')"
        :label="t('me.exportHint')"
        icon="down"
        is-link
        data-testid="me-export"
        @click="!exporting && exportData()"
      >
        <template v-if="exporting" #right-icon><van-loading size="16" /></template>
      </van-cell>
      <van-cell
        :title="t('me.delete')"
        icon="delete-o"
        is-link
        class="danger-cell"
        data-testid="me-delete"
        @click="!deleting && deleteAccount()"
      >
        <template v-if="deleting" #right-icon><van-loading size="16" /></template>
      </van-cell>
    </van-cell-group>

    <div class="me-logout">
      <van-button block round data-testid="me-logout" @click="logout">{{ t('me.logout') }}</van-button>
    </div>

    <van-popup v-model:show="showLanguage" position="bottom" round safe-area-inset-bottom data-testid="me-language-sheet">
      <div class="lang-sheet">
        <div class="lang-sheet__title">{{ t('me.language') }}</div>
        <button
          v-for="l in LANGS"
          :key="l.value"
          type="button"
          class="lang-sheet__item"
          :class="{ 'is-active': l.value === currentLocale() }"
          :data-testid="`me-language-${l.value}`"
          @click="pickLanguage(l.value)"
        >
          {{ l.label }}
          <van-icon v-if="l.value === currentLocale()" name="success" />
        </button>
        <button type="button" class="lang-sheet__cancel" data-testid="me-language-cancel" @click="showLanguage = false">
          {{ t('common.cancel') }}
        </button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.me {
  padding-bottom: 24px;
}
.me-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 12px;
  padding: 18px 16px;
  border-radius: var(--q-radius);
  background: linear-gradient(140deg, #2f5fe3 0%, #1f40a8 100%);
  color: #fff;
}
.me-head__avatar {
  flex: none;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
}
.me-head__info {
  min-width: 0;
}
.me-head__email {
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.me-head__meta {
  margin-top: 6px;
}
.me-group {
  margin-top: 0;
}
.me .q-group-title {
  margin-left: 28px;
}
.consent__name {
  font-weight: 500;
}
.consent__desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--q-text-3);
}
.consent__state {
  margin-top: 4px;
  font-size: 12px;
  color: var(--q-success);
}
.consent__state.is-off {
  color: var(--q-text-3);
}
.consent :deep(.van-cell__title) {
  padding-right: 12px;
}
.danger-cell :deep(.van-cell__title),
.danger-cell :deep(.van-cell__left-icon) {
  color: var(--q-danger);
}
.me-logout {
  margin: 24px 12px 8px;
}
.lang-sheet {
  padding: 8px 0 0;
}
.lang-sheet__title {
  padding: 12px 16px;
  text-align: center;
  font-weight: 600;
}
.lang-sheet__item,
.lang-sheet__cancel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 50px;
  border: 0;
  border-top: 1px solid var(--q-border);
  background: #fff;
  font-size: 16px;
  color: var(--q-text);
  cursor: pointer;
}
.lang-sheet__item.is-active {
  color: var(--q-primary);
  font-weight: 600;
}
.lang-sheet__cancel {
  border-top: 8px solid var(--q-bg);
  color: var(--q-text-2);
}
</style>
