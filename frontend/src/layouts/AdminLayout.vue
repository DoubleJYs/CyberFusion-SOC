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
          <el-button :icon="appStore.sidebarCollapsed ? Expand : Fold" circle @click="appStore.toggleSidebar()" />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ pageContext.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-center">
          <el-input :prefix-icon="Search" placeholder="搜索告警、资产、规则或安全记录..." clearable />
        </div>
        <div class="header-right">
          <span class="runtime-pill"><i />平台运行中</span>
          <el-tag class="experience-tag" effect="plain">{{ experience.label }}</el-tag>
          <el-segmented
            class="view-mode-switch"
            :model-value="appStore.viewMode"
            :options="viewModeOptions"
            @change="setViewMode"
          />
          <el-button type="primary" :icon="Promotion" @click="router.push('/showcase')">安全运营演示台</el-button>
          <el-dropdown trigger="click" class="entry-dropdown">
            <el-button :icon="Menu">更多入口</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="DataAnalysis" @click="router.push('/soc/dashboard')">SOC 专家后台</el-dropdown-item>
                <el-dropdown-item v-if="experience.isPlatformAdmin" :icon="Setting" @click="router.push('/system/user')">系统管理</el-dropdown-item>
                <el-dropdown-item v-if="canOpenClient" :icon="Monitor" @click="router.push('/client/workbench')">员工端</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-switch v-model="appStore.darkMode" inline-prompt active-text="玻璃" inactive-text="柔光" />
          <el-dropdown>
            <el-button text>
              <el-icon><User /></el-icon>
              {{ authStore.userInfo?.nickname || authStore.userInfo?.username || '用户' }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ authStore.roles.join(', ') || '无角色' }}</el-dropdown-item>
                <el-dropdown-item disabled>版本 {{ buildInfo.appVersion }} · {{ buildInfo.gitCommit }}</el-dropdown-item>
                <el-dropdown-item disabled>构建 {{ buildInfo.buildTime }}</el-dropdown-item>
                <el-dropdown-item divided @click="profileVisible = true">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="admin-main">
        <section class="page-heading">
          <div>
            <h1>{{ pageContext.title }}</h1>
            <span>{{ pageContext.description }}</span>
          </div>
          <div class="page-context-pills">
            <el-tag effect="dark">{{ appStore.viewMode }}</el-tag>
            <el-tag v-for="pill in pageContext.pills" :key="pill" effect="plain">{{ pill }}</el-tag>
          </div>
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
import { computed, reactive, ref } from 'vue'
import { DataAnalysis, Expand, Fold, Menu, Monitor, Promotion, Search, Setting, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import SidebarMenu from '@/components/AppLayout/SidebarMenu.vue'
import { changeCurrentPassword } from '@/api/user'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { buildInfo } from '@/utils/buildInfo'
import { roleExperience, type ViewMode } from '@/utils/roleExperience'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const experience = computed(() => roleExperience(authStore.roles, authStore.userInfo))
const canOpenClient = computed(() => experience.value.isSuperAdmin || experience.value.isPlatformAdmin || authStore.hasPermission('client:workbench:view'))
const viewModeOptions = [
  { label: '简洁', value: 'simple' },
  { label: '详细', value: 'detail' },
  { label: '专家', value: 'expert' },
]
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

const pageContext = computed(() => {
  const path = route.path
  const direct = pageCopy[path]
  if (direct) return direct
  if (path.startsWith('/soc/')) {
    return pageCopy['/soc/dashboard']
  }
  if (path.startsWith('/system/user') || path.startsWith('/system/role')) {
    return {
      title: '身份权限',
      description: '登录、RBAC、角色权限和数据范围的统一治理',
      pills: ['账号', '角色', '权限'],
    }
  }
  if (path.startsWith('/system/dept') || path.startsWith('/system/post')) {
    return {
      title: '组织基础',
      description: '部门、岗位和责任人信息服务于资产归属与工单分派',
      pills: ['组织基础', '责任归属'],
    }
  }
  if (path.startsWith('/system/log') || path.includes('/workflow') || path.includes('/excel')) {
    return {
      title: '审计记录',
      description: '查看平台操作、导入导出和流程记录，支撑问题追溯。',
      pills: ['审计日志', '流程追踪', '留痕'],
    }
  }
  if (path.startsWith('/system/')) {
    return {
      title: '系统管理',
      description: '维护平台参数、菜单、字典、公告和文件配置。',
      pills: ['平台配置', '运行支撑'],
    }
  }
  return {
    title: '工作台',
    description: '查看平台运行状态和最近操作入口。',
    pills: ['门户', '系统状态'],
  }
})

const pageCopy: Record<string, { title: string; description: string; pills: string[] }> = {
  '/soc/dashboard': {
    title: '工作台',
    description: '这个页面帮你快速了解今天的风险、告警和待处理事项。',
    pills: ['总览', '待办', '趋势'],
  },
  '/soc/capabilities': {
    title: '平台能力说明',
    description: '这个页面帮你了解当前平台覆盖了哪些安全能力和入口。',
    pills: ['能力', '入口', '说明'],
  },
  '/soc/demo-range': {
    title: '安全验证',
    description: '这个页面帮你导入演示批次，并串起事件、告警、工单、报告和通知记录。',
    pills: ['演示批次', '证据', '闭环'],
  },
  '/soc/alerts': {
    title: '告警处置',
    description: '这个页面帮你查看待处理告警，并完成确认、误报、关闭或转工单。',
    pills: ['告警', '处置', '工单'],
  },
  '/soc/rules': {
    title: '检测规则',
    description: '这个页面帮你看懂规则来源、命中情况和为什么会生成告警。',
    pills: ['规则', '命中', '映射'],
  },
  '/soc/alert-noise': {
    title: '降噪规则',
    description: '这个页面帮你管理重复告警和误报白名单，减少无效打扰。',
    pills: ['降噪', '白名单', '误报'],
  },
  '/soc/assets': {
    title: '资产风险',
    description: '这个页面帮你查看资产风险、责任人和进入单机分析。',
    pills: ['资产', '风险', '责任人'],
  },
  '/soc/vulnerabilities': {
    title: '漏洞风险',
    description: '这个页面帮你跟踪软件漏洞、影响资产和修复状态。',
    pills: ['漏洞', '修复', '优先级'],
  },
  '/soc/baselines': {
    title: '配置检查',
    description: '这个页面帮你查看安全配置检查结果和后续处理状态。',
    pills: ['配置', '检查', '整改'],
  },
  '/soc/fim': {
    title: '文件变更',
    description: '这个页面帮你复核关键文件变化，确认是否为授权变更。',
    pills: ['文件', '变更', '复核'],
  },
  '/soc/external-events': {
    title: '证据中心',
    description: '这个页面帮你查看和导入来自不同安全工具的原始安全记录。',
    pills: ['记录', '来源', '关联'],
  },
  '/soc/tickets': {
    title: '工单中心',
    description: '这个页面帮你跟踪告警处置进度、负责人和时间线。',
    pills: ['工单', '负责人', '时间线'],
  },
  '/soc/reports': {
    title: '报告中心',
    description: '这个页面帮你生成、查看和导出安全运营或安全验证报告。',
    pills: ['报告', '导出', '验收'],
  },
  '/soc/settings': {
    title: '系统管理',
    description: '这个页面帮你配置数据源、同步任务、通知通道和 dry-run 日志。',
    pills: ['数据源', '通知', '健康检查'],
  },
  '/dashboard': {
    title: '平台仪表盘',
    description: '这个页面帮你查看系统治理侧的登录、公告和操作概览。',
    pills: ['系统', '公告', '日志'],
  },
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

function setViewMode(mode: string | number | boolean) {
  appStore.setViewMode(String(mode) as ViewMode)
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
  gap: 16px;
  height: 64px;
  margin: 10px 10px 0;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.16);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.header-center {
  flex: 1 1 460px;
  max-width: 560px;
  min-width: 220px;
}

.header-center :deep(.el-input__wrapper) {
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
}

.runtime-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(39, 174, 96, 0.22);
  border-radius: 8px;
  background: rgba(236, 253, 245, 0.76);
  color: #157347;
  font-size: 12px;
  font-weight: 650;
}

.experience-tag {
  max-width: 128px;
}

.view-mode-switch {
  width: 168px;
}

.runtime-pill i {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--soc-success);
  box-shadow: 0 0 0 4px rgba(36, 168, 101, 0.12);
}

.admin-main {
  position: relative;
  z-index: 1;
  min-width: 0;
  height: calc(100vh - 84px);
  padding: 18px 20px 24px;
  overflow: auto;
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
  color: #111827;
  font-size: 24px;
  font-weight: 760;
}

.page-heading span {
  display: inline-block;
  margin-top: 5px;
  color: var(--soc-text-muted);
  font-size: 13px;
}

.page-context-pills {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
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
  .header-center,
  .runtime-pill {
    display: none;
  }

  .admin-aside {
    margin-left: 6px;
  }

  .admin-header {
    margin-right: 6px;
  }
}

@media (max-width: 1280px) {
  .header-center {
    display: none;
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

  .header-left {
    gap: 8px;
  }

  .header-right {
    margin-left: auto;
  }

  .header-right :deep(.el-switch),
  .header-right :deep(.el-button span) {
    display: none;
  }

  .admin-main {
    height: calc(100vh - 68px);
    padding: 12px;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .page-heading h1 {
    font-size: 20px;
  }

  .page-context-pills {
    justify-content: flex-start;
  }
}
</style>
