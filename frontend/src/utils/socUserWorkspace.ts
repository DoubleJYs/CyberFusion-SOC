import type { LocationQuery, RouteLocationRaw } from 'vue-router'

const scopedPaths = new Set([
  '/soc/daily-recommendations',
  '/soc/alerts',
  '/soc/incidents',
  '/soc/assets',
  '/soc/client-security',
  '/soc/vulnerabilities',
  '/soc/baselines',
  '/soc/fim',
  '/soc/external-events',
])

const workspaceCardPaths = new Set([...scopedPaths, '/soc/agents', '/soc/tickets', '/soc/reports'])

const workspaceTargetTitles: Record<string, string> = {
  '/soc/dashboard': '安全运营工作台',
  '/soc/daily-recommendations': '每日处理',
  '/soc/alerts': '告警处置',
  '/soc/incidents': '安全事件簇',
  '/soc/assets': '资产视图',
  '/soc/client-security': '员工终端安全态势',
  '/soc/vulnerabilities': '漏洞中心',
  '/soc/baselines': '基线核查',
  '/soc/fim': '文件完整性',
  '/soc/external-events': '外部事件',
  '/soc/agents': 'Agent 管理',
  '/soc/agents/install': 'Agent 安装',
  '/soc/tickets': '工单中心',
  '/soc/reports': '报告中心',
}

export function requiresUserWorkspace(path: string) {
  return scopedPaths.has(path)
}

export function workspaceTargetPath(target?: string) {
  if (!target) return '/soc/assets'
  try {
    const url = new URL(target, window.location.origin)
    return workspaceCardPaths.has(url.pathname) ? url.pathname : '/soc/assets'
  } catch {
    return '/soc/assets'
  }
}

export function workspaceTargetTitle(target?: string) {
  return workspaceTargetTitles[workspaceTargetPath(target)] || '安全运营工作台'
}

export function workspaceTargetRoute(target: string | undefined, ownerId: number): RouteLocationRaw {
  const safeTarget = workspaceTargetPath(target)
  try {
    const url = new URL(target || safeTarget, window.location.origin)
    const query: LocationQuery = {}
    url.searchParams.forEach((value, key) => { query[key] = value })
    query.ownerId = String(ownerId)
    return { path: safeTarget, query, hash: url.hash }
  } catch {
    return { path: safeTarget, query: { ownerId: String(ownerId) } }
  }
}
