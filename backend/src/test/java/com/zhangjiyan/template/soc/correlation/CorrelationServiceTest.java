package com.zhangjiyan.template.soc.correlation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.ticket.SocTicketTimeline;
import com.zhangjiyan.template.soc.ticket.SocTicketTimelineMapper;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationServiceTest {

    private static final LocalDateTime TEST_NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    void correlatesCrossSourceEvidenceIdempotentlyAndCreatesTicketTimeline() {
        Fixture fixture = fixture();

        CorrelationService.CorrelateResult first = fixture.service().correlate();
        CorrelationService.CorrelateResult second = fixture.service().correlate();

        assertThat(first.createdClusters()).isEqualTo(1);
        assertThat(second.createdClusters()).isZero();
        assertThat(fixture.clusters()).hasSize(1);
        SocIncidentCluster cluster = fixture.clusters().getFirst();
        assertThat(cluster.getAssetIp()).isEqualTo("10.20.1.15");
        assertThat(cluster.getHostname()).isEqualTo("prod-app-01");
        assertThat(cluster.getEvidenceCount()).isGreaterThanOrEqualTo(3);
        assertThat(cluster.getSourceSummary()).contains("waf").contains("zap").contains("wazuh");
        assertThat(cluster.getRecommendation()).contains("人工处置决策");
        assertThat(fixture.evidence()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(fixture.evidence()).allSatisfy(item -> assertThat(item.getRelationReason()).isNotBlank());

        SocTicket ticket = fixture.service().createTicket(cluster.getId(), new IncidentActionRequest(9L, "测试事件簇转工单"));

        assertThat(ticket.getTicketNo()).startsWith("INC-CLUSTER-");
        assertThat(fixture.timelines()).hasSize(1);
        assertThat(fixture.timelines().getFirst().getAction()).isEqualTo("事件簇转工单");
    }

    @Test
    void rejectsScriptLikeCorrelationRuleConfiguration() {
        Fixture fixture = fixture();
        CorrelationRuleRequest request = new CorrelationRuleRequest(
                "bad_rule", "危险规则", "event_count",
                "[\"waf\"]", null, "[\"assetIp\"]", 2, 300,
                "[\"curl http://example.invalid\"]", "medium", true, "draft", 1,
                "bad", "bad"
        );

        assertThatThrownBy(() -> fixture.service().createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("脚本、外部查询或命令执行");
    }

    @Test
    void keepsDifferentAssetsInSeparateClusters() {
        Fixture fixture = fixture(
                List.of(activeRule()),
                List.of(
                        event(1L, "waf", "waf_block", "WAF-A", "10.20.1.15", "prod-app-01", "DEMO-BATCH", "DEMO-ACCESS-001", 8),
                        event(2L, "zap", "web_app_finding", "ZAP-A", "10.20.1.15", "prod-app-01", "DEMO-BATCH", "DEMO-ACCESS-001", 7),
                        event(3L, "waf", "waf_block", "WAF-B", "10.20.1.16", "prod-app-02", "DEMO-BATCH", "DEMO-ACCESS-001", 6),
                        event(4L, "zap", "web_app_finding", "ZAP-B", "10.20.1.16", "prod-app-02", "DEMO-BATCH", "DEMO-ACCESS-001", 5)
                ),
                List.of(),
                List.of(),
                true
        );

        CorrelationService.CorrelateResult result = fixture.service().correlate();

        assertThat(result.createdClusters()).isEqualTo(2);
        assertThat(fixture.clusters()).extracting(SocIncidentCluster::getPrimaryAssetIp)
                .containsExactlyInAnyOrder("10.20.1.15", "10.20.1.16");
    }

    @Test
    void ignoresDisabledAndDraftRulesWhenActiveRuleExists() {
        SocCorrelationRule draft = activeRule();
        draft.setId(2L);
        draft.setRuleKey("draft_rule");
        draft.setStatus("draft");
        SocCorrelationRule disabled = activeRule();
        disabled.setId(3L);
        disabled.setRuleKey("disabled_rule");
        disabled.setStatus("disabled");
        disabled.setEnabled(false);
        SocCorrelationRule active = activeRule();
        active.setRuleKey("active_rule");
        Fixture fixture = fixture(List.of(draft, disabled, active), defaultEvents(), List.of(), List.of(), true);

        fixture.service().correlate();

        assertThat(fixture.clusters()).hasSize(1);
        assertThat(fixture.clusters().getFirst().getRuleKey()).isEqualTo("active_rule");
    }

    @Test
    void matchesTemporalOrderedSequenceAndValueCountRules() {
        SocCorrelationRule ordered = activeRule();
        ordered.setRuleKey("ordered_rule");
        ordered.setRuleName("有序多源链路");
        ordered.setRuleType("temporal_ordered");
        ordered.setSequenceJson("[\"waf\",\"zap\",\"wazuh\"]");
        SocCorrelationRule valueCount = activeRule();
        valueCount.setId(2L);
        valueCount.setRuleKey("value_count_rule");
        valueCount.setRuleName("不同规则数量");
        valueCount.setRuleType("value_count");
        valueCount.setSourceTypesJson("[\"waf\"]");
        valueCount.setThreshold(2);
        Fixture fixture = fixture(List.of(ordered, valueCount), List.of(
                event(1L, "waf", "waf_block", "WAF-1001", "10.20.1.15", "prod-app-01", "DEMO-BATCH", "DEMO-ACCESS-001", 8),
                event(2L, "zap", "web_app_finding", "ZAP-9001", "10.20.1.15", "prod-app-01", "DEMO-BATCH", "DEMO-ACCESS-001", 7),
                event(3L, "wazuh", "host_signal", "WAZUH-550", "10.20.1.15", "prod-app-01", "DEMO-BATCH", "DEMO-ACCESS-001", 6),
                event(4L, "waf", "upload_block", "WAF-1002", "10.20.1.15", "prod-app-01", "DEMO-BATCH", "DEMO-ACCESS-001", 5)
        ), List.of(), List.of(), true);

        CorrelationService.CorrelateResult result = fixture.service().correlate();

        assertThat(result.createdClusters()).isEqualTo(2);
        assertThat(fixture.clusters()).extracting(SocIncidentCluster::getRuleKey)
                .containsExactlyInAnyOrder("ordered_rule", "value_count_rule");
    }

    @Test
    void deniesIncidentDetailWhenSecurityScopeRejectsAccess() {
        SocIncidentCluster cluster = new SocIncidentCluster();
        cluster.setId(1L);
        cluster.setClusterNo("CL-DENIED");
        cluster.setCorrelationKey("denied");
        cluster.setDeleted(0);
        cluster.setOwnerId(99L);
        Fixture fixture = fixture(List.of(activeRule()), defaultEvents(), List.of(), List.of(), false);
        fixture.clusters().add(cluster);

        assertThatThrownBy(() -> fixture.service().detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
    }

    private static Fixture fixture() {
        return fixture(List.of(activeRule()), defaultEvents(), List.of(alert()), List.of(vulnerability()), true);
    }

    private static Fixture fixture(List<SocCorrelationRule> seedRules,
                                   List<SocExternalEvent> seedEvents,
                                   List<SocAlert> seedAlerts,
                                   List<SocVulnerability> seedVulnerabilities,
                                   boolean canAccess) {
        List<SocCorrelationRule> rules = new ArrayList<>(seedRules);
        List<SocIncidentCluster> clusters = new ArrayList<>();
        List<SocIncidentEvidence> evidence = new ArrayList<>();
        List<SocTicket> tickets = new ArrayList<>();
        List<SocTicketTimeline> timelines = new ArrayList<>();
        SocCorrelationRuleMapper ruleMapper = mapper(SocCorrelationRuleMapper.class, rules, null);
        SocIncidentClusterMapper clusterMapper = mapper(SocIncidentClusterMapper.class, clusters, null);
        SocIncidentEvidenceMapper evidenceMapper = mapper(SocIncidentEvidenceMapper.class, evidence, null);
        SocExternalEventMapper externalEventMapper = mapper(SocExternalEventMapper.class, new ArrayList<>(seedEvents), null);
        SocAlertMapper alertMapper = mapper(SocAlertMapper.class, new ArrayList<>(seedAlerts), null);
        SocVulnerabilityMapper vulnerabilityMapper = mapper(SocVulnerabilityMapper.class, new ArrayList<>(seedVulnerabilities), null);
        SocAssetMapper assetMapper = mapper(SocAssetMapper.class, List.of(), null);
        SocTicketMapper ticketMapper = mapper(SocTicketMapper.class, tickets, null);
        SocTicketTimelineMapper timelineMapper = mapper(SocTicketTimelineMapper.class, timelines, null);
        SocSecurityScope scope = new SocSecurityScope(null, null, null, null, null) {
            @Override
            public Long currentUserId() {
                return 1L;
            }

            @Override
            public String currentUsername() {
                return "tester";
            }

            @Override
            public boolean canAccess(Long ownerId, Long deptId) {
                return canAccess;
            }

            @Override
            public <T> void applyDataScope(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> ownerColumn, SFunction<T, Long> deptColumn) {
            }
        };
        CorrelationService service = new CorrelationService(
                ruleMapper, clusterMapper, evidenceMapper, externalEventMapper, alertMapper,
                vulnerabilityMapper, assetMapper, ticketMapper, timelineMapper, scope, new ObjectMapper()
        );
        return new Fixture(service, clusters, evidence, timelines);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> mapperType, List<?> rows, Object ignored) {
        return (T) Proxy.newProxyInstance(
                CorrelationServiceTest.class.getClassLoader(),
                new Class[]{mapperType},
                (_proxy, method, args) -> switch (method.getName()) {
                    case "selectList" -> mapperType == SocCorrelationRuleMapper.class ? activeRows(rows) : rows;
                    case "selectCount" -> (long) rows.size();
                    case "selectById" -> rows.stream()
                            .filter(row -> idOf(row) != null && idOf(row).equals(((Number) args[0]).longValue()))
                            .findFirst()
                            .orElse(null);
                    case "selectByCorrelationKey" -> rows.stream()
                            .filter(row -> row instanceof SocIncidentCluster cluster && args[0].equals(cluster.getCorrelationKey()))
                            .findFirst()
                            .orElse(null);
                    case "selectByClusterAndEvidence" -> rows.stream()
                            .filter(row -> row instanceof SocIncidentEvidence evidence
                                    && args[0].equals(evidence.getClusterId())
                                    && args[1].equals(evidence.getEvidenceType())
                                    && args[2].equals(evidence.getEvidenceId()))
                            .findFirst()
                            .orElse(null);
                    case "selectOne" -> selectOne(mapperType, rows, args == null || args.length == 0 ? null : args[0]);
                    case "insert" -> {
                        Object row = args[0];
                        assignId(row, rows.size() + 1L);
                        ((List<Object>) rows).add(row);
                        yield 1;
                    }
                    case "updateById" -> 1;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static List<?> activeRows(List<?> rows) {
        return rows.stream()
                .filter(row -> !(row instanceof SocCorrelationRule rule)
                        || (Integer.valueOf(0).equals(rule.getDeleted())
                        && Boolean.TRUE.equals(rule.getEnabled())
                        && "active".equals(rule.getStatus())))
                .toList();
    }

    private static Object selectOne(Class<?> mapperType, List<?> rows, Object wrapper) {
        if (rows.isEmpty()) {
            return null;
        }
        List<Object> values = wrapperValues(wrapper);
        if (mapperType == SocIncidentClusterMapper.class && !values.isEmpty()) {
            String correlationKey = String.valueOf(values.getFirst());
            return rows.stream()
                    .filter(row -> row instanceof SocIncidentCluster cluster && correlationKey.equals(cluster.getCorrelationKey()))
                    .findFirst()
                    .orElse(null);
        }
        if (mapperType == SocIncidentEvidenceMapper.class && values.size() >= 3) {
            Long clusterId = ((Number) values.get(0)).longValue();
            String evidenceType = String.valueOf(values.get(1));
            Long evidenceId = ((Number) values.get(2)).longValue();
            return rows.stream()
                    .filter(row -> row instanceof SocIncidentEvidence evidence
                            && clusterId.equals(evidence.getClusterId())
                            && evidenceType.equals(evidence.getEvidenceType())
                            && evidenceId.equals(evidence.getEvidenceId()))
                    .findFirst()
                    .orElse(null);
        }
        return rows.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> wrapperValues(Object wrapper) {
        if (wrapper == null) {
            return List.of();
        }
        try {
            Method method = findMethod(wrapper.getClass(), "getParamNameValuePairs");
            if (method != null) {
                method.setAccessible(true);
                Map<String, Object> values = (Map<String, Object>) method.invoke(wrapper);
                if (values != null && !values.isEmpty()) {
                    return new ArrayList<>(values.values());
                }
            }
            Field field = findField(wrapper.getClass(), "paramNameValuePairs");
            if (field != null) {
                field.setAccessible(true);
                Map<String, Object> values = (Map<String, Object>) field.get(wrapper);
                return values == null ? List.of() : new ArrayList<>(values.values());
            }
            return List.of();
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Long idOf(Object row) {
        if (row instanceof SocCorrelationRule item) return item.getId();
        if (row instanceof SocIncidentCluster item) return item.getId();
        if (row instanceof SocIncidentEvidence item) return item.getId();
        if (row instanceof SocTicket item) return item.getId();
        if (row instanceof SocAlert item) return item.getId();
        return null;
    }

    private static void assignId(Object row, Long id) {
        if (row instanceof SocIncidentCluster item) item.setId(id);
        if (row instanceof SocIncidentEvidence item) item.setId(id);
        if (row instanceof SocTicket item) item.setId(id);
        if (row instanceof SocTicketTimeline item) item.setId(id);
        if (row instanceof SocCorrelationRule item) item.setId(id);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class || type == long.class) return 0;
        return null;
    }

    private static SocCorrelationRule activeRule() {
        SocCorrelationRule rule = new SocCorrelationRule();
        rule.setId(1L);
        rule.setRuleKey("test_cross_source_chain");
        rule.setRuleName("测试多源链路");
        rule.setRuleType("cross_source_chain");
        rule.setSourceTypesJson("[\"waf\",\"zap\",\"wazuh\",\"trivy\"]");
        rule.setGroupByJson("[\"assetIp\",\"batchId\"]");
        rule.setThreshold(2);
        rule.setTimeframeSeconds(1800);
        rule.setStatus("active");
        rule.setEnabled(true);
        rule.setDeleted(0);
        return rule;
    }

    private static List<SocExternalEvent> defaultEvents() {
        return List.of(
                event(1L, "waf", "waf_block", "WAF-1001"),
                event(2L, "zap", "web_app_finding", "ZAP-9001"),
                event(3L, "wazuh", "host_signal", "WAZUH-550")
        );
    }

    private static SocExternalEvent event(Long id, String sourceType, String eventType, String ruleId) {
        return event(id, sourceType, eventType, ruleId, "10.20.1.15", "prod-app-01", "DEMO-BATCH", "DEMO-ACCESS-001", 3);
    }

    private static SocExternalEvent event(Long id, String sourceType, String eventType, String ruleId,
                                          String assetIp, String assetName, String batchId, String demoCaseId,
                                          int minutesAgo) {
        SocExternalEvent event = new SocExternalEvent();
        event.setId(id);
        event.setEventUid(sourceType.toUpperCase() + "-" + id);
        event.setSourceType(sourceType);
        event.setEventType(eventType);
        event.setSeverity("high");
        event.setRuleId(ruleId);
        event.setAssetIp(assetIp);
        event.setAssetName(assetName);
        event.setBatchId(batchId);
        event.setDemoCaseId(demoCaseId);
        event.setEventTime(TEST_NOW.minusMinutes(minutesAgo));
        event.setOwnerId(1L);
        event.setDeptId(12L);
        event.setDeleted(0);
        return event;
    }

    private static SocAlert alert() {
        SocAlert alert = new SocAlert();
        alert.setId(10L);
        alert.setAlertUid("ALERT-10");
        alert.setSourceType("waf");
        alert.setEventType("waf_block");
        alert.setSeverity("high");
        alert.setRuleId("WAF-1001");
        alert.setRuleDescription("WAF blocked request");
        alert.setAssetIp("10.20.1.15");
        alert.setAssetName("prod-app-01");
        alert.setBatchId("DEMO-BATCH");
        alert.setDemoCaseId("DEMO-ACCESS-001");
        alert.setEventTime(LocalDateTime.now().minusMinutes(2));
        alert.setOwnerId(1L);
        alert.setDeptId(12L);
        alert.setDeleted(0);
        return alert;
    }

    private static SocVulnerability vulnerability() {
        SocVulnerability vulnerability = new SocVulnerability();
        vulnerability.setId(20L);
        vulnerability.setCveId("CVE-DEMO-0001");
        vulnerability.setSourceType("trivy");
        vulnerability.setSeverity("high");
        vulnerability.setAssetIp("10.20.1.15");
        vulnerability.setAssetName("prod-app-01");
        vulnerability.setDetectedAt(LocalDateTime.now().minusMinutes(1));
        vulnerability.setOwnerId(1L);
        vulnerability.setDeptId(12L);
        vulnerability.setDeleted(0);
        return vulnerability;
    }

    private record Fixture(CorrelationService service,
                           List<SocIncidentCluster> clusters,
                           List<SocIncidentEvidence> evidence,
                           List<SocTicketTimeline> timelines) {
    }
}
