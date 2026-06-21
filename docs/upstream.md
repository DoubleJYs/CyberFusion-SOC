# Upstream And License Record

This file is the unified upstream and local-integration record for `00-cyberfusion-platform`. Curated integration programs are now copied under `integrations/` inside this project; the sibling `01-16` directories are retained as upstream/reference origins.

| Module | Role | Status | Commit SHA | License / Notice Files |
| --- | --- | --- | --- | --- |
| `01-wazuh` | Wazuh demo alerts and optional live Wazuh health adapter | Core | `1be49619f2dc` | `01-wazuh/LICENSE` |
| `02-securityonion` | External Security Onion import/reference | Optional | `c1d187599bd7` | `02-securityonion/LICENSE` |
| `03-zeek` | Zeek log import adapter | Core | `e421442902d1` | `03-zeek/doc/LICENSE` |
| `04-suricata` | Suricata `eve.json` import adapter | Core | `54322f38f8c3` | `04-suricata/LICENSE` |
| `05-sigma` | Sigma detection rule center source | Core | `994da1665119` | `05-sigma/LICENSE` |
| `06-trivy` | Trivy JSON vulnerability import adapter | Core | `f2a12375772a` | `06-trivy/LICENSE`, `06-trivy/NOTICE` |
| `07-falco` | Falco JSON import/reference | Optional | `cd822e3d746c` | `07-falco/LICENSE` |
| `08-MISP` | MISP IOC import adapter | Core | `70bc84971a1d` | `08-MISP/LICENSE`, `08-MISP/docs/license.md` |
| `09-opencti` | OpenCTI-lite external intelligence import/reference | Optional | `fe25d5c6b5f7` | `09-opencti/LICENSE` |
| `10-osquery` | osquery result import/reference | Optional | `3ea7d4a5283d` | `10-osquery/LICENSE`, `10-osquery/LICENSE-Apache-2.0`, `10-osquery/LICENSE-GPL-2.0` |
| `11-velociraptor` | Velociraptor result import/reference | Optional | `2df5a81150b9` | `11-velociraptor/LICENSE` |
| `12-cowrie` | Cowrie log import/reference | Optional | `35a9beaeeefa` | `12-cowrie/LICENSE.rst`, `12-cowrie/LICENSES/` |
| `13-CyberChef` | Analyst field analysis entry | Core | `6a3a370bb150` | `13-CyberChef/LICENSE` |
| `14-zaproxy` | ZAP JSON finding import adapter | Core | `0f3519af226e` | `14-zaproxy/LICENSE` |
| `15-juice-shop` | Future training range only | Excluded from mainline | `6244c59a47ba` | `15-juice-shop/LICENSE` |
| `16-Shuffle` | Shuffle dry-run workflow/notification adapter | Core | `59a54914e047` | `16-Shuffle/LICENSE` |

## Modification Notes

- No upstream source directory was deleted, recloned, moved, or rewritten for this implementation.
- The active project now includes curated local integration program copies under `integrations/`. These copies exclude upstream `.git`, generated dependencies, build outputs, runtime logs, credentials, and environment files.
- The system copies and extends the previously self-developed Spring Boot/Vue SOC layer pattern from `01-wazuh/sec-wazuh-soc` into `00-cyberfusion-platform`.
- Adapter code in `00-cyberfusion-platform` performs safe parsing, normalization, local demo notification logging, and report generation only.
- It does not implement attack automation, credential stuffing, phishing, malware, evasion, privilege bypass, data theft, or unauthorized scanning.

## Local Integration Program Copies

