<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Edit, Promotion, ArrowLeft } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import EngineerCard from '@/components/EngineerCard.vue'
import FlagList from '@/components/FlagList.vue'
import { clientApi } from '@/api/user'
import { isApiError } from '@/api/http'
import type { Application, EngineerCv, Project } from '@/api/types'
import { useCatalogStore } from '@/stores/catalog'
import { flagCount } from '@/utils/compliance'
import { errorText, notifyError, notifySuccess } from '@/utils/errors'
import { date, money } from '@/utils/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()

const id = computed(() => String(route.params.id))
const project = ref<Project | null>(null)
const applications = ref<Application[]>([])
const loading = ref(true)
const loadError = ref('')
const appsError = ref('')
const busy = ref<string | null>(null)

const hireTarget = ref<Application | null>(null)
const cvOpen = ref(false)
const cvFor = ref<Application | null>(null)
const cv = ref<EngineerCv | null>(null)
const cvLoading = ref(false)
const cvConsentRequired = ref(false)
const cvError = ref('')

const editable = computed(() => project.value && ['DRAFT', 'CHANGES_REQUESTED'].includes(project.value.status))
const showApplications = computed(() => project.value && ['OPEN', 'IN_CONTRACT', 'COMPLETED'].includes(project.value.status))
const milestones = computed(() => [...(project.value?.milestones ?? [])].sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0)))
const milestoneTitle = (mid: number) => {
  const idx = milestones.value.findIndex((m) => m.id === mid)
  return idx >= 0 ? `${idx + 1}. ${milestones.value[idx]!.title}` : `#${mid}`
}

const ORDER: Record<string, number> = { HIRED: 0, SELECTED: 1, SHORTLISTED: 2, SUBMITTED: 3, DECLINED: 4, WITHDRAWN: 5 }
const sortedApps = computed(() => [...applications.value].sort((a, b) => (ORDER[a.status] ?? 9) - (ORDER[b.status] ?? 9)))

