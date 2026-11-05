<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Delete, Plus, CircleCheck, WarningFilled } from '@element-plus/icons-vue'
import MoneyInput from '@/components/MoneyInput.vue'
import SkillsInput from '@/components/SkillsInput.vue'
import StatusTag from '@/components/StatusTag.vue'
import { clientApi } from '@/api/user'
import { ApiError, isApiError } from '@/api/http'
import type { Project, ProjectDeclarations, ProjectInput, ProjectStatus } from '@/api/types'
import { useCatalogStore } from '@/stores/catalog'
import { detectKeywords } from '@/utils/compliance'
import { errorText, fieldErrorsOf, notifySuccess } from '@/utils/errors'
import { exceedsTwelveMonths, money, sumAmounts } from '@/utils/format'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()

interface MilestoneRow {
  key: number
  title: string
  deliverable: string
  dueDate: string
  amountUsd: number | null
}

const DECLARATION_KEYS: Array<keyof ProjectDeclarations> = ['noControlledTech', 'noBulkUsSensitiveData', 'noEmployment', 'noProhibitedCategory']
const declTestid = (k: string) => `project-decl-${k.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`

const SKILL_SUGGESTIONS = ['Vue', 'React', 'TypeScript', 'Java', 'Spring Boot', 'Python', 'Go', 'Flutter', 'Kotlin', 'Swift', 'PostgreSQL', 'AWS', 'Figma']

let keySeq = 0
const newRow = (): MilestoneRow => ({ key: ++keySeq, title: '', deliverable: '', dueDate: '', amountUsd: null })

const isEdit = computed(() => route.name === 'project-edit')
const projectId = computed(() => (isEdit.value ? String(route.params.id) : null))

const form = reactive({
  title: '',
  description: '',
  categoryCode: '',
  skills: [] as string[],
  budgetUsd: null as number | null,
  startDate: '',
  endDate: '',
  milestones: [newRow()] as MilestoneRow[],
  declarations: { noControlledTech: false, noBulkUsSensitiveData: false, noEmployment: false, noProhibitedCategory: false } as ProjectDeclarations,
})

const errors = ref<Record<string, string>>({})
const formError = ref('')
const formErrorCode = ref('')
const loading = ref(false)
const saving = ref<'draft' | 'submit' | null>(null)
const existing = ref<Project | null>(null)

const editableStatuses: ProjectStatus[] = ['DRAFT', 'CHANGES_REQUESTED']
const readOnly = computed(() => !!existing.value && !editableStatuses.includes(existing.value.status))

const allowedCategories = computed(() => catalog.categories.filter((c) => c.allowed))
const prohibitedCategories = computed(() => catalog.categories.filter((c) => !c.allowed))
const catName = (c: { name: { en: string; es: string } ; code: string }) => (locale.value === 'es' ? c.name?.es : c.name?.en) || c.name?.en || c.code

const milestoneSum = computed(() => sumAmounts(form.milestones.map((m) => m.amountUsd)))
const sumDiff = computed(() => Math.round(((form.budgetUsd ?? 0) - milestoneSum.value) * 100) / 100)
const sumMatches = computed(() => form.budgetUsd !== null && form.budgetUsd > 0 && sumDiff.value === 0)

const keywordHits = computed(() => {
  const text = [form.title, form.description, ...form.skills, ...form.milestones.flatMap((m) => [m.title, m.deliverable])].join('\n')
  return detectKeywords(text)
})

onMounted(async () => {
  loading.value = true
  try {
    await catalog.loadCategories()
    if (projectId.value) {
      const p = await clientApi.project(projectId.value)
      existing.value = p
      form.title = p.title ?? ''
      form.description = p.description ?? ''
      form.categoryCode = p.categoryCode ?? ''
      form.skills = [...(p.skills ?? [])]
      form.budgetUsd = p.budgetUsd ?? null
      form.startDate = p.startDate ?? ''
      form.endDate = p.endDate ?? ''
      form.milestones = (p.milestones?.length ? [...p.milestones].sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0)) : [{}]).map((m) => ({
        key: ++keySeq,
        title: (m as MilestoneRow).title ?? '',
        deliverable: (m as MilestoneRow).deliverable ?? '',
        dueDate: (m as MilestoneRow).dueDate ?? '',
        amountUsd: (m as MilestoneRow).amountUsd ?? null,
      }))
      if (p.declarations) form.declarations = { ...form.declarations, ...p.declarations }
    }
  } catch (e) {
    formError.value = errorText(e)
  } finally {
    loading.value = false
  }
})

