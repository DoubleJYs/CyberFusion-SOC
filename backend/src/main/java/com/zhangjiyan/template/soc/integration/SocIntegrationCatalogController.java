package com.zhangjiyan.template.soc.integration;

import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "SOC 接入目录", description = "CyberFusion 本地接入程序目录和统一安全 API 入口")
@RestController
@RequestMapping("/soc/integrations")
public class SocIntegrationCatalogController {

    private static final String DEFAULT_IMPORT_API = "/api/soc/external-events/cyberfusion/import";

    private static final List<IntegrationCatalogItem> ITEMS = List.of(
            new IntegrationCatalogItem(
                    "wazuh",
                    "Wazuh host security evidence",
                    "integrations/wazuh-sec-soc",
                    "../01-wazuh/sec-wazuh-soc",
                    List.of("/api/soc/settings/wazuh/check", DEFAULT_IMPORT_API),
                    "optional live health check plus offline/demo evidence import",
                    "credentials from environment only; no real external sender"
            ),
            new IntegrationCatalogItem(
                    "zeek",
                    "Zeek traffic metadata",
                    "integrations/zeek-traffic-platform",
                    "../03-zeek/tools/traffic-platform",
                    List.of(DEFAULT_IMPORT_API),
                    "offline conn.log or JSON import",
                    "no packet capture or public target scan"
            ),
            new IntegrationCatalogItem(
                    "suricata",
                    "Suricata IDS EVE events",
                    "integrations/suricata-ids-console",
                    "../04-suricata/ids_console",
                    List.of(DEFAULT_IMPORT_API, "/api/soc/external-events/suricata/import"),
                    "offline eve.json JSON Lines import",
                    "no IDS rule push or live traffic capture"
            ),
            new IntegrationCatalogItem(
                    "sigma",
                    "Sigma detection rule reference",
                    "integrations/sigma-manager",
                    "../05-sigma/sigma_manager",
                    List.of("/api/soc/rules", DEFAULT_IMPORT_API),
                    "rule metadata and detection-rule event import",
                    "no executable rule expressions"
            ),
            new IntegrationCatalogItem(
                    "trivy",
                    "Trivy vulnerability JSON",
                    "integrations/trivy-platform",
                    "../06-trivy/cmd/trivy-platform",
                    List.of(DEFAULT_IMPORT_API, "/api/soc/vulnerabilities"),
                    "offline JSON import into vulnerability center",
                    "no live scan by default"
            ),
            new IntegrationCatalogItem(
                    "misp",
                    "MISP IOC evidence",
                    "integrations/misp-deploy",
                    "../08-MISP/deploy",
                    List.of(DEFAULT_IMPORT_API),
                    "authorized IOC JSON import",
                    "no real threat-intel pull without explicit environment config"
            ),
            new IntegrationCatalogItem(
                    "zap",
                    "ZAP baseline findings",
                    "integrations/zap-authorized-scan-platform",
                    "../14-zaproxy/authorized-scan-platform",
                    List.of(DEFAULT_IMPORT_API),
                    "authorized baseline report import",
                    "no full scan and no public target scan"
            ),
            new IntegrationCatalogItem(
                    "cyberchef",
                    "CyberChef field analysis",
                    "integrations/cyberchef-deploy",
                    "../13-CyberChef/deploy",
                    List.of("/api/soc/external-events/cyberchef/analyze"),
                    "local field analysis helper",
                    "analysis helper only; no data exfiltration"
            ),
            new IntegrationCatalogItem(
                    "shuffle",
                    "Shuffle dry-run workflow examples",
                    "integrations/shuffle-examples",
                    "../16-Shuffle/examples",
                    List.of("/api/soc/external-events/shuffle/demo-notification"),
                    "dry-run notification workflow examples",
                    "dry-run log only; no real webhook/email/chat sender"
            )
    );

    @Operation(summary = "查询本地接入程序目录")
    @GetMapping("/catalog")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:settings:view') or hasAuthority('soc:external-event:view')")
    public ApiResult<IntegrationCatalog> catalog() {
        return ApiResult.ok(new IntegrationCatalog(1, DEFAULT_IMPORT_API, ITEMS));
    }

    public record IntegrationCatalog(
            int version,
            String defaultImportApi,
            List<IntegrationCatalogItem> integrations
    ) {
    }

    public record IntegrationCatalogItem(
            String sourceType,
            String name,
            String localPath,
            String sourceModule,
            List<String> apis,
            String mode,
            String safety
    ) {
    }
}
