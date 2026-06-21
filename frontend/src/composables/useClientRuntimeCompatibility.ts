import { clientRuntimeCompatibility, type ClientRuntimeCompatibility, type RuntimeCapability } from '@/api/soc'

export async function loadClientRuntimeCompatibility(): Promise<ClientRuntimeCompatibility> {
  try {
    const response = await clientRuntimeCompatibility()
    return response.data.data
  } catch {
    return detectClientRuntimeFallback()
  }
}

export function detectClientRuntimeFallback(): ClientRuntimeCompatibility {
  const platformText = detectBrowserPlatform()
  const osFamily = platformText.includes('win') ? 'windows' : platformText.includes('mac') ? 'macos' : 'linux'
  const capabilities: RuntimeCapability[] = [
    {
      key: 'route_context',
      label: '页面重开上下文',
      status: 'ready',
      message: 'URL query 与浏览器本地上下文可保持当前电脑',
    },
    {
      key: 'local_terminal_guard',
      label: '白名单终端观察',
      status: 'configurable',
      message: `${labelOs(osFamily)} 前端已选择白名单命令集，后端状态待连接`,
    },
    {
      key: 'vm_console_embed',
      label: '本地 VM 控制台',
      status: 'configurable',
      message: '可配置 localhost / 内网 noVNC 控制台地址',
    },
    {
      key: 'data_root_isolation',
      label: '运行数据隔离',
      status: 'warning',
      message: '后端未返回数据根状态，请确认 Environment 目录配置',
    },
  ]
  return {
    platform: {
      osFamily,
      osName: labelOs(osFamily),
      arch: 'browser',
      javaVersion: 'backend-pending',
      browserFamily: detectBrowserFamily(),
    },
    dataRoot: {
      status: 'warning',
      environmentRoot: false,
      outsideSourceRoot: false,
      displayName: '待后端确认',
    },
    adapter: `${labelOs(osFamily)} browser fallback observer`,
    capabilities,
    checkedAt: new Date().toISOString(),
  }
}

function detectBrowserPlatform() {
  if (typeof navigator === 'undefined') return 'linux'
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } }
  return [
    nav.userAgentData?.platform,
    nav.platform,
    nav.userAgent,
  ].filter(Boolean).join(' ').toLowerCase()
}

function detectBrowserFamily() {
  if (typeof navigator === 'undefined') return 'unknown'
  const userAgent = navigator.userAgent.toLowerCase()
  if (userAgent.includes('edg/')) return 'Edge'
  if (userAgent.includes('chrome/') || userAgent.includes('chromium/')) return 'Chromium'
  if (userAgent.includes('firefox/')) return 'Firefox'
  if (userAgent.includes('safari/')) return 'Safari'
  return 'unknown'
}

function labelOs(osFamily: string) {
  if (osFamily === 'windows') return 'Windows'
  if (osFamily === 'macos') return 'macOS'
  if (osFamily === 'linux') return 'Linux'
  return 'Unknown OS'
}
