<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { adminApi } from '@/api/admin'
import type { Dispute } from '@/api/types'
import { errorText, notifySuccess } from '@/utils/errors'
import { dateTime, money } from '@/utils/format'

const { t } = useI18n()
const rows = ref<Dispute[]>([])
const loading = ref(false)
const error = ref('')

const target = ref<Dispute | null>(null)
const resolution = ref<'ACCEPT' | 'REFUND' | ''>('')
const note = ref('')
const busy = ref(false)
const resolveError = ref('')

const mid = (d: Dispute) => d.milestoneId ?? d.id

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await adminApi.disputes()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openResolve(d: Dispute) {
  target.value = d
  resolution.value = ''
  note.value = ''
  resolveError.value = ''
}

async function resolve() {
  if (!target.value) return
  resolveError.value = ''
  if (!resolution.value) {
    resolveError.value = t('admin.disputes.chooseResolution')
    return
  }
  if (!note.value.trim()) {
    resolveError.value = t('admin.noteRequired')
    return
  }
  busy.value = true
  try {
    await adminApi.resolveDispute(mid(target.value), resolution.value, note.value.trim())
    notifySuccess(t('admin.disputes.resolved'))
    target.value = null
    await load()
  } catch (e) {
    resolveError.value = errorText(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-disputes-title">{{ t('admin.disputes.title') }}</h1>
        <p class="q-sub">{{ t('admin.disputes.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-disputes-refresh" @click="load">{{ t('common.refresh') }}</el-button>
    </div>
    <div class="q-card">
      <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon />
      <el-table v-loading="loading" :data="rows" :empty-text="t('admin.disputes.none')" data-testid="admin-disputes-table">
        <el-table-column :label="t('admin.disputes.milestone')" min-width="220">
          <template #default="{ row }">
            <div class="cell-strong" :data-testid="`admin-dispute-row-${mid(row as Dispute)}`">{{ row.title }}</div>
            <div class="q-muted q-small">{{ row.projectTitle }} · {{ t('contract.number', { id: row.contractId }) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.disputes.reason')" min-width="240">
          <template #default="{ row }"><span class="q-pre">{{ row.disputeReason }}</span></template>
        </el-table-column>
        <el-table-column :label="t('admin.disputes.amount')" width="130" align="right">
          <template #default="{ row }">{{ money(row.clientAmountUsd) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.updated')" width="160">
          <template #default="{ row }">{{ dateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="120">
          <template #default="{ row }"><StatusTag :status="row.status ?? 'DISPUTED'" size="small" /></template>
        </el-table-column>
        <el-table-column width="120" align="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :data-testid="`admin-dispute-resolve-${mid(row as Dispute)}`" @click="openResolve(row as Dispute)">
              {{ t('admin.disputes.resolve') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      :model-value="!!target"
      :title="t('admin.disputes.resolveTitle')"
      width="520px"
      append-to-body
      data-testid="admin-dispute-dialog"
      @update:model-value="(v: boolean) => { if (!v) target = null }"
    >
      <template v-if="target">
        <p><strong>{{ target.title }}</strong> · {{ money(target.clientAmountUsd) }}</p>
        <p class="q-muted q-pre">{{ target.disputeReason }}</p>
        <el-radio-group v-model="resolution" class="res">
          <el-radio value="ACCEPT" data-testid="admin-dispute-resolution-accept">{{ t('admin.disputes.accept') }}</el-radio>
          <el-radio value="REFUND" data-testid="admin-dispute-resolution-refund">{{ t('admin.disputes.refund') }}</el-radio>
        </el-radio-group>
        <el-input v-model="note" type="textarea" :rows="3" maxlength="2000" :placeholder="t('admin.notePlaceholder')" data-testid="admin-dispute-note" />
        <el-alert v-if="resolveError" type="error" :closable="false" :title="resolveError" show-icon class="err" data-testid="admin-dispute-error" />
      </template>
      <template #footer>
        <el-button data-testid="admin-dispute-cancel" @click="target = null">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="busy" data-testid="admin-dispute-submit" @click="resolve">{{ t('admin.disputes.resolve') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cell-strong {
  font-weight: 600;
}
.res {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 12px;
}
.err {
  margin-top: 12px;
}
</style>
