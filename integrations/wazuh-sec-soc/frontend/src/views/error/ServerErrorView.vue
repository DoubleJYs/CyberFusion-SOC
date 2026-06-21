<template>
  <main class="error-page">
    <h1>500</h1>
    <p>服务暂时不可用，请稍后再试。</p>
    <el-button type="primary" @click="$router.replace('/dashboard')">返回仪表盘</el-button>
  </main>
</template>

<style scoped>
.error-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  align-content: center;
  gap: 12px;
}
h1 {
  margin: 0;
  font-size: 64px;
}
</style>
