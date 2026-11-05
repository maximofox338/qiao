<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Download, Paperclip, Refresh, CircleCheckFilled, Star, StarFilled } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ReasonDialog from '@/components/ReasonDialog.vue'
import { clientApi, contractApi } from '@/api/user'
import { downloadFile } from '@/api/http'
import type { Attachment, Contract, ContractDocument, ContractMilestone, Message } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { errorText, notifyError, notifySuccess } from '@/utils/errors'
import { bytes, date, dateTime, money } from '@/utils/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const user = useUserStore()

const id = computed(() => String(route.params.id))
const contract = ref<Contract | null>(null)
const loading = ref(true)
const loadError = ref('')
const busy = ref<string | null>(null)
const paid = computed(() => route.query.paid === '1')

const messages = ref<Message[]>([])
const messageBody = ref('')
const sending = ref(false)
const messagesError = ref('')

const uploaded = ref<Attachment[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const uploadMilestone = ref<number | null>(null)
const uploading = ref(false)

const reviewStars = ref(0)
const reviewComment = ref('')
const reviewing = ref(false)

const reasonFor = ref<{ milestone: ContractMilestone; kind: 'revision' | 'dispute' } | null>(null)
const reasonOpen = computed({
  get: () => !!reasonFor.value,
  set: (v: boolean) => {
    if (!v) reasonFor.value = null
  },
})

const documents = computed<ContractDocument[]>(() => {
  const order = { CLIENT_SERVICES: 0, ENGINEER_DELIVERABLES: 1 }
  return [...(contract.value?.documents ?? [])].sort((a, b) => (order[a.kind] ?? 9) - (order[b.kind] ?? 9))
})
const milestones = computed(() => [...(contract.value?.milestones ?? [])].sort((a, b) => a.seq - b.seq))
const attachments = computed<Attachment[]>(() => {
  const all = [...(contract.value?.attachments ?? []), ...milestones.value.flatMap((m) => m.attachments ?? []), ...uploaded.value]
  const seen = new Set<number>()
  return all.filter((a) => (seen.has(a.id) ? false : (seen.add(a.id), true)))
})
const isActive = computed(() => contract.value?.status === 'ACTIVE')
const team = computed(() => contract.value?.team ?? null)
const teamAccepted = computed(() => (team.value?.members ?? []).filter((m) => m.accepted).length)
const milestoneLabel = (mid?: number | null) => {
  const m = milestones.value.find((x) => x.id === mid)
  return m ? t('contract.milestoneN', { n: m.seq, title: m.title }) : ''
}

async function load() {
  loadError.value = ''
  try {
    contract.value = await clientApi.contract(id.value)
  } catch (e) {
    loadError.value = errorText(e)
  } finally {
    loading.value = false
  }
}

async function loadMessages() {
  try {
    messages.value = (await contractApi.messages(id.value)).sort((a, b) => (a.createdAt < b.createdAt ? -1 : 1))
    messagesError.value = ''
  } catch (e) {
    messagesError.value = errorText(e)
  }
}

let poll: ReturnType<typeof setInterval> | null = null
onMounted(async () => {
  await Promise.all([load(), loadMessages()])
  poll = setInterval(() => {
    if (document.visibilityState === 'visible') void loadMessages()
  }, 30_000)
})
onBeforeUnmount(() => {
  if (poll) clearInterval(poll)
})

async function acceptDoc(doc: ContractDocument) {
  busy.value = `doc-${doc.id}`
  try {
    await clientApi.acceptDocument(id.value, doc.id)
    notifySuccess(t('contract.docAccepted'))
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

function sameOrigin(url: string): string | null {
  try {
    const u = new URL(url, window.location.origin)
    return u.origin === window.location.origin ? `${u.pathname}${u.search}${u.hash}` : null
  } catch {
    return null
  }
}

async function fund(m: ContractMilestone) {
  busy.value = `fund-${m.id}`
  try {
    const res = await clientApi.checkout(m.id)
    try {
      sessionStorage.setItem(`qiao.pay.${res.paymentId}`, String(id.value))
    } catch {
      /* ignore */
    }
    const local = sameOrigin(res.url)
    if (local) await router.push(local)
    else window.location.assign(res.url) // Stripe Checkout (provider=stripe)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

async function acceptMilestone(m: ContractMilestone) {
  busy.value = `accept-${m.id}`
  try {
    await clientApi.acceptMilestone(m.id)
    notifySuccess(t('contract.milestoneAccepted'))
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

async function submitReason(reason: string) {
  const target = reasonFor.value
  if (!target) return
  busy.value = `reason-${target.milestone.id}`
  try {
    if (target.kind === 'revision') await clientApi.requestRevision(target.milestone.id, reason)
    else await clientApi.dispute(target.milestone.id, reason)
    notifySuccess(target.kind === 'revision' ? t('contract.revisionRequested') : t('contract.disputeOpened'))
    reasonFor.value = null
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

async function submitReview() {
  if (!reviewStars.value) return
  reviewing.value = true
  try {
    await clientApi.review(id.value, reviewStars.value, reviewComment.value.trim())
    notifySuccess(t('contract.reviewSent'))
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    reviewing.value = false
  }
}

async function sendMessage() {
  const body = messageBody.value.trim()
  if (!body) return
  sending.value = true
  try {
    await contractApi.sendMessage(id.value, body)
    messageBody.value = ''
    await loadMessages()
  } catch (e) {
    notifyError(e)
  } finally {
    sending.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function onFile(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 20 * 1024 * 1024) {
    notifyError(new Error(t('errors.FILE_TOO_LARGE')))
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const res = await contractApi.upload(id.value, file, uploadMilestone.value)
    uploaded.value.push({ ...res, filename: res.filename ?? file.name, sizeBytes: res.sizeBytes ?? file.size, milestoneId: uploadMilestone.value })
    notifySuccess(t('contract.uploaded'))
  } catch (e) {
    notifyError(e)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function download(a: Attachment) {
  busy.value = `dl-${a.id}`
  try {
    await downloadFile(contractApi.attachmentUrl(a.id), a.filename || `attachment-${a.id}`)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}

const mine = (m: Message) => !!user.me && m.senderUserId === user.me.id
</script>

<template>
  <div class="q-container q-page">
    <router-link to="/client" class="back q-small" data-testid="contract-back">
      <el-icon><ArrowLeft /></el-icon> {{ t('nav.dashboard') }}
    </router-link>

    <el-skeleton v-if="loading" :rows="8" animated />
    <el-result v-else-if="loadError" icon="error" :title="loadError" data-testid="contract-load-error" />
    <template v-else-if="contract">
      <div class="q-page-head">
        <div class="head-main">
          <h1 data-testid="contract-title">{{ contract.projectTitle || t('contract.number', { id: contract.id }) }}</h1>
          <p class="q-sub">
            {{ t('contract.number', { id: contract.id }) }}
            <template v-if="team"> · <span data-testid="contract-team-name">{{ t('contract.team', { name: team.name ?? '' }) }}</span></template>
            <template v-else-if="contract.engineerAlias"> · {{ t('contract.engineer') }}: {{ contract.engineerAlias }}</template>
            <template v-if="contract.projectId">
              · <router-link :to="`/client/projects/${contract.projectId}`" data-testid="contract-project-link">{{ t('contract.viewProject') }}</router-link>
            </template>
          </p>
        </div>
        <div class="head-side">
          <StatusTag :status="contract.status" testid="contract-status" />
          <div class="total q-num" data-testid="contract-total">{{ money(contract.clientTotalUsd) }}</div>
        </div>
      </div>

      <el-alert v-if="paid" type="success" :closable="false" show-icon :title="t('contract.paidBanner')" data-testid="contract-paid-banner" />
      <el-alert
        v-if="contract.status === 'PENDING_SIGNATURES'"
        type="info"
        :closable="false"
        show-icon
        :title="t('contract.pendingSignatures')"
        data-testid="contract-pending-signatures"
      />

      <section class="q-card">
        <h2>{{ t('contract.documents') }}</h2>
        <div class="q-grid docs">
          <article v-for="doc in documents" :key="doc.id" class="doc" :data-testid="`contract-doc-${doc.id}`" :data-kind="doc.kind">
            <div class="doc-head">
              <h3>{{ t(`contract.docKind.${doc.kind}`) }}</h3>
              <div class="q-muted q-small">{{ t('contract.docMeta', { version: doc.templateVersion, locale: doc.locale }) }}</div>
              <div v-if="doc.acceptedAt" class="accepted" :data-testid="`contract-doc-accepted-${doc.id}`">
                <el-icon><CircleCheckFilled /></el-icon>
                <span>{{ t('contract.acceptedOn', { date: dateTime(doc.acceptedAt) }) }}</span>
              </div>
            </div>
            <div class="q-doc-scroll">
              <MarkdownView :source="doc.body" :testid="`contract-doc-body-${doc.id}`" />
            </div>
            <div class="doc-foot">
              <span class="q-muted q-small hash" :title="doc.sha256">{{ t('contract.hash', { hash: `${doc.sha256?.slice(0, 16) ?? ''}…` }) }}</span>
              <template v-if="!doc.acceptedAt">
                <el-button
                  v-if="doc.kind === 'CLIENT_SERVICES'"
                  type="primary"
                  :loading="busy === `doc-${doc.id}`"
                  :data-testid="`contract-doc-accept-${doc.id}`"
                  @click="acceptDoc(doc)"
                >
                  {{ t('contract.accept') }}
                </el-button>
                <span v-else class="q-muted q-small" :data-testid="`contract-doc-waiting-${doc.id}`">{{ t('contract.awaitingEngineer') }}</span>
              </template>
            </div>
          </article>
        </div>
        <template v-if="team">
          <p
            class="q-muted q-small engineer-agreement"
            :data-testid="'contract-engineer-agreement'"
            :data-status="contract.engineerAcceptedAt ? 'ACCEPTED' : 'PENDING'"
          >
            {{ t('contract.teamMembersAccepted', { done: teamAccepted, total: team.members.length }) }}
          </p>
          <ul class="team-members" data-testid="contract-team">
            <li
              v-for="m in team.members"
              :key="m.alias"
              :data-testid="`contract-team-member-${m.alias}`"
              :data-status="m.accepted ? 'ACCEPTED' : 'PENDING'"
            >
              <span class="team-alias">{{ m.alias }}</span>
              <el-tag size="small" effect="plain" :type="m.role === 'LEAD' ? 'warning' : 'info'">{{ t(`contract.teamRole.${m.role}`) }}</el-tag>
              <span class="q-small" :class="m.accepted ? 'team-ok' : 'q-muted'">
                {{ m.accepted ? t('contract.memberAccepted') : t('contract.memberPending') }}
              </span>
            </li>
          </ul>
          <p class="q-muted q-small">{{ t('contract.teamNote') }}</p>
        </template>
        <p
          v-else
          class="q-muted q-small engineer-agreement"
          :data-testid="'contract-engineer-agreement'"
          :data-status="contract.engineerAcceptedAt ? 'ACCEPTED' : 'PENDING'"
        >
          {{
            contract.engineerAcceptedAt
              ? t('contract.engineerAgreementAccepted', { date: dateTime(contract.engineerAcceptedAt) })
              : t('contract.engineerAgreementPending')
          }}
        </p>
      </section>

      <section class="q-card">
        <h2>{{ t('contract.milestones') }}</h2>
        <p v-if="!isActive && contract.status === 'PENDING_SIGNATURES'" class="q-muted q-small">{{ t('contract.fundAfterSign') }}</p>
        <div class="milestones">
          <article v-for="m in milestones" :key="m.id" class="ms" :data-testid="`milestone-row-${m.id}`">
            <div class="ms-main">
              <div class="ms-title">
                <span class="ms-seq">{{ m.seq }}</span>
                <span>{{ m.title }}</span>
              </div>
              <p class="q-pre q-muted ms-deliv">{{ m.deliverable }}</p>
              <div class="q-small q-muted">{{ t('contract.due', { date: date(m.dueDate) }) }}</div>
              <div v-if="m.submittedNote" class="ms-note" :data-testid="`milestone-note-${m.id}`">
                <strong>{{ t('contract.deliveryNote') }}</strong>
                <span v-if="m.submittedAt" class="q-muted q-small"> · {{ dateTime(m.submittedAt) }}</span>
                <p class="q-pre">{{ m.submittedNote }}</p>
              </div>
              <div v-if="m.disputeReason" class="ms-note dispute">
                <strong>{{ t('contract.disputeReason') }}</strong>
                <p class="q-pre">{{ m.disputeReason }}</p>
              </div>
            </div>
            <div class="ms-side">
              <div class="ms-amount q-num" :data-testid="`milestone-amount-${m.id}`">{{ money(m.clientAmountUsd) }}</div>
              <StatusTag :status="m.status" :testid="`milestone-status-${m.id}`" size="small" />
              <div class="ms-actions">
                <el-button
                  v-if="m.status === 'PENDING_FUNDING'"
                  type="primary"
                  size="small"
                  :disabled="!isActive"
                  :loading="busy === `fund-${m.id}`"
                  :data-testid="`milestone-fund-${m.id}`"
                  @click="fund(m)"
                >
                  {{ t('contract.fund') }}
                </el-button>
                <template v-if="m.status === 'SUBMITTED'">
                  <el-button
                    type="primary"
                    size="small"
                    :loading="busy === `accept-${m.id}`"
                    :data-testid="`milestone-accept-${m.id}`"
                    @click="acceptMilestone(m)"
                  >
                    {{ t('contract.acceptDelivery') }}
                  </el-button>
                  <el-button size="small" :data-testid="`milestone-revision-${m.id}`" @click="reasonFor = { milestone: m, kind: 'revision' }">
                    {{ t('contract.requestRevision') }}
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    :data-testid="`milestone-dispute-${m.id}`"
                    @click="reasonFor = { milestone: m, kind: 'dispute' }"
                  >
                    {{ t('contract.dispute') }}
                  </el-button>
                </template>
                <span v-if="m.status === 'FUNDED' || m.status === 'REVISION_REQUESTED'" class="q-muted q-small">{{ t('contract.waitingDelivery') }}</span>
                <span v-if="m.status === 'DISPUTED'" class="q-muted q-small">{{ t('contract.disputeInReview') }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-if="contract.status === 'COMPLETED'" class="q-card" data-testid="review">
        <h2>{{ t('contract.reviewTitle') }}</h2>
        <div v-if="contract.myReview" data-testid="review-done" :data-stars="contract.myReview.stars">
          <el-rate :model-value="contract.myReview.stars" disabled />
          <p v-if="contract.myReview.comment" class="q-pre review-comment">{{ contract.myReview.comment }}</p>
          <p class="q-muted q-small">{{ t('contract.reviewThanks') }}</p>
        </div>
        <template v-else-if="contract.canReview">
          <p class="q-muted q-small">{{ t('contract.reviewHint') }}</p>
          <div class="stars" role="radiogroup" :aria-label="t('contract.reviewStars')">
            <button
              v-for="n in 5"
              :key="n"
              type="button"
              class="star"
              role="radio"
              :aria-checked="reviewStars === n"
              :aria-label="t('contract.reviewStarN', { n })"
              :data-testid="`review-star-${n}`"
              @click="reviewStars = n"
            >
              <el-icon :size="26"><StarFilled v-if="reviewStars >= n" /><Star v-else /></el-icon>
            </button>
          </div>
          <el-input
            v-model="reviewComment"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            maxlength="1000"
            show-word-limit
            :placeholder="t('contract.reviewPlaceholder')"
            data-testid="review-comment"
          />
          <div class="review-actions">
            <el-button type="primary" :disabled="!reviewStars" :loading="reviewing" data-testid="review-submit" @click="submitReview">
              {{ t('contract.reviewSubmit') }}
            </el-button>
          </div>
        </template>
      </section>

      <div class="q-grid q-grid-2 bottom">
        <section class="q-card">
          <div class="q-card-head">
            <h2>{{ t('contract.messages') }}</h2>
            <el-button :icon="Refresh" text size="small" data-testid="message-refresh" @click="loadMessages">{{ t('common.refresh') }}</el-button>
          </div>
          <el-alert v-if="messagesError" type="error" :closable="false" :title="messagesError" />
          <div class="thread" data-testid="message-list">
            <p v-if="!messages.length" class="q-muted q-small">{{ t('contract.noMessages') }}</p>
            <div v-for="msg in messages" :key="msg.id" class="msg" :class="{ mine: mine(msg) }" :data-testid="`message-item-${msg.id}`">
              <div class="msg-meta q-small">
                <strong>{{ mine(msg) ? t('contract.you') : msg.senderAlias || contract.engineerAlias || t('contract.counterpart') }}</strong>
                <span class="q-muted"> · {{ dateTime(msg.createdAt) }}</span>
                <el-tag v-if="msg.flagged" size="small" type="warning" class="msg-flag">{{ t('contract.flagged') }}</el-tag>
              </div>
              <div class="msg-body q-pre">{{ msg.body }}</div>
            </div>
          </div>
          <el-form @submit.prevent="sendMessage">
            <el-input
              v-model="messageBody"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 6 }"
              maxlength="4000"
              :placeholder="t('contract.messagePlaceholder')"
              data-testid="message-input"
              @keydown.enter.ctrl.prevent="sendMessage"
              @keydown.enter.meta.prevent="sendMessage"
            />
            <div class="send-row">
              <span class="q-muted q-small">{{ t('contract.messageHint') }}</span>
              <el-button type="primary" :loading="sending" :disabled="!messageBody.trim()" data-testid="message-send" @click="sendMessage">
                {{ t('contract.send') }}
              </el-button>
            </div>
          </el-form>
        </section>

        <section class="q-card">
          <h2>{{ t('contract.attachments') }}</h2>
          <ul v-if="attachments.length" class="files" data-testid="attachment-list">
            <li v-for="a in attachments" :key="a.id" :data-testid="`attachment-item-${a.id}`">
              <el-icon><Paperclip /></el-icon>
              <div class="file-main">
                <div class="file-name">{{ a.filename }}</div>
                <div class="q-muted q-small">
                  {{ bytes(a.sizeBytes) }}<template v-if="a.milestoneId"> · {{ milestoneLabel(a.milestoneId) }}</template>
                </div>
              </div>
              <el-button
                :icon="Download"
                size="small"
                :loading="busy === `dl-${a.id}`"
                :aria-label="t('contract.download')"
                :data-testid="`attachment-download-${a.id}`"
                @click="download(a)"
              >
                {{ t('contract.download') }}
              </el-button>
            </li>
          </ul>
          <p v-else class="q-muted q-small" data-testid="attachment-empty">{{ t('contract.noAttachments') }}</p>

          <div class="upload">
            <el-select v-model="uploadMilestone" clearable :placeholder="t('contract.attachTo')" data-testid="attachment-milestone" class="upload-select">
              <el-option
                v-for="m in milestones"
                :key="m.id"
                :value="m.id"
                :label="t('contract.milestoneN', { n: m.seq, title: m.title })"
                :data-testid="`attachment-milestone-option-${m.id}`"
              />
            </el-select>
            <input ref="fileInput" type="file" class="file-input" data-testid="attachment-file-input" @change="onFile" />
            <el-button :icon="Paperclip" :loading="uploading" data-testid="attachment-upload" @click="pickFile">{{ t('contract.upload') }}</el-button>
          </div>
          <p class="q-muted q-small">{{ t('contract.uploadHint') }}</p>
        </section>
      </div>
    </template>

    <ReasonDialog
      v-model="reasonOpen"
      testid-prefix="milestone-reason"
      :title="reasonFor?.kind === 'dispute' ? t('contract.disputeTitle') : t('contract.revisionTitle')"
      :description="reasonFor?.kind === 'dispute' ? t('contract.disputeText') : t('contract.revisionText')"
      :confirm-text="reasonFor?.kind === 'dispute' ? t('contract.dispute') : t('contract.requestRevision')"
      :confirm-type="reasonFor?.kind === 'dispute' ? 'danger' : 'primary'"
      :loading="!!busy && busy.startsWith('reason')"
      @confirm="submitReason"
    />
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  color: var(--q-text-3);
}
.head-main {
  min-width: 0;
}
.head-main h1 {
  overflow-wrap: anywhere;
}
.head-side {
  text-align: right;
}
.total {
  font-size: 1.3rem;
  font-weight: 700;
  margin-top: 6px;
}
.docs {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.doc {
  border: 1px solid var(--q-border);
  border-radius: 10px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.doc-head h3 {
  margin: 0;
}
.accepted {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  color: #1e7a46;
  font-size: 13px;
  font-weight: 600;
}
.doc :deep(.q-markdown h1) {
  font-size: 1.1rem;
}
.doc :deep(.q-markdown h2) {
  font-size: 1rem;
}
.doc-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.stars {
  display: flex;
  gap: 4px;
  margin: 8px 0 12px;
}
.star {
  border: 0;
  background: none;
  padding: 2px;
  cursor: pointer;
  color: #e6a23c;
}
.review-comment {
  margin: 6px 0;
}
.review-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.team-members {
  list-style: none;
  padding: 0;
  margin: 8px 0;
}
.team-members li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px solid var(--q-border);
}
.team-alias {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 600;
}
.team-ok {
  color: #1e7a46;
  font-weight: 600;
}
.hash {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.ms {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding: 14px 0;
  border-top: 1px solid var(--q-border);
}
.ms:first-child {
  border-top: 0;
}
.ms-main {
  min-width: 0;
  flex: 1;
}
.ms-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 650;
}
.ms-seq {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--q-accent-soft);
  color: var(--q-accent);
  display: inline-grid;
  place-items: center;
  font-size: 12px;
  flex-shrink: 0;
}
.ms-deliv {
  margin: 6px 0 4px;
}
.ms-note {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--q-bg);
  font-size: 14px;
}
.ms-note p {
  margin: 4px 0 0;
}
.ms-note.dispute {
  background: #fdf0ef;
}
.ms-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
  max-width: 50%;
}
.ms-amount {
  font-weight: 700;
}
.ms-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}
.ms-actions .el-button + .el-button {
  margin-left: 0;
}
.bottom {
  gap: 0 16px;
  align-items: start;
}
.thread {
  max-height: 380px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}
.msg {
  background: var(--q-bg);
  border-radius: 10px;
  padding: 8px 12px;
  max-width: 92%;
}
.msg.mine {
  align-self: flex-end;
  background: var(--q-accent-soft);
}
.msg-flag {
  margin-left: 6px;
}
.msg-body {
  margin-top: 2px;
}
.send-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.files {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
}
.files li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--q-border);
}
.file-main {
  flex: 1;
  min-width: 0;
}
.file-name {
  overflow-wrap: anywhere;
  font-weight: 550;
}
.upload {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.upload-select {
  flex: 1;
  min-width: 180px;
}
.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
@media (max-width: 760px) {
  .docs {
    grid-template-columns: minmax(0, 1fr);
  }
  .ms {
    flex-direction: column;
  }
  .ms-side {
    align-items: flex-start;
    max-width: none;
  }
  .ms-actions {
    justify-content: flex-start;
  }
  .head-side {
    text-align: left;
  }
}
</style>
