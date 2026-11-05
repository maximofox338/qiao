<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import type { AdminAccount, AdminRole } from '@/api/types'
import { normaliseRoles } from '@/stores/admin'
import { ElMessageBox } from 'element-plus'
import { useAdminStore } from '@/stores/admin'
import { errorText, fieldErrorsOf, notifyError, notifySuccess } from '@/utils/errors'
import { dateTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'

const { t } = useI18n()
const ROLES: AdminRole[] = ['SUPERADMIN', 'OPS', 'FINANCE', 'COMPLIANCE']

const rows = ref<AdminAccount[]>([])
const loading = ref(false)
const error = ref('')

const form = reactive({ email: '', password: '', roles: [] as AdminRole[] })
const errors = ref<Record<string, string>>({})
const creating = ref(false)
const createError = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await adminApi.admins()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const me = useAdminStore()
const resetting = ref<number | null>(null)

/** SUPERADMIN removes another admin's authenticator (lost phone); they can enrol again from their profile. */
async function resetTotp(row: AdminAccount) {
  try {
    await ElMessageBox.confirm(t('admin.admins.totpResetConfirm', { email: row.email }), t('admin.admins.totpReset'), {
      type: 'warning',
      confirmButtonText: t('admin.admins.totpReset'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  resetting.value = row.id
  try {
    await adminApi.resetTotp(row.id)
    notifySuccess(t('admin.admins.totpResetDone'))
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    resetting.value = null
  }
}

const changing = ref<number | null>(null)

/**
 * SUPERADMIN disables another admin (sessions end at once; the API refuses oneself and the last active SUPERADMIN)
 * or re-enables a disabled one (they sign in again).
 */
async function setActive(row: AdminAccount, active: boolean) {
  if (!active) {
    try {
      await ElMessageBox.confirm(t('admin.admins.disableConfirm', { email: row.email }), t('admin.admins.disable'), {
        type: 'warning',
        confirmButtonText: t('admin.admins.disable'),
        cancelButtonText: t('common.cancel'),
      })
    } catch {
      return
    }
  }
  changing.value = row.id
  try {
    if (active) await adminApi.enableAdmin(row.id)
    else await adminApi.disableAdmin(row.id)
    notifySuccess(active ? t('admin.admins.enabledDone') : t('admin.admins.disabledDone'))
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    changing.value = null
  }
}

async function create() {
  createError.value = ''
  const e: Record<string, string> = {}
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) e.email = t('fieldErrors.EMAIL')
  if (form.password.length < 12) e.password = t('admin.admins.passwordMin')
  if (!form.roles.length) e.roles = t('admin.admins.rolesRequired')
  errors.value = e
  if (Object.keys(e).length) return
  creating.value = true
  try {
    await adminApi.createAdmin({ email: form.email.trim().toLowerCase(), password: form.password, roles: form.roles })
    notifySuccess(t('admin.admins.created'))
    form.email = ''
    form.password = ''
    form.roles = []
    await load()
  } catch (err) {
    errors.value = fieldErrorsOf(err)
    createError.value = errorText(err)
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div>
    <div class="q-page-head">
      <div>
        <h1 data-testid="admin-admins-title">{{ t('admin.admins.title') }}</h1>
        <p class="q-sub">{{ t('admin.admins.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" data-testid="admin-admins-refresh" @click="load">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="q-grid admins-grid">
      <section class="q-card">
        <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon />
        <el-table v-loading="loading" :data="rows" :empty-text="t('common.empty')" data-testid="admin-admins-table">
          <el-table-column prop="email" :label="t('admin.login.email')" min-width="200">
            <template #default="{ row }">
              <span :data-testid="`admin-admins-row-${row.id}`">{{ row.email }}</span>
              <span v-if="row.id === me.admin?.id" class="q-muted q-small"> ({{ t('admin.admins.you') }})</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="130">
            <template #default="{ row }">
              <StatusTag :status="row.status ?? 'ACTIVE'" size="small" :testid="`admin-admins-status-${row.id}`" />
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.admins.roles')" min-width="200">
            <template #default="{ row }">
              <div class="q-tags">
                <el-tag v-for="r in normaliseRoles(row.roles)" :key="r" size="small" effect="plain">{{ t(`admin.roles.${r}`) }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.admins.totp')" width="190">
            <template #default="{ row }">
              <span :data-testid="`admin-admins-totp-${row.id}`" :data-status="row.totpEnabled ? 'ENABLED' : 'DISABLED'">
                {{ row.totpEnabled ? t('common.yes') : t('common.no') }}
              </span>
              <el-button
                v-if="row.totpEnabled && row.id !== me.admin?.id"
                size="small"
                text
                type="danger"
                :loading="resetting === row.id"
                :data-testid="`admin-admins-totp-reset-${row.id}`"
                @click="resetTotp(row as AdminAccount)"
              >
                {{ t('admin.admins.totpReset') }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.settlements.createdAt')" width="170">
            <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('admin.admins.actions')" width="140">
            <template #default="{ row }">
              <template v-if="row.id !== me.admin?.id">
                <el-button
                  v-if="row.status === 'DISABLED'"
                  size="small"
                  :loading="changing === row.id"
                  :data-testid="`admin-admins-enable-${row.id}`"
                  @click="setActive(row as AdminAccount, true)"
                >
                  {{ t('admin.admins.enable') }}
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="danger"
                  plain
                  :loading="changing === row.id"
                  :data-testid="`admin-admins-disable-${row.id}`"
                  @click="setActive(row as AdminAccount, false)"
                >
                  {{ t('admin.admins.disable') }}
                </el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="q-card">
        <h2>{{ t('admin.admins.create') }}</h2>
        <el-form label-position="top" @submit.prevent="create">
          <el-form-item :label="t('admin.login.email')" :error="errors.email">
            <el-input v-model="form.email" type="email" autocomplete="off" data-testid="admin-new-email" />
          </el-form-item>
          <el-form-item :label="t('admin.login.password')" :error="errors.password">
            <el-input v-model="form.password" type="password" show-password autocomplete="new-password" data-testid="admin-new-password" />
            <div class="q-muted q-small">{{ t('admin.admins.passwordMin') }}</div>
          </el-form-item>
          <el-form-item :label="t('admin.admins.roles')" :error="errors.roles">
            <el-checkbox-group v-model="form.roles" class="roles">
              <el-checkbox v-for="r in ROLES" :key="r" :value="r" :data-testid="`admin-new-role-${r}`">{{ t(`admin.roles.${r}`) }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-alert v-if="createError" type="error" :closable="false" :title="createError" show-icon data-testid="admin-new-error" />
          <el-button type="primary" :icon="Plus" native-type="submit" :loading="creating" data-testid="admin-new-submit">{{ t('admin.admins.createButton') }}</el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admins-grid {
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 0 16px;
  align-items: start;
}
.roles {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
@media (max-width: 1000px) {
  .admins-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
