<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { showFailToast, showSuccessToast } from 'vant'
import PageHeader from '@/components/PageHeader.vue'
import StateView from '@/components/StateView.vue'
import { teamApi } from '@/api/endpoints'
import type { Id, Team, TeamInvitation } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/utils/errors'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const teams = ref<Team[]>([])
const invitations = ref<TeamInvitation[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const busy = ref<string | null>(null)

const showCreate = ref(false)
const name = ref('')
const description = ref('')
const creating = ref(false)

const approved = computed(() => auth.me?.engineerProfileStatus === 'APPROVED')

async function load() {
  error.value = null
  try {
    const [list, invites] = await Promise.all([teamApi.list(), teamApi.invitations()])
    teams.value = list
    invitations.value = invites
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  auth.loadMe().catch(() => undefined)
  load()
})

async function respond(teamId: Id, accept: boolean) {
  busy.value = `${accept ? 'a' : 'd'}-${teamId}`
  try {
    if (accept) {
      await teamApi.accept(teamId)
      showSuccessToast(t('teams.accepted'))
    } else {
      await teamApi.decline(teamId)
      showSuccessToast(t('teams.declined'))
    }
    await load()
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    busy.value = null
  }
}

function openCreate() {
  name.value = ''
  description.value = ''
  showCreate.value = true
}

async function create() {
  if (!name.value.trim()) {
    showFailToast(t('teams.nameRequired'))
    return
  }
  creating.value = true
  try {
    const team = await teamApi.create({ name: name.value.trim(), description: description.value.trim() })
    showCreate.value = false
    showSuccessToast(t('teams.created'))
    await router.push(`/teams/${team.id}`)
  } catch (e) {
    showFailToast(errorMessage(e))
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div class="teams" data-testid="teams-page">
    <PageHeader :title="t('teams.title')" back="/me" />
    <div class="q-page">
      <p class="q-notice teams__explain" data-testid="teams-explain">
        <van-icon name="friends-o" /><span>{{ t('teams.explain') }}</span>
      </p>
      <StateView :loading="loading" :error="error" @retry="load">
        <template v-if="invitations.length">
          <h2 class="q-group-title">{{ t('teams.invitations') }}</h2>
          <article
            v-for="inv in invitations"
            :key="inv.teamId"
            class="q-card invite"
            :data-testid="`team-invitation-${inv.teamId}`"
          >
            <div class="invite__name">{{ inv.teamName }}</div>
            <p v-if="inv.description" class="q-muted invite__desc">{{ inv.description }}</p>
            <div class="q-muted">{{ t('teams.invitedBy', { alias: inv.leadAlias ?? '' }) }}</div>
            <div class="q-muted">{{ t('teams.members', { list: inv.memberAliases.join('、') }) }}</div>
            <div class="q-actions invite__actions">
              <van-button
                size="small"
                round
                plain
                :loading="busy === `d-${inv.teamId}`"
                :data-testid="`team-invitation-decline-${inv.teamId}`"
                @click="respond(inv.teamId, false)"
              >
                {{ t('teams.decline') }}
              </van-button>
              <van-button
                size="small"
                round
                type="primary"
                :loading="busy === `a-${inv.teamId}`"
                :data-testid="`team-invitation-accept-${inv.teamId}`"
                @click="respond(inv.teamId, true)"
              >
                {{ t('teams.accept') }}
              </van-button>
            </div>
          </article>
        </template>

        <h2 class="q-group-title">{{ t('teams.myTeams') }}</h2>
        <p v-if="!teams.length" class="q-muted teams__empty" data-testid="teams-empty">{{ t('teams.empty') }}</p>
        <router-link
          v-for="team in teams"
          :key="team.id"
          :to="`/teams/${team.id}`"
          class="q-card team-card"
          :data-testid="`team-card-${team.id}`"
        >
          <div class="q-between">
            <strong class="team-card__name">{{ team.name }}</strong>
            <span class="q-chip" :class="{ 'team-card__lead': team.myRole === 'LEAD' }">
              {{ team.myRole ? t(`teams.role.${team.myRole}`) : '' }}
            </span>
          </div>
          <p v-if="team.description" class="q-muted team-card__desc">{{ team.description }}</p>
          <div class="q-muted">{{ t('teams.activeCount', { n: team.activeMembers }) }}</div>
        </router-link>

        <div v-if="auth.me && !approved" class="q-notice q-notice--warning" data-testid="teams-need-approval">
          <van-icon name="info-o" /><span>{{ t('teams.needApproval') }}</span>
        </div>
        <van-button
          block
          round
          type="primary"
          icon="plus"
          class="teams__create"
          :disabled="!approved"
          data-testid="team-create-open"
          @click="openCreate"
        >
          {{ t('teams.create') }}
        </van-button>
      </StateView>
    </div>

    <van-popup v-model:show="showCreate" position="bottom" round closeable safe-area-inset-bottom data-testid="team-create-sheet">
      <div class="sheet">
        <h3 class="sheet__title">{{ t('teams.createTitle') }}</h3>
        <van-field
          v-model="name"
          v-testid="'team-create-name'"
          :label="t('teams.name')"
          :placeholder="t('teams.namePlaceholder')"
          maxlength="80"
          label-align="top"
        />
        <van-field
          v-model="description"
          v-testid="'team-create-description'"
          :label="t('teams.description')"
          :placeholder="t('teams.descriptionPlaceholder')"
          type="textarea"
          rows="3"
          autosize
          maxlength="500"
          show-word-limit
          label-align="top"
        />
        <van-button block round type="primary" :loading="creating" data-testid="team-create-submit" @click="create">
          {{ t('teams.create') }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.teams__explain {
  margin-bottom: 4px;
}
.teams__empty {
  margin: 8px 4px 12px;
}
.teams__create {
  margin-top: 12px;
}
.invite__name,
.team-card__name {
  font-size: 16px;
  font-weight: 600;
}
.invite__desc,
.team-card__desc {
  margin: 6px 0;
  line-height: 1.6;
}
.invite__actions {
  justify-content: flex-end;
  margin-top: 10px;
}
.team-card {
  display: block;
  color: var(--q-text);
}
.team-card__lead {
  background: var(--q-primary-soft);
  color: var(--q-primary);
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