function addMilestone() {
  form.milestones.push(newRow())
}

function removeMilestone(i: number) {
  form.milestones.splice(i, 1)
  // Field errors are index based; clear milestone errors after re-ordering.
  errors.value = Object.fromEntries(Object.entries(errors.value).filter(([k]) => !k.startsWith('milestones.')))
}

function budgetFromMilestones() {
  form.budgetUsd = milestoneSum.value
}

function validate(full: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  const req = t('fieldErrors.REQUIRED')
  if (!form.title.trim()) e.title = req
  if (!full) return e
  if (!form.description.trim()) e.description = req
  if (!form.categoryCode) e.categoryCode = req
  else if (prohibitedCategories.value.some((c) => c.code === form.categoryCode)) e.categoryCode = t('errors.PROHIBITED_CATEGORY')
  if (!form.budgetUsd || form.budgetUsd <= 0) e.budgetUsd = t('fieldErrors.POSITIVE')
  if (!form.startDate) e.startDate = req
  if (!form.endDate) e.endDate = t('errors.END_DATE_REQUIRED')
  else if (form.startDate && form.endDate < form.startDate) e.endDate = t('fieldErrors.END_BEFORE_START')
  else if (exceedsTwelveMonths(form.startDate, form.endDate)) e.endDate = t('errors.DURATION_TOO_LONG')
  if (!form.milestones.length) e.milestones = t('errors.MILESTONES_REQUIRED')
  form.milestones.forEach((m, i) => {
    if (!m.title.trim()) e[`milestones.${i}.title`] = req
    if (!m.deliverable.trim()) e[`milestones.${i}.deliverable`] = req
    if (!m.dueDate) e[`milestones.${i}.dueDate`] = req
    else if ((form.startDate && m.dueDate < form.startDate) || (form.endDate && m.dueDate > form.endDate))
      e[`milestones.${i}.dueDate`] = t('fieldErrors.DUE_OUTSIDE_RANGE')
    if (!m.amountUsd || m.amountUsd <= 0) e[`milestones.${i}.amountUsd`] = t('fieldErrors.POSITIVE')
  })
  if (form.milestones.length && form.budgetUsd && !sumMatches.value) e.milestonesSum = t('errors.MILESTONE_SUM_MISMATCH')
  if (DECLARATION_KEYS.some((k) => !form.declarations[k])) e.declarations = t('errors.DECLARATIONS_REQUIRED')
  return e
}

/** Business error codes that belong to a specific field. */
const CODE_FIELD: Record<string, string> = {
  PROHIBITED_CATEGORY: 'categoryCode',
  MILESTONES_REQUIRED: 'milestones',
  MILESTONE_SUM_MISMATCH: 'milestonesSum',
  END_DATE_REQUIRED: 'endDate',
  DURATION_TOO_LONG: 'endDate',
  DECLARATIONS_REQUIRED: 'declarations',
}

function applyServerError(err: unknown) {
  const fe = fieldErrorsOf(err)
  // Fold nested declaration errors (declarations.noEmployment) into the group error as well.
  for (const k of Object.keys(fe)) if (k.startsWith('declarations.') && !fe.declarations) fe.declarations = fe[k]!
  if (err instanceof ApiError && CODE_FIELD[err.code]) fe[CODE_FIELD[err.code]!] = errorText(err)
  errors.value = fe
  formError.value = errorText(err)
  formErrorCode.value = err instanceof ApiError ? err.code : ''
}

function payload(): ProjectInput {
  return {
    title: form.title.trim(),
    description: form.description.trim(),
    categoryCode: form.categoryCode,
    skills: form.skills,
    budgetUsd: form.budgetUsd,
    startDate: form.startDate,
    endDate: form.endDate,
    milestones: form.milestones.map((m) => ({
      title: m.title.trim(),
      deliverable: m.deliverable.trim(),
      dueDate: m.dueDate,
      amountUsd: m.amountUsd,
    })),
    declarations: { ...form.declarations },
  }
}

