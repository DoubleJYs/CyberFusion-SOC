import type { App, DirectiveBinding } from 'vue'
import { usePermissionAccess, type PermissionValue } from '@/utils/permission'

function applyPermission(el: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
  const { hasPermission } = usePermissionAccess()
  const originalDisplay = el.dataset.permissionDisplay || el.style.display || ''
  el.dataset.permissionDisplay = originalDisplay
  el.style.display = hasPermission(binding.value) ? originalDisplay : 'none'
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', {
    mounted: applyPermission,
    updated: applyPermission,
  })
}
