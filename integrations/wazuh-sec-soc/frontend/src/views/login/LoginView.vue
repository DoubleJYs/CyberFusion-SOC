<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-copy">
        <strong class="eyebrow">WAZUH SOC OPERATIONS</strong>
        <h1>Sec Wazuh SOC</h1>
        <p>企业安全监测与告警处置平台，统一承载安全总览、告警分析、工单闭环、报表导出、权限控制和审计日志。</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="login-form" @keyup.enter="submit">
        <el-alert title="本地演示账号：admin / Admin@123456，仅用于授权测试环境" type="info" show-icon :closable="false" />
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" size="large" type="password" autocomplete="current-password" show-password placeholder="请输入密码" />
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
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: 'admin', password: '', rememberMe: true })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    await router.replace(String(route.query.redirect || '/soc/dashboard'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background: #07111f;
}

.login-panel {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(320px, 400px);
  gap: 40px;
  width: min(920px, 100%);
  padding: 36px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #0c1728;
  box-shadow: var(--soc-shadow);
}

.eyebrow {
  color: var(--soc-cyan);
  font-size: 12px;
}

.login-copy h1 {
  margin: 16px 0 14px;
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
  color: #64748b;
  font-size: 13px;
}

.login-button {
  width: 100%;
}

@media (max-width: 760px) {
  .login-panel {
    grid-template-columns: 1fr;
    padding: 24px;
  }
}
</style>
