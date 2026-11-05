<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import StatusTag from '@/components/StatusTag.vue'
import { adminApi } from '@/api/admin'
import type { AdminDashboard } from '@/api/types'
import { useAdminStore } from '@/stores/admin'
import { errorText } from '@/utils/errors'
import { money, num } from '@/utils/format'

const { t, te } = useI18n()
const router = useRouter()
const admin = useAdminStore()
const data = ref<AdminDashboard | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    data.value = await adminApi.dashboard()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
})

type GroupKey = 'companies' | 'engineers' | 'projects' | 'contracts'
const groups = computed(() => {
  const d = data.value
  if (!d) return []
  const def: Array<{ key: GroupKey; focus: string; route: string; roles?: Array<'OPS' | 'COMPLIANCE' | 'FINANCE'> }> = [
    { key: 'companies', focus: 'PENDING_REVIEW', route: 'admin-companies', roles: ['OPS', 'COMPLIANCE'] },
    { key: 'engineers', focus: 'PENDING_REVIEW', route: 'admin-engineers', roles: ['OPS'] },
    { key: 'projects', focus: 'SUBMITTED', route: 'admin-projects', roles: ['OPS'] },
    { key: 'contracts', focus: 'ACTIVE', route: '' },
  ]
  return def.map((g) => {
    const byStatus = d[g.key]?.byStatus ?? {}
    return {
      ...g,
      focusCount: byStatus[g.focus] ?? 0,
      total: Object.values(byStatus).reduce((a, b) => a + (Number(b) || 0), 0),
      entries: Object.entries(byStatus).filter(([, v]) => Number(v) > 0),
      canOpen: !!g.route && admin.hasAnyRole(g.roles),
    }
  })
})

const statusLabel = (s: string) => (te(`status.${s}`) ? t(`status.${s}`) : s)
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-dashboard-title">{{ t('admin.dashboard.title') }}</h1>
        <p class="q-sub">{{ t('admin.dashboard.subtitle') }}</p>
      </div>
    </div>
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" />
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="data">
      <div class="q-grid q-grid-4">
        <div v-for="g in groups" :key="g.key" class="q-card stat" :data-testid="`admin-dashboard-card-${g.key}`">
          <div class="stat-label">{{ t(`admin.dashboard.${g.key}`) }}</div>
          <div class="stat-value q-num">{{ num(g.focusCount) }}</div>
          <div class="q-muted q-small">{{ t(`admin.dashboard.${g.key}Focus`) }} · {{ t('admin.dashboard.total', { n: num(g.total) }) }}</div>
          <ul class="breakdown">
            <li v-for="[s, n] in g.entries" :key="s">
              <span>{{ statusLabel(s) }}</span>
              <span class="q-num">{{ num(Number(n)) }}</span>
            </li>
          </ul>
          <el-button v-if="g.canOpen" link type="primary" :data-testid="`admin-dashboard-open-${g.key}`" @click="router.push({ name: g.route })">
            {{ t('admin.dashboard.open') }}
          </el-button>
        </div>
      </div>
      <div class="q-grid q-grid-3">
        <div class="q-card stat" data-testid="admin-dashboard-card-gmv">
          <div class="stat-label">{{ t('admin.dashboard.gmv') }}</div>
          <div class="stat-value q-num">{{ money(data.gmvUsd) }}</div>
          <div class="q-muted q-small">{{ t('admin.dashboard.gmvHint') }}</div>
        </div>
        <div class="q-card stat" data-testid="admin-dashboard-card-fees">
          <div class="stat-label">{{ t('admin.dashboard.fees') }}</div>
          <div class="stat-value q-num">{{ money(data.feesUsd) }}</div>
          <div class="q-muted q-small">{{ t('admin.dashboard.feesHint') }}</div>
        </div>
        <div class="q-card stat" data-testid="admin-dashboard-card-transfers">
          <div class="stat-label">{{ t('admin.dashboard.transfers', { year: data.transfers?.year }) }}</div>
          <div class="stat-row">
            <span class="stat-value q-num">{{ num(data.transfers?.uniqueEngineers) }}</span>
            <StatusTag :status="data.transfers?.level" testid="admin-dashboard-transfer-level" />
          </div>
          <div class="q-muted q-small">{{ t('admin.dashboard.transfersHint') }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.stat-label {
  color: var(--q-text-3);
  font-weight: 600;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.stat-value {
  font-size: 1.8rem;
  font-weight: 700;
  line-height: 1.2;
}
.stat-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.breakdown {
  list-style: none;
  padding: 0;
  margin: 8px 0 4px;
  width: 100%;
  font-size: 13px;
}
.breakdown li {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
  color: var(--q-text-2);
}
</style>
