<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { adminApi } from '@/api/admin'
import type { AuditEntry, TransferSummary } from '@/api/types'
import { errorText } from '@/utils/errors'
import { dateTime, num } from '@/utils/format'
import { usePaged } from '@/utils/usePaged'

const { t } = useI18n()

const thisYear = new Date().getUTCFullYear()
const years = Array.from({ length: 4 }, (_, i) => thisYear - i)
const year = ref(thisYear)
const summary = ref<TransferSummary | null>(null)
const summaryLoading = ref(false)
const summaryError = ref('')

const filters = reactive({ action: '', actorType: '' })
const applied = reactive({ action: '', actorType: '' })
const audit = usePaged<AuditEntry>((p, s) => adminApi.audit({ action: applied.action || undefined, actorType: applied.actorType || undefined }, p, s), 25)
const ACTOR_TYPES = ['USER', 'ADMIN', 'PARTNER', 'SYSTEM']

const any = computed(() => summary.value?.uniqueEngineers?.ANY ?? 0)
const limit = computed(() => summary.value?.thresholds?.limit ?? 100000)
const pct = computed(() => Math.min(100, Math.round((any.value / (limit.value || 1)) * 1000) / 10))
const barColor = computed(() => {
  switch (summary.value?.level) {
    case 'LIMIT':
    case 'CRITICAL':
      return '#c0392b'
    case 'WARN':
      return '#d48806'
    default:
      return '#1f5f8b'
  }
})
const marks = computed(() => {
  const th = summary.value?.thresholds
  if (!th) return []
  return [
    { key: 'warn', value: th.warn, left: (th.warn / (th.limit || 1)) * 100 },
    { key: 'critical', value: th.critical, left: (th.critical / (th.limit || 1)) * 100 },
  ]
})

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await adminApi.transfers(year.value)
  } catch (e) {
    summaryError.value = errorText(e)
    summary.value = null
  } finally {
    summaryLoading.value = false
  }
}

function search() {
  applied.action = filters.action.trim()
  applied.actorType = filters.actorType
  void audit.load(1)
}

function detail(d: unknown): string {
  if (d === null || d === undefined || d === '') return ''
  if (typeof d === 'string') return d
  try {
    return JSON.stringify(d)
  } catch {
    return String(d)
  }
}