async function save(submit: boolean) {
  formError.value = ''
  formErrorCode.value = ''
  errors.value = validate(submit)
  if (Object.keys(errors.value).length) {
    formError.value = t('projectForm.fixErrors')
    void scrollToFirstError()
    return
  }
  saving.value = submit ? 'submit' : 'draft'
  try {
    let saved: Project
    if (projectId.value) {
      saved = await clientApi.updateProject(projectId.value, payload())
      if (submit) saved = (await clientApi.submitProject(projectId.value)) ?? saved
    } else {
      saved = await clientApi.createProject({ ...payload(), submit })
    }
    notifySuccess(submit ? t('projectForm.submitted') : t('projectForm.saved'))
    const id = saved?.id ?? projectId.value
    if (id) await router.push(`/client/projects/${id}`)
    else await router.push('/client')
  } catch (e) {
    applyServerError(e)
    if (isApiError(e, 'VALIDATION_ERROR') && !Object.keys(errors.value).length) formError.value = errorText(e)
    void scrollToFirstError()
  } finally {
    saving.value = null
  }
}

const err = (k: string) => errors.value[k] ?? ''

/** Bring the first invalid field (or the form error) into view. */
async function scrollToFirstError() {
  await nextTick()
  const el = document.querySelector('.project-form .is-error, .project-form .field-error, [data-testid="project-form-error"]')
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
</script>

<template>
  <div class="q-container q-page project-form">
    <div class="q-page-head">
      <div>
        <h1 data-testid="project-form-title">{{ isEdit ? t('projectForm.editTitle') : t('projectForm.newTitle') }}</h1>
        <p class="q-sub">{{ t('projectForm.subtitle') }}</p>
      </div>
      <StatusTag v-if="existing" :status="existing.status" testid="project-form-status" />
    </div>

    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else>
      <el-alert
        v-if="existing?.status === 'CHANGES_REQUESTED' && existing.reviewNote"
        type="warning"
        :closable="false"
        show-icon
        :title="t('project.changesRequested')"
        :description="existing.reviewNote"
        data-testid="project-form-review-note"
      />
      <el-alert v-if="readOnly" type="info" :closable="false" show-icon :title="t('projectForm.notEditable')" data-testid="project-form-readonly" />

      <el-form label-position="top" :disabled="readOnly" @submit.prevent>
        <section class="q-card">
          <h2>{{ t('projectForm.sectionProject') }}</h2>
          <el-form-item :label="t('projectForm.titleLabel')" :error="err('title')" required>
            <el-input v-model="form.title" maxlength="200" show-word-limit :placeholder="t('projectForm.titlePlaceholder')" data-testid="project-title" />
          </el-form-item>
          <el-form-item :label="t('projectForm.description')" :error="err('description')" required>
            <el-input
              v-model="form.description"
              type="textarea"
              :autosize="{ minRows: 5, maxRows: 14 }"
              maxlength="10000"
              :placeholder="t('projectForm.descriptionPlaceholder')"
              data-testid="project-description"
            />
          </el-form-item>
          <div class="q-grid q-grid-2 tight">
            <el-form-item :label="t('projectForm.category')" :error="err('categoryCode')" required>
              <el-select v-model="form.categoryCode" filterable :placeholder="t('projectForm.categoryPlaceholder')" data-testid="project-category">
                <el-option-group :label="t('projectForm.categoriesAllowed')">
                  <el-option
                    v-for="c in allowedCategories"
                    :key="c.code"
                    :value="c.code"
                    :label="catName(c)"
                    :data-testid="`project-category-option-${c.code}`"
                  />
                </el-option-group>
                <el-option-group v-if="prohibitedCategories.length" :label="t('projectForm.categoriesProhibited')">
                  <el-option
                    v-for="c in prohibitedCategories"
                    :key="c.code"
                    :value="c.code"
                    :label="catName(c)"
                    disabled
                    :data-testid="`project-category-option-${c.code}`"
                  >
                    <span class="opt-disabled">
                      <span>{{ catName(c) }}</span>
                      <span class="opt-reason">{{ t('projectForm.prohibitedReason') }}</span>
                    </span>
                  </el-option>
                </el-option-group>
              </el-select>
            </el-form-item>
            <el-form-item :label="t('projectForm.skills')" :error="err('skills')">
              <SkillsInput v-model="form.skills" testid="project-skills" :suggestions="SKILL_SUGGESTIONS" :disabled="readOnly" />
            </el-form-item>
          </div>
          <el-alert
            v-if="keywordHits.employment.length || keywordHits.sensitive.length"
            type="warning"
            :closable="false"
            show-icon
            :title="t('projectForm.keywordTitle')"
            data-testid="project-keyword-hint"
          >
            <template #default>
              <p v-if="keywordHits.employment.length" class="hint-line">
                {{ t('projectForm.keywordEmployment', { words: keywordHits.employment.join(', ') }) }}
              </p>
              <p v-if="keywordHits.sensitive.length" class="hint-line">
                {{ t('projectForm.keywordSensitive', { words: keywordHits.sensitive.join(', ') }) }}
              </p>
            </template>
          </el-alert>
        </section>

        <section class="q-card">
          <h2>{{ t('projectForm.sectionSchedule') }}</h2>
          <p class="q-muted q-small">{{ t('projectForm.scheduleHint') }}</p>
          <div class="q-grid q-grid-3 tight">
            <el-form-item :label="t('projectForm.budget')" :error="err('budgetUsd')" required>
              <MoneyInput v-model="form.budgetUsd" data-testid="project-budget" />
            </el-form-item>
            <el-form-item :label="t('projectForm.startDate')" :error="err('startDate')" required>
              <el-input v-model="form.startDate" type="date" data-testid="project-start-date" />
            </el-form-item>
            <el-form-item :label="t('projectForm.endDate')" :error="err('endDate')" required>
              <el-input v-model="form.endDate" type="date" :min="form.startDate || undefined" data-testid="project-end-date" />
            </el-form-item>
          </div>
        </section>

        <section class="q-card">
          <div class="q-card-head">
            <h2>{{ t('projectForm.sectionMilestones') }}</h2>
            <span class="q-muted q-small">{{ t('projectForm.milestonesHint') }}</span>
          </div>
          <div v-if="err('milestones')" class="field-error block" data-testid="project-error-milestones">{{ err('milestones') }}</div>

          <div v-for="(m, i) in form.milestones" :key="m.key" class="milestone" :data-testid="`project-milestone-${i}`">
            <div class="milestone-head">
              <span class="milestone-n">{{ t('projectForm.milestoneN', { n: i + 1 }) }}</span>
              <el-button
                v-if="form.milestones.length > 1 && !readOnly"
                :icon="Delete"
                text
                type="danger"
                size="small"
                :data-testid="`project-milestone-${i}-remove`"
                @click="removeMilestone(i)"
              >
                {{ t('common.remove') }}
              </el-button>
            </div>
            <el-form-item :label="t('projectForm.milestoneTitle')" :error="err(`milestones.${i}.title`)" required>
              <el-input v-model="m.title" maxlength="200" :data-testid="`project-milestone-${i}-title`" />
            </el-form-item>
            <el-form-item :label="t('projectForm.deliverable')" :error="err(`milestones.${i}.deliverable`)" required>
              <el-input
                v-model="m.deliverable"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 8 }"
                maxlength="4000"
                :placeholder="t('projectForm.deliverablePlaceholder')"
                :data-testid="`project-milestone-${i}-deliverable`"
              />
            </el-form-item>
            <div class="q-grid q-grid-2 tight">
              <el-form-item :label="t('projectForm.dueDate')" :error="err(`milestones.${i}.dueDate`)" required>
                <el-input
                  v-model="m.dueDate"
                  type="date"
                  :min="form.startDate || undefined"
                  :max="form.endDate || undefined"
                  :data-testid="`project-milestone-${i}-due-date`"
                />
              </el-form-item>
              <el-form-item :label="t('projectForm.amount')" :error="err(`milestones.${i}.amountUsd`)" required>
                <MoneyInput v-model="m.amountUsd" :data-testid="`project-milestone-${i}-amount`" />
              </el-form-item>
            </div>
          </div>

          <el-button v-if="!readOnly" :icon="Plus" data-testid="project-add-milestone" @click="addMilestone">{{ t('projectForm.addMilestone') }}</el-button>

          <div class="sum" :class="{ ok: sumMatches, bad: !sumMatches && !!form.budgetUsd }" data-testid="project-milestone-sum" :data-match="sumMatches ? 'true' : 'false'">
            <el-icon v-if="sumMatches" class="sum-icon"><CircleCheck /></el-icon>
            <el-icon v-else-if="form.budgetUsd" class="sum-icon"><WarningFilled /></el-icon>
            <span>{{ t('projectForm.sumLine', { sum: money(milestoneSum), budget: money(form.budgetUsd ?? 0) }) }}</span>
            <span v-if="!sumMatches && form.budgetUsd" class="sum-diff" data-testid="project-milestone-sum-diff">
              {{ t('projectForm.sumDiff', { diff: money(sumDiff) }) }}
            </span>
            <el-button
              v-if="!sumMatches && milestoneSum > 0 && !readOnly"
              link
              type="primary"
              data-testid="project-budget-from-milestones"
              @click="budgetFromMilestones"
            >
              {{ t('projectForm.useSum') }}
            </el-button>
          </div>
          <div v-if="err('milestonesSum')" class="field-error" data-testid="project-error-milestones-sum">{{ err('milestonesSum') }}</div>
        </section>

        <section class="q-card">
          <h2>{{ t('projectForm.sectionDeclarations') }}</h2>
          <p class="q-muted q-small">{{ t('projectForm.declarationsHint') }}</p>
          <div class="decls">
            <el-checkbox v-for="k in DECLARATION_KEYS" :key="k" v-model="form.declarations[k]" class="decl" :data-testid="declTestid(k)">
              {{ t(`projectForm.decl.${k}`) }}
            </el-checkbox>
          </div>
          <div v-if="err('declarations')" class="field-error" data-testid="project-error-declarations">{{ err('declarations') }}</div>
        </section>

        <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" data-testid="project-form-error">
          <template v-if="formErrorCode === 'COMPANY_NOT_APPROVED'" #default>
            <router-link to="/client/company" data-testid="project-form-company-link">{{ t('projectForm.goCompany') }}</router-link>
          </template>
        </el-alert>

        <div v-if="!readOnly" class="actions-bar">
          <el-button class="cancel-btn" data-testid="project-cancel" @click="router.back()">{{ t('common.cancel') }}</el-button>
          <div class="q-actions">
            <el-button :loading="saving === 'draft'" :disabled="!!saving" data-testid="project-save-draft" @click="save(false)">
              {{ t('projectForm.saveDraft') }}
            </el-button>
            <el-button type="primary" :loading="saving === 'submit'" :disabled="!!saving" data-testid="project-submit" @click="save(true)">
              {{ t('projectForm.submit') }}
            </el-button>
          </div>
        </div>
      </el-form>
    </template>
  </div>
