<template>
  <main class="error-page">
    <section class="error-card">
      <span>SESSION REQUIRED</span>
      <h1>401</h1>
      <p>登录状态不可用，请重新登录后进入 CyberFusion SOC。</p>
      <el-button type="primary" @click="$router.replace('/login')">返回登录</el-button>
    </section>
  </main>
</template>

<style scoped>
.error-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(247, 243, 235, 0.88) 50%, rgba(238, 245, 247, 0.9)),
    var(--soc-canvas);
}

.error-card {
  display: grid;
  justify-items: start;
  gap: 10px;
  width: min(460px, 100%);
  padding: 28px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: var(--soc-surface);
  box-shadow: var(--soc-shadow), var(--soc-glass-highlight);
  backdrop-filter: blur(22px) saturate(1.14);
}

.error-card span {
  color: var(--soc-warm-strong);
  font-size: 12px;
  font-weight: 760;
}

h1 {
  margin: 0;
  color: var(--soc-text);
  font-size: 64px;
}

p {
  margin: 0 0 8px;
  color: var(--soc-text-muted);
  line-height: 1.7;
}
</style>
