<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import FlagList from '@/components/FlagList.vue'
import { adminApi } from '@/api/admin'
import type { Project, ProjectDeclarations } from '@/api/types'
import { useCatalogStore } from '@/stores/catalog'
import { flagCount, flagKeywords } from '@/utils/compliance'
import { errorText, notifySuccess } from '@/utils/errors'
import { highlightKeywords } from '@/utils/markdown'
import { date, money, sumAmounts } from '@/utils/format'
import { usePaged } from '@/utils/usePaged'

const { t } = useI18n()
const catalog = useCatalogStore()
const STATUSES = ['SUBMITTED', 'CHANGES_REQUESTED', 'OPEN', 'REJECTED', 'IN_CONTRACT', 'COMPLETED', 'DRAFT', '']
const status = ref('SUBMITTED')
const list = usePaged<Project>((p, s) => adminApi.projects(status.value, p, s))

const open = ref(false)
const current = ref<Project | null>(null)
const detailLoading = ref(false)
const note = ref('')
const busy = ref<string | null>(null)
const reviewError = ref('')

const DECL_KEYS: Array<keyof ProjectDeclarations> = ['noControlledTech', 'noBulkUsSensitiveData', 'noEmployment', 'noProhibitedCategory']

const keywords = computed(() => flagKeywords(current.value?.flags))
const milestones = computed(() => [...(current.value?.milestones ?? [])].sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0)))
const msSum = computed(() => sumAmounts(milestones.value.map((m) => m.amountUsd)))
const category = computed(() => catalog.categories.find((c) => c.code === current.value?.categoryCode))
const hl = (text?: string | null) => highlightKeywords(text, keywords.value)

onMounted(() => {
  void catalog.loadCategories().catch(() => [])
  void list.load(1)
})
watch(status, () => list.load(1))

async function openProject(row: Project) {
  current.value = row
  note.value = ''
  reviewError.value = ''
  open.value = true
  detailLoading.value = true
  try {
    current.value = { ...row, ...(await adminApi.project(row.id)) }
  } catch (e) {
    reviewError.value = errorText(e)
  } finally {
    detailLoading.value = false
  }
}

