<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import StateView from '@/components/StateView.vue'
import { engineerApi } from '@/api/endpoints'
import type { Application, Id } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useConfirmStore } from '@/stores/confirm'
import { formatDate, formatUsd } from '@/utils/format'
import { errorMessage } from '@/utils/errors'

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirmStore()

const apps = ref<Application[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref<string | null>(null)
const busy = ref<string | null>(null)

const WITHDRAWABLE = ['SUBMITTED', 'SHORTLISTED', 'SELECTED']

async function load() {
  error.value = null
  try {
    const [list] = await Promise.all([engineerApi.applications(), auth.loadConsents().catch(() => [])])
    apps.value = [...list].sort((a, b) => String(b.createdAt ?? '').localeCompare(String(a.createdAt ?? '')))
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}
onMounted(load)

function cvShared(a: Application) {
  return auth.hasConsent('SHARE_FULL_CV', a.projectId)
}

async function grantCv(a: Application) {
  busy.value = `cv-${a.id}`
  try {
    await engineerApi.cvConsent(a.id, true)
    await auth.loadConsents().catch(() => [])
    showSuccessToast(t('applications.cvGrantSuccess'))
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    busy.value = null
  }
}

async function withdraw(id: Id) {
  const ok = await confirm.open({
    title: t('applications.withdrawTitle'),
    message: t('applications.withdrawConfirm'),
    confirmText: t('applications.withdraw'),
    danger: true,
  })
  if (!ok) return
  busy.value = `w-${id}`
  try {
    await engineerApi.withdraw(id)
    showSuccessToast(t('applications.withdrawn'))
    await load()
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div class="apps" data-testid="applications-page">
    <PageHeader :title="t('applications.title')" />
    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div class="q-page apps__page">
        <StateView :loading="loading" :error="error" :empty="!apps.length" :empty-text="t('applications.empty')" @retry="load">
          <template #action>
            <van-button round type="primary" size="small" to="/projects" data-testid="applications-browse">
              {{ t('applications.emptyAction') }}
            </van-button>
          </template>

          <article v-for="a in apps" :key="a.id" class="q-card app" :data-testid="`application-${a.id}`">
            <div class="app__head">
              <router-link :to="`/projects/${a.projectId}`" class="app__title" :data-testid="`application-project-${a.id}`">
                {{ a.projectTitle || t('common.projectRef', { id: a.projectId }) }}
              </router-link>
              <StatusTag kind="application" :status="a.status" :data-testid="`application-status-${a.id}`" />
            </div>
            <div class="app__meta">
              <span v-if="a.createdAt" class="q-muted">{{ t('applications.appliedAt', { date: formatDate(a.createdAt) }) }}</span>
              <span v-if="a.talentTotalUsd !== undefined && a.talentTotalUsd !== null">
                <span class="q-muted">{{ t('applications.total') }}</span>
                <span class="q-amount app__amount">{{ formatUsd(a.talentTotalUsd) }}</span>
              </span>
            </div>
            <p v-if="a.coverNote" class="app__note">{{ a.coverNote }}</p>

            <!-- SELECTED: ask for the per-project full-CV consent -->
            <div v-if="a.status === 'SELECTED'" class="cv-prompt" :data-testid="`cv-consent-${a.id}`">
              <template v-if="cvShared(a)">
                <div class="cv-prompt__done" :data-testid="`cv-consent-granted-${a.id}`">
                  <van-icon name="passed" /> {{ t('applications.cvGranted') }}
                </div>
              </template>
              <template v-else>
                <div class="cv-prompt__title"><van-icon name="fire-o" /> {{ t('applications.cvPromptTitle') }}</div>
                <p class="cv-prompt__body">{{ t('applications.cvPromptBody') }}</p>
                <van-button
                  type="primary"
                  size="small"
                  round
                  :loading="busy === `cv-${a.id}`"
                  :data-testid="`cv-consent-grant-${a.id}`"
                  @click="grantCv(a)"
                >
                  {{ t('applications.cvGrant') }}
                </van-button>
              </template>
            </div>

            <div v-if="WITHDRAWABLE.includes(a.status)" class="app__actions">
              <van-button
                size="small"
                plain
                round
                :loading="busy === `w-${a.id}`"
                :data-testid="`application-withdraw-${a.id}`"
                @click="withdraw(a.id)"
              >
                {{ t('applications.withdraw') }}
              </van-button>
            </div>
          </article>
        </StateView>
      </div>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.apps__page {
  min-height: calc(100vh - 100px);
}
.app__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.app__title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--q-text);
}
.app__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
}
.app__amount {
  margin-left: 6px;
}
.app__note {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f8fa;
  color: var(--q-text-2);
  font-size: 13px;
  line-height: 1.6;
}
.cv-prompt {
  margin-top: 12px;
  padding: 12px;
  border-radius: 10px;
  background: linear-gradient(135deg, #eef3ff 0%, #f5f8ff 100%);
  border: 1px solid #d6e0fb;
}
.cv-prompt__title {
  font-weight: 600;
  color: var(--q-primary-dark);
}
.cv-prompt__body {
  margin: 6px 0 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--q-text-2);
}
.cv-prompt__done {
  color: var(--q-success);
  font-size: 13px;
}
.app__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
