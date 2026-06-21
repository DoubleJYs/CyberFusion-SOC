# Change Visibility Matrix

Date: 2026-06-20

Scope: CyberFusion SOC P4.5 visibility reconciliation. This document checks whether recently implemented capabilities are visible through routes, components, API wrappers, backend endpoints, menus, permissions, seed data, manuals, screenshots, and smoke tests. It does not introduce new SOC business capability.

Legend: `Yes` means present in current source or seed. `N/A` means not expected for that surface. `Stale` means the screenshot or gallery reference predates the latest P4.5 visibility pass and must not be treated as fresh proof until regenerated.

| Capability | Frontend route exists | Component exists | API wrapper exists | Backend endpoint exists | sys_menu exists | Permission exists | demo admin assigned | analyst/security engineer assigned | employee denied if needed | SQL seed exists | fallbackProtectedRoutes exists | user-manual updated | screenshot exists | smoke covered | visible in UI |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `/showcase` customer demo mode | Yes: `frontend/src/router/index.ts` | Yes: `frontend/src/views/showcase/ShowcaseView.vue` | Yes: `frontend/src/api/showcase.ts` | Uses existing Demo Range/report/event APIs | N/A: hardcoded top/sidebar entry | Auth required, no special menu permission | Yes: authenticated admin can open | Yes: authenticated analyst can open | Employee can open only if authenticated; no SOC policy access granted | N/A | Top-level static route | Yes | Yes: `acceptance-02-showcase.png`; gallery may be stale | Yes: `release-pages.spec.ts`, `visibility.spec.ts`, `check-visibility.sh` | Yes: top entry `安全运营演示台` |
| `/soc/policies` policy and rule center | Yes: `menuRoutes.ts` | Yes: `PolicyCenterView.vue` | Yes: `soc.ts` | Yes: `/api/soc/policies/*` | Yes: `sys_menu.id=2015` | Yes: `soc:policy:list` and policy button permissions | Yes: role 1 and 3 | Security admin yes; analyst no for policy management by default | Yes: employee receives 403 on policy APIs | Yes: `sql/data.sql`; patch script provided | Yes | Yes | Yes: `acceptance-05-policies.png`; older gallery stale | Yes | Yes: SOC expert menu `策略与规则` |
| Local check policy Tab | Same as `/soc/policies` | Yes: first tab `本机检查策略` | Yes: local policy API wrappers | Yes: `/soc/policies/local-check-commands` and `/client/local-terminal/commands` | Covered by `/soc/policies` menu | `soc:policy:*` | Yes | Security admin yes; analyst no publish | Employee can read active client commands only, cannot manage policies | Yes: `soc_local_check_command` seed | N/A | Yes | Policies screenshot exists; needs refresh after P4.5 | Yes | Yes |
| Event adapter mapping Tab | Same as `/soc/policies` | Yes: tab `事件适配映射` | Yes: event adapter wrappers and preview | Yes: `/soc/policies/event-adapters/*` | Covered by `/soc/policies` menu | `soc:policy:list/update/publish/disable` | Yes | Security admin yes; analyst publish should be 403 | Employee denied policy APIs | Yes: adapter profile/mapping seed | N/A | Yes | Policies screenshot exists; needs refresh after P4.5 | Yes | Yes |
| Response playbook Tab or `/soc/playbooks` | Tab under `/soc/policies`; no separate `/soc/playbooks` route | Yes: tab `处置剧本` | Yes: playbook wrappers in `soc.ts` | Yes: `/soc/policies/playbooks/*` | Covered by `/soc/policies` menu | `soc:policy:*`; apply uses `soc:playbook:apply` | Yes | Analyst can apply playbook via `2420`; cannot maintain policy | Employee denied policy APIs | Yes: `soc_response_playbook` seed | N/A | Yes | Policies screenshot exists; needs refresh after P4.5 | Yes | Yes |
| Alert detail recommended playbook | Existing `/soc/alerts` | Yes: `AlertCenterView.vue` drawer section | Yes: `alertPlaybookSuggestions`, `applyAlertPlaybook` | Yes: `/soc/alerts/{id}/playbook-suggestions`, `/apply-playbook` | Alerts menu exists | `soc:alert:view`, `soc:playbook:apply` | Yes | Analyst yes for apply | Employee denied SOC alert pages by missing role/menu | Yes: playbook seeds | Yes: `/soc/alerts` | Yes | Alert screenshot exists but does not prove drawer; stale until refreshed | Yes | Visible after opening alert detail |
| Ticket detail task checklist | Existing `/soc/tickets` | Yes: `TicketView.vue` `处置剧本任务` | Yes: ticket detail/task wrappers | Yes: `/soc/tickets/{id}`, task action endpoints | Tickets menu exists | `soc:ticket:view`, `soc:ticket-task:update` | Yes | Analyst yes | Employee uses `/client/tasks`, not SOC ticket API | Yes: schema for `soc_ticket_task` | Yes: `/soc/tickets` | Yes | Ticket screenshot exists but drawer/detail should be refreshed | Yes | Visible in ticket detail |
| Employee `我的电脑` | Yes: `/client/workbench` | Yes: `ClientWorkbenchView.vue` | Yes: client wrappers in `soc.ts` | Yes: `/client/*` APIs | N/A: client shell route | Auth required | Yes | Yes if authenticated | Employee allowed for own context | N/A | Static client route | Yes | Yes: `acceptance-07-client-workbench.png` | Yes | Yes |
| Employee `我的待办` | Yes: `/client/tasks` plus legacy `/client/operations` | Yes: `ClientOperationsView.vue` | Yes: `listClientTasks`/task actions | Yes: `/api/client/tasks` | N/A | Auth required and scoped backend | Yes | Yes if authenticated | Employee allowed only own tasks | N/A | Static client route added in P4.5 | Yes | No fresh screenshot; mark stale until regenerated | Yes: `visibility.spec.ts`, `check-visibility.sh` | Yes: client nav now points to `/client/tasks` |
| Employee `本机检查` | Yes: `/client/local-range` | Yes: `ClientLocalRangeView.vue` | Yes: local terminal command wrappers | Yes: `/client/local-terminal/commands`, `/run`, `/local-run` | N/A | Auth required and backend commandKey boundary | Yes | Yes if authenticated | Employee allowed active commands only | Yes: local check seed | Static client route | Yes | Yes: `acceptance-08-client-local-range.png`; may be stale after UI refinements | Yes | Yes |
| Adapter preview | No page route; action in `/soc/policies` | Yes: mapping drawer/editor | Yes | Yes: `/soc/policies/event-adapters/{id}/preview` | Covered by `/soc/policies` | `soc:policy:list` | Yes | Security admin yes; analyst preview only if granted policy list | Employee denied | Yes | N/A | Yes | Policies screenshot should be refreshed for drawer | Yes | Visible through adapter detail action |
| Demo Range batch import | Yes: `/soc/demo-range` and `/showcase` action | Yes: `DemoRangeView.vue`, `ShowcaseView.vue` | Yes | Yes: `/soc/demo-range/batches/import` | Yes: `2013`, button `2414` | `soc:demo-range:import` | Yes | Analyst yes by seed | Employee denied SOC import | Yes | Yes: `/soc/demo-range` | Yes | Yes: `acceptance-03-demo-range.png` | Yes | Yes |
| `security_validation` report | Yes: `/soc/reports` | Yes: `ReportView.vue` | Yes | Yes: `/soc/reports/generate` | Yes: reports menu/button | `soc:report:view`, `soc:report:generate` | Yes | Analyst yes | Employee denied SOC reports | Yes | Yes: `/soc/reports` | Yes | Yes: `acceptance-06-reports.png` | Yes | Yes |
| Notification dry-run log | Yes: `/soc/settings` expert page | Yes: `SettingsView.vue` | Yes | Yes: `/soc/settings/notification-logs`, dry-run dispatch | Yes: settings menu | `soc:settings:view` | Yes | Security admin yes | Employee denied | Yes: channel/log seed | Yes: `/soc/settings` | Yes | Older screenshot exists; stale until refreshed | Yes | Visible under settings expert page |

## Root Causes Found

1. `/client/tasks` was missing as a new employee-facing route; only legacy `/client/operations` existed. P4.5 adds `/client/tasks` as an alias to the existing task component and updates client navigation to use it.
2. Existing database instances do not automatically rerun `sql/schema.sql` or `sql/data.sql`. For an already initialized local database, apply schema, seed data, and `scripts/sql/apply-latest-menu-and-policy-seed.sql` idempotently instead of deleting data.
3. Runtime confusion can come from stale Vite servers, cached auth/menu state, old backend Java processes, or old screenshots. P4.5 adds `scripts/smoke/check-visibility.sh` and lightweight frontend build info in the user menu.
4. If `check-visibility.sh` shows routes and menus passing but policy/adapter/playbook APIs still return 500, restart the backend from the latest `backend/target/classes` before taking fresh screenshots.

## Screenshots

`docs/screenshots/manifest.json` contains acceptance screenshots from the previous release smoke. They are useful as inventory, but any screenshot predating this P4.5 pass is marked stale until regenerated with:

```sh
pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts
```

## Verification Entry Points

```sh
scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
pnpm --dir frontend exec playwright test tests/e2e/visibility.spec.ts
```
