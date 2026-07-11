<template>
  <div class="client-layout">
    <header class="client-header">
      <RouterLink class="client-brand" :to="clientRoute('/client/workbench')">
        <span class="client-brand-mark">CF</span>
        <div>
          <strong>我的电脑安全助手</strong>
          <span>查看状态、处理待办、提交日志</span>
        </div>
      </RouterLink>
      <nav class="client-nav">
        <RouterLink :to="clientRoute('/client/workbench')">我的电脑</RouterLink>
        <RouterLink :to="clientRoute('/client/protection')">本机保护</RouterLink>
        <RouterLink :to="clientRoute('/client/operations')">我的待办</RouterLink>
        <RouterLink :to="clientRoute('/client/data-report')">提交日志</RouterLink>
      </nav>
      <div class="client-actions">
        <el-button v-if="showReturnButton" :icon="ArrowLeft" @click="returnToOrigin">{{ returnButtonLabel }}</el-button>
        <el-button v-if="canOpenAdmin" :icon="Monitor" @click="openAdmin">管理端</el-button>
        <el-dropdown>
          <el-button text>
            <el-icon><User /></el-icon>
            {{ authStore.userInfo?.nickname || authStore.userInfo?.username || '用户' }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ authStore.roles.join(', ') || '普通用户' }}</el-dropdown-item>
              <el-dropdown-item disabled>版本 {{ buildInfo.appVersion }} · {{ buildInfo.gitCommit }}</el-dropdown-item>
              <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main class="client-main" @click.capture="captureReturnFocus">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, Monitor, User } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { buildClientDeviceRouteQuery } from '@/composables/useClientDeviceContext'
import { useClientBackendNavigation } from '@/composables/useClientBackendNavigation'
import { useReturnFocusNavigation } from '@/composables/useReturnFocusNavigation'
import { buildInfo } from '@/utils/buildInfo'
import { defaultRouteForExperience } from '@/utils/roleExperience'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const appStore = useAppStore()
const { canOpenBackendPath, openBackend } = useClientBackendNavigation()
const { captureReturnFocus } = useReturnFocusNavigation({ route, router, containerSelector: '.client-main' })
const adminEntryPath = computed(() => defaultRouteForExperience(authStore.roles, authStore.userInfo, authStore.menus))
const canOpenAdmin = computed(() => {
  const path = adminEntryPath.value
  return (path.startsWith('/soc') || path.startsWith('/system')) && canOpenBackendPath(path)
})
const returnTarget = computed(() => appStore.returnRoute)
const hasReturnTarget = computed(() => Boolean(returnTarget.value && returnTarget.value.fullPath !== route.fullPath))
const showReturnButton = computed(() => hasReturnTarget.value || route.path !== '/client/workbench')
const returnButtonLabel = computed(() => hasReturnTarget.value ? '返回原界面' : '返回我的电脑')

function clientRoute(path: string) {
  return {
    path,
    query: buildClientDeviceRouteQuery({
      ip: typeof route.query.ip === 'string' ? route.query.ip : '',
      host: typeof route.query.host === 'string' ? route.query.host : '',
      os: typeof route.query.os === 'string' ? route.query.os : '',
    }),
  }
}

async function logout() {
  await authStore.logout()
  appStore.clearReturnRoute()
  await router.replace('/login')
}

async function returnToOrigin() {
  const target = hasReturnTarget.value ? returnTarget.value?.fullPath : clientRoute('/client/workbench')
  if (!target) return
  appStore.beginReturnNavigation()
  await router.push(target)
}

function openAdmin() {
  void openBackend(adminEntryPath.value)
}
</script>

<style scoped>
.client-layout {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(247, 243, 236, 0.9) 46%, rgba(239, 246, 247, 0.92)),
    var(--soc-canvas);
}

.client-layout::before {
  position: fixed;
  inset: 0;
  pointer-events: none;
  content: "";
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.56), transparent 34%),
    repeating-linear-gradient(90deg, rgba(52, 64, 84, 0.025) 0 1px, transparent 1px 112px);
}

.client-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: minmax(220px, auto) minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  margin: 10px;
  padding: 12px 14px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.14);
}

.client-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.client-brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(135deg, #efb466, #d4934a 52%, #31b6c6);
  box-shadow: 0 12px 24px rgba(212, 147, 74, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.5);
  font-size: 12px;
  font-weight: 760;
}

.client-brand div {
  display: grid;
  gap: 2px;
}

.client-brand strong {
  color: var(--soc-text);
  font-size: 16px;
}

.client-brand span:last-child {
  color: var(--soc-text-muted);
  font-size: 12px;
}

.client-nav,
.client-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.client-nav {
  justify-content: center;
}

.client-nav a {
  padding: 8px 12px;
  border-radius: 8px;
  color: var(--soc-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.client-nav a.router-link-active,
.client-nav a:hover {
  color: var(--soc-warm-strong);
  background: rgba(255, 246, 232, 0.78);
}

.client-actions {
  justify-content: flex-end;
}

.client-main {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1440px;
  min-height: calc(100vh - 96px);
  margin: 0 auto;
  padding: 0 18px 28px;
}

@media (max-width: 900px) {
  .client-header {
    grid-template-columns: 1fr;
  }

  .client-nav {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .client-actions {
    justify-content: space-between;
  }
}
</style>
