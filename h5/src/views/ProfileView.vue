<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import StateView from '@/components/StateView.vue'
import { engineerApi, publicApi } from '@/api/endpoints'
import type { EngineerProfile, EngineerProfileInput, LanguageLevel, LegalDoc } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { PROVINCES, provinceLabel } from '@/data/provinces'
import { errorMessage, isCode } from '@/utils/errors'
import { toNumber } from '@/utils/format'

const { t, locale } = useI18n()
const auth = useAuthStore()

const LEVELS: LanguageLevel[] = ['NONE', 'BASIC', 'GOOD', 'FLUENT']
const SUGGESTED_SKILLS = [
  'Vue',
  'React',
  'TypeScript',
  'Java',
  'Spring Boot',
  'Python',
  'Go',
  'Node.js',
  'Flutter',
  'Android',
  'iOS',
  'MySQL',
  'Docker',
  'Figma',
  'STM32',
  'SolidWorks',
  'Altium Designer',
]

const loading = ref(true)
const loadError = ref<string | null>(null)
const saving = ref(false)
const submitting = ref(false)
const grantingCb = ref(false)
const profile = ref<EngineerProfile | null>(null)
const declaration = ref<LegalDoc | null>(null)
const declarationError = ref(false)
const showRegion = ref(false)
const skillInput = ref('')

const form = reactive({
  headline: '',
  bio: '',
  skills: [] as string[],
  years: '',
  region: '',
  englishLevel: 'NONE' as LanguageLevel,
  spanishLevel: 'NONE' as LanguageLevel,
  rate: '',
  hours: '',
  cvText: '',
  education: '',
  portfolioLinks: [] as string[],
  declarationNoRestrictions: false,
})

const status = computed(() => profile.value?.status ?? auth.me?.engineerProfileStatus ?? 'NONE')
const locked = computed(() => status.value === 'PENDING_REVIEW')
const canSubmit = computed(() => ['NONE', 'DRAFT', 'REJECTED'].includes(status.value))
const hasCrossBorder = computed(() => auth.hasConsent('CROSS_BORDER_PROCESSING'))
const regionText = computed(() => provinceLabel(form.region, locale.value))
const provinceColumns = computed(() =>
  PROVINCES.map((p) => ({ text: locale.value === 'en' ? p.value : p.zh, value: p.value })),
)
const suggestions = computed(() =>
  SUGGESTED_SKILLS.filter((s) => !form.skills.some((x) => x.toLowerCase() === s.toLowerCase())).slice(0, 8),
)

function fill(p: EngineerProfile | null) {
  profile.value = p
  if (!p) return
  form.headline = p.headline ?? ''
  form.bio = p.bio ?? ''
  form.skills = Array.isArray(p.skills) ? [...p.skills] : []
  form.years = p.yearsExperience === null || p.yearsExperience === undefined ? '' : String(p.yearsExperience)
  form.region = p.region ?? ''
  form.englishLevel = (p.englishLevel as LanguageLevel) || 'NONE'
  form.spanishLevel = (p.spanishLevel as LanguageLevel) || 'NONE'
  form.rate = p.hourlyRateUsd === null || p.hourlyRateUsd === undefined ? '' : String(p.hourlyRateUsd)
  form.hours =
    p.availabilityHoursWeek === null || p.availabilityHoursWeek === undefined ? '' : String(p.availabilityHoursWeek)
  form.cvText = p.cvText ?? ''
  form.education = p.education ?? ''
  form.portfolioLinks = Array.isArray(p.portfolioLinks) ? [...p.portfolioLinks] : []
  form.declarationNoRestrictions = !!p.declarationNoRestrictions
}

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const [p] = await Promise.all([
      engineerApi.profile().catch((e) => {
        if (isCode(e, 'NOT_FOUND')) return null
        throw e
      }),
      auth.me ? Promise.resolve(auth.me) : auth.loadMe(),
    ])
    fill(p)
  } catch (e) {
    loadError.value = errorMessage(e)
  } finally {
    loading.value = false
  }
  loadDeclaration()
}

