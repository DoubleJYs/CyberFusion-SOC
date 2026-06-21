<template>
  <el-menu :default-active="$route.path" router :collapse="collapsed" class="sidebar-menu">
    <template v-for="item in visibleMenus" :key="item.id">
      <el-sub-menu v-if="item.children?.length" :index="item.path || String(item.id)">
        <template #title>
          <el-icon><component :is="iconOf(item.icon)" /></el-icon>
          <span>{{ item.name }}</span>
        </template>
        <el-menu-item v-for="child in item.children" :key="child.id" :index="child.path || ''">
          <el-icon><component :is="iconOf(child.icon)" /></el-icon>
          <span>{{ child.name }}</span>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-else :index="item.path || ''">
        <el-icon><component :is="iconOf(item.icon)" /></el-icon>
        <span>{{ item.name }}</span>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script setup lang="ts">
import * as Icons from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

defineProps<{ collapsed: boolean }>()

const authStore = useAuthStore()
const fallbackMenus: import('@/types/system').MenuItem[] = [
  { id: 1, parentId: 0, name: '仪表盘', path: '/dashboard', type: 'menu', sort: 1, visible: 1, status: 1, icon: 'DataLine' },
]
const visibleMenus = computed(() => (authStore.menus.length ? authStore.menus : fallbackMenus).filter((item) => item.visible !== 0))

function iconOf(name?: string) {
  return (Icons as Record<string, unknown>)[name || 'Menu'] || Icons.Menu
}
</script>

<style scoped>
.sidebar-menu {
  height: calc(100vh - 56px);
  border-right: 0;
}
</style>
