<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BrandLogo from '@/components/BrandLogo.vue'
import { useAuthStore } from '@/stores/auth'
import { PROHIBITED_CATEGORIES } from '@/data/categories'
import { applyLocale, currentLocale } from '@/i18n'

const { t } = useI18n()
const auth = useAuthStore()

const features = [
  { key: 'project', icon: 'todo-list-o' },
  { key: 'pay', icon: 'balance-o' },
  { key: 'vpn', icon: 'passed' },
  { key: 'privacy', icon: 'shield-o' },
]
const steps = ['s1', 's2', 's3', 's4', 's5', 's6']
const otherLocale = computed(() => (currentLocale() === 'en' ? 'zh-CN' : 'en'))
const otherLocaleLabel = computed(() => (otherLocale.value === 'en' ? 'EN' : '中文'))

function toggleLocale() {
  applyLocale(otherLocale.value)
}
</script>

<template>
  <div class="landing" data-testid="landing">
    <header class="hero">
      <div class="hero__bar">
        <div class="q-row">
          <BrandLogo :size="30" light />
          <span class="hero__brand">{{ t('app.name') }}</span>
        </div>
        <button type="button" class="hero__lang" data-testid="landing-locale" @click="toggleLocale">
          {{ otherLocaleLabel }}
        </button>
      </div>
      <p class="hero__eyebrow">{{ t('landing.eyebrow') }}</p>
      <h1 class="hero__title" data-testid="landing-title">{{ t('landing.title') }}</h1>
      <p class="hero__subtitle">{{ t('landing.subtitle') }}</p>
      <div class="hero__cta">
        <van-button
          v-if="auth.isLoggedIn"
          block
          round
          class="hero__btn"
          to="/projects"
          data-testid="landing-enter"
        >
          {{ t('landing.ctaEnter') }}
        </van-button>
        <van-button v-else block round class="hero__btn" to="/login" data-testid="landing-login">
          {{ t('landing.ctaLogin') }}
        </van-button>
      </div>
      <div class="hero__pills">
        <span><van-icon name="passed" /> {{ t('landing.features.vpnTitle') }}</span>
        <span><van-icon name="passed" /> {{ t('landing.features.payTitle') }}</span>
      </div>
    </header>

    <main class="landing__main">
      <section class="q-card">
        <h2 class="q-section-title">{{ t('landing.whatTitle') }}</h2>
        <p class="landing__p">{{ t('landing.whatBody') }}</p>
      </section>

      <section class="features">
        <div v-for="f in features" :key="f.key" class="feature" :data-testid="`landing-feature-${f.key}`">
          <div class="feature__icon"><van-icon :name="f.icon" /></div>
          <h3 class="feature__title">{{ t(`landing.features.${f.key}Title`) }}</h3>
          <p class="feature__body">{{ t(`landing.features.${f.key}Body`) }}</p>
        </div>
      </section>

      <section class="q-card">
        <h2 class="q-section-title">{{ t('landing.stepsTitle') }}</h2>
        <ol class="steps">
          <li v-for="(s, i) in steps" :key="s" class="steps__item">
            <span class="steps__num">{{ i + 1 }}</span>
            <span>{{ t(`landing.steps.${s}`) }}</span>
          </li>
        </ol>
      </section>

      <section class="q-card prohibited" data-testid="landing-prohibited">
        <h2 class="q-section-title">
          <span class="q-row"><van-icon name="warning-o" class="prohibited__icon" />{{ t('landing.prohibitedTitle') }}</span>
        </h2>
        <p class="landing__p">{{ t('landing.prohibitedIntro') }}</p>
        <ul class="prohibited__list">
          <li v-for="code in PROHIBITED_CATEGORIES" :key="code" :data-testid="`landing-prohibited-${code}`">
            <strong>{{ t(`categories.${code}`) }}</strong>
            <span>{{ t(`landing.prohibited.${code}`) }}</span>
          </li>
        </ul>
        <p class="landing__p landing__p--small">{{ t('landing.prohibitedOutro') }}</p>
      </section>

      <section class="q-card">
        <h2 class="q-section-title">{{ t('landing.dataTitle') }}</h2>
        <p class="landing__p">{{ t('landing.dataBody') }}</p>
      </section>

      <div class="landing__bottom-cta">
        <van-button
          v-if="!auth.isLoggedIn"
          block
          round
          type="primary"
          to="/login"
          data-testid="landing-login-bottom"
        >
          {{ t('landing.ctaLogin') }}
        </van-button>
      </div>

      <footer class="landing__footer">
        <nav class="landing__legal">
          <router-link to="/legal/terms" data-testid="landing-legal-terms">{{ t('legal.docs.terms') }}</router-link>
          <router-link to="/legal/privacy" data-testid="landing-legal-privacy">{{ t('legal.docs.privacy') }}</router-link>
          <router-link to="/legal/cross-border" data-testid="landing-legal-cross-border">
            {{ t('legal.docs.cross-border') }}
          </router-link>
        </nav>
        <p>{{ t('landing.footer') }}</p>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.landing {
  min-height: 100vh;
  background: var(--q-bg);
}
.hero {
  position: relative;
  padding: calc(14px + env(safe-area-inset-top)) 20px 64px;
  color: #fff;
  background:
    radial-gradient(120% 80% at 100% 0%, rgba(255, 255, 255, 0.18) 0%, rgba(255, 255, 255, 0) 60%),
    linear-gradient(160deg, #2f5fe3 0%, #2346b8 55%, #1a3591 100%);
  overflow: hidden;
}
.hero::after {
  content: '';
  position: absolute;
  right: -40px;
  bottom: 18px;
  width: 260px;
  height: 120px;
  border: 10px solid rgba(255, 255, 255, 0.08);
  border-bottom: 0;
  border-radius: 260px 260px 0 0;
  pointer-events: none;
}
.hero__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 36px;
}
.hero__brand {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.hero__lang {
  height: 28px;
  padding: 0 12px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.hero__eyebrow {
  display: inline-block;
  margin: 0 0 12px;
  padding: 3px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 12px;
}
.hero__title {
  margin: 0 0 12px;
  font-size: 28px;
  line-height: 1.3;
  font-weight: 700;
  white-space: pre-line;
  letter-spacing: 0.5px;
}
.hero__subtitle {
  margin: 0 0 24px;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.86);
}
.hero__btn {
  height: 46px;
  border: 0;
  color: var(--q-primary);
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 6px 20px rgba(10, 30, 90, 0.25);
}
.hero__pills {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 16px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}
.hero__pills .van-icon {
  margin-right: 2px;
  vertical-align: -2px;
}
.landing__main {
  position: relative;
  margin-top: -36px;
  padding: 0 12px 24px;
}
.landing__p {
  margin: 0;
  color: var(--q-text-2);
  line-height: 1.75;
}
.landing__p + .landing__p,
.landing__p--small {
  margin-top: 12px;
}
.landing__p--small {
  font-size: 12px;
  color: var(--q-text-3);
  line-height: 1.6;
}
.features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}
.feature {
  background: var(--q-card);
  border-radius: var(--q-radius);
  padding: 14px 12px;
}
.feature__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 18px;
  margin-bottom: 10px;
}
.feature__title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
}
.feature__body {
  margin: 0;
  font-size: 12px;
  color: var(--q-text-3);
  line-height: 1.6;
}
.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0;
}
.steps__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  color: var(--q-text-2);
}
.steps__item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 11px;
  top: 34px;
  height: 14px;
  border-left: 1px dashed #c9cdd4;
}
.steps__num {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.prohibited__icon {
  color: var(--q-warning);
  font-size: 18px;
}
.prohibited__list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  border-radius: 10px;
  background: #fafafa;
  border: 1px solid var(--q-border);
}
.prohibited__list li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  font-size: 13px;
}
.prohibited__list li + li {
  border-top: 1px solid var(--q-border);
}
.prohibited__list strong {
  font-weight: 600;
  color: var(--q-text);
}
.prohibited__list span {
  font-size: 12px;
  color: var(--q-text-3);
  line-height: 1.55;
}
.landing__bottom-cta {
  margin: 20px 4px 8px;
}
.landing__footer {
  padding: 16px 0 calc(16px + env(safe-area-inset-bottom));
  text-align: center;
  color: var(--q-text-3);
  font-size: 12px;
}
.landing__legal {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px 16px;
  margin-bottom: 8px;
}
.landing__legal a {
  color: var(--q-text-2);
}
</style>
