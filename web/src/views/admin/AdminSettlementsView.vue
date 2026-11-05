<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Refresh, Plus } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { adminApi } from '@/api/admin'
import { downloadFile } from '@/api/http'
import type { SettlementBatch } from '@/api/types'
import { errorText, notifyError, notifySuccess } from '@/utils/errors'
import { dateTime, money, sumAmounts } from '@/utils/format'

const { t } = useI18n()

function previousMonth(): string {
  const d = new Date()
  d.setUTCDate(1)
  d.setUTCMonth(d.getUTCMonth() - 1)
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

const period = ref(previousMonth())
const periodError = ref('')
const creating = ref(false)
const createError = ref('')

const batches = ref<SettlementBatch[]>([])
const loading = ref(false)
const error = ref('')
const busy = ref<string | null>(null)

const open = ref(false)
const current = ref<SettlementBatch | null>(null)
const detailLoading = ref(false)

const lines = computed(() => current.value?.lines ?? [])
const linesTotal = computed(() => sumAmounts(lines.value.map((l) => l.talentAmountUsd)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    batches.value = (await adminApi.settlements()).sort((a, b) => (a.period < b.period ? 1 : -1))
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function create() {
  periodError.value = ''
  createError.value = ''
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(period.value.trim())) {
    periodError.value = t('admin.settlements.periodInvalid')
    return
  }
  creating.value = true
  try {
    const batch = await adminApi.createSettlement(period.value.trim())
    notifySuccess(t('admin.settlements.created'))
    await load()
    if (batch?.id) await view(batch)
  } catch (e) {
    createError.value = errorText(e)
  } finally {
    creating.value = false
  }
}

async function view(b: SettlementBatch) {
  current.value = b
  open.value = true
  detailLoading.value = true
  try {
    current.value = { ...b, ...(await adminApi.settlement(b.id)) }
  } catch (e) {
    notifyError(e)
  } finally {
    detailLoading.value = false
  }
}

async function csv(b: SettlementBatch) {
  busy.value = `csv-${b.id}`
  try {
    await downloadFile(adminApi.settlementCsvUrl(b.id), `settlement-${b.period}-${b.id}.csv`)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

async function markExported(b: SettlementBatch) {
  busy.value = `exp-${b.id}`
  try {
    await adminApi.markExported(b.id)
    notifySuccess(t('admin.settlements.exported'))
    await load()
    if (current.value?.id === b.id) await view(b)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-settlements-title">{{ t('admin.settlements.title') }}</h1>
        <p class="q-sub">{{ t('admin.settlements.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-settlements-refresh" @click="load">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="q-card">
      <h3>{{ t('admin.settlements.create') }}</h3>
      <p class="q-muted q-small">{{ t('admin.settlements.createHint') }}</p>
      <el-form inline class="create" @submit.prevent="create">
        <el-form-item :label="t('admin.settlements.period')" :error="periodError">
          <el-input v-model="period" maxlength="7" :placeholder="t('admin.settlements.periodPlaceholder')" class="period" data-testid="admin-settlement-period" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Plus" native-type="submit" :loading="creating" data-testid="admin-settlement-create">
            {{ t('admin.settlements.createButton') }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="createError" type="warning" :closable="false" :title="createError" show-icon data-testid="admin-settlement-error" />
    </div>

    <div class="q-card">
      <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon />
      <el-table v-loading="loading" :data="batches" :empty-text="t('common.empty')" data-testid="admin-settlements-table">
        <el-table-column :label="t('admin.settlements.period')" width="120">
          <template #default="{ row }"><span class="cell-strong" :data-testid="`admin-settlement-row-${row.id}`">{{ row.period }}</span></template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="130">
          <template #default="{ row }"><StatusTag :status="row.status" size="small" :testid="`admin-settlement-status-${row.id}`" /></template>
        </el-table-column>
        <el-table-column :label="t('admin.settlements.lines')" width="90" align="right">
          <template #default="{ row }">{{ row.lineCount ?? row.lines?.length ?? '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.settlements.total')" width="140" align="right">
          <template #default="{ row }">{{ row.totalUsd !== undefined ? money(row.totalUsd) : '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.settlements.createdAt')" min-width="150">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.settlements.exportedAt')" min-width="150">
          <template #default="{ row }">{{ dateTime(row.exportedAt) }}</template>
        </el-table-column>
        <el-table-column min-width="300" align="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" :data-testid="`admin-settlement-view-${row.id}`" @click="view(row as SettlementBatch)">{{ t('admin.settlements.viewLines') }}</el-button>
              <el-button size="small" :icon="Download" :loading="busy === `csv-${row.id}`" :data-testid="`admin-settlement-csv-${row.id}`" @click="csv(row as SettlementBatch)">
                {{ t('admin.settlements.csv') }}
              </el-button>
              <el-button
                v-if="row.status === 'DRAFT'"
                size="small"
                type="primary"
                plain
                :loading="busy === `exp-${row.id}`"
                :data-testid="`admin-settlement-export-${row.id}`"
                @click="markExported(row as SettlementBatch)"
              >
                {{ t('admin.settlements.markExported') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="open" :title="t('admin.settlements.batchTitle', { period: current?.period ?? '' })" size="min(900px, 100vw)" append-to-body data-testid="admin-settlement-drawer">
      <div v-if="current" v-loading="detailLoading" class="q-stack">
        <div class="q-actions">
          <StatusTag :status="current.status" />
          <span class="q-muted q-small">{{ t('admin.settlements.summary', { n: lines.length, total: money(linesTotal) }) }}</span>
          <div class="spacer" />
          <el-button size="small" :icon="Download" :loading="busy === `csv-${current.id}`" data-testid="admin-settlement-drawer-csv" @click="csv(current)">
            {{ t('admin.settlements.csv') }}
          </el-button>
          <el-button
            v-if="current.status === 'DRAFT'"
            size="small"
            type="primary"
            :loading="busy === `exp-${current.id}`"
            data-testid="admin-settlement-drawer-export"
            @click="markExported(current)"
          >
            {{ t('admin.settlements.markExported') }}
          </el-button>
        </div>
        <el-table :data="lines" :empty-text="t('common.empty')" data-testid="admin-settlement-lines" size="small">
          <el-table-column :label="t('admin.settlements.lineId')" width="80">
            <template #default="{ row }">{{ row.lineId ?? row.id }}</template>
          </el-table-column>
          <el-table-column prop="alias" :label="t('engineer.alias')" width="110" />
          <el-table-column prop="partnerRef" :label="t('admin.engineers.partnerRef')" width="130" />
          <el-table-column prop="engineerEmail" :label="t('admin.engineers.email')" min-width="180" />
          <el-table-column prop="projectTitle" :label="t('admin.settlements.project')" min-width="160" />
          <el-table-column prop="contractMilestoneId" :label="t('admin.settlements.milestoneId')" width="110" />
          <el-table-column :label="t('admin.settlements.talentAmount')" width="130" align="right">
            <template #default="{ row }">{{ money(row.talentAmountUsd) }}</template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="110">
            <template #default="{ row }"><StatusTag :status="row.status" size="small" /></template>
          </el-table-column>
          <el-table-column :label="t('admin.settlements.netPaid')" width="140" align="right">
            <template #default="{ row }">{{ row.netPaidCny != null ? `¥${Number(row.netPaidCny).toFixed(2)}` : '—' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.create {
  display: flex;
  flex-wrap: wrap;
}
.period {
  width: 140px;
}
.cell-strong {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}
.row-actions .el-button + .el-button {
  margin-left: 0;
}
.spacer {
  flex: 1;
}
</style>
