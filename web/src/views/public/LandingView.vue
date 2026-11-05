<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Checked, Coin, Lock, Medal, CircleCheckFilled, Clock, Wallet } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { money } from '@/utils/format'

const { t } = useI18n()
const router = useRouter()
const user = useUserStore()

const values = [
  { key: 'vetted', icon: Medal },
  { key: 'legal', icon: Checked },
  { key: 'milestones', icon: Coin },
  { key: 'privacy', icon: Lock },
]
const steps = ['company', 'project', 'choose', 'fund']

const sample = computed(() => [
  { title: t('landing.sample.m1'), amount: money(2400), status: t('landing.sample.accepted'), icon: CircleCheckFilled, cls: 'ok' },
  { title: t('landing.sample.m2'), amount: money(3100), status: t('landing.sample.funded'), icon: Wallet, cls: 'funded' },
  { title: t('landing.sample.m3'), amount: money(1900), status: t('landing.sample.pending'), icon: Clock, cls: 'pending' },
])

function start() {
  void router.push(user.isLoggedIn ? '/client/projects/new' : { name: 'login', query: { redirect: '/client/projects/new' } })
}
</script>

<template>
  <div class="landing">
    <section class="hero">
      <div class="q-container hero-inner">
        <div class="hero-copy">
          <p class="eyebrow" data-testid="landing-eyebrow">{{ t('landing.eyebrow') }}</p>
          <h1 data-testid="landing-title">{{ t('landing.title') }}</h1>
          <p class="lead">{{ t('landing.lead') }}</p>
          <div class="q-actions">
            <el-button type="primary" size="large" data-testid="landing-cta" @click="start">{{ t('landing.cta') }}</el-button>
            <el-button size="large" data-testid="landing-how" @click="router.push({ hash: '#how' })">{{ t('landing.howLink') }}</el-button>
          </div>
          <p class="q-muted q-small hero-note">{{ t('landing.note') }}</p>
        </div>
        <div class="hero-card" aria-hidden="true">
          <div class="hc-head">
            <div>
              <div class="hc-title">{{ t('landing.sample.project') }}</div>
              <div class="q-muted q-small">{{ t('landing.sample.meta') }}</div>
            </div>
            <span class="hc-badge">{{ t('landing.sample.active') }}</span>
          </div>
          <ol class="hc-list">
            <li v-for="(m, i) in sample" :key="i" :class="m.cls">
              <el-icon class="hc-icon"><component :is="m.icon" /></el-icon>
              <span class="hc-m">{{ m.title }}</span>
              <span class="hc-amount q-num">{{ m.amount }}</span>
              <span class="hc-status">{{ m.status }}</span>
            </li>
          </ol>
          <div class="hc-foot q-small">{{ t('landing.sample.foot') }}</div>
        </div>
      </div>
    </section>

    <section class="q-container section">
      <div class="values q-grid q-grid-4">
        <div v-for="v in values" :key="v.key" class="value" :data-testid="`landing-value-${v.key}`">
          <div class="value-icon"><el-icon :size="20"><component :is="v.icon" /></el-icon></div>
          <h3>{{ t(`landing.values.${v.key}.title`) }}</h3>
          <p class="q-muted">{{ t(`landing.values.${v.key}.text`) }}</p>
        </div>
      </div>
    </section>

    <section id="how" class="section how">
      <div class="q-container">
        <h2>{{ t('landing.how.title') }}</h2>
        <ol class="steps q-grid q-grid-4">
          <li v-for="(s, i) in steps" :key="s" class="step">
            <span class="step-n">{{ i + 1 }}</span>
            <h3>{{ t(`landing.how.${s}.title`) }}</h3>
            <p class="q-muted">{{ t(`landing.how.${s}.text`) }}</p>
          </li>
        </ol>
      </div>
    </section>

    <section class="q-container section">
      <div class="q-grid q-grid-2">
        <div class="q-card rule">
          <h3>{{ t('landing.rules.projects.title') }}</h3>
          <p class="q-muted">{{ t('landing.rules.projects.text') }}</p>
        </div>
        <div class="q-card rule">
          <h3>{{ t('landing.rules.categories.title') }}</h3>
          <p class="q-muted">{{ t('landing.rules.categories.text') }}</p>
        </div>
      </div>
    </section>

    <section class="q-container section final">
      <div class="final-box">
        <div>
          <h2>{{ t('landing.final.title') }}</h2>
          <p class="q-muted">{{ t('landing.final.text') }}</p>
        </div>
        <el-button type="primary" size="large" data-testid="landing-cta-bottom" @click="start">{{ t('landing.final.cta') }}</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  background: linear-gradient(180deg, #ffffff 0%, var(--q-bg) 100%);
  border-bottom: 1px solid var(--q-border);
}
.hero-inner {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 48px;
  align-items: center;
  padding-top: 64px;
  padding-bottom: 64px;
}
.eyebrow {
  color: var(--q-accent);
  font-weight: 650;
  font-size: 13px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin-bottom: 12px;
}
.hero h1 {
  font-size: 2.4rem;
  letter-spacing: -0.01em;
  margin-bottom: 16px;
}
.lead {
  font-size: 1.1rem;
  color: var(--q-text-2);
  margin-bottom: 24px;
  max-width: 560px;
}
.hero-note {
  margin-top: 16px;
}
.hero-card {
  background: var(--q-surface);
  border: 1px solid var(--q-border);
  border-radius: 14px;
  box-shadow: 0 12px 32px rgba(16, 24, 40, 0.08);
  padding: 20px;
}
.hc-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--q-border);
}
.hc-title {
  font-weight: 650;
}
.hc-badge {
  font-size: 12px;
  font-weight: 600;
  color: #1e7a46;
  background: #e8f5ee;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
}
.hc-list {
  list-style: none;
  padding: 0;
  margin: 8px 0;
}
.hc-list li {
  display: grid;
  grid-template-columns: 22px 1fr auto;
  grid-template-areas: 'icon title amount' 'icon status status';
  column-gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--q-border);
}
.hc-list li:last-child {
  border-bottom: 0;
}
.hc-icon {
  grid-area: icon;
  margin-top: 3px;
}
.hc-m {
  grid-area: title;
  font-weight: 550;
}
.hc-amount {
  grid-area: amount;
  font-weight: 600;
}
.hc-status {
  grid-area: status;
  font-size: 13px;
  color: var(--q-text-3);
}
.ok .hc-icon {
  color: #1e7a46;
}
.funded .hc-icon {
  color: var(--q-accent);
}
.pending .hc-icon {
  color: var(--q-text-3);
}
.hc-foot {
  color: var(--q-text-3);
  background: var(--q-bg);
  border-radius: 8px;
  padding: 8px 10px;
}
.section {
  padding-top: 56px;
  padding-bottom: 8px;
}
.value-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--q-accent-soft);
  color: var(--q-accent);
  margin-bottom: 12px;
}
.value h3 {
  margin-bottom: 6px;
}
.how {
  margin-top: 48px;
  background: var(--q-surface);
  border-top: 1px solid var(--q-border);
  border-bottom: 1px solid var(--q-border);
  padding-bottom: 48px;
}
.steps {
  list-style: none;
  padding: 0;
  margin: 24px 0 0;
}
.step-n {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1.5px solid var(--q-accent);
  color: var(--q-accent);
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 10px;
}
.rule {
  margin-bottom: 0;
}
.final {
  padding-bottom: 64px;
}
.final-box {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
  border-radius: 14px;
  padding: 28px;
  background: var(--q-accent-soft);
  border: 1px solid var(--el-color-primary-light-8);
}
.final-box h2 {
  margin-bottom: 4px;
}
.final-box p {
  margin: 0;
}

@media (max-width: 900px) {
  .hero-inner {
    grid-template-columns: minmax(0, 1fr);
    gap: 32px;
    padding-top: 40px;
    padding-bottom: 40px;
  }
  .hero h1 {
    font-size: 1.9rem;
  }
}
@media (max-width: 640px) {
  .hero h1 {
    font-size: 1.6rem;
  }
  .section {
    padding-top: 36px;
  }
  .final-box {
    padding: 20px;
  }
}
</style>
