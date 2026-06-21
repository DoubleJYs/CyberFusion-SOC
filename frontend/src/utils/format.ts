export function formatStatus(status: 'enabled' | 'disabled'): string {
  return status === 'enabled' ? '启用' : '停用'
}

export function nowText(): string {
  return new Date().toLocaleString('zh-CN', { hour12: false })
}