async function loadApplications() {
  appsError.value = ''
  try {
    applications.value = await clientApi.applications(id.value)
  } catch (e) {
    appsError.value = errorText(e)
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [p] = await Promise.all([clientApi.project(id.value), catalog.loadCategories().catch(() => [])])
    project.value = p
    if (showApplications.value) await loadApplications()
  } catch (e) {
    loadError.value = errorText(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function submitForReview() {
  busy.value = 'submit'
  try {
    project.value = (await clientApi.submitProject(id.value)) ?? project.value
    notifySuccess(t('projectForm.submitted'))
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

async function act(app: Application, action: 'shortlist' | 'decline' | 'select') {
  busy.value = `${action}-${app.id}`
  try {
    await clientApi[action](app.id)
    notifySuccess(t(`applications.done.${action}`))
    await loadApplications()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

async function confirmHire() {
  const app = hireTarget.value
  if (!app) return
  busy.value = `hire-${app.id}`
  try {
    const res = await clientApi.hire(app.id)
    const contractId = res?.contractId ?? res?.id
    hireTarget.value = null
    notifySuccess(t('applications.done.hire'))
    if (contractId) await router.push(`/client/contracts/${contractId}`)
    else await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

async function viewCv(app: Application) {
  cvFor.value = app
  cv.value = null
  cvConsentRequired.value = false
  cvError.value = ''
  cvOpen.value = true
  cvLoading.value = true
  try {
    cv.value = await clientApi.cv(app.id)
  } catch (e) {
    if (isApiError(e, 'CV_CONSENT_REQUIRED')) cvConsentRequired.value = true
    else cvError.value = errorText(e)
  } finally {
    cvLoading.value = false
  }
}

const canShortlist = (a: Application) => a.status === 'SUBMITTED'
const canSelect = (a: Application) => a.status === 'SUBMITTED' || a.status === 'SHORTLISTED'
const canDecline = (a: Application) => ['SUBMITTED', 'SHORTLISTED', 'SELECTED'].includes(a.status)
const canHire = (a: Application) => a.status === 'SELECTED' && project.value?.status === 'OPEN'
const safeLink = (u: string) => (/^https?:\/\//i.test(u) ? u : undefined)
</script>

<template>
  <div class="q-container q-page">
    <router-link to="/client" class="back q-small" data-testid="project-back">
      <el-icon><ArrowLeft /></el-icon> {{ t('nav.dashboard') }}
    </router-link>

    <el-skeleton v-if="loading" :rows="8" animated />
    <el-result v-else-if="loadError" icon="error" :title="loadError" data-testid="project-load-error" />
    <template v-else-if="project">
      <div class="q-page-head">
        <div class="head-main">
          <h1 data-testid="project-title">{{ project.title }}</h1>
          <p class="q-sub">{{ catalog.categoryName(project.categoryCode) }} · {{ date(project.startDate) }} – {{ date(project.endDate) }}</p>
        </div>
        <div class="q-actions">
          <StatusTag :status="project.status" testid="project-status" />
          <el-button v-if="editable" :icon="Edit" data-testid="project-edit" @click="router.push(`/client/projects/${project.id}/edit`)">
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="editable"
            type="primary"
            :icon="Promotion"
            :loading="busy === 'submit'"
            data-testid="project-detail-submit"
            @click="submitForReview"
          >
            {{ t('projectForm.submit') }}
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="project.status === 'SUBMITTED'"
        type="info"
        :closable="false"
        show-icon
        :title="t('project.underReview')"
        data-testid="project-under-review"
      />
      <el-alert
        v-if="project.reviewNote"
        :type="project.status === 'REJECTED' ? 'error' : project.status === 'CHANGES_REQUESTED' ? 'warning' : 'info'"
        :closable="false"
        show-icon
        :title="project.status === 'REJECTED' ? t('project.rejected') : project.status === 'CHANGES_REQUESTED' ? t('project.changesRequested') : t('common.reviewNote')"
        data-testid="project-review-note"
      >
        <template #default><span class="q-pre">{{ project.reviewNote }}</span></template>
      </el-alert>
      <el-alert
        v-if="flagCount(project.flags)"
        type="warning"
        :closable="false"
        show-icon
        :title="t('project.flaggedTitle')"
        data-testid="project-flags"
      >
        <template #default>
          <p class="flag-text">{{ t('project.flaggedText') }}</p>
          <FlagList :flags="project.flags" />
        </template>
      </el-alert>

      <div class="q-grid detail-grid">
        <section class="q-card">
          <h2>{{ t('project.description') }}</h2>
          <p class="q-pre" data-testid="project-description">{{ project.description }}</p>
          <div v-if="project.skills?.length" class="q-tags">
            <el-tag v-for="s in project.skills" :key="s" type="info" effect="plain">{{ s }}</el-tag>
          </div>
        </section>
        <section class="q-card">
          <h2>{{ t('project.summary') }}</h2>
          <dl class="q-kv">
            <dt>{{ t('project.budget') }}</dt>
            <dd class="q-num" data-testid="project-budget">{{ money(project.budgetUsd) }}</dd>
            <dt>{{ t('project.category') }}</dt>
            <dd>{{ catalog.categoryName(project.categoryCode) }}</dd>
            <dt>{{ t('project.dates') }}</dt>
            <dd>{{ date(project.startDate) }} – {{ date(project.endDate) }}</dd>
            <dt>{{ t('project.milestonesCount') }}</dt>
            <dd>{{ milestones.length }}</dd>
          </dl>
        </section>
      </div>

      <section class="q-card">
        <h2>{{ t('project.milestones') }}</h2>
        <div class="q-table-wrap">
          <el-table :data="milestones" data-testid="project-milestones" :empty-text="t('common.empty')">
            <el-table-column type="index" :label="'#'" width="50" />
            <el-table-column prop="title" :label="t('projectForm.milestoneTitle')" min-width="160" />
            <el-table-column prop="deliverable" :label="t('projectForm.deliverable')" min-width="220">
              <template #default="{ row }"><span class="q-pre">{{ row.deliverable }}</span></template>
            </el-table-column>
            <el-table-column :label="t('projectForm.dueDate')" width="130">
              <template #default="{ row }">{{ date(row.dueDate) }}</template>
            </el-table-column>
            <el-table-column :label="t('projectForm.amount')" width="130" align="right">
              <template #default="{ row }"><span class="q-num">{{ money(row.amountUsd) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section v-if="showApplications" class="q-card" data-testid="applications">
        <div class="q-card-head">
          <h2>{{ t('applications.title') }}</h2>
          <span class="q-muted q-small">{{ t('applications.count', { n: applications.length }) }}</span>
        </div>
        <p class="q-muted q-small">{{ t('applications.privacyNote') }}</p>
        <el-alert v-if="appsError" type="error" :closable="false" :title="appsError" show-icon />
        <el-empty v-if="!applications.length && !appsError" :description="t('applications.none')" :image-size="70" data-testid="applications-empty" />

        <article v-for="a in sortedApps" :key="a.id" class="app" :class="`app-${a.status.toLowerCase()}`" :data-testid="`application-card-${a.id}`">
          <div class="app-top">
            <EngineerCard :engineer="a.engineer" :testid-prefix="`application-${a.id}`" class="app-eng" />
            <div class="app-price">
              <StatusTag :status="a.status" :testid="`application-status-${a.id}`" size="small" />
              <div class="price q-num" :data-testid="`application-total-${a.id}`">{{ money(a.clientTotalUsd) }}</div>
              <div class="q-muted q-small">{{ t('applications.totalLabel') }}</div>
            </div>
          </div>

          <div v-if="a.coverNote" class="cover">
            <div class="q-muted q-small">{{ t('applications.coverNote') }}</div>
            <p class="q-pre">{{ a.coverNote }}</p>
          </div>

          <details v-if="a.prices?.length" class="prices">
            <summary :data-testid="`application-prices-toggle-${a.id}`">{{ t('applications.breakdown') }}</summary>
            <ul>
              <li v-for="p in a.prices" :key="p.milestoneId">
                <span>{{ milestoneTitle(p.milestoneId) }}</span>
                <span class="q-num">{{ money(p.clientAmountUsd ?? p.talentAmountUsd) }}</span>
              </li>
            </ul>
          </details>

          <div class="app-actions">
            <el-button size="small" :data-testid="`application-cv-${a.id}`" @click="viewCv(a)">{{ t('applications.viewCv') }}</el-button>
            <span v-if="!a.cvAvailable" class="q-muted q-small cv-note">{{ t('applications.cvNotShared') }}</span>
            <div class="spacer" />
            <el-button
              v-if="canShortlist(a)"
              size="small"
              :loading="busy === `shortlist-${a.id}`"
              :data-testid="`application-shortlist-${a.id}`"
              @click="act(a, 'shortlist')"
            >
              {{ t('applications.shortlist') }}
            </el-button>
            <el-button
              v-if="canDecline(a)"
              size="small"
              type="danger"
              plain
              :loading="busy === `decline-${a.id}`"
              :data-testid="`application-decline-${a.id}`"
              @click="act(a, 'decline')"
            >
              {{ t('applications.decline') }}
            </el-button>
            <el-button
              v-if="canSelect(a)"
              size="small"
              type="primary"
              plain
              :loading="busy === `select-${a.id}`"
              :data-testid="`application-select-${a.id}`"
              @click="act(a, 'select')"
            >
              {{ t('applications.select') }}
            </el-button>
            <el-button
              v-if="canHire(a)"
              size="small"
              type="primary"
              :data-testid="`application-hire-${a.id}`"
              @click="hireTarget = a"
            >
              {{ t('applications.hire') }}
            </el-button>
          </div>
        </article>
      </section>
    </template>

    <el-dialog
      :model-value="!!hireTarget"
      :title="t('applications.hireTitle')"
      width="480px"
      append-to-body
      data-testid="hire-dialog"
      @update:model-value="(v: boolean) => { if (!v) hireTarget = null }"
    >
      <p v-if="hireTarget">
        {{ t('applications.hireText', { alias: hireTarget.engineer.alias, total: money(hireTarget.clientTotalUsd) }) }}
      </p>
      <p class="q-muted q-small">{{ t('applications.hireNote') }}</p>
      <template #footer>
        <el-button data-testid="hire-cancel" @click="hireTarget = null">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="!!busy && busy.startsWith('hire')" data-testid="hire-confirm" @click="confirmHire">
          {{ t('applications.hireConfirm') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cvOpen" :title="t('applications.cvTitle', { alias: cvFor?.engineer.alias ?? '' })" width="640px" append-to-body data-testid="cv-dialog">
      <div v-loading="cvLoading" class="cv-body">
        <el-alert
          v-if="cvConsentRequired"
          type="info"
          :closable="false"
          show-icon
          :title="t('errors.CV_CONSENT_REQUIRED')"
          :description="t('applications.cvConsentExplain')"
          data-testid="cv-consent-required"
        />
        <el-alert v-else-if="cvError" type="error" :closable="false" show-icon :title="cvError" />
        <template v-if="cv">
          <h3>{{ t('applications.cv') }}</h3>
          <p class="q-pre" data-testid="cv-text">{{ cv.cvText || '—' }}</p>
          <h3>{{ t('applications.education') }}</h3>
          <p class="q-pre" data-testid="cv-education">{{ cv.education || '—' }}</p>
          <h3>{{ t('applications.portfolio') }}</h3>
          <ul v-if="cv.portfolioLinks?.length" class="links" data-testid="cv-portfolio">
            <li v-for="l in cv.portfolioLinks" :key="l">
              <a v-if="safeLink(l)" :href="safeLink(l)" target="_blank" rel="noopener noreferrer nofollow">{{ l }}</a>
              <span v-else>{{ l }}</span>
            </li>
          </ul>
          <p v-else>—</p>
          <p class="q-muted q-small">{{ t('applications.cvPrivacy') }}</p>
        </template>
      </div>
      <template #footer>
        <el-button data-testid="cv-close" @click="cvOpen = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  color: var(--q-text-3);
}
.head-main {
  min-width: 0;
}
.head-main h1 {
  overflow-wrap: anywhere;
}
.detail-grid {
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 0 16px;
}
.flag-text {
  margin: 0 0 6px;
}
.app {
  border: 1px solid var(--q-border);
  border-radius: 10px;
  padding: 16px;
  margin-top: 12px;
}
.app-selected,
.app-hired {
  border-color: var(--el-color-primary-light-5);
  background: #fbfdff;
}
.app-declined,
.app-withdrawn {
  opacity: 0.7;
}
.app-top {
  display: flex;
  gap: 16px;
  justify-content: space-between;
}
.app-eng {
  flex: 1;
  min-width: 0;
}
.app-price {
  text-align: right;
  flex-shrink: 0;
}
.price {
  font-size: 1.25rem;
  font-weight: 700;
  margin-top: 8px;
}
.cover {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--q-border);
}
.cover p {
  margin: 4px 0 0;
}
.prices {
  margin-top: 8px;
}
.prices summary {
  cursor: pointer;
  color: var(--q-accent);
  font-size: 14px;
}
.prices ul {
  list-style: none;
  padding: 0;
  margin: 8px 0 0;
}
.prices li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
  border-bottom: 1px solid var(--q-border);
  font-size: 14px;
}
.app-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 14px;
}
.app-actions .el-button + .el-button {
  margin-left: 0;
}
.spacer {
  flex: 1;
}
.cv-body {
  min-height: 80px;
}
.links {
  padding-left: 1.2em;
  overflow-wrap: anywhere;
}
@media (max-width: 760px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .app-top {
    flex-direction: column;
  }
  .app-price {
    text-align: left;
    display: flex;
    align-items: baseline;
    gap: 10px;
    flex-wrap: wrap;
  }
  .price {
    margin-top: 0;
  }
}
</style>
