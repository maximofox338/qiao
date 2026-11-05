<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import PageHeader from '@/components/PageHeader.vue'
import StateView from '@/components/StateView.vue'
import { teamApi } from '@/api/endpoints'
import type { Team, TeamMember } from '@/api/types'
import { useConfirmStore } from '@/stores/confirm'
import { errorMessage } from '@/utils/errors'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const confirm = useConfirmStore()

const id = computed(() => String(route.params.id))
const team = ref<Team | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const busy = ref<string | null>(null)
const alias = ref('')

const showEdit = ref(false)
const editName = ref('')
const editDescription = ref('')

const isLead = computed(() => team.value?.myRole === 'LEAD' && team.value?.myStatus === 'ACTIVE')
const isInvited = computed(() => team.value?.myStatus === 'INVITED')
const isMember = computed(() => team.value?.myRole === 'MEMBER' && team.value?.myStatus === 'ACTIVE')

async function load() {
  error.value = null
  try {
    team.value = await teamApi.get(id.value)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function run(key: string, fn: () => Promise<unknown>, done: string) {
  busy.value = key
  try {
    await fn()
    showSuccessToast(done)
    return true
  } catch (e) {
    showFailToast(errorMessage(e))
    return false
  } finally {
    busy.value = null
  }
}

async function invite() {
  const value = alias.value.trim()
  if (!value) {
    showFailToast(t('team.aliasRequired'))
    return
  }
  if (await run('invite', async () => (team.value = await teamApi.invite(id.value, value)), t('team.invited'))) {
    alias.value = ''
  }
}

async function remove(m: TeamMember) {
  const ok = await confirm.open({
    title: t('team.removeTitle'),
    message: t('team.removeConfirm', { alias: m.alias }),
    danger: true,
  })
  if (!ok) return
  await run(`rm-${m.id}`, async () => (team.value = await teamApi.remove(id.value, m.id)), t('team.removed'))
}

async function leave() {
  const ok = await confirm.open({ title: t('team.leave'), message: t('team.leaveConfirm'), danger: true })
  if (!ok) return
  if (await run('leave', () => teamApi.leave(id.value), t('team.left'))) router.replace('/teams')
}

async function dissolve() {
  const ok = await confirm.open({ title: t('team.dissolve'), message: t('team.dissolveConfirm'), danger: true })
  if (!ok) return
  if (await run('dissolve', () => teamApi.dissolve(id.value), t('team.dissolved'))) router.replace('/teams')
}

async function respond(accept: boolean) {
  if (accept) {
    await run('accept', async () => (team.value = await teamApi.accept(id.value)), t('teams.accepted'))
  } else if (await run('decline', () => teamApi.decline(id.value), t('teams.declined'))) {
    router.replace('/teams')
  }
}

function openEdit() {
  if (!team.value) return
  editName.value = team.value.name
  editDescription.value = team.value.description ?? ''
  showEdit.value = true
}

async function saveEdit() {
  if (!editName.value.trim()) {
    showFailToast(t('teams.nameRequired'))
    return
  }
  const ok = await run(
    'edit',
    async () => (team.value = await teamApi.update(id.value, { name: editName.value.trim(), description: editDescription.value.trim() })),
    t('team.saved'),
  )
  if (ok) showEdit.value = false
}
</script>

<template>
  <div data-testid="team-page">
    <PageHeader :title="t('team.title')" back="/teams" />
    <StateView :loading="loading" :error="error" @retry="load">
      <div v-if="team" class="q-page">
        <section class="q-card">
          <div class="q-between">
            <h1 class="team__name" data-testid="team-name">{{ team.name }}</h1>
            <button v-if="isLead" type="button" class="team__edit" data-testid="team-edit-open" @click="openEdit">
              <van-icon name="edit" /> {{ t('team.edit') }}
            </button>
          </div>
          <p v-if="team.description" class="q-pre q-muted team__desc" data-testid="team-description">{{ team.description }}</p>
          <div class="q-muted">{{ t('team.lead', { alias: team.leadAlias ?? '' }) }} · {{ t('teams.activeCount', { n: team.activeMembers }) }}</div>
          <p
            class="q-notice team__state"
            :class="team.canApply ? 'q-notice--success' : ''"
            data-testid="team-can-apply"
            :data-status="team.canApply ? 'READY' : 'NOT_READY'"
          >
            <van-icon :name="team.canApply ? 'passed' : 'info-o'" />
            <span>{{ team.canApply ? t('team.readyHint') : t('team.canApplyHint') }}</span>
          </p>
        </section>

        <section v-if="isInvited" class="q-card" data-testid="team-invited">
          <p class="team__invited">{{ t('team.invitedNotice') }}</p>
          <div class="q-actions team__actions">
            <van-button size="small" round plain :loading="busy === 'decline'" data-testid="team-decline" @click="respond(false)">
              {{ t('teams.decline') }}
            </van-button>
            <van-button size="small" round type="primary" :loading="busy === 'accept'" data-testid="team-accept" @click="respond(true)">
              {{ t('teams.accept') }}
            </van-button>
          </div>
        </section>

        <section class="q-card">
          <h2 class="q-section-title">{{ t('team.members') }}</h2>
          <p class="q-muted team__privacy">{{ t('team.privacy') }}</p>
          <article
            v-for="m in team.members"
            :key="m.id"
            class="member"
            :data-testid="`team-member-${m.alias}`"
            :data-status="m.status"
          >
            <div class="member__avatar">{{ m.alias.slice(-2) }}</div>
            <div class="member__info">
              <div class="member__alias">
                {{ m.alias }}
                <span class="q-chip member__role" :class="{ 'is-lead': m.role === 'LEAD' }">{{ t(`teams.role.${m.role}`) }}</span>
                <span v-if="m.status !== 'ACTIVE'" class="q-chip">{{ t(`teams.memberStatus.${m.status}`) }}</span>
              </div>
              <div v-if="m.headline" class="q-muted member__headline">{{ m.headline }}</div>
              <div v-if="m.skills?.length" class="q-tags member__skills">
                <span v-for="s in m.skills" :key="s" class="q-chip">{{ s }}</span>
              </div>
            </div>
            <van-button
              v-if="isLead && m.role !== 'LEAD' && (m.status === 'ACTIVE' || m.status === 'INVITED')"
              size="mini"
              plain
              round
              :loading="busy === `rm-${m.id}`"
              :data-testid="`team-member-remove-${m.alias}`"
              @click="remove(m)"
            >
              {{ m.status === 'INVITED' ? t('team.cancelInvite') : t('team.remove') }}
            </van-button>
          </article>
        </section>

        <section v-if="isLead" class="q-card" data-testid="team-invite">
          <h2 class="q-section-title">{{ t('team.invite') }}</h2>
          <p class="q-muted team__hint">{{ t('team.inviteHint') }}</p>
          <div class="invite-row">
            <van-field
              v-model="alias"
              v-testid="'team-invite-alias'"
              :placeholder="t('team.invitePlaceholder')"
              maxlength="16"
              class="invite-row__field"
              @keyup.enter="invite"
            />
            <van-button type="primary" round size="small" :loading="busy === 'invite'" data-testid="team-invite-submit" @click="invite">
              {{ t('team.inviteSend') }}
            </van-button>
          </div>
        </section>

        <van-button v-if="isMember" block round plain type="danger" :loading="busy === 'leave'" data-testid="team-leave" @click="leave">
          {{ t('team.leave') }}
        </van-button>
        <van-button v-if="isLead" block round plain type="danger" :loading="busy === 'dissolve'" data-testid="team-dissolve" @click="dissolve">
          {{ t('team.dissolve') }}
        </van-button>
      </div>
    </StateView>

    <van-popup v-model:show="showEdit" position="bottom" round closeable safe-area-inset-bottom data-testid="team-edit-sheet">
      <div class="sheet">
        <h3 class="sheet__title">{{ t('team.editTitle') }}</h3>
        <van-field v-model="editName" v-testid="'team-edit-name'" :label="t('teams.name')" maxlength="80" label-align="top" />
        <van-field
          v-model="editDescription"
          v-testid="'team-edit-description'"
          :label="t('teams.description')"
          type="textarea"
          rows="3"
          autosize
          maxlength="500"
          show-word-limit
          label-align="top"
        />
        <van-button block round type="primary" :loading="busy === 'edit'" data-testid="team-edit-save" @click="saveEdit">
          {{ t('common.save') }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.team__name {
  margin: 0;
  font-size: 20px;
}
.team__edit {
  border: 0;
  background: none;
  color: var(--q-primary);
  font-size: 13px;
}
.team__desc {
  margin: 8px 0;
  line-height: 1.6;
}
.team__state {
  margin: 12px 0 0;
}
.team__invited {
  margin: 0;
  font-weight: 600;
}
.team__actions {
  justify-content: flex-end;
  margin-top: 10px;
}
.team__privacy,
.team__hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.6;
}
.member {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid #f2f3f5;
}
.member__avatar {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}
.member__info {
  flex: 1;
  min-width: 0;
}
.member__alias {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-family: ui-monospace, Menlo, Consolas, monospace;
}
.member__role.is-lead {
  background: var(--q-primary-soft);
  color: var(--q-primary);
}
.member__headline {
  margin-top: 2px;
  font-size: 13px;
}
.member__skills {
  margin-top: 6px;
}
.invite-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.invite-row__field {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid var(--q-border);
  border-radius: 8px;
}
.invite-row__field::after {
  display: none;
}
.q-page > .van-button {
  margin-top: 12px;
}
.sheet {
  padding: 20px 16px 16px;
}
.sheet__title {
  margin: 0 0 12px;
  font-size: 17px;
}
.sheet .van-button {
  margin-top: 16px;
}
</style>
