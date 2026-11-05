<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import StateView from '@/components/StateView.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { contractApi, engineerApi } from '@/api/endpoints'
import type { ContractDetail, ContractDocument, ContractMilestone, Id, Message } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatDate, formatDateTime, formatUsd, sameId } from '@/utils/format'
import { errorMessage } from '@/utils/errors'

const MAX_FILE = 20 * 1024 * 1024
const { t, te } = useI18n()
const route = useRoute()
const auth = useAuthStore()

const id = computed(() => String(route.params.id))
const contract = ref<ContractDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const expanded = reactive<Record<string, boolean>>({})
const accepting = ref<string | null>(null)

const messages = ref<Message[]>([])
const messagesError = ref(false)
const draft = ref('')
const sending = ref(false)
const threadEl = ref<HTMLElement | null>(null)
let poll: number | undefined

type UploadItem = UploaderFileListItem & { attachmentId?: Id }
const submitTarget = ref<ContractMilestone | null>(null)
const showSubmit = ref(false)
const submitNote = ref('')
const files = ref<UploadItem[]>([])
const submitting = ref(false)

const documents = computed(() => contract.value?.documents ?? [])
const milestones = computed(() => [...(contract.value?.milestones ?? [])].sort((a, b) => a.seq - b.seq))
const isActive = computed(() => contract.value?.status === 'ACTIVE')
const doneCount = computed(() => milestones.value.filter((m) => m.status === 'ACCEPTED').length)

