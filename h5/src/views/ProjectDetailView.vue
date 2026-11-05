<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import PageHeader from '@/components/PageHeader.vue'
import StateView from '@/components/StateView.vue'
import ConsentSheet from '@/components/ConsentSheet.vue'
import { engineerApi, teamApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { Project, Team } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useMetaStore } from '@/stores/meta'
import { countryName, formatDate, formatUsd, toNumber } from '@/utils/format'
import { errorMessage, isCode } from '@/utils/errors'

const { t } = useI18n()
const route = useRoute()
const auth = useAuthStore()
const meta = useMetaStore()

const id = computed(() => String(route.params.id))
const project = ref<Project | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const coverNote = ref('')
const prices = reactive<Record<string, string>>({})
const applying = ref(false)
const showConsent = ref(false)
const consentLoading = ref(false)

// Team bids (spec §12): only the lead of a team with at least two active members can bid for it.
const leadTeams = ref<Team[]>([])
const mode = ref<'individual' | 'team'>('individual')
const teamId = ref('')
const shares = reactive<Record<string, string>>({})
const selectedTeam = computed(() => leadTeams.value.find((tm) => String(tm.id) === teamId.value) ?? null)
const teamMembers = computed(() => selectedTeam.value?.members.filter((m) => m.status === 'ACTIVE') ?? [])
const shareTotal = computed(() => Math.round(teamMembers.value.reduce((sum, m) => sum + toNumber(shares[m.alias]), 0) * 100) / 100)
const sharesValid = computed(
  () => shareTotal.value === 100 && teamMembers.value.every((m) => toNumber(shares[m.alias]) > 0),
)

const milestones = computed(() => [...(project.value?.milestones ?? [])].sort((a, b) => a.seq - b.seq))
const total = computed(() => milestones.value.reduce((sum, m) => sum + toNumber(prices[String(m.id)]), 0))
const approved = computed(() => auth.me?.engineerProfileStatus === 'APPROVED')
const durationText = computed(() =>
  project.value ? `${formatDate(project.value.startDate)} – ${formatDate(project.value.endDate)}` : '',
)

