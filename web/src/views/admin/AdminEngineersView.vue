<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { adminApi } from '@/api/admin'
import type { AdminEngineer } from '@/api/types'
import { errorText, notifySuccess } from '@/utils/errors'
import { dateTime, money } from '@/utils/format'
import { usePaged } from '@/utils/usePaged'

const { t, te } = useI18n()
const STATUSES = ['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'DRAFT', '']
const status = ref('PENDING_REVIEW')
const list = usePaged<AdminEngineer>((p, s) => adminApi.engineers(status.value, p, s))

const open = ref(false)
const current = ref<AdminEngineer | null>(null)
const detailLoading = ref(false)
const partnerRef = ref('')
const note = ref('')
const busy = ref<'APPROVE' | 'REJECT' | null>(null)
const reviewError = ref('')

const canApprove = computed(() => partnerRef.value.trim().length > 0)
const level = (v?: string) => (v && te(`engineer.level.${v}`) ? t(`engineer.level.${v}`) : (v ?? '—'))

onMounted(() => list.load(1))
watch(status, () => list.load(1))

async function openEngineer(row: AdminEngineer) {
  current.value = row
  partnerRef.value = row.partnerRef ?? ''
  note.value = ''
  reviewError.value = ''
  open.value = true
  detailLoading.value = true
  try {
    const full = await adminApi.engineer(row.id)
    current.value = { ...row, ...full }
    partnerRef.value = full.partnerRef ?? partnerRef.value
  } catch (e) {
    reviewError.value = errorText(e)
  } finally {
    detailLoading.value = false
  }
}

async function review(decision: 'APPROVE' | 'REJECT') {
  if (!current.value) return
  reviewError.value = ''
  if (decision === 'APPROVE' && !canApprove.value) {
    reviewError.value = t('admin.engineers.partnerRefRequired')
    return
  }
  if (decision === 'REJECT' && !note.value.trim()) {
    reviewError.value = t('admin.noteRequired')
    return
  }
  busy.value = decision
  try {
    await adminApi.reviewEngineer(current.value.id, {
      decision,
      note: note.value.trim(),
      ...(partnerRef.value.trim() ? { partnerRef: partnerRef.value.trim() } : {}),
    })
    notifySuccess(t(decision === 'APPROVE' ? 'admin.engineers.approved' : 'admin.engineers.rejected'))
    open.value = false
    await list.load()
  } catch (e) {
    reviewError.value = errorText(e)
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-engineers-title">{{ t('admin.engineers.title') }}</h1>
        <p class="q-sub">{{ t('admin.engineers.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-engineers-refresh" @click="list.load()">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="q-card">
      <el-radio-group v-model="status" size="small" class="filters" data-testid="admin-engineers-status">
        <el-radio-button v-for="s in STATUSES" :key="s || 'all'" :value="s" :data-testid="`admin-engineers-status-${s || 'all'}`">
          {{ s ? t(`status.${s}`) : t('common.all') }}
        </el-radio-button>
      </el-radio-group>
      <el-alert v-if="list.error.value" type="error" :closable="false" :title="list.error.value" show-icon />
      <el-table v-loading="list.loading.value" :data="list.rows.value" :empty-text="t('common.empty')" data-testid="admin-engineers-table" @row-click="openEngineer">
        <el-table-column :label="t('engineer.alias')" width="130">
          <template #default="{ row }">
            <span class="mono" :data-testid="`admin-engineer-row-${row.id}`">{{ row.alias }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="headline" :label="t('engineer.headline')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="region" :label="t('engineer.region')" width="120" />
        <el-table-column :label="t('engineer.years')" width="90" align="right">
          <template #default="{ row }">{{ row.yearsExperience }}</template>
        </el-table-column>
        <el-table-column :label="t('engineer.rate')" width="110" align="right">
          <template #default="{ row }">{{ money(row.hourlyRateUsd) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="150">
          <template #default="{ row }"><StatusTag :status="row.status" size="small" :testid="`admin-engineer-status-${row.id}`" /></template>
        </el-table-column>
        <el-table-column width="110" align="right">
          <template #default="{ row }">
            <el-button size="small" :data-testid="`admin-engineer-open-${row.id}`" @click.stop="openEngineer(row as AdminEngineer)">{{ t('admin.review') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="list.total.value > list.size.value"
        class="pager"
        layout="prev, pager, next, total"
        :total="list.total.value"
        :page-size="list.size.value"
        :current-page="list.page.value"
        @current-change="list.load"
      />
    </div>

    <el-drawer v-model="open" :title="current?.alias ?? ''" size="min(640px, 100vw)" append-to-body data-testid="admin-engineer-drawer">
      <div v-if="current" v-loading="detailLoading" class="q-stack">
        <div class="q-actions">
          <StatusTag :status="current.status" testid="admin-engineer-status" />
          <span v-if="current.declarationNoRestrictions" class="q-small ok">{{ t('admin.engineers.declarationOk', { date: dateTime(current.declarationAt) }) }}</span>
          <span v-else class="q-small bad">{{ t('admin.engineers.declarationMissing') }}</span>
        </div>
        <el-alert type="warning" :closable="false" :title="t('admin.engineers.piiNotice')" show-icon />
        <dl class="q-kv">
          <dt>{{ t('admin.engineers.email') }}</dt>
          <dd data-testid="admin-engineer-email">{{ current.email || '—' }}</dd>
          <dt>{{ t('engineer.alias') }}</dt>
          <dd class="mono">{{ current.alias }}</dd>
          <dt>{{ t('engineer.headline') }}</dt>
          <dd>{{ current.headline }}</dd>
          <dt>{{ t('admin.engineers.bio') }}</dt>
          <dd class="q-pre">{{ current.bio || '—' }}</dd>
          <dt>{{ t('admin.engineers.skills') }}</dt>
          <dd>
            <div class="q-tags">
              <el-tag v-for="s in current.skills ?? []" :key="s" size="small" type="info">{{ s }}</el-tag>
            </div>
          </dd>
          <dt>{{ t('engineer.years') }}</dt>
          <dd>{{ t('engineer.yearsValue', { n: current.yearsExperience ?? 0 }) }}</dd>
          <dt>{{ t('engineer.region') }}</dt>
          <dd>{{ current.region || '—' }}</dd>
          <dt>{{ t('engineer.english') }}</dt>
          <dd>{{ level(current.englishLevel) }}</dd>
          <dt>{{ t('engineer.spanish') }}</dt>
          <dd>{{ level(current.spanishLevel) }}</dd>
          <dt>{{ t('engineer.rate') }}</dt>
          <dd>{{ t('engineer.rateValue', { rate: money(current.hourlyRateUsd) }) }}</dd>
          <dt>{{ t('engineer.availability') }}</dt>
          <dd>{{ t('engineer.availabilityValue', { n: current.availabilityHoursWeek ?? 0 }) }}</dd>
          <dt>{{ t('applications.cv') }}</dt>
          <dd class="q-pre" data-testid="admin-engineer-cv">{{ current.cvText || '—' }}</dd>
          <dt>{{ t('applications.education') }}</dt>
          <dd class="q-pre">{{ current.education || '—' }}</dd>
          <dt>{{ t('applications.portfolio') }}</dt>
          <dd>
            <div v-for="l in current.portfolioLinks ?? []" :key="l" class="link">{{ l }}</div>
            <span v-if="!current.portfolioLinks?.length">—</span>
          </dd>
          <dt>{{ t('admin.engineers.partnerRef') }}</dt>
          <dd class="mono">{{ current.partnerRef || '—' }}</dd>
          <dt>{{ t('common.reviewNote') }}</dt>
          <dd class="q-pre">{{ current.reviewNote || '—' }}</dd>
        </dl>

        <div v-if="current.status !== 'DRAFT'" class="review">
          <h3>{{ t('admin.review') }}</h3>
          <el-form label-position="top" @submit.prevent>
            <el-form-item :label="t('admin.engineers.partnerRef')" required>
              <el-input v-model="partnerRef" maxlength="64" :placeholder="t('admin.engineers.partnerRefPlaceholder')" data-testid="admin-engineer-partner-ref" />
            </el-form-item>
            <el-form-item :label="t('common.reviewNote')">
              <el-input v-model="note" type="textarea" :rows="3" maxlength="2000" :placeholder="t('admin.notePlaceholder')" data-testid="admin-engineer-note" />
            </el-form-item>
          </el-form>
          <p v-if="!canApprove" class="q-muted q-small" data-testid="admin-engineer-approve-hint">{{ t('admin.engineers.partnerRefRequired') }}</p>
          <el-alert v-if="reviewError" type="error" :closable="false" :title="reviewError" show-icon data-testid="admin-engineer-error" />
          <div class="q-actions">
            <el-button type="primary" :disabled="!canApprove" :loading="busy === 'APPROVE'" data-testid="admin-engineer-approve" @click="review('APPROVE')">
              {{ t('admin.approve') }}
            </el-button>
            <el-button type="danger" plain :loading="busy === 'REJECT'" data-testid="admin-engineer-reject" @click="review('REJECT')">
              {{ t('admin.reject') }}
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filters {
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 600;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.ok {
  color: #1e7a46;
}
.bad {
  color: var(--el-color-danger);
}
.link {
  overflow-wrap: anywhere;
}
.review {
  border-top: 1px solid var(--q-border);
  padding-top: 12px;
}
.review h3 {
  margin-top: 0;
}
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