async function loadDeclaration() {
  declarationError.value = false
  try {
    declaration.value = await publicApi.legal('engineer-declaration', locale.value)
  } catch {
    declarationError.value = true
  }
}

onMounted(load)

function addSkill(value?: string) {
  const v = (value ?? skillInput.value).trim().replace(/[,，、]+$/, '')
  if (!v) return
  if (!form.skills.some((s) => s.toLowerCase() === v.toLowerCase()) && form.skills.length < 30) form.skills.push(v)
  if (value === undefined) skillInput.value = ''
}
function removeSkill(i: number) {
  form.skills.splice(i, 1)
}
function addLink() {
  if (form.portfolioLinks.length < 10) form.portfolioLinks.push('')
}
function removeLink(i: number) {
  form.portfolioLinks.splice(i, 1)
}
function levelOf(lang: 'english' | 'spanish'): LanguageLevel {
  return lang === 'english' ? form.englishLevel : form.spanishLevel
}
function setLevel(lang: 'english' | 'spanish', lv: LanguageLevel) {
  if (lang === 'english') form.englishLevel = lv
  else form.spanishLevel = lv
}
function onRegionConfirm({ selectedValues }: { selectedValues: (string | number)[] }) {
  form.region = String(selectedValues[0] ?? '')
  showRegion.value = false
}

function payload(): EngineerProfileInput {
  const num = (s: string) => (s.trim() === '' ? null : toNumber(s))
  return {
    headline: form.headline.trim(),
    bio: form.bio.trim(),
    skills: form.skills,
    yearsExperience: num(form.years),
    region: form.region,
    englishLevel: form.englishLevel,
    spanishLevel: form.spanishLevel,
    hourlyRateUsd: num(form.rate),
    availabilityHoursWeek: num(form.hours),
    cvText: form.cvText.trim(),
    education: form.education.trim(),
    portfolioLinks: form.portfolioLinks.map((l) => l.trim()).filter(Boolean),
    declarationNoRestrictions: form.declarationNoRestrictions,
  }
}

async function persist(): Promise<boolean> {
  if (skillInput.value.trim()) addSkill()
  try {
    const saved = await engineerApi.saveProfile(payload())
    if (saved && typeof saved === 'object') profile.value = { ...profile.value, ...saved }
    return true
  } catch (e) {
    showFailToast(errorMessage(e))
    return false
  }
}

async function save() {
  saving.value = true
  try {
    if (await persist()) {
      showSuccessToast(t('profile.saved'))
      auth.loadMe().catch(() => undefined)
    }
  } finally {
    saving.value = false
  }
}

async function grantCrossBorder() {
  grantingCb.value = true
  try {
    await auth.setConsent('CROSS_BORDER_PROCESSING', true)
    showSuccessToast(t('profile.crossBorderGranted'))
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    grantingCb.value = false
  }
}

