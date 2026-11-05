<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Star } from '@element-plus/icons-vue'
import type { PseudonymizedEngineer } from '@/api/types'
import { money } from '@/utils/format'

const props = defineProps<{ engineer: PseudonymizedEngineer; testidPrefix: string }>()
const { t, te } = useI18n()

const languages = computed(() => {
  const e = props.engineer
  if (e.languages?.length) return e.languages.join(', ')
  const parts = [t('engineer.chinese')]
  const level = (v?: string) => (v && te(`engineer.level.${v}`) ? t(`engineer.level.${v}`) : v)
  if (e.englishLevel && e.englishLevel !== 'NONE') parts.push(`${t('engineer.english')} (${level(e.englishLevel)})`)
  if (e.spanishLevel && e.spanishLevel !== 'NONE') parts.push(`${t('engineer.spanish')} (${level(e.spanishLevel)})`)
  return parts.join(', ')
})
</script>

<template>
  <div class="eng">
    <div class="eng-head">
      <div class="eng-avatar" aria-hidden="true">{{ engineer.alias?.slice(-2) || '··' }}</div>
      <div class="eng-id">
        <div class="eng-alias" :data-testid="`${testidPrefix}-alias`">{{ engineer.alias }}</div>
        <div class="eng-headline">{{ engineer.headline }}</div>
      </div>
      <div v-if="engineer.ratingCount" class="eng-rating" :title="t('engineer.rating')">
        <el-icon><Star /></el-icon>
        {{ Number(engineer.ratingAvg ?? 0).toFixed(1) }}
        <span class="q-muted">({{ engineer.ratingCount }})</span>
      </div>
    </div>
    <div class="q-tags eng-skills">
      <el-tag v-for="s in engineer.skills ?? []" :key="s" size="small" type="info" effect="plain">{{ s }}</el-tag>
    </div>
    <dl class="eng-facts">
      <div>
        <dt>{{ t('engineer.years') }}</dt>
        <dd>{{ t('engineer.yearsValue', { n: engineer.yearsExperience ?? 0 }) }}</dd>
      </div>
      <div>
        <dt>{{ t('engineer.region') }}</dt>
        <dd>{{ engineer.region || '—' }}</dd>
      </div>
      <div>
        <dt>{{ t('engineer.languages') }}</dt>
        <dd>{{ languages }}</dd>
      </div>
      <div>
        <dt>{{ t('engineer.rate') }}</dt>
        <dd>{{ t('engineer.rateValue', { rate: money(engineer.hourlyRateUsd) }) }}</dd>
      </div>
      <div>
        <dt>{{ t('engineer.availability') }}</dt>
        <dd>{{ t('engineer.availabilityValue', { n: engineer.availabilityHoursWeek ?? 0 }) }}</dd>
      </div>
    </dl>
  </div>
</template>

<style scoped>
.eng-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.eng-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--q-accent-soft);
  color: var(--q-accent);
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 13px;
  flex-shrink: 0;
}
.eng-id {
  min-width: 0;
  flex: 1;
}
.eng-alias {
  font-weight: 650;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  letter-spacing: 0.02em;
}
.eng-headline {
  color: var(--q-text-2);
  overflow-wrap: anywhere;
}
.eng-rating {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #b7791f;
  font-weight: 600;
  white-space: nowrap;
}
.eng-skills {
  margin: 10px 0;
}
.eng-facts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px 16px;
  margin: 0;
}
.eng-facts dt {
  color: var(--q-text-3);
  font-size: 12px;
}
.eng-facts dd {
  margin: 0;
  font-weight: 550;
}
</style>
