<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { CreditCard } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { paymentApi } from '@/api/user'
import type { Payment } from '@/api/types'
import { errorText } from '@/utils/errors'
import { money } from '@/utils/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const paymentId = computed(() => String(route.params.paymentId))
const payment = ref<Payment | null>(null)
const loading = ref(true)
const paying = ref(false)
const error = ref('')

const contractId = computed<string | null>(() => {
  if (payment.value?.contractId) return String(payment.value.contractId)
  if (typeof route.query.contract === 'string') return route.query.contract
  try {
    return sessionStorage.getItem(`qiao.pay.${paymentId.value}`)
  } catch {
    return null
  }
})

const backTo = computed(() => (contractId.value ? `/client/contracts/${contractId.value}` : '/client'))

onMounted(async () => {
  try {
    payment.value = await paymentApi.get(paymentId.value)
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
})

async function pay() {
  paying.value = true
  error.value = ''
  try {
    await paymentApi.confirmFake(paymentId.value)
    try {
      sessionStorage.removeItem(`qiao.pay.${paymentId.value}`)
    } catch {
      /* ignore */
    }
    await router.replace(contractId.value ? { path: backTo.value, query: { paid: '1' } } : '/client')
  } catch (e) {
    error.value = errorText(e)
  } finally {
    paying.value = false
  }
}
</script>

<template>
  <div class="q-container q-page pay">
    <div class="q-card pay-card">
      <div class="test-badge" data-testid="pay-test-mode">{{ t('pay.testMode') }}</div>
      <div class="pay-icon"><el-icon :size="26"><CreditCard /></el-icon></div>
      <h1>{{ t('pay.title') }}</h1>
      <p class="q-muted">{{ t('pay.subtitle') }}</p>

      <el-skeleton v-if="loading" :rows="2" animated />
      <template v-else-if="payment">
        <div class="amount q-num" data-testid="pay-amount">{{ money(payment.amountUsd) }}</div>
        <div class="status">
          <StatusTag :status="payment.status" testid="pay-status" />
        </div>
        <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" data-testid="pay-error" />
        <el-button
          v-if="payment.status === 'PENDING'"
          type="primary"
          size="large"
          class="full"
          :loading="paying"
          data-testid="pay-confirm"
          @click="pay"
        >
          {{ t('pay.pay') }}
        </el-button>
        <el-alert v-else-if="payment.status === 'PAID'" type="success" :closable="false" show-icon :title="t('pay.alreadyPaid')" />
        <p class="q-muted q-small note">{{ t('pay.note') }}</p>
      </template>
      <el-alert v-else type="error" :closable="false" show-icon :title="error || t('errors.NOT_FOUND')" data-testid="pay-error" />

      <router-link :to="backTo" class="back q-small" data-testid="pay-back">{{ t('pay.back') }}</router-link>
    </div>
  </div>
</template>

<style scoped>
.pay {
  display: flex;
  justify-content: center;
}
.pay-card {
  width: 100%;
  max-width: 420px;
  text-align: center;
  padding: 32px 28px;
  margin-top: 24px;
  position: relative;
}
.test-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: #8a5300;
  background: #fff4e0;
  border-radius: 999px;
  padding: 2px 8px;
}
.pay-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: var(--q-accent-soft);
  color: var(--q-accent);
  display: grid;
  place-items: center;
  margin: 0 auto 12px;
}
.amount {
  font-size: 2.2rem;
  font-weight: 700;
  margin: 16px 0 8px;
}
.status {
  margin-bottom: 20px;
}
.full {
  width: 100%;
}
.note {
  margin-top: 16px;
}
.back {
  display: inline-block;
  margin-top: 8px;
}
</style>