async function load() {
  error.value = null
  try {
    contract.value = await engineerApi.contract(id.value)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function loadMessages(scroll = false) {
  try {
    const list = await contractApi.messages(id.value)
    messages.value = [...list].sort((a, b) => {
      const ta = a.createdAt ?? ''
      const tb = b.createdAt ?? ''
      return ta === tb ? Number(a.id) - Number(b.id) : ta.localeCompare(tb)
    })
    messagesError.value = false
    if (scroll) scrollThread()
  } catch {
    messagesError.value = true
  }
}

function scrollThread() {
  nextTick(() => {
    if (threadEl.value) threadEl.value.scrollTop = threadEl.value.scrollHeight
  })
}

onMounted(async () => {
  if (!auth.me) auth.loadMe().catch(() => undefined)
  await load()
  if (contract.value) loadMessages(true)
  poll = window.setInterval(() => {
    if (document.visibilityState === 'visible' && contract.value) loadMessages()
  }, 20000)
})
onBeforeUnmount(() => window.clearInterval(poll))

// ---------- documents ----------
function docTitle(d: ContractDocument) {
  return te(`contract.docKind.${d.kind}`) ? t(`contract.docKind.${d.kind}`) : d.kind
}
function isMine(d: ContractDocument) {
  return d.kind === 'ENGINEER_DELIVERABLES'
}
async function acceptDoc(d: ContractDocument) {
  accepting.value = String(d.id)
  try {
    await engineerApi.acceptDocument(id.value, d.id)
    showSuccessToast(t('contract.acceptSuccess'))
    await load()
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    accepting.value = null
  }
}

// ---------- milestones ----------
function canSubmit(m: ContractMilestone) {
  return isActive.value && (m.status === 'FUNDED' || m.status === 'REVISION_REQUESTED')
}
function openSubmit(m: ContractMilestone) {
  submitTarget.value = m
  submitNote.value = ''
  files.value = []
  showSubmit.value = true
}

async function uploadOne(item: UploadItem) {
  if (!item.file || !submitTarget.value) return
  item.status = 'uploading'
  item.message = t('contract.uploading')
  try {
    const res = await contractApi.upload(id.value, item.file, submitTarget.value.id)
    item.attachmentId = res.id
    item.status = 'done'
    item.message = ''
  } catch (e) {
    item.status = 'failed'
    item.message = t('contract.uploadFailed')
    showFailToast(errorMessage(e))
  }
}

function onAfterRead(items: UploaderFileListItem | UploaderFileListItem[]) {
  const list = Array.isArray(items) ? items : [items]
  // Operate on the reactive copies held by the uploader's v-model.
  for (const raw of list) {
    const item = files.value.find((f) => f.file === raw.file) ?? (raw as UploadItem)
    uploadOne(item)
  }
}

function onOversize() {
  showFailToast(t('contract.tooLarge'))
}

async function confirmSubmit() {
  const m = submitTarget.value
  if (!m) return
  if (!submitNote.value.trim()) {
    showFailToast(t('contract.noteRequired'))
    return
  }
  if (files.value.some((f) => f.status === 'uploading')) {
    showFailToast(t('contract.waitUpload'))
    return
  }
  submitting.value = true
  try {
    const ids = files.value.filter((f) => f.status === 'done' && f.attachmentId !== undefined).map((f) => f.attachmentId!)
    await engineerApi.submitMilestone(m.id, submitNote.value.trim(), ids)
    showSubmit.value = false
    showSuccessToast(t('contract.submitSuccess'))
    await load()
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    submitting.value = false
  }
}

// ---------- messages ----------
function isOwn(m: Message) {
  return sameId(m.senderUserId, auth.me?.id)
}
async function send() {
  const body = draft.value.trim()
  if (!body || sending.value) return
  sending.value = true
  try {
    const msg = await contractApi.sendMessage(id.value, body)
    draft.value = ''
    if (msg && typeof msg === 'object' && 'id' in msg) {
      messages.value.push(msg)
      scrollThread()
    }
    loadMessages(true)
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="contract-detail" data-testid="contract-page">
    <PageHeader :title="t('contract.title')" back="/contracts" />
    <StateView :loading="loading" :error="error" @retry="load">
      <div v-if="contract" class="q-page">
        <!-- Summary -->
        <section class="q-card summary">
          <div class="q-between summary__head">
            <h1 class="summary__title" data-testid="contract-title">
              {{ contract.projectTitle || t('common.projectRef', { id: contract.projectId ?? contract.id }) }}
            </h1>
            <StatusTag kind="contract" :status="contract.status" data-testid="contract-status" />
          </div>
          <div class="summary__stats">
            <div>
              <div class="q-muted">{{ t('contracts.total') }}</div>
              <div class="q-amount q-amount--accent summary__amount">{{ formatUsd(contract.talentTotalUsd) }}</div>
            </div>
            <div v-if="milestones.length" class="summary__progress">
              <div class="q-muted">{{ t('contract.milestones') }}</div>
              <div class="summary__count">{{ t('contracts.progress', { done: doneCount, total: milestones.length }) }}</div>
            </div>
          </div>
          <p v-if="contract.status === 'PENDING_SIGNATURES'" class="q-notice summary__hint">
            <van-icon name="info-o" /><span>{{ t('contract.signHint') }}</span>
          </p>
        </section>

        <!-- Documents -->
        <section v-if="documents.length" class="q-card">
          <h2 class="q-section-title">{{ t('contract.documents') }}</h2>
          <article
            v-for="d in documents"
            :key="d.id"
            class="doc"
            :class="{ 'doc--pending': isMine(d) && !d.acceptedAt }"
            :data-testid="`contract-doc-${d.id}`"
          >
            <div class="doc__head">
              <van-icon name="description" class="doc__icon" />
              <div class="doc__info">
                <div class="doc__title">{{ docTitle(d) }}</div>
                <div class="q-muted doc__ver">
                  <span v-if="d.templateVersion">{{ t('contract.docVersion', { v: d.templateVersion }) }}</span>
                  <span v-if="d.sha256" class="doc__hash">SHA-256 {{ d.sha256.slice(0, 12) }}…</span>
                </div>
              </div>
              <span v-if="d.acceptedAt" class="doc__state doc__state--ok" :data-testid="`contract-doc-accepted-${d.id}`">
                <van-icon name="passed" /> {{ t('contract.accepted', { date: formatDate(d.acceptedAt) }) }}
              </span>
              <span v-else-if="isMine(d)" class="doc__state doc__state--todo">{{ t('contract.pending') }}</span>
              <span v-else class="doc__state">{{ t('contract.clientSide') }}</span>
            </div>
            <div class="doc__body" :class="{ 'is-expanded': expanded[String(d.id)] }">
              <MarkdownView :source="d.body" />
            </div>
            <button
              type="button"
              class="doc__toggle"
              :data-testid="`contract-doc-toggle-${d.id}`"
              @click="expanded[String(d.id)] = !expanded[String(d.id)]"
            >
              {{ expanded[String(d.id)] ? t('contract.collapse') : t('contract.expand') }}
              <van-icon :name="expanded[String(d.id)] ? 'arrow-up' : 'arrow-down'" />
            </button>
            <van-button
              v-if="isMine(d) && !d.acceptedAt"
              block
              round
              type="primary"
              :loading="accepting === String(d.id)"
              :data-testid="`contract-accept-${d.id}`"
              @click="acceptDoc(d)"
            >
              {{ t('contract.accept') }}
            </van-button>
          </article>
          <p
            class="q-muted client-agreement"
            data-testid="contract-client-agreement"
            :data-status="contract.clientAcceptedAt ? 'ACCEPTED' : 'PENDING'"
          >
            {{
              contract.clientAcceptedAt
                ? t('contract.clientAgreementAccepted', { date: formatDate(contract.clientAcceptedAt) })
                : t('contract.clientAgreementPending')
            }}
          </p>
        </section>

        <!-- Milestones -->
        <section class="q-card">
          <h2 class="q-section-title">{{ t('contract.milestones') }}</h2>
          <article v-for="m in milestones" :key="m.id" class="ms" :data-testid="`milestone-${m.id}`">
            <div class="ms__head">
              <span class="ms__seq">{{ m.seq }}</span>
              <div class="ms__info">
                <div class="ms__title">{{ m.title }}</div>
                <div class="q-muted">{{ t('project.due', { date: formatDate(m.dueDate) }) }}</div>
              </div>
              <div class="ms__right">
                <div class="q-amount">{{ formatUsd(m.talentAmountUsd) }}</div>
                <StatusTag kind="milestone" :status="m.status" :data-testid="`milestone-status-${m.id}`" />
              </div>
            </div>
            <p class="ms__deliverable"><span>{{ t('project.deliverable') }}</span>{{ m.deliverable }}</p>

            <div v-if="m.status === 'REVISION_REQUESTED' && (m.revisionReason || m.disputeReason)" class="q-notice q-notice--warning ms__note">
              <van-icon name="info-o" />
              <span><strong>{{ t('contract.revisionReason') }}{{ t('common.colon') }}</strong>{{ m.revisionReason || m.disputeReason }}</span>
            </div>
            <div v-if="m.status === 'DISPUTED' && m.disputeReason" class="q-notice q-notice--danger ms__note">
              <van-icon name="warning-o" />
              <span><strong>{{ t('contract.disputeReason') }}{{ t('common.colon') }}</strong>{{ m.disputeReason }}</span>
            </div>
            <div v-if="m.submittedNote" class="ms__submitted">
              <div class="q-muted">
                {{ t('contract.submittedNote') }}
                <template v-if="m.submittedAt"> · {{ t('contract.submittedAt', { date: formatDateTime(m.submittedAt) }) }}</template>
              </div>
              <p class="q-pre">{{ m.submittedNote }}</p>
            </div>
            <p v-if="m.acceptedAt" class="q-muted ms__accepted">{{ t('contract.acceptedAt', { date: formatDate(m.acceptedAt) }) }}</p>

            <van-button
              v-if="canSubmit(m)"
              block
              round
              type="primary"
              icon="upgrade"
              class="ms__btn"
              :data-testid="`milestone-submit-${m.id}`"
              @click="openSubmit(m)"
            >
              {{ m.status === 'REVISION_REQUESTED' ? t('contract.resubmit') : t('contract.submit') }}
            </van-button>
            <p v-else-if="m.status === 'PENDING_FUNDING'" class="q-muted ms__hint">
              <van-icon name="clock-o" /> {{ isActive ? t('contract.notFundedHint') : t('contract.inactiveHint') }}
            </p>
          </article>
        </section>

        <!-- Messages -->
        <section class="q-card messages" data-testid="messages">
          <h2 class="q-section-title">
            {{ t('contract.messages') }}
            <button type="button" class="messages__refresh" data-testid="messages-refresh" @click="loadMessages(true)">
              <van-icon name="replay" /> {{ t('common.refresh') }}
            </button>
          </h2>
          <p class="q-muted messages__hint">{{ t('contract.messageHint') }}</p>
          <div ref="threadEl" class="thread" data-testid="message-list">
            <p v-if="messagesError" class="q-muted thread__empty">{{ t('common.loadFailed') }}</p>
            <p v-else-if="!messages.length" class="q-muted thread__empty">{{ t('contract.noMessages') }}</p>
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="bubble-row"
              :class="{ 'is-own': isOwn(msg) }"
              :data-testid="`message-${msg.id}`"
            >
              <div class="bubble-row__who">{{ isOwn(msg) ? t('contract.me') : t('contract.other') }}</div>
              <div class="bubble">{{ msg.body }}</div>
              <div class="bubble-row__meta">
                <span v-if="msg.createdAt">{{ formatDateTime(msg.createdAt) }}</span>
                <span v-if="msg.flagged" class="bubble-row__flag"><van-icon name="warning-o" /> {{ t('contract.flagged') }}</span>
              </div>
            </div>
          </div>
          <div class="composer">
            <van-field
              v-model="draft"
              v-testid="'message-input'"
              type="textarea"
              rows="1"
              :autosize="{ maxHeight: 120 }"
              maxlength="4000"
              :placeholder="t('contract.messagePlaceholder')"
              class="composer__input"
            />
            <van-button
              type="primary"
              round
              size="small"
              :loading="sending"
              :disabled="!draft.trim()"
              class="composer__send"
              data-testid="message-send"
              @click="send"
            >
              {{ t('contract.send') }}
            </van-button>
          </div>
        </section>
      </div>
    </StateView>

    <!-- Submit delivery -->
    <van-popup
      v-model:show="showSubmit"
      position="bottom"
      round
      closeable
      safe-area-inset-bottom
      data-testid="milestone-submit-sheet"
    >
      <div v-if="submitTarget" class="submit-sheet">
        <h3 class="submit-sheet__title">{{ t('contract.submitTitle') }}</h3>
        <p class="q-muted submit-sheet__sub">
          {{ t('project.milestoneN', { n: submitTarget.seq }) }} · {{ submitTarget.title }}
        </p>
        <van-field
          v-model="submitNote"
          v-testid="'milestone-note'"
          type="textarea"
          rows="4"
          autosize
          maxlength="4000"
          show-word-limit
          :label="t('contract.note')"
          :placeholder="t('contract.notePlaceholder')"
          label-align="top"
          class="submit-sheet__note"
        />
        <div class="submit-sheet__files">
          <div class="q-between">
            <span>{{ t('contract.files') }}</span>
            <span class="q-muted">{{ t('contract.filesHint') }}</span>
          </div>
          <van-uploader
            v-model="files"
            v-testid="'milestone-file'"
            multiple
            accept="*"
            upload-icon="plus"
            :max-size="MAX_FILE"
            :max-count="10"
            :after-read="onAfterRead"
            :preview-full-image="false"
            class="submit-sheet__uploader"
            @oversize="onOversize"
          />
        </div>
        <van-button
          block
          round
          type="primary"
          size="large"
          :loading="submitting"
          data-testid="milestone-submit-confirm"
          @click="confirmSubmit"
        >
          {{ t('contract.confirmSubmit') }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.summary__title {
  margin: 0;
  font-size: 18px;
  line-height: 1.4;
}
.summary__head {
  align-items: flex-start;
}
.summary__stats {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 14px;
}
.summary__amount {
  font-size: 24px;
  line-height: 1.3;
}
.summary__progress {
  text-align: right;
}
.summary__count {
  font-weight: 600;
}
.summary__hint {
  margin: 12px 0 0;
}
.doc {
  padding: 12px;
  border: 1px solid var(--q-border);
  border-radius: 10px;
}
.doc + .doc {
  margin-top: 10px;
}
.doc--pending {
  border-color: #c5d3f7;
  background: #fafcff;
}
.doc__head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.doc__icon {
  font-size: 26px;
  color: var(--q-primary);
}
.doc__info {
  flex: 1;
  min-width: 0;
}
.doc__title {
  font-weight: 600;
}
.doc__ver {
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.doc__hash {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.doc__state {
  flex: none;
  font-size: 12px;
  color: var(--q-text-3);
}
.doc__state--ok {
  color: var(--q-success);
}
.doc__state--todo {
  color: var(--q-warning);
  font-weight: 600;
}
.doc__body {
  position: relative;
  max-height: 140px;
  overflow: hidden;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #f2f3f5;
}
.doc__body:not(.is-expanded)::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 48px;
  background: linear-gradient(rgba(255, 255, 255, 0), #fff);
}
.doc__body.is-expanded {
  max-height: none;
}
.doc__body :deep(.q-markdown) {
  font-size: 13px;
}
.doc__toggle {
  display: block;
  width: 100%;
  margin: 6px 0 10px;
  border: 0;
  background: none;
  color: var(--q-primary);
  font-size: 13px;
  cursor: pointer;
}
.ms {
  padding: 12px 0;
}
.ms + .ms {
  border-top: 1px solid #f2f3f5;
}
.ms__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.ms__seq {
  flex: none;
  width: 24px;
  height: 24px;
  margin-top: 1px;
  border-radius: 50%;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ms__info {
  flex: 1;
  min-width: 0;
}
.ms__title {
  font-weight: 600;
  line-height: 1.4;
}
.ms__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.ms__deliverable {
  margin: 8px 0 0 34px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--q-text-2);
  white-space: pre-wrap;
}
.ms__deliverable span {
  margin-right: 6px;
  color: var(--q-text-3);
}
.ms__note,
.ms__submitted,
.ms__accepted,
.ms__hint,
.ms__btn {
  margin: 10px 0 0 34px;
  width: auto;
}
.ms__submitted {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f8fa;
  font-size: 13px;
}
.ms__submitted p {
  margin: 4px 0 0;
  color: var(--q-text-2);
}
.ms__hint {
  font-size: 12px;
}
.messages__refresh {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 0;
  background: none;
  color: var(--q-text-3);
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;
}
.messages__hint {
  margin: -6px 0 10px;
  font-size: 12px;
}
.thread {
  max-height: 360px;
  overflow-y: auto;
  padding: 8px;
  border-radius: 10px;
  background: #f7f8fa;
}
.thread__empty {
  margin: 16px 0;
  text-align: center;
}
.bubble-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin: 8px 0;
}
.bubble-row.is-own {
  align-items: flex-end;
}
.bubble-row__who {
  font-size: 11px;
  color: var(--q-text-3);
  margin: 0 4px 2px;
}
.bubble {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 4px 14px 14px 14px;
  background: #fff;
  color: var(--q-text);
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 1px 1px rgba(0, 0, 0, 0.03);
}
.is-own .bubble {
  border-radius: 14px 4px 14px 14px;
  background: var(--q-primary);
  color: #fff;
}
.bubble-row__meta {
  display: flex;
  gap: 8px;
  margin: 2px 4px 0;
  font-size: 11px;
  color: var(--q-text-3);
}
.bubble-row__flag {
  color: var(--q-warning);
}
.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-top: 10px;
}
.composer__input {
  flex: 1;
  padding: 8px 12px;
  border-radius: 18px;
  background: #f2f3f5;
}
.composer__input::after {
  display: none;
}
.composer__send {
  flex: none;
  height: 36px;
  padding: 0 16px;
}
.submit-sheet {
  padding: 24px 16px 16px;
}
.submit-sheet__title {
  margin: 0;
  font-size: 17px;
}
.submit-sheet__sub {
  margin: 4px 0 12px;
}
.submit-sheet__note {
  padding: 0;
}
.submit-sheet__note::after {
  display: none;
}
.submit-sheet__note :deep(.van-field__label) {
  margin-bottom: 6px;
}
.submit-sheet__note :deep(.van-field__body) {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
}
.submit-sheet__files {
  margin: 16px 0;
}
.submit-sheet__uploader {
  margin-top: 8px;
}
.client-agreement {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
}
</style>
