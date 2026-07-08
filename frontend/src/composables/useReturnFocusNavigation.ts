import { nextTick, onBeforeUnmount, watch } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'

const STORAGE_PREFIX = 'soc:return-focus:v1:'
const CANDIDATE_SELECTOR = [
  '[data-return-focus-key]',
  '[data-return-focus-item]',
  '.integration-card',
  '.algorithm-card',
  '.incident-row',
  '.showcase-step-card',
  '.summary-card',
  '.kpi-card',
  '.el-table__row',
  '[role="link"]',
  '[role="button"]',
  'button',
  'a[href]',
].join(',')

interface ReturnFocusSnapshot {
  fullPath: string
  selector: string
  candidateIndex: number
  text: string
  containerScrollTop: number
  windowScrollY: number
  createdAt: number
}

interface ReturnFocusOptions {
  route: RouteLocationNormalizedLoaded
  router: Router
  containerSelector: string
}

export function useReturnFocusNavigation(options: ReturnFocusOptions) {
  let highlightTimer: ReturnType<typeof setTimeout> | undefined
  let revealTimer: ReturnType<typeof setTimeout> | undefined
  let revealRun = 0

  function captureReturnFocus(event: MouseEvent) {
    const container = resolveContainer(event)
    const target = event.target instanceof Element ? closestCandidate(event.target, container) : null
    if (!target) return
    const candidates = candidateElements(container)
    const key = target.getAttribute('data-return-focus-key') || ''
    const snapshot: ReturnFocusSnapshot = {
      fullPath: options.route.fullPath,
      selector: key ? `[data-return-focus-key="${escapeCss(key)}"]` : '',
      candidateIndex: Math.max(0, candidates.indexOf(target)),
      text: normalizeText(target.innerText || target.textContent || ''),
      containerScrollTop: container.scrollTop,
      windowScrollY: window.scrollY,
      createdAt: Date.now(),
    }
    writeSnapshot(snapshot)
  }

  function scheduleReveal() {
    if (revealTimer) clearTimeout(revealTimer)
    revealTimer = setTimeout(() => {
      void revealReturnFocus(0, ++revealRun)
    }, 40)
  }

  async function revealReturnFocus(attempt: number, runId: number) {
    if (runId !== revealRun) return
    const snapshot = readSnapshot(options.route.fullPath)
    if (!snapshot) return
    const container = resolveContainer()
    await nextTick()
    if (runId !== revealRun) return
    const target = findSnapshotTarget(container, snapshot)
    if (!target) {
      if (attempt < 5) {
        const delays = [80, 180, 360, 720, 1200]
        revealTimer = setTimeout(() => {
          void revealReturnFocus(attempt + 1, runId)
        }, delays[attempt] || 1200)
        return
      }
      container.scrollTo({ top: snapshot.containerScrollTop, behavior: 'smooth' })
      if (!isScrollable(container)) window.scrollTo({ top: snapshot.windowScrollY, behavior: 'smooth' })
      removeSnapshot(options.route.fullPath)
      return
    }

    removeSnapshot(options.route.fullPath)
    target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' })
    highlightTarget(target)
  }

  watch(
    () => options.route.fullPath,
    () => scheduleReveal(),
    { immediate: true },
  )

  onBeforeUnmount(() => {
    if (highlightTimer) clearTimeout(highlightTimer)
    if (revealTimer) clearTimeout(revealTimer)
  })

  function resolveContainer(event?: MouseEvent) {
    const current = event?.currentTarget
    if (current instanceof HTMLElement) return current
    const selected = document.querySelector(options.containerSelector)
    return selected instanceof HTMLElement ? selected : document.body
  }

  function highlightTarget(target: HTMLElement) {
    if (highlightTimer) clearTimeout(highlightTimer)
    document.querySelectorAll('.return-focus-highlight').forEach((item) => item.classList.remove('return-focus-highlight'))

    const hadTabIndex = target.hasAttribute('tabindex')
    target.classList.add('return-focus-highlight')
    if (!hadTabIndex) target.setAttribute('tabindex', '-1')
    target.focus({ preventScroll: true })

    highlightTimer = setTimeout(() => {
      target.classList.remove('return-focus-highlight')
      if (!hadTabIndex) target.removeAttribute('tabindex')
    }, 5200)
  }

  return { captureReturnFocus, revealReturnFocus: scheduleReveal }
}

function closestCandidate(start: Element, container: HTMLElement) {
  let current: Element | null = start
  while (current && current !== container) {
    if (current instanceof HTMLElement && current.matches(CANDIDATE_SELECTOR)) return current
    current = current.parentElement
  }
  return null
}

function candidateElements(container: HTMLElement) {
  return Array.from(container.querySelectorAll(CANDIDATE_SELECTOR)).filter((item): item is HTMLElement => item instanceof HTMLElement)
}

function findSnapshotTarget(container: HTMLElement, snapshot: ReturnFocusSnapshot) {
  if (snapshot.selector) {
    const keyed = container.querySelector(snapshot.selector)
    if (keyed instanceof HTMLElement) return keyed
  }
  const candidates = candidateElements(container)
  const indexed = candidates[snapshot.candidateIndex]
  if (indexed) return indexed
  if (!snapshot.text) return null
  return candidates.find((item) => textMatches(normalizeText(item.innerText || item.textContent || ''), snapshot.text)) || null
}

function textMatches(candidate: string, expected: string) {
  if (!candidate || !expected) return false
  const shortExpected = expected.slice(0, 120)
  return candidate.includes(shortExpected) || shortExpected.includes(candidate.slice(0, 80))
}

function storageKey(fullPath: string) {
  return `${STORAGE_PREFIX}${fullPath}`
}

function writeSnapshot(snapshot: ReturnFocusSnapshot) {
  sessionStorage.setItem(storageKey(snapshot.fullPath), JSON.stringify(snapshot))
}

function readSnapshot(fullPath: string) {
  const raw = sessionStorage.getItem(storageKey(fullPath))
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as ReturnFocusSnapshot
    if (!parsed.fullPath || Date.now() - parsed.createdAt > 30 * 60 * 1000) {
      removeSnapshot(fullPath)
      return null
    }
    return parsed
  } catch {
    removeSnapshot(fullPath)
    return null
  }
}

function removeSnapshot(fullPath: string) {
  sessionStorage.removeItem(storageKey(fullPath))
}

function normalizeText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
}

function escapeCss(value: string) {
  if (window.CSS?.escape) return window.CSS.escape(value)
  return value.replace(/["\\]/g, '\\$&')
}

function isScrollable(element: HTMLElement) {
  return element.scrollHeight > element.clientHeight + 2
}
