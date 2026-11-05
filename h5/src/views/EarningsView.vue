<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import StateView from '@/components/StateView.vue'
import { engineerApi } from '@/api/endpoints'
import type { Earning } from '@/api/types'
import { formatDate, formatUsd, toNumber } from '@/utils/format'
import { errorMessage } from '@/utils/errors'

const { t } = useI18n()
const items = ref<Earning[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref<string | null>(null)

async function load() {
  error.value = null
  try {
    items.value = await engineerApi.earnings()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}
onMounted(load)

const totals = computed(() => {
  let accepted = 0
  let paid = 0
  let pending = 0
  for (const e of items.value) {
    const amount = toNumber(e.talentAmountUsd)
    if (e.milestoneStatus === 'ACCEPTED') accepted += amount
    if (e.settlementStatus === 'PAID') paid += amount
    else if (e.milestoneStatus === 'ACCEPTED') pending += amount
  }
  return { accepted, paid, pending }
})
</script>

<template>
  <div data-testid="earnings-page">
    <PageHeader :title="t('earnings.title')" back="/me" />
    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div class="q-page earnings__page">
        <section class="summary" data-testid="earnings-summary">
          <div class="summary__main">
            <div class="summary__label">{{ t('earnings.accepted') }}</div>
            <div class="summary__value">{{ formatUsd(totals.accepted) }}</div>
          </div>
          <div class="summary__row">
            <div>
              <div class="summary__label">{{ t('earnings.paid') }}</div>
              <div class="summary__sub">{{ formatUsd(totals.paid) }}</div>
            </div>
            <div>
              <div class="summary__label">{{ t('earnings.pending') }}</div>
              <div class="summary__sub">{{ formatUsd(totals.pending) }}</div>
            </div>
          </div>
        </section>
        <p class="q-muted earnings__note">{{ t('earnings.note') }}</p>

        <StateView :loading="loading" :error="error" :empty="!items.length" :empty-text="t('earnings.empty')" @retry="load">
          <div class="q-card q-card--flush">
            <div
              v-for="e in items"
              :key="e.contractMilestoneId"
              class="earning"
              :data-testid="`earning-${e.contractMilestoneId}`"
            >
              <div class="earning__info">
                <div class="earning__title">{{ e.projectTitle }}</div>
                <div class="q-row earning__tags">
                  <StatusTag kind="milestone" :status="e.milestoneStatus" />
                  <span v-if="e.paidAt" class="q-muted">{{ t('earnings.paidAt', { date: formatDate(e.paidAt) }) }}</span>
                </div>
              </div>
              <div class="earning__right">
                <div class="q-amount">{{ formatUsd(e.talentAmountUsd) }}</div>
                <StatusTag
                  kind="settlement"
                  :status="e.settlementStatus"
                  :data-testid="`earning-settlement-${e.contractMilestoneId}`"
                />
              </div>
            </div>
          </div>
        </StateView>
      </div>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.earnings__page {
  min-height: calc(100vh - 60px);
}
.summary {
  padding: 18px 16px;
  border-radius: var(--q-radius);
  color: #fff;
  background: linear-gradient(140deg, #2f5fe3 0%, #1f40a8 100%);
}
.summary__label {
  font-size: 12px;
  opacity: 0.8;
}
.summary__value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.summary__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
}
.summary__sub {
  margin-top: 2px;
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.earnings__note {
  margin: 10px 4px 14px;
  font-size: 12px;
  line-height: 1.6;
}
.earning {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}
.earning + .earning {
  border-top: 1px solid #f2f3f5;
}
.earning__info {
  flex: 1;
  min-width: 0;
}
.earning__title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.earning__tags {
  margin-top: 6px;
  flex-wrap: wrap;
}
.earning__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
</style>