| Source | Local path | CyberFusion API entrypoint | Notes |
| --- | --- | --- | --- |
| `01-wazuh/sec-wazuh-soc` | `integrations/wazuh-sec-soc` | `GET /api/soc/settings/wazuh/check`; `POST /api/soc/external-events/cyberfusion/import` with `sourceType=wazuh` | Reference SOC app copied without `node_modules`, `target`, `.git`, or runtime env files. |
| `02-securityonion/soc-hunt` | `integrations/securityonion-soc-hunt` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=suricata` or `zeek` | Lightweight SOC hunt normalizer and demo data. |
| `03-zeek/tools/traffic-platform` | `integrations/zeek-traffic-platform` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=zeek` | Zeek traffic metadata adapter reference. |
| `04-suricata/ids_console` | `integrations/suricata-ids-console` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=suricata` | Suricata EVE console and sample source. |
| `05-sigma/sigma_manager` | `integrations/sigma-manager` | `GET /api/soc/rules`; `POST /api/soc/external-events/cyberfusion/import` with `sourceType=sigma` | Sigma rule lifecycle reference. |
| `06-trivy/cmd/trivy-platform` | `integrations/trivy-platform` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=trivy` | Trivy JSON enters `soc_vulnerability`. |
| `07-falco/runtime-security-platform` | `integrations/falco-runtime-security-platform` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=falco` | Optional runtime evidence reference. |
| `08-MISP/deploy` | `integrations/misp-deploy` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=misp` | Deploy reference only; no real IOC pull configured. |
| `10-osquery/audit-platform` | `integrations/osquery-audit-platform` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=osquery` | Optional audit evidence reference. |
| `11-velociraptor/ir_platform` | `integrations/velociraptor-ir-platform` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=velociraptor` | Optional IR task reference. |
| `13-CyberChef/deploy` | `integrations/cyberchef-deploy` | `POST /api/soc/external-events/cyberchef/analyze` | Deploy reference for local field-analysis tooling. |
| `14-zaproxy/authorized-scan-platform` | `integrations/zap-authorized-scan-platform` | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=zap` | Authorized baseline wrapper reference; no full scan by default. |
| `16-Shuffle/examples` | `integrations/shuffle-examples` | `POST /api/soc/external-events/shuffle/demo-notification` | Dry-run workflow and connector examples only. |

The machine-readable local catalog is `integrations/catalog.json`. The same metadata is available to the platform through `GET /api/soc/integrations/catalog`; this endpoint is read-only and does not execute integration programs.

## Adapter Field Mapping

| Adapter | Source field | Normalized field | Required / optional | Severity mapping | Dedup key | Alert link rule | Sample file | Failure case |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| WAF | `ruleId`, `ruleName`, `action`, `targetUrl`, `httpStatus` | `ruleId`, `ruleName`, `action`, `targetUrl`, `severity` | `ruleId` and `action` required for clear evidence; missing `ruleId` falls back to `WAF-DEMO` | `block` defaults to `high`; `detect` defaults to `medium`; explicit `severity` is normalized | `WAF-{hash(requestId|demoCaseId|eventType)}` | `linkAlerts=true` links WAF demo evidence to `soc_alert` | `demo-data/waf-demo-events.jsonl` | Invalid JSON line is skipped and reported in import errors |
| ZAP | `pluginid`, `name`, `riskdesc`, `url` | `ruleId=pluginid`, `ruleName=name`, `eventType=web_app_finding` | `pluginid` optional; `name` and `url` recommended | `riskcode=3` or High -> `high`; `2` or Medium -> `medium`; otherwise `low` | `ZAP-{hash(pluginid|name|url)}` | `linkAlerts=true` links normalized web findings | `demo-data/demo-range/offline-batch.json` | Missing URL becomes `web-target`; malformed JSON fails that record |
| Trivy | `VulnerabilityID`, `PkgName`, `InstalledVersion`, `FixedVersion`, `Severity` | `cveId`, `softwareName`, `softwareVersion`, `fixSuggestion`, `severity` | `VulnerabilityID` and `PkgName` recommended | Trivy severity normalized to `critical/high/medium/low` | `cveId + softwareName` | Writes `soc_vulnerability`; does not directly create alerts | `demo-data/demo-range/offline-batch.json` | Empty `Results/Vulnerabilities` imports no rows |
| Wazuh | `rule.id`, `rule.level`, `rule.description`, `agent.ip`, `data.srcip` | `ruleId`, `ruleName`, `severity`, `assetIp`, `srcIp` | `rule.id` optional; `rule.description` recommended | level >= 12 -> `critical`; >= 8 -> `high`; >= 4 -> `medium`; otherwise `low` | `WAZUH-{hash(id|raw)}` | `linkAlerts=true` links `xdr_alert`; seed `mock` alerts are shown as Wazuh rules | `demo-data/demo-range/offline-batch.json` | Missing `agent.ip` falls back to data destination fields or scoped owner/dept |
| Suricata | `alert.signature_id`, `alert.signature`, `alert.severity`, `src_ip`, `dest_ip` | `ruleId`, `ruleName`, `severity`, `srcIp`, `destIp`, `eventType` | `alert` object required for IDS alert mapping | alert severity and signature context map to `critical/high/medium/low` | `SURICATA-{hash(timestamp|flow_id|signature)}` | `alert` events link when `linkAlerts=true`; HTTP/DNS rows can remain evidence only | Suricata `eve.json` | Non-alert rows become `http_anomaly` or `dns_activity` without IDS signature |
| Zeek | `uid`, `id.orig_h`, `id.resp_h`, `proto`, `service`, `ts` | `ruleId=uid`, `ruleName=Zeek service/proto log`, `eventType=network_connection` | `uid` or connection tuple recommended | Demo connection evidence defaults to `medium` | `ZEEK-{hash(uid|line)}` | `linkAlerts=true` links network connection evidence | Zeek `conn.log` or JSON | TSV rows need a valid `#fields` header |
| Sigma | `id`, `title`, `name`, `tags`, `logsource` | `ruleId=id`, `ruleName=title/name`, `eventType=detection_rule` | `id` optional; `title/name` recommended | Rule metadata defaults to `low` until runtime events reuse the rule ID | `SIGMA-{hash(id|title)}` | Sigma imports are rule lifecycle metadata; later events with the same rule ID drive alerting | Sigma JSON converted from rule YAML | YAML must be converted to JSON before this safe importer |

## Redistribution Notes

When redistributing any upstream artifact, retain the upstream LICENSE/NOTICE files listed above and include the commit SHA. If additional adapter source files are copied into `00-cyberfusion-platform`, record the copied files, license, original path, commit SHA, and local modifications here before release.
