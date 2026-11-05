<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Avatar,
  CollectionTag,
  DataBoard,
  Document,
  Key,
  Lock,
  Menu as MenuIcon,
  Message,
  Money,
  OfficeBuilding,
  Star,
  SwitchButton,
  User,
  Warning,
} from '@element-plus/icons-vue'
import LocaleSwitch from '@/components/LocaleSwitch.vue'
import { ADMIN_SECTIONS } from '@/router'
import { useAdminStore } from '@/stores/admin'

const ICONS: Record<string, Component> = {
  DataBoard,
  OfficeBuilding,
  User,
  Document,
  Warning,
  Money,
  Lock,
  CollectionTag,
  Message,
  Key,
  Star,
  Avatar,
}

const { t } = useI18n()
const admin = useAdminStore()
const route = useRoute()
const router = useRouter()
const drawer = ref(false)

const items = computed(() =>
  ADMIN_SECTIONS.filter((s) => admin.hasAnyRole(s.roles)).map((s) => ({
    ...s,
    key: s.name.replace(/^admin-/, ''),
    to: { name: s.name },
    iconComponent: ICONS[s.icon] ?? DataBoard,
  })),
)

const activeName = computed(() => String(route.name ?? 'admin-dashboard'))

watch(
  () => route.fullPath,
  () => {
    drawer.value = false
  },
)

function go(name: string) {
  void router.push({ name })
}

function logout() {
  admin.clear()
  void router.push({ name: 'admin-login' })
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-side">
      <div class="side-brand">
        <span class="side-logo" aria-hidden="true">桥</span>
        <span>{{ t('admin.brand') }}</span>
      </div>
      <el-menu :default-active="activeName" class="side-menu" @select="go">
        <el-menu-item v-for="it in items" :key="it.name" :index="it.name" :data-testid="`admin-menu-${it.key}`">
          <el-icon><component :is="it.iconComponent" /></el-icon>
          <span>{{ t(it.titleKey) }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <el-drawer v-model="drawer" direction="ltr" size="260px" :with-header="false" append-to-body class="admin-drawer">
      <div class="side-brand">
        <span class="side-logo" aria-hidden="true">桥</span>
        <span>{{ t('admin.brand') }}</span>
      </div>
      <el-menu :default-active="activeName" class="side-menu" @select="go">
        <el-menu-item v-for="it in items" :key="it.name" :index="it.name" :data-testid="`admin-menu-${it.key}-mobile`">
          <el-icon><component :is="it.iconComponent" /></el-icon>
          <span>{{ t(it.titleKey) }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <div class="admin-main">
      <header class="admin-top">
        <button type="button" class="menu-toggle" :aria-label="t('nav.menu')" data-testid="admin-menu-toggle" @click="drawer = true">
          <el-icon :size="20"><MenuIcon /></el-icon>
        </button>
        <div class="top-title">{{ route.meta.titleKey ? t(route.meta.titleKey) : '' }}</div>
        <div class="top-right">
          <div class="who hide-sm">
            <span class="who-email" data-testid="admin-current-email">{{ admin.admin?.email }}</span>
            <span class="who-roles">
              <el-tag v-for="r in admin.roles" :key="r" size="small" effect="plain">{{ t(`admin.roles.${r}`) }}</el-tag>
            </span>
          </div>
          <LocaleSwitch area="admin" testid-prefix="admin-locale" />
          <el-button :icon="SwitchButton" text data-testid="admin-logout" @click="logout">
            <span class="hide-sm">{{ t('nav.logout') }}</span>
          </el-button>
        </div>
      </header>
      <el-alert
        v-if="route.query.denied"
        type="warning"
        :closable="false"
        show-icon
        class="denied"
        :title="t('admin.denied')"
        data-testid="admin-denied"
      />
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
  background: var(--q-bg);
}
.admin-side {
  width: 232px;
  flex-shrink: 0;
  background: var(--q-surface);
  border-right: 1px solid var(--q-border);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}
.side-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 20px;
  font-weight: 700;
  border-bottom: 1px solid var(--q-border);
}
.side-logo {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: var(--q-accent);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
}
.side-menu {
  border-right: 0;
}
.admin-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.admin-top {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  background: var(--q-surface);
  border-bottom: 1px solid var(--q-border);
  position: sticky;
  top: 0;
  z-index: 20;
}
.top-title {
  font-weight: 650;
  font-size: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.top-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.who {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}
.who-email {
  font-size: 13px;
  color: var(--q-text-2);
}
.who-roles {
  display: flex;
  gap: 4px;
  margin-top: 2px;
}
.menu-toggle {
  display: none;
  border: 1px solid var(--q-border);
  background: var(--q-surface);
  border-radius: 6px;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--q-text-2);
  flex-shrink: 0;
}
.denied {
  margin: 16px 20px 0;
  width: auto;
}
.admin-content {
  padding: 20px;
  min-width: 0;
}

@media (max-width: 900px) {
  .admin-side {
    display: none;
  }
  .menu-toggle {
    display: inline-flex;
  }
  .hide-sm {
    display: none;
  }
  .admin-top {
    padding: 0 16px;
  }
  .admin-content {
    padding: 16px;
  }
  .denied {
    margin: 16px 16px 0;
  }
}
</style>
