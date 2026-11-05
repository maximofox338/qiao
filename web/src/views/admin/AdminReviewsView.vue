<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import type { AdminReview } from '@/api/types'
import { dateTime } from '@/utils/format'
import { usePaged } from '@/utils/usePaged'

const { t } = useI18n()
const side = ref<'' | 'CLIENT' | 'ENGINEER'>('')
const list = usePaged<AdminReview>((p, s) => adminApi.reviews(side.value, p, s), 25)

onMounted(() => list.load(1))
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-reviews-title">{{ t('admin.reviews.title') }}</h1>
        <p class="q-sub">{{ t('admin.reviews.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-reviews-refresh" @click="list.load()">{{ t('common.refresh') }}</el-button>
    </div>
    <div class="q-card">
      <el-radio-group v-model="side" class="filter" data-testid="admin-reviews-side" @change="list.load(1)">
        <el-radio-button value="" data-testid="admin-reviews-side-all">{{ t('admin.reviews.all') }}</el-radio-button>
        <el-radio-button value="CLIENT" data-testid="admin-reviews-side-CLIENT">{{ t('admin.reviews.fromClients') }}</el-radio-button>
        <el-radio-button value="ENGINEER" data-testid="admin-reviews-side-ENGINEER">{{ t('admin.reviews.fromEngineers') }}</el-radio-button>
      </el-radio-group>
      <el-alert v-if="list.error.value" type="error" :closable="false" :title="list.error.value" show-icon />
      <div class="q-table-wrap">
        <el-table v-loading="list.loading.value" :data="list.rows.value" :empty-text="t('common.empty')" data-testid="admin-reviews-table">
          <el-table-column :label="t('admin.reviews.date')" width="170">
            <template #default="{ row }">
              <span :data-testid="`admin-review-row-${row.id}`">{{ dateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.reviews.project')" min-width="180">
            <template #default="{ row }">{{ row.projectTitle }} <span class="q-muted">#{{ row.contractId }}</span></template>
          </el-table-column>
          <el-table-column :label="t('admin.reviews.from')" min-width="160">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.fromSide === 'CLIENT' ? 'primary' : 'success'">
                {{ t(`admin.reviews.side.${row.fromSide}`) }}
              </el-tag>
              {{ row.fromLabel }}
            </template>
          </el-table-column>
          <el-table-column prop="toLabel" :label="t('admin.reviews.to')" min-width="160" />
          <el-table-column :label="t('admin.reviews.stars')" width="150">
            <template #default="{ row }">
              <el-rate :model-value="row.stars" disabled :data-testid="`admin-review-stars-${row.id}`" />
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.reviews.comment')" min-width="240">
            <template #default="{ row }"><span class="q-pre">{{ row.comment || '—' }}</span></template>
          </el-table-column>
        </el-table>
      </div>
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
  </div>
</template>

<style scoped>
.filter {
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
