<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { adminApi } from '@/api/admin'
import type { OutboxMail } from '@/api/types'
import { dateTime } from '@/utils/format'
import { usePaged } from '@/utils/usePaged'

const { t } = useI18n()
const list = usePaged<OutboxMail>((p, s) => adminApi.outbox(p, s), 25)
const viewing = ref<OutboxMail | null>(null)

onMounted(() => list.load(1))
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-outbox-title">{{ t('admin.outbox.title') }}</h1>
        <p class="q-sub">{{ t('admin.outbox.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-outbox-refresh" @click="list.load()">{{ t('common.refresh') }}</el-button>
    </div>
    <div class="q-card">
      <el-alert v-if="list.error.value" type="error" :closable="false" :title="list.error.value" show-icon />
      <el-table v-loading="list.loading.value" :data="list.rows.value" :empty-text="t('common.empty')" data-testid="admin-outbox-table" @row-click="(r: OutboxMail) => (viewing = r)">
        <el-table-column :label="t('admin.outbox.date')" width="170">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="toEmail" :label="t('admin.outbox.to')" min-width="200">
          <template #default="{ row }"><span :data-testid="`admin-outbox-row-${row.id}`">{{ row.toEmail }}</span></template>
        </el-table-column>
        <el-table-column prop="subject" :label="t('admin.outbox.subject')" min-width="240" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" size="small" /></template>
        </el-table-column>
        <el-table-column width="100" align="right">
          <template #default="{ row }">
            <el-button size="small" :data-testid="`admin-outbox-view-${row.id}`" @click.stop="viewing = row as OutboxMail">{{ t('common.view') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="prev, pager, next, total"
        :total="list.total.value"
        :page-size="list.size.value"
        :current-page="list.page.value"
        hide-on-single-page
        @current-change="list.load"
      />
    </div>

    <el-dialog
      :model-value="!!viewing"
      :title="viewing?.subject ?? ''"
      width="640px"
      append-to-body
      data-testid="admin-outbox-dialog"
      @update:model-value="(v: boolean) => { if (!v) viewing = null }"
    >
      <template v-if="viewing">
        <p class="q-muted q-small">{{ t('admin.outbox.to') }}: {{ viewing.toEmail }} · {{ dateTime(viewing.createdAt) }}</p>
        <pre class="body" data-testid="admin-outbox-body">{{ viewing.body }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.body {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--q-bg);
  border-radius: 8px;
  padding: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  max-height: 60vh;
  overflow: auto;
}
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