onMounted(() => {
  void loadSummary()
  void audit.load(1)
})
watch(year, loadSummary)
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-compliance-title">{{ t('admin.compliance.title') }}</h1>
        <p class="q-sub">{{ t('admin.compliance.subtitle') }}</p>
      </div>
    </div>

    <section class="q-card" data-testid="admin-transfers">
      <div class="q-card-head">
        <h2>{{ t('admin.compliance.transfers') }}</h2>
        <el-select v-model="year" class="year" data-testid="admin-transfers-year">
          <el-option v-for="y in years" :key="y" :value="y" :label="String(y)" :data-testid="`admin-transfers-year-option-${y}`" />
        </el-select>
      </div>
      <p class="q-muted q-small">{{ t('admin.compliance.transfersHint') }}</p>
      <el-alert v-if="summaryError" type="error" :closable="false" :title="summaryError" show-icon />
      <div v-loading="summaryLoading">
        <template v-if="summary">
          <div class="counter">
            <div>
              <div class="big q-num" data-testid="admin-transfers-any">{{ num(any) }}</div>
              <div class="q-muted q-small">{{ t('admin.compliance.uniqueAny', { limit: num(limit) }) }}</div>
            </div>
            <StatusTag :status="summary.level" testid="admin-transfers-level" size="large" />
          </div>
          <div class="bar" role="progressbar" :aria-valuenow="pct" aria-valuemin="0" aria-valuemax="100">
            <div class="bar-fill" :style="{ width: `${pct}%`, background: barColor }" />
            <div v-for="m in marks" :key="m.key" class="bar-mark" :style="{ left: `${m.left}%` }" :title="t(`admin.compliance.threshold.${m.key}`)" />
          </div>
          <div class="thresholds q-small">
            <span>{{ t('admin.compliance.threshold.warn') }}: <strong class="q-num">{{ num(summary.thresholds.warn) }}</strong></span>
            <span>{{ t('admin.compliance.threshold.critical') }}: <strong class="q-num">{{ num(summary.thresholds.critical) }}</strong></span>
            <span>{{ t('admin.compliance.threshold.limit') }}: <strong class="q-num">{{ num(summary.thresholds.limit) }}</strong></span>
          </div>
          <div class="q-grid q-grid-3 kinds">
            <div class="kind">
              <div class="q-muted q-small">{{ t('admin.compliance.kind.PSEUDONYMIZED_PROFILE') }}</div>
              <div class="mid q-num" data-testid="admin-transfers-pseudonymized">{{ num(summary.uniqueEngineers.PSEUDONYMIZED_PROFILE) }}</div>
            </div>
            <div class="kind">
              <div class="q-muted q-small">{{ t('admin.compliance.kind.FULL_CV') }}</div>
              <div class="mid q-num" data-testid="admin-transfers-full-cv">{{ num(summary.uniqueEngineers.FULL_CV) }}</div>
            </div>
            <div class="kind">
              <div class="q-muted q-small">{{ t('admin.compliance.kind.ANY') }}</div>
              <div class="mid q-num">{{ num(summary.uniqueEngineers.ANY) }}</div>
            </div>
          </div>
        </template>
      </div>
    </section>

    <section class="q-card">
      <h2>{{ t('admin.compliance.audit') }}</h2>
      <el-form inline class="filters" @submit.prevent="search">
        <el-form-item :label="t('admin.compliance.action')">
          <el-input v-model="filters.action" clearable :placeholder="t('admin.compliance.actionPlaceholder')" data-testid="admin-audit-action" />
        </el-form-item>
        <el-form-item :label="t('admin.compliance.actorType')">
          <el-select v-model="filters.actorType" clearable :placeholder="t('common.all')" class="actor" data-testid="admin-audit-actor-type">
            <el-option v-for="a in ACTOR_TYPES" :key="a" :value="a" :label="t(`admin.compliance.actor.${a}`)" :data-testid="`admin-audit-actor-type-option-${a}`" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" native-type="submit" data-testid="admin-audit-search">{{ t('common.search') }}</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="audit.error.value" type="error" :closable="false" :title="audit.error.value" show-icon />
      <el-table v-loading="audit.loading.value" :data="audit.rows.value" :empty-text="t('common.empty')" size="small" data-testid="admin-audit-table">
        <el-table-column :label="t('admin.compliance.when')" width="170">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.compliance.actorLabel')" width="140">
          <template #default="{ row }">{{ t(`admin.compliance.actor.${row.actorType}`) }}<span v-if="row.actorId" class="q-muted"> #{{ row.actorId }}</span></template>
        </el-table-column>
        <el-table-column prop="action" :label="t('admin.compliance.action')" min-width="170">
          <template #default="{ row }"><code class="action">{{ row.action }}</code></template>
        </el-table-column>
        <el-table-column :label="t('admin.compliance.entity')" width="160">
          <template #default="{ row }">{{ row.entityType || '—' }}<span v-if="row.entityId" class="q-muted"> #{{ row.entityId }}</span></template>
        </el-table-column>
        <el-table-column :label="t('admin.compliance.detail')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><span class="detail">{{ detail(row.detail) }}</span></template>
        </el-table-column>
        <el-table-column prop="ip" :label="t('admin.compliance.ip')" width="130" />
      </el-table>
      <el-pagination
        class="pager"
        layout="prev, pager, next, total"
        :total="audit.total.value"
        :page-size="audit.size.value"
        :current-page="audit.page.value"
        hide-on-single-page
        data-testid="admin-audit-pagination"
        @current-change="audit.load"
      />
    </section>
  </div>
</template>

<style scoped>
.year {
  width: 120px;
}
.counter {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 8px 0 12px;
}
.big {
  font-size: 2.2rem;
  font-weight: 700;
  line-height: 1.1;
}
.bar {
  position: relative;
  height: 10px;
  border-radius: 999px;
  background: #eef1f4;
  overflow: visible;
}
.bar-fill {
  height: 100%;
  border-radius: 999px;
  min-width: 2px;
  transition: width 0.3s;
}
.bar-mark {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 2px;
  background: var(--q-text-3);
  opacity: 0.6;
}
.thresholds {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 10px;
  color: var(--q-text-2);
}
.kinds {
  margin-top: 16px;
}
.kind {
  background: var(--q-bg);
  border-radius: 8px;
  padding: 10px 12px;
}
.mid {
  font-size: 1.3rem;
  font-weight: 650;
}
.filters {
  display: flex;
  flex-wrap: wrap;
}
.actor {
  width: 160px;
}
.action {
  font-size: 12px;
  background: #f1f3f5;
  padding: 1px 5px;
  border-radius: 4px;
}
.detail {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
