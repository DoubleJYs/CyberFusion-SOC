package com.zhangjiyan.template.soc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheckMapper;
import com.zhangjiyan.template.soc.demo.DemoRangeBatchImportRequest;
import com.zhangjiyan.template.soc.external.CyberFusionImportRequest;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEventMapper;
import com.zhangjiyan.template.soc.noise.SocAlertWhitelistMapper;
import com.zhangjiyan.template.soc.notification.SocNotificationService;
import com.zhangjiyan.template.soc.report.SocReportMapper;
import com.zhangjiyan.template.soc.settings.SocSyncTaskMapper;
import com.zhangjiyan.template.soc.settings.SocWazuhConfigMapper;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.ticket.SocTicketTimelineMapper;
import com.zhangjiyan.template.soc.ticket.TicketStateMachine;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SocOperationServiceWafImportTest {

    @Test
    void importsWafBlockAndLinksAlert() {
        SocAsset asset = new SocAsset();
        asset.setId(1L);
        asset.setHostname("prod-app-01");
        asset.setIp("10.20.1.15");
        asset.setOwnerId(5L);
        asset.setDeptId(12L);
        asset.setRiskLevel("critical");
        asset.setLastSeenAt(LocalDateTime.now());

        AtomicReference<SocAlert> insertedAlert = new AtomicReference<>();
        AtomicReference<SocExternalEvent> insertedEvent = new AtomicReference<>();
        SocAssetMapper assetMapper = proxyMapper(SocAssetMapper.class, asset, null, null);
        SocAlertMapper alertMapper = proxyMapper(SocAlertMapper.class, null, insertedAlert, null);
        SocExternalEventMapper externalEventMapper = proxyMapper(SocExternalEventMapper.class, null, null, insertedEvent);
        SocSecurityScope securityScope = new SocSecurityScope(null, null, null, null, null) {
            @Override
            public boolean canAccess(Long ownerId, Long deptId) {
                return Long.valueOf(5L).equals(ownerId) && Long.valueOf(12L).equals(deptId);
            }
        };
        SocOperationService service = new SocOperationService(
                alertMapper,
                assetMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                externalEventMapper,
                null,
                null,
                securityScope,
                null,
                null,
                null,
                new ObjectMapper()
        );

        String content = """
                {"sourceType":"waf","eventType":"waf_block","severity":"high","assetIp":"10.20.1.15","targetUrl":"https://demo.internal.local/admin","httpMethod":"POST","httpStatus":403,"action":"block","ruleId":"WAF-DEMO-1001","ruleName":"Admin route protected by WAF policy","engine":"CyberFusion Demo Gateway","requestId":"waf-demo-req-0001","demoCaseId":"demo-range-waf-block","evidenceSummary":"Demo gateway blocked an unauthorized admin route request before it reached prod-app-01.","timestamp":"2026-06-18T10:00:00+08:00","sourceIp":"203.0.113.80"}
                """;

        SocOperationService.CyberFusionImportResult result = service.importCyberFusionEvents(
                new CyberFusionImportRequest("waf", content, true)
        );

        assertThat(result.sourceType()).isEqualTo("waf");
        assertThat(result.importedEvents()).isEqualTo(1);
        assertThat(result.createdEvents()).isEqualTo(1);
        assertThat(result.linkedAlerts()).isEqualTo(1);
        assertThat(result.skippedLines()).isZero();

        assertThat(insertedAlert.get()).isNotNull();
        assertThat(insertedAlert.get().getSourceType()).isEqualTo("waf");
        assertThat(insertedAlert.get().getSeverity()).isEqualTo("high");
        assertThat(insertedAlert.get().getRuleId()).isEqualTo("WAF-DEMO-1001");
        assertThat(insertedAlert.get().getAssetIp()).isEqualTo("10.20.1.15");

        assertThat(insertedEvent.get()).isNotNull();
        assertThat(insertedEvent.get().getSourceType()).isEqualTo("waf");
        assertThat(insertedEvent.get().getEventType()).isEqualTo("waf_block");
        assertThat(insertedEvent.get().getRuleId()).isEqualTo("WAF-DEMO-1001");
        assertThat(insertedEvent.get().getAssetIp()).isEqualTo("10.20.1.15");
        assertThat(insertedEvent.get().getAlertId()).isEqualTo(77L);
        assertThat(insertedEvent.get().getRawEvent()).contains("demo-range-waf-block");
        assertThat(insertedEvent.get().getNormalizedEvent()).contains("evidence_summary");
    }

    @Test
    void importsZapFindingWithoutWritingUrlToAssetIp() {
        AtomicReference<SocAlert> insertedAlert = new AtomicReference<>();
        AtomicReference<SocExternalEvent> insertedEvent = new AtomicReference<>();
        SocAlertMapper alertMapper = proxyMapper(SocAlertMapper.class, null, insertedAlert, null);
        SocExternalEventMapper externalEventMapper = proxyMapper(SocExternalEventMapper.class, null, null, insertedEvent);
        SocSecurityScope securityScope = new SocSecurityScope(null, null, null, null, null) {
            @Override
            public Long currentUserId() {
                return 1L;
            }

            @Override
            public Long currentDeptId() {
                return 1L;
            }

            @Override
            public boolean canAccess(Long ownerId, Long deptId) {
                return true;
            }
        };
        SocOperationService service = new SocOperationService(
                alertMapper,
                proxyMapper(SocAssetMapper.class, null, null, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                externalEventMapper,
                null,
                null,
                securityScope,
                null,
                null,
                null,
                new ObjectMapper()
        );

        String content = """
                [{"pluginid":"10021","name":"Demo protective header review","riskdesc":"Medium","url":"https://demo.internal.local/login?batch=DEMO-RANGE-OFFLINE-V1","batchId":"DEMO-RANGE-OFFLINE-V1","demoCaseId":"input-validation-risk","evidenceSummary":"Offline ZAP-style finding used for SOC validation only."}]
                """;

        SocOperationService.CyberFusionImportResult result = service.importCyberFusionEvents(
                new CyberFusionImportRequest("zap", content, true)
        );

        assertThat(result.sourceType()).isEqualTo("zap");
        assertThat(result.importedEvents()).isEqualTo(1);
        assertThat(result.linkedAlerts()).isEqualTo(1);
        assertThat(insertedEvent.get()).isNotNull();
        assertThat(insertedEvent.get().getSourceType()).isEqualTo("zap");
        assertThat(insertedEvent.get().getEventType()).isEqualTo("web_app_finding");
        assertThat(insertedEvent.get().getAssetIp()).isNull();
        assertThat(insertedEvent.get().getIoc()).isEqualTo("https://demo.internal.local/login?batch=DEMO-RANGE-OFFLINE-V1");
        assertThat(insertedAlert.get()).isNotNull();
        assertThat(insertedAlert.get().getSourceType()).isEqualTo("zap");
    }

    @Test
    void importsOfflineDemoRangeBatchWithDeterministicSummary() {
        SocAsset asset = new SocAsset();
        asset.setId(1L);
        asset.setHostname("prod-app-01");
        asset.setIp("10.20.1.15");
        asset.setOwnerId(5L);
        asset.setDeptId(12L);
        asset.setRiskLevel("critical");
        asset.setLastSeenAt(LocalDateTime.now());

        AtomicInteger alertInserts = new AtomicInteger();
        AtomicInteger eventInserts = new AtomicInteger();
        AtomicInteger vulnerabilityInserts = new AtomicInteger();
        AtomicReference<SocVulnerability> insertedVulnerability = new AtomicReference<>();
        SocAssetMapper assetMapper = proxyMapper(SocAssetMapper.class, asset, null, null);
        SocAlertMapper alertMapper = proxyMapper(SocAlertMapper.class, null, null, null, null, alertInserts);
        SocExternalEventMapper externalEventMapper = proxyMapper(SocExternalEventMapper.class, null, null, null, null, eventInserts);
        SocVulnerabilityMapper vulnerabilityMapper = proxyMapper(SocVulnerabilityMapper.class, null, null, null, insertedVulnerability, vulnerabilityInserts);
        SocSecurityScope securityScope = new SocSecurityScope(null, null, null, null, null) {
            @Override
            public boolean canAccess(Long ownerId, Long deptId) {
                return true;
            }
        };
        SocOperationService service = new SocOperationService(
                alertMapper,
                assetMapper,
                null,
                null,
                null,
                null,
                null,
                vulnerabilityMapper,
                null,
                null,
                null,
                externalEventMapper,
                null,
                null,
                securityScope,
                null,
                null,
                null,
                new ObjectMapper()
        );

        SocOperationService.DemoRangeBatchImportResult result = service.importDemoRangeBatch(
                new DemoRangeBatchImportRequest(null, true)
        );

        assertThat(result.batchId()).isEqualTo("DEMO-RANGE-OFFLINE-V1");
        assertThat(result.importedEvents()).as(result.sources().toString()).isEqualTo(6);
        assertThat(result.createdAlerts()).isEqualTo(6);
        assertThat(result.createdVulnerabilities()).isEqualTo(1);
        assertThat(result.skippedItems()).isZero();
        assertThat(result.failedItems()).isZero();
        assertThat(result.sources()).extracting(SocOperationService.DemoRangeSourceImportResult::sourceType)
                .containsExactly("waf", "zap", "trivy", "suricata", "zeek", "wazuh");
        assertThat(alertInserts.get()).isEqualTo(6);
        assertThat(eventInserts.get()).isEqualTo(6);
        assertThat(vulnerabilityInserts.get()).isEqualTo(1);
        assertThat(insertedVulnerability.get()).isNotNull();
        assertThat(insertedVulnerability.get().getSourceType()).isEqualTo("trivy");
        assertThat(insertedVulnerability.get().getCveId()).isEqualTo("CVE-2026-DEMO-RANGE-0001");
        assertThat(insertedVulnerability.get().getFixSuggestion()).contains("batchId=DEMO-RANGE-OFFLINE-V1");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyMapper(Class<T> mapperType,
                                     SocAsset selectedAsset,
                                     AtomicReference<SocAlert> insertedAlert,
                                     AtomicReference<SocExternalEvent> insertedEvent) {
        return proxyMapper(mapperType, selectedAsset, insertedAlert, insertedEvent, null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyMapper(Class<T> mapperType,
                                     SocAsset selectedAsset,
                                     AtomicReference<SocAlert> insertedAlert,
                                     AtomicReference<SocExternalEvent> insertedEvent,
                                     AtomicReference<SocVulnerability> insertedVulnerability,
                                     AtomicInteger insertCount) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (_proxy, method, args) -> {
                    if ("selectOne".equals(method.getName())) {
                        return selectedAsset;
                    }
                    if ("insert".equals(method.getName()) && args != null && args.length == 1) {
                        if (insertCount != null) {
                            insertCount.incrementAndGet();
                        }
                        if (args[0] instanceof SocAlert alert) {
                            alert.setId(77L);
                            if (insertedAlert != null) {
                                insertedAlert.set(alert);
                            }
                        }
                        if (args[0] instanceof SocExternalEvent event) {
                            if (insertedEvent != null) {
                                insertedEvent.set(event);
                            }
                        }
                        if (args[0] instanceof SocVulnerability vulnerability) {
                            vulnerability.setId(88L);
                            if (insertedVulnerability != null) {
                                insertedVulnerability.set(vulnerability);
                            }
                        }
                        return 1;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                }
        );
    }
}
