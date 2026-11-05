<script setup lang="ts">
// Separate consent SHARE_PROFILE_PSEUDONYMIZED (docs/SPEC.md §1.5, §1.8): lists exactly what clients see.
defineProps<{ show: boolean; loading?: boolean }>()
defineEmits<{ 'update:show': [value: boolean]; accept: []; cancel: [] }>()

const visible = ['alias', 'headline', 'skills', 'years', 'region', 'languages', 'rate', 'availability', 'rating', 'bid']
const hidden = ['identity', 'cv']
</script>

<template>
  <van-popup
    :show="show"
    position="bottom"
    round
    closeable
    safe-area-inset-bottom
    class="consent-sheet"
    data-testid="consent-pseudonymized-sheet"
    @update:show="(v: boolean) => $emit('update:show', v)"
  >
    <div class="consent-sheet__body">
      <div class="consent-sheet__icon"><van-icon name="shield-o" /></div>
      <h3 class="consent-sheet__title">{{ $t('consentSheet.title') }}</h3>
      <p class="consent-sheet__intro">{{ $t('consentSheet.intro') }}</p>
      <ul class="consent-sheet__list consent-sheet__list--visible">
        <li v-for="k in visible" :key="k">
          <van-icon name="eye-o" />
          <span>{{ $t(`consentSheet.visible.${k}`) }}</span>
        </li>
      </ul>
      <p class="consent-sheet__subtitle">{{ $t('consentSheet.hiddenTitle') }}</p>
      <ul class="consent-sheet__list consent-sheet__list--hidden">
        <li v-for="k in hidden" :key="k">
          <van-icon name="closed-eye" />
          <span>{{ $t(`consentSheet.hidden.${k}`) }}</span>
        </li>
      </ul>
      <p class="consent-sheet__note">{{ $t('consentSheet.note') }}</p>
      <router-link class="consent-sheet__link" to="/legal/privacy" data-testid="consent-pseudonymized-privacy">
        {{ $t('legal.docs.privacy') }}
      </router-link>
    </div>
    <div class="consent-sheet__footer">
      <van-button block plain data-testid="consent-pseudonymized-cancel" @click="$emit('cancel')">
        {{ $t('consentSheet.cancel') }}
      </van-button>
      <van-button
        block
        type="primary"
        :loading="loading"
        data-testid="consent-pseudonymized-accept"
        @click="$emit('accept')"
      >
        {{ $t('consentSheet.accept') }}
      </van-button>
    </div>
  </van-popup>
</template>

<style scoped>
.consent-sheet__body {
  max-height: 70vh;
  overflow-y: auto;
  padding: 28px 20px 8px;
}
.consent-sheet__icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 22px;
  margin-bottom: 12px;
}
.consent-sheet__title {
  margin: 0 0 8px;
  font-size: 18px;
}
.consent-sheet__intro,
.consent-sheet__note {
  margin: 0 0 12px;
  color: var(--q-text-2);
  font-size: 14px;
  line-height: 1.6;
}
.consent-sheet__note {
  font-size: 12px;
  color: var(--q-text-3);
  margin-top: 12px;
}
.consent-sheet__subtitle {
  margin: 16px 0 8px;
  font-weight: 600;
}
.consent-sheet__list {
  list-style: none;
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  display: grid;
  gap: 8px;
}
.consent-sheet__list--visible {
  background: var(--q-primary-soft);
  grid-template-columns: 1fr 1fr;
}
.consent-sheet__list--hidden {
  background: #f7f8fa;
}
.consent-sheet__list li {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.5;
  color: var(--q-text);
}
.consent-sheet__list .van-icon {
  margin-top: 3px;
  color: var(--q-primary);
}
.consent-sheet__list--hidden .van-icon {
  color: var(--q-text-3);
}
.consent-sheet__link {
  font-size: 13px;
}
.consent-sheet__footer {
  display: flex;
  gap: 12px;
  padding: 12px 20px 16px;
}
</style>
