<template>
  <el-sub-menu v-if="visibleChildren.length" :index="item.path || String(item.id)">
    <template #title>
      <el-icon><component :is="iconOf(item.icon)" /></el-icon>
      <span>{{ item.name }}</span>
    </template>
    <SidebarMenuNode v-for="child in visibleChildren" :key="child.id" :item="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="item.path || ''">
    <el-icon><component :is="iconOf(item.icon)" /></el-icon>
    <span>{{ item.name }}</span>
  </el-menu-item>
</template>

<script setup lang="ts">
import * as Icons from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { MenuItem } from '@/types/system'

const props = defineProps<{ item: MenuItem }>()

const visibleChildren = computed(() =>
  (props.item.children || []).filter((child) => child.visible !== 0 && child.status !== 0 && child.type !== 'button'),
)

function iconOf(name?: string) {
  return (Icons as Record<string, unknown>)[name || 'Menu'] || Icons.Menu
}
</script>
