package com.zhangjiyan.template.soc.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.correlation.SocIncidentCluster;
import com.zhangjiyan.template.soc.correlation.SocIncidentClusterMapper;
import com.zhangjiyan.template.soc.keeper.SocClientCheckupMapper;
import com.zhangjiyan.template.soc.keeper.SocClientRecommendationAction;
import com.zhangjiyan.template.soc.keeper.SocClientRecommendationActionMapper;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.SocTicketTaskMapper;
import com.zhangjiyan.template.soc.risk.SocAssetRiskFactorMapper;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshotMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {

    private final Fixture fixture = new Fixture();
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(
                mapper(SocIncidentClusterMapper.class, fixture.incidents, null, null, fixture),
                mapper(SocVulnerabilityMapper.class, fixture.vulnerabilities, null, null, fixture),
                mapper(SocTicketMapper.class, fixture.tickets, null, null, fixture),
                mapper(SocTicketTaskMapper.class, fixture.tasks, null, null, fixture),
                mapper(SocAlertMapper.class, fixture.alerts, null, null, fixture),
                mapper(SocAssetMapper.class, fixture.assets, asset(), null, fixture),
                mapper(SocClientCheckupMapper.class, List.of(), null, null, fixture),
                mapper(SocAssetRiskSnapshotMapper.class, List.of(), null, null, fixture),
                mapper(SocAssetRiskFactorMapper.class, List.of(), null, null, fixture),
                mapper(SocClientRecommendationActionMapper.class, fixture.actions, null, fixture.insertedActions, fixture),
                new TestSecurityScope()
        );
    }

    @Test
    void topRecommendationsRankExplainableIncidentVulnerabilityTicketAndTaskActions() {
        fixture.incidents.add(incident());
        fixture.vulnerabilities.add(vulnerability());
        fixture.tickets.add(overdueTicket());
        fixture.tasks.add(pendingEmployeeTask());
        fixture.alerts.add(alert());

        List<RecommendationService.RecommendationItem> items = service.topRecommendations(10);

        assertThat(items).extracting(RecommendationService.RecommendationItem::relatedBizType)
                .contains("incident", "vulnerability", "ticket", "client_task");
        assertThat(items).allSatisfy(item -> {
            assertThat(item.title()).isNotBlank();
            assertThat(item.priority()).isIn("critical", "high", "medium", "low");
            assertThat(item.reason()).isNotBlank();
            assertThat(item.recommendedAction()).isNotBlank();
            assertThat(item.relatedBizId()).isNotNull();
            assertThat(item.assigneeType()).isIn("analyst", "employee");
            assertThat(item.status()).isNotBlank();
        });
        assertThat(items).isSortedAccordingTo((left, right) -> Integer.compare(right.priorityScore(), left.priorityScore()));
    }

    @Test
    void confirmedRecommendationIsDownRankedButStillAuditable() {
        fixture.incidents.add(incident());
        SocClientRecommendationAction action = new SocClientRecommendationAction();
        action.setRecommendationKey("incident-101");
        action.setActionType("confirm");
        action.setOwnerId(1L);
        action.setDeptId(10L);
        fixture.actions.add(action);

        List<RecommendationService.RecommendationItem> items = service.topRecommendations(5);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.key()).isEqualTo("incident-101");
            assertThat(item.status()).isEqualTo("confirmed");
            assertThat(item.priorityScore()).isLessThan(incidentRawScore());
        });
    }

    @Test
    void clientNextActionsUsePlainEmployeeLanguageAndCurrentAssetScope() {
        fixture.assets.add(asset());
        fixture.tasks.add(pendingEmployeeTask());
        fixture.alerts.add(alert());

        List<RecommendationService.ClientNextAction> actions = service.clientNextActions("10.20.1.15", 5);

        assertThat(actions).extracting(RecommendationService.ClientNextAction::title)
                .contains("请确认安全团队分配的待办", "请先完成本机检查");
        assertThat(actions).allSatisfy(action -> {
            assertThat(action.recommendedAction()).doesNotContain("shell", "raw", "JSON");
            assertThat(action.reason()).isNotBlank();
        });
    }

    @Test
    void recordActionWritesRecommendationAdoptionWithoutExecutingAnything() {
        fixture.incidents.add(incident());

        RecommendationService.RecommendationActionRecord record = service.recordAction("incident-101",
                new RecommendationActionRequest("view", "incident", 101L, "10.20.1.15", "prod-app-01", "查看推荐"));

        assertThat(record.id()).isEqualTo(9001L);
        assertThat(record.recommendationKey()).isEqualTo("incident-101");
        assertThat(record.actionType()).isEqualTo("view");
        assertThat(fixture.insertedActions).singleElement().satisfies(action -> {
            assertThat(action.getRecommendationKey()).isEqualTo("incident-101");
            assertThat(action.getRelatedType()).isEqualTo("incident");
            assertThat(action.getRelatedId()).isEqualTo(101L);
            assertThat(action.getOperatorName()).isEqualTo("admin");
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> mapperType, List<?> rows, Object selectOneFallback,
                                List<SocClientRecommendationAction> insertedActions, Fixture fixture) {
        return (T) Proxy.newProxyInstance(mapperType.getClassLoader(), new Class<?>[]{mapperType}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "selectList" -> rows;
                case "selectOne" -> rows.isEmpty() ? selectOneFallback : rows.getFirst();
                case "selectById" -> selectById(rows, args == null ? null : args[0]);
                case "insert" -> {
                    Object entity = args == null || args.length == 0 ? null : args[0];
                    if (entity instanceof SocClientRecommendationAction action) {
                        action.setId(9001L);
                        action.setCreatedAt(LocalDateTime.parse("2026-06-22T12:00:00"));
                        insertedActions.add(action);
                        fixture.actions.add(action);
                    }
                    yield 1;
                }
                default -> defaultValue(method.getReturnType());
            };
        });
    }

    private static Object selectById(List<?> rows, Object id) {
        if (id == null) return null;
        for (Object row : rows) {
            try {
                Object rowId = row.getClass().getMethod("getId").invoke(row);
                if (id.equals(rowId)) return row;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (boolean.class.equals(type)) return false;
        if (void.class.equals(type)) return null;
        return 0;
    }

    private static SocIncidentCluster incident() {
        SocIncidentCluster incident = new SocIncidentCluster();
        incident.setId(101L);
        incident.setClusterNo("INC-101");
        incident.setTitle("WAF/ZAP/Wazuh 关联事件");
        incident.setSeverity("critical");
        incident.setStatus("open");
        incident.setScore(100);
        incident.setEvidenceCount(5);
        incident.setAssetId(15L);
        incident.setAssetIp("10.20.1.15");
        incident.setHostname("prod-app-01");
        incident.setOwnerId(1L);
        incident.setDeptId(10L);
        incident.setDeleted(0);
        return incident;
    }

    private static int incidentRawScore() {
        return 95 + 100 / 4 + 5;
    }

    private static SocVulnerability vulnerability() {
        SocVulnerability vulnerability = new SocVulnerability();
        vulnerability.setId(201L);
        vulnerability.setSoftwareName("demo-lib");
        vulnerability.setCveId("CVE-DEMO-0001");
        vulnerability.setSeverity("high");
        vulnerability.setStatus("open");
        vulnerability.setAssetIp("10.20.1.15");
        vulnerability.setAssetName("prod-app-01");
        vulnerability.setOwnerId(1L);
        vulnerability.setDeptId(10L);
        vulnerability.setDeleted(0);
        return vulnerability;
    }

    private static SocTicket overdueTicket() {
        SocTicket ticket = new SocTicket();
        ticket.setId(301L);
        ticket.setTicketNo("TICKET-301");
        ticket.setAlertId(401L);
        ticket.setStatus("处理中");
        ticket.setDueAt(LocalDateTime.now().minusHours(2));
        ticket.setAssigneeId(1L);
        ticket.setDeptId(10L);
        ticket.setDeleted(0);
        return ticket;
    }

    private static SocTicketTask pendingEmployeeTask() {
        SocTicketTask task = new SocTicketTask();
        task.setId(501L);
        task.setTicketId(301L);
        task.setAlertId(401L);
        task.setTaskKey("employee-check");
        task.setTaskName("提交本机检查记录");
        task.setAssigneeType("employee");
        task.setAssigneeId(1L);
        task.setStatus("pending");
        task.setSortOrder(10);
        task.setDeleted(0);
        return task;
    }

    private static SocAlert alert() {
        SocAlert alert = new SocAlert();
        alert.setId(401L);
        alert.setAssetIp("10.20.1.15");
        alert.setAssetName("prod-app-01");
        alert.setDeleted(0);
        return alert;
    }

    private static SocAsset asset() {
        SocAsset asset = new SocAsset();
        asset.setId(15L);
        asset.setIp("10.20.1.15");
        asset.setHostname("prod-app-01");
        asset.setOwnerId(1L);
        asset.setDeptId(10L);
        asset.setDeleted(0);
        return asset;
    }

    private static class Fixture {
        private final List<SocIncidentCluster> incidents = new ArrayList<>();
        private final List<SocVulnerability> vulnerabilities = new ArrayList<>();
        private final List<SocTicket> tickets = new ArrayList<>();
        private final List<SocTicketTask> tasks = new ArrayList<>();
        private final List<SocAlert> alerts = new ArrayList<>();
        private final List<SocAsset> assets = new ArrayList<>();
        private final List<SocClientRecommendationAction> actions = new ArrayList<>();
        private final List<SocClientRecommendationAction> insertedActions = new ArrayList<>();
    }

    private static class TestSecurityScope extends SocSecurityScope {
        private TestSecurityScope() {
            super(null, null, null, null, null);
        }

        @Override
        public boolean canViewAllData() {
            return true;
        }

        @Override
        public boolean canAccess(Long ownerId, Long deptId) {
            return true;
        }

        @Override
        public <T> void applyDataScope(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> ownerColumn, SFunction<T, Long> deptColumn) {
            // Unit tests use in-memory mapper proxies and all rows are in scope.
        }

        @Override
        public Long currentUserId() {
            return 1L;
        }

        @Override
        public Long currentDeptId() {
            return 10L;
        }

        @Override
        public String currentUsername() {
            return "admin";
        }
    }
}
