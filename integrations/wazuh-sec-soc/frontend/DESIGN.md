# DESIGN.md

## Source And Adaptation

- Source reference: `VoltAgent/awesome-design-md/design-md/claude/DESIGN.md`.
- Selection reason: the original file has structured tokens, compact developer-tool surfaces, dark code-window patterns, and strong component guardrails that can be adapted to a security operations console.
- Adaptation rule: this project does not copy the source brand, logo, typography, product wording, warm cream/coral identity, or recognizable assets. It keeps only the idea of tokenized components and dense work surfaces.

## Visual Positioning

This product is a dark enterprise SOC console. The interface should feel operational, calm, and information-dense. Screens prioritize scanning, filtering, comparing severity, and taking clear response actions. Avoid marketing sections, decorative hero layouts, large empty cards, one-note gradients, and brand-like illustrations.

## Tokens

Use `src/styles/design-tokens.css` as the implementation source of truth.

- Canvas: deep graphite `#07111f`.
- Surface: layered navy graphite `#0c1728`, `#111f33`, `#18263a`.
- Text: high contrast `#e6edf7`, muted `#93a4b8`.
- Accent: cyan `#2dd4bf` for active navigation and safe progress, blue `#60a5fa` for system links.
- Severity: critical red, high orange, medium amber, low blue, info slate.
- Radius: 6px for panels and controls, 8px maximum for cards.
- Typography: Inter/PingFang SC/Microsoft YaHei, no negative letter spacing.

## Components

- `RiskCard`: compact KPI card with label, value, delta, and severity color strip.
- `SeverityBadge`: strong color mapping for critical/high/medium/low.
- `StatusBadge`: clear workflow states for alert and ticket status.
- `AssetRiskTag`: risk label tuned for asset tables.
- `AttackTimeline`: vertical event/ticket timeline for detail drawers.
- `RiskTrendChart`: ECharts line/bar panel using `src/styles/echarts-theme.ts`.

## Layout Rules

- Sidebar + header shell is always visible after login.
- Lists require search, severity/status filters, pagination, batch action affordance, and detail drawer.
- Drawer content must expose raw identifiers, business status, owner/dept, and audit-relevant action buttons.
- Buttons for destructive or state-changing actions must use confirmations or explicit action text.
- Tables should keep row height compact but readable.

## Prohibited

- Do not expose Wazuh credentials, certificates, internal URLs, or token values in the browser.
- Do not use source brand marks, source product names, or source page copy from the selected DESIGN.md.
- Do not create attack automation, credential abuse, phishing, malware, evasion, or data exfiltration UI.