</template>

<style scoped>
.project-form {
  max-width: 900px;
}
.tight {
  column-gap: 16px;
  row-gap: 0;
}
.tight :deep(.el-select) {
  width: 100%;
}
.opt-disabled {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.opt-reason {
  font-size: 12px;
  color: var(--q-text-3);
}
.hint-line {
  margin: 2px 0;
}
.milestone {
  border: 1px solid var(--q-border);
  border-radius: 8px;
  padding: 14px 16px 2px;
  margin-bottom: 12px;
  background: #fcfcfd;
}
.milestone-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.milestone-n {
  font-weight: 650;
  color: var(--q-accent);
}
.sum {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--q-bg);
  font-variant-numeric: tabular-nums;
}
.sum.ok {
  background: #ecf7f0;
  color: #1e7a46;
}
.sum.bad {
  background: #fdf0ef;
  color: #b42318;
}
.sum-diff {
  font-weight: 600;
}
.block {
  margin-bottom: 8px;
}
.decls {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.decl {
  height: auto;
  white-space: normal;
  align-items: flex-start;
  margin-right: 0;
}
.decl :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.5;
}
.decl :deep(.el-checkbox__input) {
  margin-top: 3px;
}
.actions-bar {
  position: sticky;
  margin-top: 16px;
  bottom: 0;
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--q-border);
  border-radius: var(--q-radius);
  box-shadow: 0 -4px 16px rgba(16, 24, 40, 0.06);
}
@media (max-width: 640px) {
  .cancel-btn {
    display: none;
  }
  .actions-bar {
    padding: 10px 12px;
    justify-content: flex-end;
  }
}
</style>
