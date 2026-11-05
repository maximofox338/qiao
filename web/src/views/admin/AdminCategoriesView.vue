<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import type { AdminCategory } from '@/api/types'
import { useCatalogStore } from '@/stores/catalog'
import { errorText, notifyError, notifySuccess } from '@/utils/errors'

const { t } = useI18n()
const catalog = useCatalogStore()

interface Row {
  code: string
  allowed: boolean
  nameEn: string
  nameEs: string
  nameZh: string
  sort: number
  dirty: boolean
}

const rows = ref<Row[]>([])
const loading = ref(false)
const error = ref('')
const busy = ref<string | null>(null)

function toRow(c: AdminCategory): Row {
  return {
    code: c.code,
    allowed: !!c.allowed,
    nameEn: c.nameEn ?? c.name?.en ?? '',
    nameEs: c.nameEs ?? c.name?.es ?? '',
    nameZh: c.nameZh ?? c.name?.zh ?? '',
    sort: c.sort ?? 0,
    dirty: false,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = (await adminApi.categories()).map(toRow).sort((a, b) => a.sort - b.sort || Number(b.allowed) - Number(a.allowed))
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function save(row: Row) {
  if (!row.nameEn.trim() || !row.nameEs.trim() || !row.nameZh.trim()) {
    notifyError(new Error(t('admin.categories.namesRequired')))
    return
  }
  busy.value = row.code
  try {
    await adminApi.updateCategory(row.code, { allowed: row.allowed, nameEn: row.nameEn.trim(), nameEs: row.nameEs.trim(), nameZh: row.nameZh.trim() })
    row.dirty = false
    notifySuccess(t('admin.categories.saved'))
    void catalog.loadCategories(true).catch(() => [])
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
        <h1 data-testid="admin-categories-title">{{ t('admin.categories.title') }}</h1>
        <p class="q-sub">{{ t('admin.categories.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-categories-refresh" @click="load">{{ t('common.refresh') }}</el-button>
    </div>
    <div class="q-card">
      <el-alert type="warning" :closable="false" show-icon :title="t('admin.categories.warning')" />
      <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon />
      <el-table v-loading="loading" :data="rows" :empty-text="t('common.empty')" data-testid="admin-categories-table" :row-class-name="({ row }: { row: Row }) => (row.allowed ? '' : 'prohibited-row')">
        <el-table-column :label="t('admin.categories.code')" width="170">
          <template #default="{ row }"><code :data-testid="`admin-category-row-${row.code}`">{{ row.code }}</code></template>
        </el-table-column>
        <el-table-column :label="t('admin.categories.allowed')" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.allowed" :data-testid="`admin-category-allowed-${row.code}`" @change="row.dirty = true" />
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.categories.nameEn')" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.nameEn" size="small" :data-testid="`admin-category-name-en-${row.code}`" @input="row.dirty = true" />
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.categories.nameEs')" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.nameEs" size="small" :data-testid="`admin-category-name-es-${row.code}`" @input="row.dirty = true" />
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.categories.nameZh')" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.nameZh" size="small" :data-testid="`admin-category-name-zh-${row.code}`" @input="row.dirty = true" />
          </template>
        </el-table-column>
        <el-table-column width="100" align="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.dirty ? 'primary' : 'default'"
              :disabled="!row.dirty"
              :loading="busy === row.code"
              :data-testid="`admin-category-save-${row.code}`"
              @click="save(row as Row)"
            >
              {{ t('common.save') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
:deep(.prohibited-row) {
  background: #fff8f7;
}
code {
  font-size: 12px;
}
</style>
