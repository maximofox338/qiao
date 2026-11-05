<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { clientApi } from '@/api/user'
import { isApiError } from '@/api/http'
import type { Company, Contract, Project } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { useCatalogStore } from '@/stores/catalog'
import { date, money } from '@/utils/format'
import { notifyError } from '@/utils/errors'

const { t } = useI18n()
const router = useRouter()
const user = useUserStore()
const catalog = useCatalogStore()

const company = ref<Company | null>(null)
const projects = ref<Project[]>([])
const contracts = ref<Contract[]>([])
const loading = ref(true)

const companyStatus = computed(() => company.value?.status ?? user.me?.companyStatus ?? null)

const banner = computed(() => {
  switch (companyStatus.value) {
    case 'PENDING_REVIEW':
      return { type: 'info' as const, title: t('dashboard.banner.PENDING_REVIEW.title'), text: t('dashboard.banner.PENDING_REVIEW.text'), cta: '' }
    case 'APPROVED':
      return { type: 'success' as const, title: t('dashboard.banner.APPROVED.title'), text: t('dashboard.banner.APPROVED.text'), cta: '' }
    case 'REJECTED':
      return { type: 'error' as const, title: t('dashboard.banner.REJECTED.title'), text: t('dashboard.banner.REJECTED.text'), cta: t('dashboard.banner.REJECTED.cta') }
    case 'BLOCKED':
      return { type: 'error' as const, title: t('dashboard.banner.BLOCKED.title'), text: t('dashboard.banner.BLOCKED.text'), cta: '' }
    default:
      return { type: 'warning' as const, title: t('dashboard.banner.DRAFT.title'), text: t('dashboard.banner.DRAFT.text'), cta: t('dashboard.banner.DRAFT.cta') }
  }
})

onMounted(async () => {
  loading.value = true
  const results = await Promise.allSettled([clientApi.company(), clientApi.projects(), clientApi.contracts(), catalog.loadCategories()])
  const [c, p, k] = results
  if (c.status === 'fulfilled') company.value = c.value
  else if (!isApiError(c.reason, 'NOT_FOUND')) notifyError(c.reason)
  if (p.status === 'fulfilled') projects.value = p.value
  else notifyError(p.reason)
  if (k.status === 'fulfilled') contracts.value = k.value
  loading.value = false
})

function openProject(p: Project) {
  void router.push(`/client/projects/${p.id}`)
}
</script>

<template>
  <div class="q-container q-page">
    <div class="q-page-head">
      <div>
        <h1 data-testid="dashboard-title">{{ t('dashboard.title') }}</h1>
        <p class="q-sub">{{ company?.name || user.me?.email }}</p>
      </div>
      <el-button type="primary" :icon="Plus" data-testid="dashboard-new-project" @click="router.push('/client/projects/new')">
        {{ t('dashboard.newProject') }}
      </el-button>
    </div>

    <el-skeleton v-if="loading" :rows="5" animated />
    <template v-else>
      <div class="banner q-card" :class="`banner-${banner.type}`" data-testid="company-status-banner">
        <div class="banner-body">
          <div class="banner-title">
            <span>{{ banner.title }}</span>
            <StatusTag :status="companyStatus ?? 'DRAFT'" testid="company-status" size="small" />
          </div>
          <p class="q-muted">{{ banner.text }}</p>
          <p v-if="company?.reviewNote && (companyStatus === 'REJECTED' || companyStatus === 'BLOCKED')" class="review-note" data-testid="company-review-note">
            <strong>{{ t('common.reviewNote') }}:</strong> {{ company.reviewNote }}
          </p>
        </div>
        <el-button v-if="banner.cta" type="primary" plain data-testid="dashboard-company-cta" @click="router.push('/client/company')">
          {{ banner.cta }}
        </el-button>
      </div>

      <section class="q-card">
        <div class="q-card-head">
          <h2>{{ t('dashboard.projects') }}</h2>
          <span class="q-muted q-small">{{ t('dashboard.projectsCount', { n: projects.length }) }}</span>
        </div>
        <el-empty v-if="!projects.length" :description="t('dashboard.noProjects')" :image-size="80" data-testid="dashboard-no-projects">
          <el-button type="primary" data-testid="dashboard-empty-new-project" @click="router.push('/client/projects/new')">
            {{ t('dashboard.newProject') }}
          </el-button>
        </el-empty>
        <ul v-else class="list" data-testid="project-list">
          <li v-for="p in projects" :key="p.id" class="row" :data-testid="`project-row-${p.id}`" @click="openProject(p)">
            <div class="row-main">
              <router-link :to="`/client/projects/${p.id}`" class="row-title" :data-testid="`project-link-${p.id}`" @click.stop>{{ p.title }}</router-link>
              <div class="q-muted q-small">
                {{ catalog.categoryName(p.categoryCode) }} · {{ date(p.startDate) }} – {{ date(p.endDate) }}
              </div>
            </div>
            <div class="row-side">
              <span class="q-num">{{ money(p.budgetUsd) }}</span>
              <StatusTag :status="p.status" :testid="`project-status-${p.id}`" size="small" />
            </div>
          </li>
        </ul>
      </section>

      <section v-if="contracts.length" class="q-card">
        <div class="q-card-head">
          <h2>{{ t('dashboard.contracts') }}</h2>
        </div>
        <ul class="list" data-testid="contract-list">
          <li v-for="c in contracts" :key="c.id" class="row" :data-testid="`contract-row-${c.id}`" @click="router.push(`/client/contracts/${c.id}`)">
            <div class="row-main">
              <router-link :to="`/client/contracts/${c.id}`" class="row-title" :data-testid="`contract-link-${c.id}`" @click.stop>
                {{ c.projectTitle || t('contract.number', { id: c.id }) }}
              </router-link>
              <div class="q-muted q-small">
                <span v-if="c.engineerAlias">{{ c.engineerAlias }} · </span>{{ t('contract.number', { id: c.id }) }}
              </div>
            </div>
            <div class="row-side">
              <span class="q-num">{{ money(c.clientTotalUsd) }}</span>
              <StatusTag :status="c.status" :testid="`contract-status-${c.id}`" size="small" />
            </div>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.banner {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  border-left: 4px solid var(--el-color-warning);
}
.banner-info {
  border-left-color: var(--q-accent);
}
.banner-success {
  border-left-color: var(--el-color-success);
}
.banner-error {
  border-left-color: var(--el-color-danger);
}
.banner-body {
  flex: 1;
  min-width: 240px;
}
.banner-body p {
  margin: 4px 0 0;
}
.banner-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-weight: 650;
  font-size: 1.05rem;
}
.review-note {
  color: var(--q-text-2);
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 8px;
  border-top: 1px solid var(--q-border);
  cursor: pointer;
  border-radius: 6px;
}
.row:first-child {
  border-top: 0;
}
.row:hover {
  background: var(--q-bg);
}
.row-main {
  min-width: 0;
}
.row-title {
  font-weight: 600;
  color: var(--q-text);
  overflow-wrap: anywhere;
}
.row-side {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
@media (max-width: 640px) {
  .row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
