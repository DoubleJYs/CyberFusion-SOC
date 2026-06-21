import {
  clientDeviceProfile,
  clientDevices,
  listAssets,
  type AssetItem,
  type ClientDeviceMetrics,
  type ClientDeviceProfile,
} from '@/api/soc'

export const DEMO_CLIENT_IP = '10.20.1.15'
export const DEMO_CLIENT_HOSTNAME = 'prod-app-01'
const CLIENT_DEVICE_CONTEXT_STORAGE_KEY = 'cyberfusion_client_device_context'
const SECURITY_KEEPER_ACCEPTED_STORAGE_KEY = 'cyberfusion_security_keeper_service_accepted'

type ClientDeviceContext = {
  ip: string
  host: string
  os?: string
}

export const emptyClientMetrics: ClientDeviceMetrics = {
  riskScore: 0,
  alerts: 0,
  openVulnerabilities: 0,
  failedBaselines: 0,
  pendingFileIntegrity: 0,
  pendingExternalEvents: 0,
  summary: '暂无当前电脑证据',
}

export async function loadClientAssets() {
  try {
    const res = await clientDevices()
    const data = res.data.data
    return {
      records: Array.isArray(data) ? data : data.records,
      clientApiAvailable: true,
    }
  } catch {
    const res = await listAssets({ pageNum: 1, pageSize: 100 })
    return {
      records: res.data.data.records,
      clientApiAvailable: false,
    }
  }
}

export function chooseClientAsset(
  records: AssetItem[],
  options: {
    routeIp?: string
    routeHost?: string
    currentNames?: Array<string | undefined>
    allowDemoFallback?: boolean
    allowFirstFallback?: boolean
    preferAcceptedLocal?: boolean
  } = {},
) {
  const routeIp = options.routeIp || ''
  const routeHost = options.routeHost || ''
  const currentNames = (options.currentNames || [])
    .filter(Boolean)
    .map((item) => String(item).toLowerCase())
  const stored = readClientDeviceContext()
  const localAsset = findClientLocalAsset(records)
  if (options.preferAcceptedLocal && hasAcceptedSecurityKeeperService() && localAsset && (!routeIp || isDemoClientContext(routeIp, routeHost))) {
    saveClientDeviceContext(localAsset)
    return localAsset
  }

  const selected = (routeIp ? records.find((asset) => asset.ip === routeIp) : undefined)
    || (routeHost ? records.find((asset) => asset.hostname === routeHost) : undefined)
    || records.find((asset) => {
      const owner = String(asset.ownerName || '').toLowerCase()
      return owner && currentNames.some((name) => owner === name || owner.includes(name))
    })
    || (stored?.ip ? records.find((asset) => asset.ip === stored.ip) : undefined)
    || (stored?.host ? records.find((asset) => asset.hostname === stored.host) : undefined)
    || (options.allowDemoFallback ? records.find((asset) => asset.ip === DEMO_CLIENT_IP || asset.hostname === DEMO_CLIENT_HOSTNAME) : undefined)
    || (options.allowFirstFallback ? records[0] : undefined)
  saveClientDeviceContext(selected)
  return selected
}

export function findClientLocalAsset(records: AssetItem[]) {
  return records.find((asset) => asset.sourceType === 'client-local')
}

export function isDemoClientContext(ip?: string, host?: string) {
  return ip === DEMO_CLIENT_IP || host === DEMO_CLIENT_HOSTNAME
}

export function hasAcceptedSecurityKeeperService() {
  if (!canUseLocalStorage()) return false
  return localStorage.getItem(SECURITY_KEEPER_ACCEPTED_STORAGE_KEY) === '1'
}

export function acceptSecurityKeeperService(asset: AssetItem) {
  saveClientDeviceContext(asset)
  if (!canUseLocalStorage()) return
  localStorage.setItem(SECURITY_KEEPER_ACCEPTED_STORAGE_KEY, '1')
}

export function revokeSecurityKeeperService() {
  if (!canUseLocalStorage()) return
  localStorage.removeItem(SECURITY_KEEPER_ACCEPTED_STORAGE_KEY)
}

export async function loadClientProfile(asset: AssetItem) {
  const res = await clientDeviceProfile(asset.ip)
  return res.data.data
}

export function buildEmptyClientProfile(asset: AssetItem): ClientDeviceProfile {
  return {
    asset,
    metrics: {
      ...emptyClientMetrics,
      riskScore: fallbackRiskScore(asset.riskLevel),
    },
    alerts: [],
    vulnerabilities: [],
    baselines: [],
    fileIntegrityEvents: [],
    externalEvents: [],
    timeline: [],
  }
}

function fallbackRiskScore(riskLevel?: string) {
  if (riskLevel === 'critical') return 90
  if (riskLevel === 'high') return 72
  if (riskLevel === 'medium') return 42
  return 0
}

export function readClientDeviceContext(): ClientDeviceContext | undefined {
  if (!canUseLocalStorage()) return undefined
  try {
    const raw = localStorage.getItem(CLIENT_DEVICE_CONTEXT_STORAGE_KEY)
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as Partial<ClientDeviceContext>
    if (!parsed.ip && !parsed.host) return undefined
    return {
      ip: String(parsed.ip || ''),
      host: String(parsed.host || ''),
      os: parsed.os ? String(parsed.os) : undefined,
    }
  } catch {
    return undefined
  }
}

export function saveClientDeviceContext(asset?: AssetItem | null): void {
  if (!asset || !canUseLocalStorage()) return
  try {
    const context: ClientDeviceContext = {
      ip: asset.ip,
      host: asset.hostname,
      os: asset.osType,
    }
    localStorage.setItem(CLIENT_DEVICE_CONTEXT_STORAGE_KEY, JSON.stringify(context))
  } catch {
    // Browser storage can be unavailable in restricted/private modes; route query fallback still works.
  }
}

export function buildClientDeviceRouteQuery(
  current?: Partial<{ ip: string; host: string; os: string }>,
): Record<string, string> {
  const stored = readClientDeviceContext()
  const useStored = stored && (!current?.ip || isDemoClientContext(current.ip, current.host))
  const query = {
    ip: useStored ? stored.ip : current?.ip || stored?.ip || '',
    host: useStored ? stored.host : current?.host || stored?.host || '',
    os: useStored ? stored.os || '' : current?.os || stored?.os || '',
  }
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => Boolean(value)),
  )
}

function canUseLocalStorage() {
  try {
    return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
  } catch {
    return false
  }
}
