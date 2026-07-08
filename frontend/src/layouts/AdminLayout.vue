<template>
  <el-container class="admin-layout" :class="{ dark: appStore.darkMode }">
    <el-aside :width="appStore.sidebarCollapsed ? '70px' : '250px'" class="admin-aside">
      <div class="brand" :class="{ collapsed: appStore.sidebarCollapsed }">
        <span class="brand-mark">CF</span>
        <div v-if="!appStore.sidebarCollapsed" class="brand-copy">
          <strong>CyberFusion SOC</strong>
          <span>统一安全运营平台</span>
        </div>
      </div>
      <SidebarMenu :collapsed="appStore.sidebarCollapsed" />
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button v-if="showReturnButton" :icon="ArrowLeft" @click="returnToOrigin">{{ returnButtonLabel }}</el-button>
        </div>
        <div class="header-center">
          <el-input :prefix-icon="Search" placeholder="搜索告警、资产、规则或安全记录..." clearable />
        </div>
        <div class="header-right">
          <el-button type="primary" :icon="DataAnalysis" @click="router.push(EXPERT_HOME_PATH)">安全运营工作台</el-button>
          <el-dropdown trigger="click" class="entry-dropdown" popper-class="entry-dropdown-popper">
            <el-button :icon="Menu">更多入口</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="Promotion" @click="router.push('/showcase')">安全运营演示台</el-dropdown-item>
                <el-dropdown-item v-if="experience.isPlatformAdmin" divided :icon="Setting" @click="router.push('/dashboard')">系统管理</el-dropdown-item>
                <el-dropdown-item v-if="canOpenClient" :icon="Monitor" @click="router.push('/client/workbench')">我的电脑安全助手</el-dropdown-item>
                <el-dropdown-item divided @click="profileVisible = true">个人中心</el-dropdown-item>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="admin-main" @click.capture="captureReturnFocus">
        <RouterView />
      </el-main>
    </el-container>
    <el-drawer v-model="profileVisible" title="个人中心" size="420px">
      <section class="profile-section">
        <div class="profile-row"><span>账号</span><strong>{{ authStore.userInfo?.username || '-' }}</strong></div>
        <div class="profile-row"><span>昵称</span><strong>{{ authStore.userInfo?.nickname || '-' }}</strong></div>
        <div class="profile-row"><span>邮箱</span><strong>{{ authStore.userInfo?.email || '-' }}</strong></div>
        <div class="profile-row"><span>手机</span><strong>{{ authStore.userInfo?.mobile || '-' }}</strong></div>
        <div class="profile-row"><span>角色</span><strong>{{ authStore.roles.join(', ') || '-' }}</strong></div>
      </section>
      <el-divider />
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" :loading="passwordSaving" @click="savePassword">修改密码</el-button>
      </el-form>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  ArrowLeft,
  DataAnalysis,
  Menu,
  Monitor,
  Promotion,
  Search,
  Setting,
} from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import SidebarMenu from '@/components/AppLayout/SidebarMenu.vue'
import { changeCurrentPassword } from '@/api/user'
import { useReturnFocusNavigation } from '@/composables/useReturnFocusNavigation'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { EXPERT_HOME_PATH, roleExperience } from '@/utils/roleExperience'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()
const experience = computed(() => roleExperience(authStore.roles, authStore.userInfo))
const canOpenClient = computed(() => experience.value.isSuperAdmin || experience.value.isPlatformAdmin || authStore.hasPermission('client:workbench:view'))
const { captureReturnFocus } = useReturnFocusNavigation({ route, router, containerSelector: '.admin-main' })
const returnTarget = computed(() => appStore.returnRoute)
const hasReturnTarget = computed(() => Boolean(returnTarget.value && returnTarget.value.fullPath !== route.fullPath))
const showReturnButton = computed(() => hasReturnTarget.value || route.path !== EXPERT_HOME_PATH)
const returnButtonLabel = computed(() => hasReturnTarget.value ? '返回原界面' : '返回工作台')
const profileVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function logout() {
  await authStore.logout()
  appStore.clearReturnRoute()
  await router.replace('/login')
}

async function returnToOrigin() {
  const target = hasReturnTarget.value ? returnTarget.value?.fullPath : EXPERT_HOME_PATH
  if (!target || target === route.fullPath) return
  appStore.beginReturnNavigation()
  await router.push(target)
}

async function savePassword() {
  if (!(await passwordFormRef.value?.validate().catch(() => false))) return
  passwordSaving.value = true
  try {
    await changeCurrentPassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    profileVisible.value = false
    await logout()
  } finally {
    passwordSaving.value = false
  }
}

</script>

<style scoped>
.admin-layout {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(247, 243, 235, 0.86) 48%, rgba(238, 245, 247, 0.92)),
    var(--soc-canvas);
}

.admin-layout::before {
  position: fixed;
  inset: 0;
  pointer-events: none;
  content: "";
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.5), transparent 32%),
    repeating-linear-gradient(90deg, rgba(52, 64, 84, 0.025) 0 1px, transparent 1px 120px);
}

.admin-aside {
  z-index: 2;
  margin: 10px 0 10px 10px;
  overflow: hidden;
  border: 1px solid rgba(200, 192, 179, 0.6);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 0 18px 44px rgba(91, 77, 53, 0.12), inset 1px 1px 0 rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(24px) saturate(1.18);
  transition: width 0.2s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 16px;
  border-bottom: 1px solid var(--soc-border);
}

.brand.collapsed {
  justify-content: center;
  padding: 0;
}

.brand-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.brand-copy strong {
  color: #111827;
  font-size: 16px;
  letter-spacing: 0;
}

.brand-copy span {
  color: var(--soc-text-muted);
  font-size: 11px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(135deg, #efb466, #d4934a 52%, #31b6c6);
  box-shadow: 0 12px 24px rgba(212, 147, 74, 0.24), inset 0 1px 0 rgba(255, 255, 255, 0.48);
  font-size: 12px;
  font-weight: 700;
}

.admin-header {
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  height: 64px;
  margin: 10px 10px 0;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.16);
}

.header-left {
  display: flex;
  align-items: center;
  min-width: fit-content;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.header-center {
  flex: 1 1 560px;
  max-width: 680px;
  min-width: 260px;
}

.header-center :deep(.el-input__wrapper) {
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
}

.admin-main {
  position: relative;
  z-index: 1;
  min-width: 0;
  height: calc(100vh - 84px);
  padding: 18px 20px 24px;
  overflow: auto;
}

.profile-section {
  display: grid;
  gap: 10px;
}

.profile-row {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 32px;
}

.profile-row span {
  color: var(--soc-text-muted);
}

.profile-row strong {
  overflow-wrap: anywhere;
  font-weight: 600;
}

@media (max-width: 980px) {
  .admin-header {
    gap: 10px;
  }

  .header-center {
    flex-basis: 320px;
    min-width: 220px;
  }

  .admin-aside {
    margin-left: 6px;
  }

  .admin-header {
    margin-right: 6px;
  }
}

@media (max-width: 760px) {
  .admin-aside {
    width: 70px !important;
    min-width: 70px !important;
    max-width: 70px !important;
    margin: 6px 0 6px 6px;
  }

  .brand {
    justify-content: center;
    height: 56px;
    padding: 0;
  }

  .brand-copy {
    display: none;
  }

  .admin-header {
    height: 56px;
    margin: 6px 6px 0;
    padding: 0 10px;
  }

  .header-right {
    margin-left: auto;
  }

  .header-center {
    min-width: 0;
  }

  .admin-main {
    height: calc(100vh - 68px);
    padding: 12px;
  }

}
</style>
