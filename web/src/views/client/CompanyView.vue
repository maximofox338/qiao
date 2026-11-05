<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { clientApi, publicApi } from '@/api/user'
import { isApiError } from '@/api/http'
import type { Company, LegalText } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { countryOptions } from '@/utils/countries'
import { errorText, fieldErrorsOf, notifySuccess } from '@/utils/errors'
import { dateTime } from '@/utils/format'

const { t, locale } = useI18n()
const router = useRouter()
const user = useUserStore()

const SIZES = ['1-10', '11-50', '51-200', '201-1000', '1000+']
const sizeTestid = (s: string) => s.replace('+', 'plus')

const company = ref<Company | null>(null)
const form = reactive({ name: '', country: '', website: '', size: '' })
const errors = ref<Record<string, string>>({})
const declarations = ref<LegalText | null>(null)
const declLoading = ref(false)
const accepted = ref(false)
const loading = ref(true)
const saving = ref(false)
const submitting = ref(false)
const formError = ref('')

const countries = computed(() => {
  void locale.value
  return countryOptions()
})
const status = computed(() => company.value?.status ?? 'DRAFT')
const editable = computed(() => status.value === 'DRAFT' || status.value === 'REJECTED')

function fill(c: Company | null) {
  company.value = c
  form.name = c?.name ?? ''
  form.country = c?.country ?? ''
  form.website = c?.website ?? ''
  form.size = c?.size ?? ''
}

async function loadDeclarations() {
  declLoading.value = true
  try {
    declarations.value = await publicApi.legal('client-declarations', locale.value)
  } catch (e) {
    formError.value = errorText(e)
  } finally {
    declLoading.value = false
  }
}

onMounted(async () => {
  try {
    fill(await clientApi.company())
  } catch (e) {
    if (!isApiError(e, 'NOT_FOUND')) formError.value = errorText(e)
  } finally {
    loading.value = false
  }
  await loadDeclarations()
})

watch(locale, loadDeclarations)

function validate(): boolean {
  const e: Record<string, string> = {}
  if (!form.name.trim()) e.name = t('fieldErrors.REQUIRED')
  if (!form.country) e.country = t('fieldErrors.REQUIRED')
  if (form.website && !/^https?:\/\/[^\s.]+\.[^\s]+$/i.test(form.website.trim())) e.website = t('fieldErrors.URL')
  if (!form.size) e.size = t('fieldErrors.REQUIRED')
  errors.value = e
  return Object.keys(e).length === 0
}

async function save(silent = false): Promise<boolean> {
  formError.value = ''
  if (!validate()) return false
  saving.value = true
  try {
    const saved = await clientApi.saveCompany({
      name: form.name.trim(),
      country: form.country,
      website: form.website.trim(),
      size: form.size,
    })
    if (saved) fill(saved)
    if (!silent) notifySuccess(t('company.saved'))
    return true
  } catch (e) {
    errors.value = fieldErrorsOf(e)
    formError.value = errorText(e)
    return false
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (!accepted.value) {
    formError.value = t('company.acceptRequired')
    return
  }
  if (!declarations.value) return
  submitting.value = true
  try {
    if (!(await save(true))) return
    const res = await clientApi.acceptDeclarations(declarations.value.version)
    if (res && typeof res === 'object' && 'status' in res) fill(res)
    else fill(await clientApi.company())
    await user.loadMe(true).catch(() => null)
    notifySuccess(t('company.submitted'))
    void router.push('/client')
  } catch (e) {
    formError.value = errorText(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="q-container q-page company">
    <div class="q-page-head">
      <div>
        <h1 data-testid="company-title">{{ t('company.title') }}</h1>
        <p class="q-sub">{{ t('company.subtitle') }}</p>
      </div>
      <StatusTag :status="status" testid="company-status" />
    </div>

    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else>
      <el-alert
        v-if="!editable"
        :type="status === 'APPROVED' ? 'success' : status === 'BLOCKED' ? 'error' : 'info'"
        :closable="false"
        show-icon
        :title="t(`company.locked.${status}`)"
        data-testid="company-locked"
      />
      <el-alert
        v-if="company?.reviewNote && status === 'REJECTED'"
        type="error"
        :closable="false"
        show-icon
        :title="t('company.rejectedTitle')"
        :description="company.reviewNote"
        data-testid="company-review-note"
      />
      <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" data-testid="company-error" />

      <section class="q-card">
        <h2>{{ t('company.details') }}</h2>
        <el-form label-position="top" :disabled="!editable" class="q-grid q-grid-2" @submit.prevent="save()">
          <el-form-item :label="t('company.name')" :error="errors.name" required>
            <el-input v-model="form.name" maxlength="200" autocomplete="organization" data-testid="company-name" />
          </el-form-item>
          <el-form-item :label="t('company.country')" :error="errors.country" required>
            <el-select v-model="form.country" filterable :placeholder="t('company.countryPlaceholder')" data-testid="company-country">
              <el-option
                v-for="c in countries"
                :key="c.code"
                :value="c.code"
                :label="`${c.name} (${c.code})`"
                :data-testid="`company-country-option-${c.code}`"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('company.website')" :error="errors.website">
            <el-input v-model="form.website" maxlength="300" inputmode="url" :placeholder="t('company.websitePlaceholder')" data-testid="company-website" />
          </el-form-item>
          <el-form-item :label="t('company.size')" :error="errors.size" required>
            <el-select v-model="form.size" :placeholder="t('company.sizePlaceholder')" data-testid="company-size">
              <el-option
                v-for="s in SIZES"
                :key="s"
                :value="s"
                :label="t('company.sizeOption', { size: s })"
                :data-testid="`company-size-option-${sizeTestid(s)}`"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div v-if="editable" class="q-actions">
          <el-button :loading="saving" data-testid="company-save" @click="save()">{{ t('company.save') }}</el-button>
        </div>
      </section>

      <section class="q-card">
        <div class="q-card-head">
          <h2>{{ declarations?.title || t('company.declarationsTitle') }}</h2>
          <span v-if="declarations" class="q-muted q-small" data-testid="company-declarations-version">
            {{ t('legal.version', { version: declarations.version }) }}
          </span>
        </div>
        <p class="q-muted">{{ t('company.declarationsIntro') }}</p>
        <div v-loading="declLoading" class="q-doc-scroll">
          <MarkdownView v-if="declarations" :source="declarations.body" testid="company-declarations-text" />
        </div>

        <template v-if="editable">
          <el-checkbox v-model="accepted" class="accept" data-testid="company-accept-declarations">
            {{ t('company.acceptDeclarations') }}
          </el-checkbox>
          <div class="q-actions">
            <el-button
              type="primary"
              :disabled="!accepted || !declarations"
              :loading="submitting"
              data-testid="company-submit"
              @click="submit"
            >
              {{ t('company.submit') }}
            </el-button>
          </div>
        </template>
        <p v-else-if="company?.declarationsAt" class="q-muted q-small accepted-at" data-testid="company-declarations-accepted">
          {{ t('company.declarationsAccepted', { date: dateTime(company.declarationsAt), version: company.declarationsVersion ?? '' }) }}
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.company {
  max-width: 900px;
}
.q-grid-2 {
  column-gap: 16px;
  row-gap: 0;
}
.q-grid-2 :deep(.el-select) {
  width: 100%;
}
.accept {
  margin: 16px 0;
  height: auto;
  white-space: normal;
  align-items: flex-start;
}
.accept :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.5;
}
.accept :deep(.el-checkbox__input) {
  margin-top: 3px;
}
.accepted-at {
  margin-top: 12px;
}
</style>
