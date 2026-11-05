<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { adminApi } from '@/api/admin'
import type { AdminCompany } from '@/api/types'
import { countryName } from '@/utils/countries'
import { errorText, notifySuccess } from '@/utils/errors'
import { dateTime } from '@/utils/format'
import { usePaged } from '@/utils/usePaged'

const { t } = useI18n()
const STATUSES = ['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'BLOCKED', 'DRAFT', '']
const status = ref('PENDING_REVIEW')
const list = usePaged<AdminCompany>((p, s) => adminApi.companies(status.value, p, s))

const open = ref(false)
const current = ref<AdminCompany | null>(null)
const detailLoading = ref(false)
const sanctions = ref<'CLEAR' | 'HIT' | ''>('')
const note = ref('')
const busy = ref<'APPROVE' | 'REJECT' | null>(null)
const reviewError = ref('')

const canReview = computed(() => !!current.value && current.value.status !== 'DRAFT')
const canApprove = computed(() => sanctions.value === 'CLEAR')
const members = computed(() => {
  const c = current.value
  if (!c) return []
  if (c.memberEmails?.length) return c.memberEmails
  return (c.members ?? []).map((m) => m.email ?? `#${m.userId}`)
})

onMounted(() => list.load(1))
watch(status, () => list.load(1))

async function openCompany(row: AdminCompany) {
  current.value = row
  sanctions.value = row.sanctionsStatus === 'CLEAR' || row.sanctionsStatus === 'HIT' ? row.sanctionsStatus : ''
  note.value = ''
  reviewError.value = ''
  open.value = true
  detailLoading.value = true
  try {
    current.value = { ...row, ...(await adminApi.company(row.id)) }
  } catch (e) {
    reviewError.value = errorText(e)
  } finally {
    detailLoading.value = false
  }
}

async function review(decision: 'APPROVE' | 'REJECT') {
  if (!current.value) return
  reviewError.value = ''
  if (!sanctions.value) {
    reviewError.value = t('admin.companies.sanctionsRequired')
    return
  }
  if (decision === 'APPROVE' && sanctions.value !== 'CLEAR') {
    reviewError.value = t('admin.companies.approveNeedsClear')
    return
  }
  if (decision === 'REJECT' && !note.value.trim()) {
    reviewError.value = t('admin.noteRequired')
    return
  }
  busy.value = decision
  try {
    await adminApi.reviewCompany(current.value.id, { decision, sanctionsStatus: sanctions.value, note: note.value.trim() })
    notifySuccess(t(decision === 'APPROVE' ? 'admin.companies.approved' : 'admin.companies.rejected'))
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
        <h1 data-testid="admin-companies-title">{{ t('admin.companies.title') }}</h1>
        <p class="q-sub">{{ t('admin.companies.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-companies-refresh" @click="list.load()">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="q-card">
      <el-radio-group v-model="status" size="small" class="filters" data-testid="admin-companies-status">
        <el-radio-button v-for="s in STATUSES" :key="s || 'all'" :value="s" :data-testid="`admin-companies-status-${s || 'all'}`">
          {{ s ? t(`status.${s}`) : t('common.all') }}
        </el-radio-button>
      </el-radio-group>
      <el-alert v-if="list.error.value" type="error" :closable="false" :title="list.error.value" show-icon />
      <el-table v-loading="list.loading.value" :data="list.rows.value" :empty-text="t('common.empty')" data-testid="admin-companies-table" @row-click="openCompany">
        <el-table-column prop="name" :label="t('company.name')" min-width="180">
          <template #default="{ row }">
            <span class="cell-strong" :data-testid="`admin-company-row-${row.id}`">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('company.country')" min-width="140">
          <template #default="{ row }">{{ countryName(row.country) }}</template>
        </el-table-column>
        <el-table-column prop="size" :label="t('company.size')" width="100" />
        <el-table-column :label="t('admin.companies.sanctions')" width="130">
          <template #default="{ row }"><StatusTag :status="row.sanctionsStatus ?? 'PENDING'" size="small" /></template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="150">
          <template #default="{ row }"><StatusTag :status="row.status" size="small" :testid="`admin-company-status-${row.id}`" /></template>
        </el-table-column>
        <el-table-column :label="t('common.updated')" min-width="150">
          <template #default="{ row }">{{ dateTime(row.declarationsAt || row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column width="110" align="right">
          <template #default="{ row }">
            <el-button size="small" :data-testid="`admin-company-open-${row.id}`" @click.stop="openCompany(row as AdminCompany)">{{ t('admin.review') }}</el-button>
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
        data-testid="admin-companies-pagination"
        @current-change="list.load"
      />
    </div>

    <el-drawer v-model="open" :title="current?.name ?? ''" size="min(560px, 100vw)" append-to-body data-testid="admin-company-drawer">
      <div v-if="current" v-loading="detailLoading" class="q-stack">
        <div class="q-actions">
          <StatusTag :status="current.status" testid="admin-company-status" />
          <StatusTag :status="current.sanctionsStatus ?? 'PENDING'" testid="admin-company-sanctions-status" />
        </div>
        <dl class="q-kv">
          <dt>{{ t('company.name') }}</dt>
          <dd data-testid="admin-company-name">{{ current.name }}</dd>
          <dt>{{ t('company.country') }}</dt>
          <dd>{{ countryName(current.country) }} ({{ current.country }})</dd>
          <dt>{{ t('company.website') }}</dt>
          <dd>{{ current.website || '—' }}</dd>
          <dt>{{ t('company.size') }}</dt>
          <dd>{{ current.size || '—' }}</dd>
          <dt>{{ t('admin.companies.members') }}</dt>
          <dd>{{ members.length ? members.join(', ') : '—' }}</dd>
          <dt>{{ t('admin.companies.declarations') }}</dt>
          <dd>
            <template v-if="current.declarationsAt">
              {{ t('admin.companies.declarationsValue', { version: current.declarationsVersion ?? '', date: dateTime(current.declarationsAt) }) }}
              <div v-if="current.declarationsHash" class="q-muted q-small hash">{{ current.declarationsHash }}</div>
            </template>
            <template v-else>{{ t('admin.companies.declarationsNone') }}</template>
          </dd>
          <dt>{{ t('admin.companies.sanctionsChecked') }}</dt>
          <dd>{{ dateTime(current.sanctionsCheckedAt) }}</dd>
          <dt>{{ t('common.reviewNote') }}</dt>
          <dd class="q-pre">{{ current.reviewNote || '—' }}</dd>
        </dl>

        <div v-if="canReview" class="review">
          <h3>{{ t('admin.review') }}</h3>
          <div class="q-small q-muted">{{ t('admin.companies.sanctionsLabel') }}</div>
          <el-radio-group v-model="sanctions" data-testid="admin-company-sanctions">
            <el-radio value="CLEAR" data-testid="admin-company-sanctions-clear">{{ t('status.CLEAR') }}</el-radio>
            <el-radio value="HIT" data-testid="admin-company-sanctions-hit">{{ t('status.HIT') }}</el-radio>
          </el-radio-group>
          <el-input
            v-model="note"
            type="textarea"
            :rows="3"
            maxlength="2000"
            :placeholder="t('admin.notePlaceholder')"
            data-testid="admin-company-note"
          />
          <p v-if="!canApprove" class="q-muted q-small" data-testid="admin-company-approve-hint">{{ t('admin.companies.approveNeedsClear') }}</p>
          <el-alert v-if="reviewError" type="error" :closable="false" :title="reviewError" show-icon data-testid="admin-company-error" />
          <div class="q-actions">
            <el-button type="primary" :disabled="!canApprove" :loading="busy === 'APPROVE'" data-testid="admin-company-approve" @click="review('APPROVE')">
              {{ t('admin.approve') }}
            </el-button>
            <el-button type="danger" plain :loading="busy === 'REJECT'" data-testid="admin-company-reject" @click="review('REJECT')">
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
.cell-strong {
  font-weight: 600;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.review {
  border-top: 1px solid var(--q-border);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.review h3 {
  margin: 0;
}
.hash {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  overflow-wrap: anywhere;
}
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
