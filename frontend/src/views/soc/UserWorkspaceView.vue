<template>
  <div class="page-shell user-workspace-page">
    <section class="soc-page-hero workspace-hero">
      <div>
        <span class="soc-page-kicker">USER SCOPE / {{ targetTitle.toUpperCase() }}</span>
        <h1>选择用户进入{{ targetTitle }}</h1>
        <p>卡片只展示该用户在“{{ targetTitle }}”中的业务数据，进入后保留用户数据边界。</p>
      </div>
    </section>
    <UserWorkspaceCards :target="target" :focused-owner-id="focusedOwnerId" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import UserWorkspaceCards from '@/components/security/UserWorkspaceCards.vue'
import { workspaceTargetTitle } from '@/utils/socUserWorkspace'

const route = useRoute()
const target = computed(() => typeof route.query.target === 'string' ? route.query.target : '/soc/assets')
const targetTitle = computed(() => workspaceTargetTitle(target.value))
const focusedOwnerId = computed(() => typeof route.query.focusOwnerId === 'string' ? Number(route.query.focusOwnerId) : 0)
</script>

<style scoped>
.user-workspace-page { display: grid; gap: 14px; }
.workspace-hero { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
@media (max-width: 640px) { .workspace-hero { align-items: flex-start; flex-direction: column; } }
</style>
