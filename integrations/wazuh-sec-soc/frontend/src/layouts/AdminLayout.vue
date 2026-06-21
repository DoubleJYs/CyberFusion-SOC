<template>
  <el-container class="admin-layout" :class="{ dark: appStore.darkMode }">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '232px'" class="admin-aside">
      <div class="brand" :class="{ collapsed: appStore.sidebarCollapsed }">
        <span class="brand-mark">SOC</span>
        <strong v-if="!appStore.sidebarCollapsed">Sec Wazuh SOC</strong>
      </div>
      <SidebarMenu :collapsed="appStore.sidebarCollapsed" />
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button :icon="appStore.sidebarCollapsed ? Expand : Fold" circle @click="appStore.toggleSidebar()" />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-switch v-model="appStore.darkMode" inline-prompt active-text="暗" inactive-text="亮" />
          <el-dropdown>
            <el-button text>
              <el-icon><User /></el-icon>
              {{ authStore.userInfo?.nickname || authStore.userInfo?.username || '用户' }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ authStore.roles.join(', ') || '无角色' }}</el-dropdown-item>
                <el-dropdown-item divided @click="profileVisible = true">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="admin-main">
        <section class="page-heading">
          <h1>{{ route.meta.title }}</h1>
          <span>Wazuh 引擎之上的企业安全运营业务层</span>
        </section>
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
import { reactive, ref } from 'vue'
import { Expand, Fold, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import SidebarMenu from '@/components/AppLayout/SidebarMenu.vue'
import { changeCurrentPassword } from '@/api/user'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
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
  await router.replace('/login')
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
  min-height: 100vh;
  background: var(--soc-canvas);
}

.admin-layout.dark {
  background: var(--soc-canvas);
}

.admin-aside {
  border-right: 1px solid var(--soc-border);
  background: var(--soc-surface);
  transition: width 0.2s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--soc-border);
}

.brand.collapsed {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #fff;
  background: var(--soc-cyan);
  font-size: 12px;
  font-weight: 700;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--soc-border);
  background: var(--soc-surface);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.admin-main {
  min-width: 0;
  padding: 18px;
}

.page-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.page-heading h1 {
  margin: 0;
  font-size: 22px;
}

.page-heading span {
  color: var(--soc-text-muted);
  font-size: 13px;
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
</style>
