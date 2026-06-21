# CyberFusion Integration Programs

This directory keeps the integration-side programs that CyberFusion SOC actually references during demos, adapter validation, and local handoff.

The goal is to keep the unified SOC project self-contained while avoiding full upstream repositories, generated dependencies, runtime logs, databases, credentials, and build outputs.

CyberFusion exposes the same local integration catalog through the read-only API:

```text
GET /api/soc/integrations/catalog
```

This endpoint only returns catalog metadata. It does not execute integration programs, connect to external systems, or import evidence.

## Integration Layout

| Directory | Source | Purpose | CyberFusion API |
| --- | --- | --- | --- |
| `wazuh-sec-soc/` | `../01-wazuh/sec-wazuh-soc` | Local Wazuh-style SOC reference app, schemas, docs, and deploy templates. | `GET /api/soc/settings/wazuh/check`, `POST /api/soc/external-events/cyberfusion/import` with `sourceType=wazuh` |
| `securityonion-soc-hunt/` | `../02-securityonion/soc-hunt` | Lightweight Security Onion / SOC hunt normalizer and demo UI. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=suricata` or `zeek` |
| `zeek-traffic-platform/` | `../03-zeek/tools/traffic-platform` | Zeek traffic metadata demo adapter. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=zeek` |
| `suricata-ids-console/` | `../04-suricata/ids_console` | Suricata EVE demo console and JSON Lines source. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=suricata` |
| `sigma-manager/` | `../05-sigma/sigma_manager` | Sigma rule management reference. | `GET /api/soc/rules`, `POST /api/soc/external-events/cyberfusion/import` with `sourceType=sigma` |
| `trivy-platform/` | `../06-trivy/cmd/trivy-platform` | Local Trivy wrapper entrypoint. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=trivy` |
| `falco-runtime-security-platform/` | `../07-falco/runtime-security-platform` | Optional Falco-style runtime evidence reference. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=falco` |
| `misp-deploy/` | `../08-MISP/deploy` | MISP deploy reference for authorized IOC labs. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=misp` |
| `osquery-audit-platform/` | `../10-osquery/audit-platform` | Optional osquery audit evidence reference. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=osquery` |
| `velociraptor-ir-platform/` | `../11-velociraptor/ir_platform` | Optional Velociraptor IR task reference. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=velociraptor` |
| `cyberchef-deploy/` | `../13-CyberChef/deploy` | CyberChef deployment reference. | `POST /api/soc/external-events/cyberchef/analyze` |
| `zap-authorized-scan-platform/` | `../14-zaproxy/authorized-scan-platform` | Authorized ZAP baseline wrapper reference. | `POST /api/soc/external-events/cyberfusion/import` with `sourceType=zap` |
| `shuffle-examples/` | `../16-Shuffle/examples` | Shuffle connector and dry-run workflow examples. | `POST /api/soc/external-events/shuffle/demo-notification` |

## Boundaries

- Runtime data still belongs under `/Users/zhangjiyan/Environment`, not in this source tree.
- Real secrets, tokens, certificates, API keys, Docker volumes, DB files, generated logs, and uploaded customer data must not be stored here.
- These copies do not grant permission to run scans, attacks, public-target tests, or real notification senders.
- CyberFusion remains the single SOC entrypoint. Integration programs feed defensive evidence through the APIs listed above.

## Packaging Rule

When refreshing this directory from sibling upstream projects, copy only source, examples, docs, deploy templates, and tests. Exclude:

- `.git`
- `.runtime-env`
- `node_modules`
- `target`, `dist`, `build`
- caches such as `__pycache__`, `.pytest_cache`, `.gradle`
- logs, uploads, DB files, Docker volumes, and real `.env` files
