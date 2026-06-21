# Upstream And License Notes

## Wazuh

- URL: [https://github.com/wazuh/wazuh](https://github.com/wazuh/wazuh)
- Local commit SHA: `1be49619f2dc0a6f77fdfc833e76ccb299d62ecf`
- License: GNU GPL v2, with upstream OpenSSL linking notes in Wazuh `LICENSE`.
- Usage: bottom-layer SIEM/XDR engine only.
- Modification: none in this project; Wazuh Core and Wazuh Dashboard are not modified.
- Compliance: retain upstream license and notices when redistributing Wazuh artifacts; document any future connector changes.
- Risk: GPL obligations apply if Wazuh source or derivative binaries are redistributed.

## wazuh-docker

- URL: [https://github.com/wazuh/wazuh-docker](https://github.com/wazuh/wazuh-docker)
- HEAD commit SHA checked on 2026-05-27: `96308e55ceae884ef8b1b5742101d1be255aa168`
- License: GNU GPL v2. The upstream repository describes Wazuh Docker as "License GPLv2".
- Usage: reference deployment pattern for Wazuh single-node only.
- Modification: no upstream file copied into this source tree.
- Compliance: keep license, notices, and changed-file records if Wazuh Docker files are copied later.
- Risk: Wazuh images need enough memory and host resources; default demo credentials must never be committed.

## VoltAgent/awesome-design-md

- URL: [https://github.com/VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md)
- HEAD commit SHA checked on 2026-05-27: `ce17d6c67e43c4dbb19f9b4f9a775a1d004bed75`
- Selected source: `design-md/claude/DESIGN.md`
- License: MIT.
- Usage reason: compact developer-tool/data-platform design constraints map well to a dense security console.
- Modification: brand, logo, copy, warm visual identity, and recognizable assets were not copied; implementation uses custom dark SOC tokens and components.
- Risk: avoid presenting the adapted UI as VoltAgent/Claude-branded work.

## Modification Record

- Added self-developed backend modules for alerts, assets, tickets, reports, settings, Wazuh connection wrapper, RBAC-gated APIs, and audit coverage.
- Added self-developed Vue SOC pages, design tokens, ECharts theme, and security components.
- Added MySQL schema/seed data for P0 simulated security workflow.
- Added deployment and acceptance documentation for this project.