async function load() {
  loading.value = true
  error.value = null
  try {
    const p = await engineerApi.project(id.value)
    project.value = p
    for (const m of p.milestones ?? []) prices[String(m.id)] = String(toNumber(m.talentAmountUsd) || '')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

/** Even split in hundredths of a percent; the lead (first member) takes the remainder. */
function evenSplit() {
  const members = teamMembers.value
  if (!members.length) return
  const base = Math.floor(10000 / members.length) / 100
  members.forEach((m, i) => {
    shares[m.alias] = String(i === 0 ? Math.round((100 - base * (members.length - 1)) * 100) / 100 : base)
  })
}

function memberAmount(alias: string) {
  return (total.value * toNumber(shares[alias])) / 100
}

async function loadTeams() {
  try {
    leadTeams.value = (await teamApi.list()).filter((tm) => tm.myRole === 'LEAD' && tm.canApply)
    if (leadTeams.value.length && !teamId.value) teamId.value = String(leadTeams.value[0]!.id)
  } catch {
    leadTeams.value = []
  }
}

watch(teamId, evenSplit)

onMounted(() => {
  load()
  meta.loadCategories()
  meta.loadConfig()
  auth.loadMe().catch(() => undefined)
  loadTeams()
})

function validate(): boolean {
  if (!coverNote.value.trim()) {
    showFailToast(t('project.coverRequired'))
    return false
  }
  if (milestones.value.some((m) => !(toNumber(prices[String(m.id)]) > 0))) {
    showFailToast(t('project.invalidPrice'))
    return false
  }
  if (mode.value === 'team' && (!selectedTeam.value || !sharesValid.value)) {
    showFailToast(t('project.splitInvalid'))
    return false
  }
  return true
}

/** Aliases named by a team-bid error (members missing consent, approval, or already applied). */
function errorAliases(e: unknown): string[] {
  const data = e instanceof ApiError ? (e.data as { aliases?: string[] } | null) : null
  return Array.isArray(data?.aliases) ? data!.aliases : []
}

async function onApply() {
  if (!validate()) return
  if (!auth.hasConsent('SHARE_PROFILE_PSEUDONYMIZED')) {
    showConsent.value = true
    return
  }
  await doApply()
}

async function doApply() {
  if (!project.value) return
  applying.value = true
  const body = {
    coverNote: coverNote.value.trim(),
    prices: milestones.value.map((m) => ({
      milestoneId: m.id,
      talentAmountUsd: Math.round(toNumber(prices[String(m.id)]) * 100) / 100,
    })),
  }
  try {
    if (mode.value === 'team' && selectedTeam.value) {
      await engineerApi.applyAsTeam(project.value.id, {
        ...body,
        teamId: selectedTeam.value.id,
        shares: teamMembers.value.map((m) => ({ alias: m.alias, sharePct: Math.round(toNumber(shares[m.alias]) * 100) / 100 })),
      })
      showSuccessToast(t('project.teamSuccess'))
    } else {
      await engineerApi.apply(project.value.id, body)
      showSuccessToast(t('project.success'))
    }
    project.value = { ...project.value, applied: true }
  } catch (e) {
    const others = errorAliases(e).filter((a) => a !== selectedTeam.value?.leadAlias)
    if (mode.value === 'team' && others.length && isCode(e, 'CONSENT_REQUIRED')) {
      showFailToast(t('project.membersMissingConsent', { aliases: others.join('、') }))
    } else if (mode.value === 'team' && others.length && isCode(e, 'PROFILE_NOT_APPROVED')) {
      showFailToast(t('project.membersNotApproved', { aliases: others.join('、') }))
    } else if (mode.value === 'team' && others.length && isCode(e, 'ALREADY_APPLIED')) {
      showFailToast(t('project.membersAlreadyApplied', { aliases: others.join('、') }))
    } else if (isCode(e, 'CONSENT_REQUIRED')) {
      showConsent.value = true
    } else if (isCode(e, 'ALREADY_APPLIED')) {
      project.value = { ...project.value, applied: true }
      showFailToast(errorMessage(e))
    } else {
      showFailToast(errorMessage(e))
    }
  } finally {
    applying.value = false
  }
}

async function acceptConsent() {
  consentLoading.value = true
  try {
    await auth.setConsent('SHARE_PROFILE_PSEUDONYMIZED', true)
    showConsent.value = false
    await doApply()
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    consentLoading.value = false
  }
}
</script>

<template>
  <div class="detail" data-testid="project-page">
    <PageHeader :title="t('project.title')" back="/projects" />
    <StateView :loading="loading" :error="error" @retry="load">
      <div v-if="project" class="q-page">
        <section class="q-card head">
          <h1 class="head__title" data-testid="project-title">{{ project.title }}</h1>
          <div class="head__meta">
            <span class="q-chip head__cat">{{ meta.categoryName(project.categoryCode) }}</span>
            <span v-if="project.applied" class="q-chip head__applied">{{ t('projects.applied') }}</span>
          </div>
          <div class="head__budget">
            <div class="q-muted">{{ t('project.talentBudget') }}</div>
            <div class="q-amount q-amount--accent head__amount" data-testid="project-talent-budget">
              {{ formatUsd(project.talentBudgetUsd) }}
            </div>
            <p class="head__hint">{{ t('project.budgetHint') }}</p>
          </div>
          <dl class="facts">
            <div class="facts__item">
              <dt>{{ t('project.clientCountry') }}</dt>
              <dd data-testid="project-client-country">{{ countryName(project.clientCountry) }}</dd>
            </div>
            <div class="facts__item">
              <dt>{{ t('project.period') }}</dt>
              <dd>{{ durationText }}</dd>
            </div>
          </dl>
        </section>

        <section class="q-card">
          <h2 class="q-section-title">{{ t('project.description') }}</h2>
          <p class="q-pre detail__desc" data-testid="project-description">{{ project.description }}</p>
          <template v-if="project.skills?.length">
            <h3 class="detail__sub">{{ t('project.skills') }}</h3>
            <div class="q-tags">
              <span v-for="s in project.skills" :key="s" class="q-chip">{{ s }}</span>
            </div>
          </template>
        </section>

        <section class="q-card">
          <h2 class="q-section-title">
            {{ t('project.milestones') }}
            <small>{{ t('projects.milestones', { n: milestones.length }) }}</small>
          </h2>
          <ol class="timeline">
            <li v-for="m in milestones" :key="m.id" class="timeline__item" :data-testid="`project-milestone-${m.id}`">
              <span class="timeline__dot">{{ m.seq }}</span>
              <div class="timeline__body">
                <div class="q-between">
                  <strong class="timeline__title">{{ m.title }}</strong>
                  <span class="q-amount">{{ formatUsd(m.talentAmountUsd) }}</span>
                </div>
                <div class="q-muted">{{ t('project.due', { date: formatDate(m.dueDate) }) }}</div>
                <p class="timeline__deliverable">
                  <span class="timeline__label">{{ t('project.deliverable') }}</span>{{ m.deliverable }}
                </p>
              </div>
            </li>
          </ol>
        </section>

        <!-- Apply -->
        <section v-if="project.applied" class="q-card applied" data-testid="project-applied">
          <van-icon name="checked" class="applied__icon" />
          <h2 class="applied__title">{{ t('project.appliedTitle') }}</h2>
          <p class="q-muted">{{ t('project.appliedBody') }}</p>
          <van-button round plain type="primary" size="small" to="/applications" data-testid="project-view-applications">
            {{ t('project.viewApplications') }}
          </van-button>
        </section>

        <section v-else class="q-card q-card--apply" data-testid="apply-form">
          <h2 class="q-section-title">{{ t('project.applyTitle') }}</h2>
          <div v-if="auth.me && !approved" class="q-notice q-notice--warning apply__notice" data-testid="apply-not-approved">
            <van-icon name="info-o" />
            <span>
              {{ t('project.needApproval') }}
              <router-link to="/profile" data-testid="apply-go-profile">{{ t('project.goProfile') }}</router-link>
            </span>
          </div>

          <div v-if="leadTeams.length" class="apply__mode" data-testid="apply-mode">
            <div class="apply__prices-title">{{ t('project.applyMode') }}</div>
            <van-radio-group v-model="mode" direction="horizontal">
              <van-radio name="individual" data-testid="apply-mode-individual">{{ t('project.modeIndividual') }}</van-radio>
              <van-radio name="team" data-testid="apply-mode-team">{{ t('project.modeTeam') }}</van-radio>
            </van-radio-group>
          </div>
          <p v-else-if="approved" class="q-muted apply__team-hint" data-testid="apply-team-hint">{{ t('project.noTeamHint') }}</p>

          <div v-if="mode === 'team' && leadTeams.length" class="apply__team" data-testid="apply-team">
            <template v-if="leadTeams.length > 1">
              <div class="apply__prices-title">{{ t('project.chooseTeam') }}</div>
              <van-radio-group v-model="teamId" class="apply__teams">
                <van-radio v-for="tm in leadTeams" :key="tm.id" :name="String(tm.id)" :data-testid="`apply-team-${tm.id}`">
                  {{ tm.name }}
                </van-radio>
              </van-radio-group>
            </template>
            <div v-else class="apply__team-name">{{ selectedTeam?.name }}</div>
            <div class="q-between apply__split-head">
              <span class="apply__prices-title">{{ t('project.split') }}</span>
              <button type="button" class="apply__even" data-testid="apply-share-even" @click="evenSplit">{{ t('project.splitEven') }}</button>
            </div>
            <p class="apply__hint">{{ t('project.splitHint') }}</p>
            <div v-for="m in teamMembers" :key="m.alias" class="price-row" :data-testid="`apply-share-row-${m.alias}`">
              <div class="price-row__label">
                <span class="price-row__seq">{{ t(`teams.role.${m.role}`) }}</span>
                <span class="price-row__name share__alias">{{ m.alias }}</span>
                <span class="q-muted price-row__ref" :data-testid="`apply-share-amount-${m.alias}`">
                  {{ t('project.memberAmount', { amount: formatUsd(memberAmount(m.alias)) }) }}
                </span>
              </div>
              <div class="price-row__input share__input">
                <van-field
                  v-model="shares[m.alias]"
                  v-testid="`apply-share-${m.alias}`"
                  type="number"
                  maxlength="6"
                  input-align="right"
                  placeholder="0"
                  class="price-row__field"
                />
                <span class="price-row__currency">%</span>
              </div>
            </div>
            <div
              class="apply__total"
              data-testid="apply-share-total"
              :data-valid="sharesValid ? 'true' : 'false'"
              :class="{ 'is-invalid': !sharesValid }"
            >
              <span>{{ t('project.splitTotal', { pct: shareTotal }) }}</span>
              <van-icon :name="sharesValid ? 'passed' : 'warning-o'" />
            </div>
          </div>

          <van-field
            v-model="coverNote"
            v-testid="'apply-cover-note'"
            :label="t('project.coverNote')"
            :placeholder="t('project.coverPlaceholder')"
            type="textarea"
            rows="4"
            autosize
            maxlength="3000"
            show-word-limit
            label-align="top"
            class="apply__cover"
          />

          <div class="apply__prices">
            <div class="apply__prices-title">{{ t('project.prices') }}</div>
            <div v-for="m in milestones" :key="m.id" class="price-row">
              <div class="price-row__label">
                <span class="price-row__seq">{{ t('project.milestoneN', { n: m.seq }) }}</span>
                <span class="price-row__name">{{ m.title }}</span>
                <span class="q-muted price-row__ref">{{ t('project.reference') }} {{ formatUsd(m.talentAmountUsd) }}</span>
              </div>
              <div class="price-row__input">
                <span class="price-row__currency">$</span>
                <van-field
                  v-model="prices[String(m.id)]"
                  v-testid="`apply-price-${m.id}`"
                  type="number"
                  maxlength="9"
                  input-align="right"
                  placeholder="0.00"
                  class="price-row__field"
                />
              </div>
            </div>
          </div>

          <div class="apply__total">
            <span>{{ t('project.total') }}</span>
            <span class="q-amount q-amount--accent apply__total-amount" data-testid="apply-total">{{ formatUsd(total) }}</span>
          </div>
          <p class="apply__hint">
            {{ meta.feePct !== null ? t('project.priceHint', { fee: meta.feePct }) : t('project.priceHintNoFee') }}
            {{ t('project.cnyHint') }}
          </p>
          <van-button
            block
            round
            type="primary"
            size="large"
            :loading="applying"
            data-testid="apply-submit"
            @click="onApply"
          >
            {{ t('project.submit') }}
          </van-button>
        </section>
      </div>
    </StateView>

    <ConsentSheet
      v-model:show="showConsent"
      :loading="consentLoading"
      @accept="acceptConsent"
      @cancel="showConsent = false"
    />
  </div>
</template>

<style scoped>
.head__title {
  margin: 0 0 10px;
  font-size: 20px;
  line-height: 1.4;
  font-weight: 700;
}
.head__meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.head__cat {
  background: var(--q-primary-soft);
  color: var(--q-primary);
}
.head__applied {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.head__budget {
  margin-top: 16px;
  padding: 14px;
  border-radius: 10px;
  background: linear-gradient(135deg, #fff8f0 0%, #fff3e6 100%);
}
.head__amount {
  font-size: 26px;
  line-height: 1.3;
  margin-top: 2px;
}
.head__hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #a6703d;
}
.facts {
  display: grid;
  grid-template-columns: auto 1fr;
  column-gap: 24px;
  gap: 12px;
  margin: 14px 0 0;
}
.facts__item dt {
  font-size: 12px;
  color: var(--q-text-3);
}
.facts__item:last-child {
  text-align: right;
}
.facts__item dd {
  margin: 2px 0 0;
  white-space: nowrap;
  font-size: 14px;
  color: var(--q-text);
}
.detail__desc {
  margin: 0;
  color: var(--q-text-2);
  line-height: 1.75;
}
.detail__sub {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}
.timeline__item {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 16px;
}
.timeline__item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 26px;
  bottom: 2px;
  border-left: 1px dashed #c9cdd4;
}
.timeline__dot {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--q-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.timeline__body {
  flex: 1;
  min-width: 0;
}
.timeline__title {
  font-weight: 600;
}
.timeline__deliverable {
  margin: 6px 0 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f8fa;
  font-size: 13px;
  line-height: 1.6;
  color: var(--q-text-2);
  white-space: pre-wrap;
}
.timeline__label {
  margin-right: 6px;
  color: var(--q-text-3);
}
.applied {
  text-align: center;
  padding: 24px 16px;
}
.applied__icon {
  font-size: 44px;
  color: var(--q-success);
}
.applied__title {
  margin: 8px 0 4px;
  font-size: 17px;
}
.applied .van-button {
  margin-top: 12px;
}
.q-card--apply {
  padding-bottom: 20px;
}
.apply__notice {
  margin-bottom: 12px;
}
.apply__cover {
  padding: 0;
}
.apply__cover :deep(.van-field__label) {
  margin-bottom: 6px;
}
.apply__cover :deep(.van-field__body) {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
}
.apply__cover::after {
  display: none;
}
.apply__prices {
  margin-top: 16px;
}
.apply__prices-title {
  margin-bottom: 8px;
  color: var(--q-text-2);
}
.price-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}
.price-row__label {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.price-row__seq {
  font-size: 12px;
  color: var(--q-primary);
}
.price-row__name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.price-row__ref {
  font-size: 12px;
}
.price-row__input {
  flex: none;
  width: 132px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid var(--q-border);
  background: #fff;
}
.price-row__input:focus-within {
  border-color: var(--q-primary);
}
.price-row__currency {
  color: var(--q-text-3);
}
.price-row__field {
  padding: 8px 0 8px 4px;
  font-size: 16px;
  font-weight: 600;
}
.price-row__field::after {
  display: none;
}
.apply__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 14px;
}
.apply__total-amount {
  font-size: 22px;
}
.apply__mode {
  margin-bottom: 14px;
}
.apply__team-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.6;
}
.apply__team {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 10px;
  background: #f7f9ff;
  border: 1px solid #dfe6fb;
}
.apply__teams {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 10px;
}
.apply__team-name {
  font-weight: 600;
  margin-bottom: 8px;
}
.apply__split-head {
  margin-top: 4px;
}
.apply__even {
  border: 0;
  background: none;
  color: var(--q-primary);
  font-size: 13px;
}
.share__alias {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
.share__input {
  width: 110px;
}
.apply__total.is-invalid {
  color: var(--q-danger);
}
.apply__hint {
  margin: 6px 0 16px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--q-text-3);
}
</style>