async function review(decision: 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES') {
  if (!current.value) return
  reviewError.value = ''
  if (decision !== 'APPROVE' && !note.value.trim()) {
    reviewError.value = t('admin.noteRequired')
    return
  }
  busy.value = decision
  try {
    await adminApi.reviewProject(current.value.id, { decision, note: note.value.trim() })
    notifySuccess(t(`admin.projects.done.${decision}`))
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
        <h1 data-testid="admin-projects-title">{{ t('admin.projects.title') }}</h1>
        <p class="q-sub">{{ t('admin.projects.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-projects-refresh" @click="list.load()">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="q-card">
      <el-radio-group v-model="status" size="small" class="filters" data-testid="admin-projects-status">
        <el-radio-button v-for="s in STATUSES" :key="s || 'all'" :value="s" :data-testid="`admin-projects-status-${s || 'all'}`">
          {{ s ? t(`status.${s}`) : t('common.all') }}
        </el-radio-button>
      </el-radio-group>
      <el-alert v-if="list.error.value" type="error" :closable="false" :title="list.error.value" show-icon />
      <el-table
        v-loading="list.loading.value"
        :data="list.rows.value"
        :empty-text="t('common.empty')"
        :row-class-name="({ row }: { row: Project }) => (flagCount(row.flags) ? 'flagged-row' : '')"
        data-testid="admin-projects-table"
        @row-click="openProject"
      >
        <el-table-column :label="t('projectForm.titleLabel')" min-width="220">
          <template #default="{ row }">
            <span class="cell-strong" :data-testid="`admin-project-row-${row.id}`">{{ row.title }}</span>
            <div v-if="row.companyName" class="q-muted q-small">{{ row.companyName }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.projects.flags')" width="110">
          <template #default="{ row }">
            <el-tag v-if="flagCount(row.flags)" type="danger" effect="dark" size="small" :data-testid="`admin-project-flags-${row.id}`">
              <el-icon class="flag-icon"><WarningFilled /></el-icon>{{ flagCount(row.flags) }}
            </el-tag>
            <span v-else class="q-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('projectForm.category')" min-width="160">
          <template #default="{ row }">{{ catalog.categoryName(row.categoryCode) }}</template>
        </el-table-column>
        <el-table-column :label="t('project.budget')" width="120" align="right">
          <template #default="{ row }">{{ money(row.budgetUsd) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="160">
          <template #default="{ row }"><StatusTag :status="row.status" size="small" :testid="`admin-project-status-${row.id}`" /></template>
        </el-table-column>
        <el-table-column width="110" align="right">
          <template #default="{ row }">
            <el-button size="small" :data-testid="`admin-project-open-${row.id}`" @click.stop="openProject(row as Project)">{{ t('admin.review') }}</el-button>
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

    <el-drawer v-model="open" :title="current?.title ?? ''" size="min(720px, 100vw)" append-to-body data-testid="admin-project-drawer">
      <div v-if="current" v-loading="detailLoading" class="q-stack">
        <div class="q-actions">
          <StatusTag :status="current.status" testid="admin-project-status" />
          <el-tag v-if="category && !category.allowed" type="danger" data-testid="admin-project-category-prohibited">{{ t('admin.projects.categoryProhibited') }}</el-tag>
        </div>

        <el-alert v-if="keywords.length" type="error" :closable="false" show-icon :title="t('admin.projects.flaggedTitle', { n: keywords.length })" data-testid="admin-project-flags">
          <template #default>
            <p class="flag-text">{{ t('admin.projects.flaggedText') }}</p>
            <FlagList :flags="current.flags" />
          </template>
        </el-alert>

        <dl class="q-kv">
          <dt>{{ t('admin.projects.company') }}</dt>
          <dd>{{ current.companyName || (current.companyId ? `#${current.companyId}` : '—') }}</dd>
          <dt>{{ t('projectForm.category') }}</dt>
          <dd>{{ catalog.categoryName(current.categoryCode) }} <span class="q-muted">({{ current.categoryCode }})</span></dd>
          <dt>{{ t('project.budget') }}</dt>
          <dd class="q-num">{{ money(current.budgetUsd) }}</dd>
          <dt>{{ t('project.dates') }}</dt>
          <dd>{{ date(current.startDate) }} – {{ date(current.endDate) }}</dd>
          <dt>{{ t('projectForm.skills') }}</dt>
          <dd>
            <div class="q-tags"><el-tag v-for="s in current.skills ?? []" :key="s" size="small" type="info">{{ s }}</el-tag></div>
          </dd>
        </dl>

        <div>
          <h3>{{ t('project.description') }}</h3>
          <!-- highlightKeywords escapes the text before adding <mark> -->
          <div class="q-pre desc" data-testid="admin-project-description" v-html="hl(current.description)" />
        </div>

        <div>
          <h3>{{ t('project.milestones') }}</h3>
          <ol class="ms">
            <li v-for="m in milestones" :key="m.id ?? m.title">
              <div class="ms-head">
                <strong v-html="hl(m.title)" />
                <span class="q-num">{{ money(m.amountUsd) }} · {{ date(m.dueDate) }}</span>
              </div>
              <div class="q-pre q-muted" v-html="hl(m.deliverable)" />
            </li>
          </ol>
          <p class="q-small" :class="msSum === Number(current.budgetUsd) ? 'ok' : 'bad'">
            {{ t('projectForm.sumLine', { sum: money(msSum), budget: money(current.budgetUsd) }) }}
          </p>
        </div>

        <div>
          <h3>{{ t('admin.projects.declarations') }}</h3>
          <ul class="decls">
            <li v-for="k in DECL_KEYS" :key="k" :class="current.declarations?.[k] ? 'ok' : 'bad'">
              {{ current.declarations?.[k] ? '✓' : '✗' }} {{ t(`projectForm.decl.${k}`) }}
            </li>
          </ul>
        </div>

        <div v-if="current.reviewNote" class="q-small">
          <strong>{{ t('common.reviewNote') }}:</strong> <span class="q-pre">{{ current.reviewNote }}</span>
        </div>

        <div v-if="['SUBMITTED', 'CHANGES_REQUESTED', 'OPEN'].includes(current.status)" class="review">
          <h3>{{ t('admin.review') }}</h3>
          <el-input v-model="note" type="textarea" :rows="3" maxlength="2000" :placeholder="t('admin.projects.notePlaceholder')" data-testid="admin-project-note" />
          <el-alert v-if="reviewError" type="error" :closable="false" :title="reviewError" show-icon data-testid="admin-project-error" />
          <div class="q-actions">
            <el-button type="primary" :loading="busy === 'APPROVE'" data-testid="admin-project-approve" @click="review('APPROVE')">{{ t('admin.approve') }}</el-button>
            <el-button type="warning" plain :loading="busy === 'REQUEST_CHANGES'" data-testid="admin-project-request-changes" @click="review('REQUEST_CHANGES')">
              {{ t('admin.projects.requestChanges') }}
            </el-button>
            <el-button type="danger" plain :loading="busy === 'REJECT'" data-testid="admin-project-reject" @click="review('REJECT')">{{ t('admin.reject') }}</el-button>
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
.flag-icon {
  margin-right: 3px;
  vertical-align: -2px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.flag-text {
  margin: 0 0 6px;
}
.desc {
  background: var(--q-bg);
  border-radius: 8px;
  padding: 10px 12px;
}
.ms {
  padding-left: 1.2em;
  margin: 0;
}
.ms li {
  padding: 6px 0;
  border-bottom: 1px solid var(--q-border);
}
.ms-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.decls {
  list-style: none;
  padding: 0;
  margin: 0;
}
.ok {
  color: #1e7a46;
}
.bad {
  color: var(--el-color-danger);
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
:deep(.flagged-row) {
  background: #fff8f7;
}
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
