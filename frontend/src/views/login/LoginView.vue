<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-copy">
        <strong class="eyebrow">CYBERFUSION SECURITY OPERATIONS</strong>
        <h1>CyberFusion SOC</h1>
        <p>统一融合 Wazuh、Zeek、Suricata、MISP、Trivy、ZAP、CyberChef 与 Shuffle 的安全运营主系统。</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="login-form" @keyup.enter="submit">
        <el-alert title="本地演示账号：admin，密码：Admin@123456，仅用于授权测试环境" type="info" show-icon :closable="false" />
        <el-alert v-if="loginError" type="warning" show-icon :closable="false">
          <template #title>
            <span>{{ loginError }}</span>
            <el-button v-if="showHealthAction" link type="primary" class="health-link" @click="openHealth">查看健康诊断</el-button>
          </template>
        </el-alert>
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入账号" @input="clearLoginError" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" size="large" type="password" autocomplete="new-password" show-password placeholder="请输入密码" @input="clearLoginError" />
        </el-form-item>
        <div class="form-row">
          <el-checkbox v-model="form.rememberMe">记住我</el-checkbox>
          <span>后端校验菜单、按钮、接口和数据权限</span>
        </div>
        <el-button type="primary" size="large" :loading="loading" class="login-button" @click="submit">登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { firstRoutePathFromMenus } from '@/router/menuRoutes'

const authStore = useAuthStore()
const appStore = useAppStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loginError = ref('')
const showHealthAction = computed(() => loginError.value.includes('登录服务暂时不可用') || loginError.value.includes('后端'))
const formRef = ref<FormInstance>()
const form = reactive({ username: 'admin', password: 'Admin@123456', rememberMe: true })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (loading.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  loginError.value = ''
  loading.value = true
  try {
    await authStore.login(form)
    appStore.applyRoleExperience(authStore.roles, authStore.userInfo)
    ElMessage.success('登录成功')
    await router.replace(String(route.query.redirect || firstRoutePathFromMenus(authStore.menus, authStore.roles, authStore.userInfo)))
  } catch (error) {
    loginError.value = normalizeLoginError(error)
  } finally {
    loading.value = false
  }
}

function normalizeLoginError(error: unknown) {
  const axiosLike = error as {
    response?: {
      status?: number
      data?: { message?: unknown }
      headers?: Record<string, unknown>
    }
    message?: string
  }
  const backendMessage = typeof axiosLike.response?.data?.message === 'string' ? axiosLike.response.data.message : ''
  const message = backendMessage || (error instanceof Error ? error.message : '')
  if (axiosLike.response?.status === 429 || message.includes('429') || message.includes('频繁')) {
    const retryAfter = axiosLike.response?.headers?.['retry-after']
    const retryText = typeof retryAfter === 'string' && retryAfter.trim() ? `约 ${retryAfter} 秒后再试。` : '请稍后再试。'
    return `登录请求过于频繁，${retryText}请避免连续点击登录按钮。`
  }
  if ((axiosLike.response?.status && axiosLike.response.status >= 500) || message.includes('status code 500')) {
    return '登录服务暂时不可用。请确认后端已启动、数据库已初始化，并访问 /api/health 查看诊断；当前本地种子账号默认使用 README 中记录的演示密码。'
  }
  return message || '登录失败，请检查账号、密码和后端服务状态。'
}

function clearLoginError() {
  if (loginError.value) {
    loginError.value = ''
  }
}

function openHealth() {
  window.open('/api/health', '_blank', 'noopener,noreferrer')
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(247, 242, 234, 0.9) 52%, rgba(235, 244, 247, 0.94)),
    var(--soc-canvas);
}

.login-panel {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(320px, 400px);
  gap: 40px;
  width: min(920px, 100%);
  padding: 36px;
  border: 1px solid rgba(190, 183, 171, 0.58);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 28px 80px rgba(91, 77, 53, 0.16), var(--soc-glass-highlight);
  backdrop-filter: blur(26px) saturate(1.15);
}

.eyebrow {
  color: var(--soc-warm-strong);
  font-size: 12px;
}

.login-copy h1 {
  margin: 16px 0 14px;
  color: #111827;
  font-size: 36px;
}

.login-copy p {
  margin: 0;
  color: var(--soc-text-muted);
  line-height: 1.8;
}

.login-form {
  display: grid;
  gap: 12px;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--soc-text-muted);
  font-size: 13px;
}

.login-button {
  width: 100%;
}

.health-link {
  margin-left: 8px;
  padding: 0;
  vertical-align: baseline;
}

@media (max-width: 760px) {
  .login-panel {
    grid-template-columns: 1fr;
    padding: 24px;
  }
}
</style>
