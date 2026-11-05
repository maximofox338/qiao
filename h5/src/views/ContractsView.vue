<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import StateView from '@/components/StateView.vue'
import { engineerApi } from '@/api/endpoints'
import type { ContractSummary } from '@/api/types'
import { formatDate, formatUsd } from '@/utils/format'
import { errorMessage } from '@/utils/errors'

const { t } = useI18n()
const contracts = ref<ContractSummary[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref<string | null>(null)

async function load() {
  error.value = null
  try {
    contracts.value = await engineerApi.contracts()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}
onMounted(load)

function progress(c: ContractSummary) {
  const ms = c.milestones ?? []
  if (!ms.length) return null
  const done = ms.filter((m) => m.status === 'ACCEPTED').length
  return { done, total: ms.length, pct: Math.round((done / ms.length) * 100) }
}
</script>

<template>
  <div data-testid="contracts-page">
    <PageHeader :title="t('contracts.title')" />
    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div class="q-page contracts__page">
        <StateView
          :loading="loading"
          :error="error"
          :empty="!contracts.length"
          :empty-text="t('contracts.empty')"
          @retry="load"
        >
          <template #action>
            <p class="q-muted">{{ t('contracts.emptyHint') }}</p>
          </template>
          <router-link
            v-for="c in contracts"
            :key="c.id"
            :to="`/contracts/${c.id}`"
            class="q-card contract"
            :data-testid="`contract-card-${c.id}`"
          >
            <div class="contract__head">
              <h3 class="contract__title">{{ c.projectTitle || t('common.projectRef', { id: c.projectId ?? c.id }) }}</h3>
              <StatusTag kind="contract" :status="c.status" />
            </div>
            <div v-if="c.status === 'PENDING_SIGNATURES'" class="contract__todo">
              <van-icon name="edit" /> {{ t('contracts.signNeeded') }}
            </div>
            <div v-if="progress(c)" class="contract__progress">
              <van-progress
                :percentage="progress(c)!.pct"
                :show-pivot="false"
                stroke-width="6"
                color="var(--q-primary)"
                track-color="#eef0f3"
              />
              <span class="q-muted">{{ t('contracts.progress', { done: progress(c)!.done, total: progress(c)!.total }) }}</span>
            </div>
            <div class="contract__foot">
              <span class="q-muted">{{ formatDate(c.activatedAt || c.createdAt) }}</span>
              <span v-if="c.talentTotalUsd !== undefined && c.talentTotalUsd !== null">
                <span class="q-muted">{{ t('contracts.total') }}</span>
                <span class="q-amount contract__amount">{{ formatUsd(c.talentTotalUsd) }}</span>
              </span>
            </div>
          </router-link>
        </StateView>
      </div>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.contracts__page {
  min-height: calc(100vh - 100px);
}
.contract {
  display: block;
  color: inherit;
}
.contract__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.contract__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}
.contract__todo {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--q-warning-soft);
  color: var(--q-warning);
  font-size: 12px;
}
.contract__progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}
.contract__progress .van-progress {
  flex: 1;
}
.contract__foot {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
  font-size: 13px;
}
.contract__amount {
  margin-left: 6px;
  font-size: 16px;
}
</style>