async function submit() {
  if (!form.headline.trim() || (form.skills.length === 0 && !skillInput.value.trim()) || !form.region) {
    showFailToast(t('profile.requiredMissing'))
    return
  }
  if (!form.declarationNoRestrictions) {
    showFailToast(t('profile.needDeclaration'))
    return
  }
  if (!hasCrossBorder.value) {
    showFailToast(t('profile.needCrossBorder'))
    return
  }
  submitting.value = true
  try {
    if (!(await persist())) return
    const res = await engineerApi.submitProfile()
    if (res && typeof res === 'object') profile.value = { ...profile.value, ...res }
    profile.value = { ...profile.value, status: 'PENDING_REVIEW' }
    showSuccessToast(t('profile.submitted'))
    auth.loadMe().catch(() => undefined)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="profile" data-testid="profile-page">
    <PageHeader :title="t('profile.title')" back="/me" />
    <StateView :loading="loading" :error="loadError" @retry="load">
      <div class="q-page profile__page" :class="{ 'profile__page--bar': !locked }">
        <!-- Status -->
        <section class="q-card status-card" data-testid="profile-status-card">
          <div class="q-between">
            <div>
              <div class="q-muted">{{ t('profile.alias') }}</div>
              <div class="status-card__alias" data-testid="profile-alias">{{ profile?.alias || '—' }}</div>
            </div>
            <StatusTag kind="profile" :status="status" data-testid="profile-status" />
          </div>
          <p class="status-card__hint">{{ t(`profile.statusHint.${status}`) }}</p>
          <p v-if="profile?.ratingCount" class="q-muted" data-testid="profile-rating">
            {{ t('profile.rating', { avg: Number(profile.ratingAvg ?? 0).toFixed(1), count: profile.ratingCount }) }}
          </p>
          <div v-if="status === 'REJECTED' && profile?.reviewNote" class="q-notice q-notice--danger" data-testid="profile-review-note">
            <van-icon name="warning-o" />
            <span><strong>{{ t('profile.reviewNote') }}{{ t('common.colon') }}</strong>{{ profile.reviewNote }}</span>
          </div>
          <p class="q-muted status-card__alias-hint">{{ t('profile.aliasHint') }}</p>
        </section>

        <fieldset class="profile__fields" :disabled="locked">
          <!-- Basics -->
          <section class="q-card q-card--form">
            <h2 class="q-section-title">
              {{ t('profile.sections.basic') }}
              <span class="q-badge q-badge--pseudo">{{ t('profile.visiblePseudo') }}</span>
            </h2>
            <van-field
              v-model="form.headline"
              v-testid="'profile-headline'"
              :label="t('profile.headline')"
              :placeholder="t('profile.headlinePlaceholder')"
              :readonly="locked"
              maxlength="120"
              required
              label-align="top"
            />
            <van-field
              v-model="form.bio"
              v-testid="'profile-bio'"
              :label="t('profile.bio')"
              :placeholder="t('profile.bioPlaceholder')"
              :readonly="locked"
              type="textarea"
              rows="3"
              autosize
              maxlength="2000"
              show-word-limit
              label-align="top"
            />
            <van-field
              v-testid="'profile-region'"
              :model-value="regionText"
              :label="t('profile.region')"
              :placeholder="t('profile.regionPlaceholder')"
              readonly
              required
              is-link
              :clickable="!locked"
              label-align="top"
              @click="!locked && (showRegion = true)"
            />
          </section>

          <!-- Skills -->
          <section class="q-card q-card--form">
            <h2 class="q-section-title">
              {{ t('profile.sections.skills') }}
              <span class="q-badge q-badge--pseudo">{{ t('profile.visiblePseudo') }}</span>
            </h2>
            <div class="skills">
              <div class="skills__label">{{ t('profile.skills') }} <span class="req">*</span></div>
              <div class="q-tags skills__tags" data-testid="profile-skills">
                <span v-for="(s, i) in form.skills" :key="s" class="skill-tag">
                  {{ s }}
                  <button
                    v-if="!locked"
                    type="button"
                    class="skill-tag__x"
                    :aria-label="t('profile.portfolioRemove')"
                    :data-testid="`profile-skill-remove-${i}`"
                    @click="removeSkill(i)"
                  >
                    <van-icon name="cross" />
                  </button>
                </span>
              </div>
              <div v-if="!locked" class="skills__add">
                <van-field
                  v-model="skillInput"
                  v-testid="'profile-skill-input'"
                  :placeholder="t('profile.skillPlaceholder')"
                  maxlength="40"
                  enterkeyhint="done"
                  class="skills__input"
                  @keyup.enter="addSkill()"
                />
                <van-button size="small" type="primary" plain data-testid="profile-skill-add" @click="addSkill()">
                  {{ t('profile.skillAdd') }}
                </van-button>
              </div>
              <div v-if="!locked && suggestions.length" class="skills__suggest">
                <span class="q-muted">{{ t('profile.skillSuggest') }}</span>
                <button
                  v-for="s in suggestions"
                  :key="s"
                  type="button"
                  class="suggest-chip"
                  :data-testid="`profile-skill-suggest-${s.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`"
                  @click="addSkill(s)"
                >
                  + {{ s }}
                </button>
              </div>
            </div>
            <van-field
              v-model="form.years"
              v-testid="'profile-years'"
              :label="t('profile.years')"
              type="digit"
              maxlength="2"
              :readonly="locked"
              placeholder="0"
              input-align="right"
            >
              <template #extra><span class="unit">{{ t('profile.yearsUnit') }}</span></template>
            </van-field>
          </section>

          <!-- Languages -->
          <section class="q-card q-card--form">
            <h2 class="q-section-title">
              {{ t('profile.sections.languages') }}
              <span class="q-badge q-badge--pseudo">{{ t('profile.visiblePseudo') }}</span>
            </h2>
            <div v-for="lang in (['english', 'spanish'] as const)" :key="lang" class="lang-row">
              <div class="lang-row__label">{{ t(`profile.${lang}`) }}</div>
              <div class="segmented" role="radiogroup" :aria-label="t(`profile.${lang}`)">
                <button
                  v-for="lv in LEVELS"
                  :key="lv"
                  type="button"
                  role="radio"
                  class="segmented__item"
                  :class="{ 'is-active': levelOf(lang) === lv }"
                  :aria-checked="levelOf(lang) === lv"
                  :disabled="locked"
                  :data-testid="`profile-${lang}-${lv}`"
                  @click="setLevel(lang, lv)"
                >
                  {{ t(`profile.levels.${lv}`) }}
                </button>
              </div>
            </div>
          </section>

          <!-- Work preferences -->
          <section class="q-card q-card--form">
            <h2 class="q-section-title">
              {{ t('profile.sections.work') }}
              <span class="q-badge q-badge--pseudo">{{ t('profile.visiblePseudo') }}</span>
            </h2>
            <van-field
              v-model="form.rate"
              v-testid="'profile-rate'"
              :label="t('profile.rate')"
              type="number"
              maxlength="7"
              :readonly="locked"
              placeholder="0"
              input-align="right"
            >
              <template #extra><span class="unit">{{ t('profile.rateUnit') }}</span></template>
            </van-field>
            <p class="field-hint">{{ t('profile.rateHint') }}</p>
            <van-field
              v-model="form.hours"
              v-testid="'profile-hours'"
              :label="t('profile.hours')"
              type="digit"
              maxlength="2"
              :readonly="locked"
              placeholder="0"
              input-align="right"
            >
              <template #extra><span class="unit">{{ t('profile.hoursUnit') }}</span></template>
            </van-field>
          </section>

          <!-- CV & portfolio -->
          <section class="q-card q-card--form">
            <h2 class="q-section-title">
              {{ t('profile.sections.cv') }}
              <span class="q-badge q-badge--private"><van-icon name="lock" /> {{ t('profile.visibleOnGrant') }}</span>
            </h2>
            <p class="q-notice cv-hint">
              <van-icon name="info-o" />
              <span>{{ t('profile.cvHint') }}</span>
            </p>
            <van-field
              v-model="form.cvText"
              v-testid="'profile-cv'"
              :label="t('profile.cvText')"
              :placeholder="t('profile.cvPlaceholder')"
              :readonly="locked"
              type="textarea"
              rows="4"
              autosize
              maxlength="10000"
              label-align="top"
            />
            <van-field
              v-model="form.education"
              v-testid="'profile-education'"
              :label="t('profile.education')"
              :placeholder="t('profile.educationPlaceholder')"
              :readonly="locked"
              type="textarea"
              rows="2"
              autosize
              maxlength="2000"
              label-align="top"
            />
            <div class="links">
              <div class="skills__label">{{ t('profile.portfolio') }}</div>
              <div v-for="(_, i) in form.portfolioLinks" :key="i" class="links__row">
                <van-field
                  v-model="form.portfolioLinks[i]"
                  v-testid="`profile-link-${i}`"
                  :placeholder="t('profile.portfolioPlaceholder')"
                  :readonly="locked"
                  type="url"
                  maxlength="300"
                  class="links__input"
                />
                <button
                  v-if="!locked"
                  type="button"
                  class="links__remove"
                  :aria-label="t('profile.portfolioRemove')"
                  :data-testid="`profile-link-remove-${i}`"
                  @click="removeLink(i)"
                >
                  <van-icon name="delete-o" />
                </button>
              </div>
              <van-button
                v-if="!locked && form.portfolioLinks.length < 10"
                size="small"
                icon="plus"
                plain
                class="links__add"
                data-testid="profile-link-add"
                @click="addLink"
              >
                {{ t('profile.portfolioAdd') }}
              </van-button>
            </div>
          </section>

          <!-- Declaration -->
          <section class="q-card">
            <h2 class="q-section-title">{{ t('profile.sections.declaration') }}</h2>
            <div class="declaration" data-testid="profile-declaration-text">
              <MarkdownView v-if="declaration" :source="declaration.body" />
              <p v-else-if="declarationError" class="q-muted">
                {{ t('profile.declarationLoadFailed') }}
                <a href="#" data-testid="profile-declaration-retry" @click.prevent="loadDeclaration">{{ t('common.retry') }}</a>
              </p>
              <van-loading v-else size="18" />
            </div>
            <van-checkbox
              v-model="form.declarationNoRestrictions"
              shape="square"
              icon-size="16px"
              class="declaration__check"
              :disabled="locked"
              data-testid="profile-declaration"
            >
              {{ t('profile.declarationCheck') }}
            </van-checkbox>
            <p v-if="declaration" class="q-muted declaration__ver">{{ t('legal.version', { v: declaration.version }) }}</p>

            <div v-if="!hasCrossBorder" class="q-notice q-notice--warning crossborder" data-testid="profile-crossborder-missing">
              <van-icon name="info-o" />
              <div>
                <p>{{ t('profile.needCrossBorder') }}</p>
                <div class="q-row">
                  <van-button
                    size="small"
                    type="warning"
                    :loading="grantingCb"
                    data-testid="profile-grant-crossborder"
                    @click="grantCrossBorder"
                  >
                    {{ t('profile.grantCrossBorder') }}
                  </van-button>
                  <router-link to="/legal/cross-border" data-testid="profile-link-crossborder">
                    {{ t('legal.docs.cross-border') }}
                  </router-link>
                </div>
              </div>
            </div>
          </section>
        </fieldset>

        <!-- KYC / payouts -->
        <section class="q-card kyc" data-testid="profile-kyc">
          <h2 class="q-section-title">
            <span class="q-row"><van-icon name="shield-o" class="kyc__icon" />{{ t('profile.kycTitle') }}</span>
          </h2>
          <p class="kyc__body">{{ t('profile.kycBody') }}</p>
        </section>
      </div>

      <div v-if="!locked" class="q-fixed-bottom profile__bar">
        <van-button plain type="primary" :loading="saving" data-testid="profile-save" @click="save">
          {{ t('profile.save') }}
        </van-button>
        <van-button
          v-if="canSubmit"
          type="primary"
          :loading="submitting"
          data-testid="profile-submit"
          @click="submit"
        >
          {{ t('profile.submit') }}
        </van-button>
      </div>
    </StateView>

    <van-popup v-model:show="showRegion" position="bottom" round safe-area-inset-bottom>
      <van-picker
        :title="t('profile.region')"
        :columns="provinceColumns"
        :model-value="form.region ? [form.region] : []"
        data-testid="profile-region-picker"
        @confirm="onRegionConfirm"
        @cancel="showRegion = false"
      >
        <template #confirm><span data-testid="profile-region-confirm">{{ t('common.confirm') }}</span></template>
        <template #cancel><span data-testid="profile-region-cancel">{{ t('common.cancel') }}</span></template>
      </van-picker>
    </van-popup>
  </div>
</template>

<style scoped>
.profile__page--bar {
  padding-bottom: calc(84px + env(safe-area-inset-bottom));
}
.profile__fields {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
}
.status-card__alias {
  margin-top: 2px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
  font-variant-numeric: tabular-nums;
}
.status-card__hint {
  margin: 12px 0 8px;
  color: var(--q-text-2);
  font-size: 13px;
  line-height: 1.6;
}
.status-card__alias-hint {
  margin: 8px 0 0;
  font-size: 12px;
}
.q-card--form {
  padding: 16px 4px 8px;
}
.q-card--form .q-section-title {
  padding: 0 12px;
}
.q-card--form :deep(.van-cell) {
  padding-left: 12px;
  padding-right: 12px;
}
.q-card--form :deep(.van-field__label--top) {
  margin-bottom: 6px;
}
.skills,
.links {
  padding: 10px 12px;
}
.skills__label {
  font-size: 14px;
  color: var(--q-text-2);
  margin-bottom: 8px;
}
.req {
  color: var(--van-field-required-mark-color, #ee0a24);
}
.skills__tags:empty {
  display: none;
}
.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 28px;
  padding: 0 4px 0 10px;
  border-radius: 14px;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 13px;
}
.skill-tag__x {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.skills__add {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}
.skills__input,
.links__input {
  flex: 1;
  padding: 6px 10px;
  border-radius: 8px;
  background: #f5f6f8;
}
.skills__input::after,
.links__input::after {
  display: none;
}
.skills__suggest {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}
.suggest-chip {
  height: 26px;
  padding: 0 10px;
  border: 1px dashed #c9cdd4;
  border-radius: 13px;
  background: #fff;
  color: var(--q-text-2);
  font-size: 12px;
  cursor: pointer;
}
.unit {
  margin-left: 6px;
  color: var(--q-text-3);
}
.field-hint {
  margin: -4px 12px 4px;
  font-size: 12px;
  color: var(--q-text-3);
  line-height: 1.5;
}
.lang-row {
  padding: 8px 12px 12px;
}
.lang-row__label {
  margin-bottom: 8px;
  color: var(--q-text-2);
}
.segmented {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.segmented__item {
  height: 34px;
  border: 1px solid var(--q-border);
  border-radius: 8px;
  background: #fff;
  color: var(--q-text-2);
  font-size: 13px;
  cursor: pointer;
}
.segmented__item.is-active {
  border-color: var(--q-primary);
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-weight: 600;
}
.segmented__item:disabled {
  cursor: default;
  opacity: 0.7;
}
.cv-hint {
  margin: 0 12px 4px;
}
.links__row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.links__remove {
  width: 32px;
  height: 32px;
  border: 0;
  background: none;
  color: var(--q-text-3);
  font-size: 18px;
  cursor: pointer;
}
.links__add {
  margin-top: 2px;
}
.declaration {
  max-height: 220px;
  overflow-y: auto;
  padding: 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: #f7f8fa;
  border: 1px solid var(--q-border);
}
.declaration :deep(.q-markdown) {
  font-size: 13px;
}
.declaration__check :deep(.van-checkbox__label) {
  font-size: 13px;
  line-height: 1.6;
  color: var(--q-text);
}
.declaration__ver {
  margin: 8px 0 0 24px;
  font-size: 12px;
}
.crossborder {
  margin-top: 14px;
}
.crossborder p {
  margin: 0 0 8px;
}
.kyc__icon {
  color: var(--q-success);
  font-size: 18px;
}
.kyc__body {
  margin: 0;
  color: var(--q-text-2);
  font-size: 13px;
  line-height: 1.7;
}
.profile__bar {
  display: flex;
  gap: 12px;
}
.profile__bar > * {
  flex: 1;
}
</style>
