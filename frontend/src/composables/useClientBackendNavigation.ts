import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter, type LocationQueryRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { roleExperience } from '@/utils/roleExperience'

const BACKEND_PERMISSIONS: Array<{ prefix: string; permission: string }> = [
  { prefix: '/soc/external-events', permission: 'soc:external-event:view' },
  { prefix: '/soc/vulnerabilities', permission: 'soc:vulnerability:view' },
  { prefix: '/soc/incidents', permission: 'soc:incident:list' },
  { prefix: '/soc/tickets', permission: 'soc:ticket:view' },
  { prefix: '/soc/assets', permission: 'soc:asset:view' },
  { prefix: '/soc/alerts', permission: 'soc:alert:view' },
  { prefix: '/soc/dashboard', permission: 'soc:dashboard:view' },
  { prefix: '/soc/policies', permission: 'soc:policy:list' },
  { prefix: '/system', permission: 'system:user:view' },
]

export function useClientBackendNavigation() {
  const router = useRouter()
  const authStore = useAuthStore()
  const experience = computed(() => roleExperience(authStore.roles, authStore.userInfo))

  function canOpenBackendPath(path: string) {
    if (experience.value.isEmployee || experience.value.isCustomer) return false
    const permission = permissionForPath(path)
    return permission ? authStore.hasPermission(permission) : experience.value.isPlatformAdmin
  }

  async function openBackend(path: string, query: LocationQueryRaw = {}) {
    if (!canOpenBackendPath(path)) {
      ElMessage.warning('当前账号没有后台管理权限，请在安全管家内处理或联系安全团队。')
      return false
    }
    await router.push({ path, query })
    return true
  }

  return {
    canOpenBackendPath,
    openBackend,
  }
}

function permissionForPath(path: string) {
  const match = BACKEND_PERMISSIONS.find((item) => path === item.prefix || path.startsWith(`${item.prefix}/`))
  return match?.permission
}
