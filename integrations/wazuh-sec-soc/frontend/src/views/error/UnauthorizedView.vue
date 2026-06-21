<template>
  <section class="error-page">
    <h1>401</h1>
    <p>登录状态不可用，请重新登录。</p>
    <el-button type="primary" @click="$router.replace('/login')">返回登录</el-button>
  </section>
</template>

<style scoped>
.error-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  gap: 12px;
}
</style>
