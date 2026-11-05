<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import { engineerApi } from '@/api/endpoints'
import { asList } from '@/api/client'
import type { Page, Project } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useMetaStore } from '@/stores/meta'
import { countryName, formatDate, formatUsd } from '@/utils/format'
import { errorMessage } from '@/utils/errors'

const PAGE_SIZE = 20
const { t } = useI18n()
const auth = useAuthStore()
const meta = useMetaStore()

const q = ref('')
const category = ref('')
const projects = ref<Project[]>([])
const page = ref(0)
const loading = ref(false)
const finished = ref(false)
const failed = ref(false)
const errorText = ref('')
const refreshing = ref(false)
let requestSeq = 0

const profileStatus = computed(() => auth.me?.engineerProfileStatus ?? 'NONE')
const showBanner = computed(() => !!auth.me && profileStatus.value !== 'APPROVED')
const categories = computed(() => meta.allowedCodes())

onMounted(() => {
  meta.loadCategories()
})

async function load() {
  const seq = ++requestSeq
  loading.value = true
  try {
    const res = await engineerApi.projects({
      q: q.value.trim() || undefined,
      category: category.value || undefined,
      page: page.value + 1,
      size: PAGE_SIZE,
    })
    if (seq !== requestSeq) return
    const records = asList(res)
    projects.value.push(...records)
    page.value += 1
    const total = Array.isArray(res) ? records.length : (res as Page<Project>).total
    finished.value = Array.isArray(res) || records.length < PAGE_SIZE || projects.value.length >= total
  } catch (e) {
    if (seq !== requestSeq) return
    failed.value = true
    errorText.value = errorMessage(e)
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      refreshing.value = false
    }
  }
}

function reset() {
  projects.value = []
  page.value = 0
  finished.value = false
  failed.value = false
  load()
}

function pickCategory(code: string) {
  if (category.value === code) return
  category.value = code
  reset()
}
</script>

<template>
  <div class="projects" data-testid="projects-page">
    <PageHeader :title="t('projects.title')" />
    <div class="projects__filters">
      <van-search
        v-model="q"
        v-testid="'projects-search'"
        shape="round"
        :placeholder="t('projects.searchPlaceholder')"
        class="projects__search"
        @search="reset"
        @clear="reset"
      />
      <div class="chips" role="tablist" data-testid="projects-categories">
        <button
          type="button"
          class="chip"
          :class="{ 'is-active': category === '' }"
          role="tab"
          :aria-selected="category === ''"
          data-testid="projects-category-all"
          @click="pickCategory('')"
        >
          {{ t('projects.all') }}
        </button>
        <button
          v-for="code in categories"
          :key="code"
          type="button"
          class="chip"
          :class="{ 'is-active': category === code }"
          role="tab"
          :aria-selected="category === code"
          :data-testid="`projects-category-${code}`"
          @click="pickCategory(code)"
        >
          {{ meta.categoryName(code) }}
        </button>
      </div>
    </div>

    <router-link v-if="showBanner" to="/profile" class="banner" data-testid="projects-profile-banner">
      <van-icon name="info-o" />
      <span class="banner__text">{{ t(`projects.banner.${profileStatus}`) }}</span>
      <span class="banner__action">{{ t('projects.bannerAction') }} <van-icon name="arrow" /></span>
    </router-link>

    <van-pull-refresh v-model="refreshing" @refresh="reset">
      <van-list
        v-model:loading="loading"
        v-model:error="failed"
        :finished="finished"
        :finished-text="projects.length ? t('common.noMore') : ''"
        :error-text="errorText || t('common.loadFailed')"
        :loading-text="t('common.loading')"
        class="projects__list"
        data-testid="projects-list"
        @load="load"
      >
        <router-link
          v-for="p in projects"
          :key="p.id"
          :to="`/projects/${p.id}`"
          class="project-card"
          :data-testid="`project-card-${p.id}`"
        >
          <div class="project-card__head">
            <h3 class="project-card__title">{{ p.title }}</h3>
            <span v-if="p.applied" class="project-card__applied" :data-testid="`project-applied-${p.id}`">
              {{ t('projects.applied') }}
            </span>
          </div>
          <div class="project-card__meta">
            <span class="q-chip q-chip--cat">{{ meta.categoryName(p.categoryCode) }}</span>
            <span class="project-card__country">
              <van-icon name="location-o" /> {{ t('projects.clientFrom', { country: countryName(p.clientCountry) }) }}
            </span>
          </div>
          <p class="project-card__desc">{{ p.description }}</p>
          <div v-if="p.skills?.length" class="q-tags project-card__skills">
            <span v-for="s in p.skills.slice(0, 5)" :key="s" class="q-chip">{{ s }}</span>
          </div>
          <div class="project-card__foot">
            <div>
              <div class="q-muted project-card__label">{{ t('projects.talentBudget') }}</div>
              <div class="q-amount q-amount--accent project-card__budget">
                {{ formatUsd(p.talentBudgetUsd, { compact: true }) }}
              </div>
            </div>
            <div class="project-card__right">
              <div>{{ t('projects.milestones', { n: p.milestones?.length ?? 0 }) }}</div>
              <div class="q-muted">{{ formatDate(p.startDate) }} – {{ formatDate(p.endDate) }}</div>
            </div>
          </div>
        </router-link>
        <van-empty
          v-if="finished && !projects.length && !failed"
          image="search"
          :description="t('projects.empty')"
          data-testid="projects-empty"
        />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.projects {
  min-height: 100vh;
}
.projects__filters {
  position: sticky;
  top: calc(46px + env(safe-area-inset-top));
  z-index: 5;
  background: #fff;
  padding-bottom: 8px;
  box-shadow: 0 1px 0 var(--q-border);
}
.projects__search {
  padding: 8px 12px 6px;
}
.chips {
  display: flex;
  gap: 8px;
  padding: 2px 12px 2px;
  overflow-x: auto;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
.chips::-webkit-scrollbar {
  display: none;
}
.chip {
  flex: none;
  height: 28px;
  padding: 0 12px;
  border: 0;
  border-radius: 14px;
  background: #f2f3f5;
  color: var(--q-text-2);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.chip.is-active {
  background: var(--q-primary);
  color: #fff;
  font-weight: 500;
}
.banner {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 12px 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--q-warning-soft);
  color: #a24d07;
  font-size: 13px;
}
.banner__text {
  flex: 1;
}
.banner__action {
  flex: none;
  font-weight: 600;
}
.projects__list {
  padding: 12px 12px 8px;
  min-height: 50vh;
}
.project-card {
  display: block;
  padding: 16px;
  margin-bottom: 12px;
  border-radius: var(--q-radius);
  background: #fff;
  color: inherit;
  transition: transform 0.1s;
}
.project-card:active {
  transform: scale(0.99);
  background: #fcfcfd;
}
.project-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.project-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--q-text);
}
.project-card__applied {
  flex: none;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--q-success-soft);
  color: var(--q-success);
  font-size: 11px;
  line-height: 20px;
}
.project-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--q-text-3);
}
.q-chip--cat {
  background: var(--q-primary-soft);
  color: var(--q-primary);
}
.project-card__country .van-icon {
  vertical-align: -1px;
}
.project-card__desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--q-text-2);
}
.project-card__skills {
  margin-top: 10px;
}
.project-card__foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f2f3f5;
}
.project-card__label {
  font-size: 12px;
}
.project-card__budget {
  font-size: 20px;
  line-height: 1.3;
}
.project-card__right {
  text-align: right;
  font-size: 12px;
  color: var(--q-text-2);
}
</style>
