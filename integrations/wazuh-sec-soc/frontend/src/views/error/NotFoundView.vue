<template>
  <main class="not-found">
    <h1>404</h1>
    <p>页面不存在或已移动。</p>
    <el-button type="primary" @click="$router.replace('/dashboard')">回到仪表盘</el-button>
  </main>
</template>

<style scoped>
.not-found {
  display: grid;
  min-height: 100vh;
  place-items: center;
  align-content: center;
  gap: 12px;
}

.not-found h1 {
  margin: 0;
  font-size: 64px;
}
</style>
