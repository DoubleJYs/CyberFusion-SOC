package com.zhangjiyan.template.soc.playbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.AlertActionRequest;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.ticket.SocTicketTimelineMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponsePlaybookServiceTest {

    @Test
    void suggestsMatchingActivePlaybookBySourceEventAndSeverity() {
        SocAlert alert = alert("waf", "waf_block", "high");
        SocResponsePlaybook playbook = playbook(30L, "waf", "waf_block", "medium", "active", 1);
        Fixture fixture = fixture(alert, ticket(), List.of(playbook(31L, "zap", "web_app_finding", "low", "active", 1), playbook), List.of(step(301L, false)));

        List<ResponsePlaybookService.PlaybookSuggestion> suggestions = fixture.service().suggestionsForAlert(10L);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().playbook().getSourceType()).isEqualTo("waf");
        assertThat(suggestions.getFirst().matchReason()).contains("sourceType=waf", "eventType=waf_block");
    }

    @Test
    void rejectsDangerousScriptOrScannerTextBeforeCreate() {
        Fixture fixture = fixture(alert("waf", "waf_block", "high"), ticket(), List.of(), List.of(step(301L, false)));
        PlaybookRequest request = new PlaybookRequest(
                "PB-BAD", "危险剧本", "waf", "waf_block", "*", "low",
                null, "curl http://example.invalid", "draft", true, 10, "bad",
                List.of(new PlaybookStepRequest(null, "triage", "复核", "triage", "analyst", "核对证据", "说明", false, 10, true))
        );

        assertThatThrownBy(() -> fixture.service().create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("攻击、脚本、扫描、下载或自动修复语义");
    }

    @Test
    void applyingPlaybookCreatesTicketTasksAndReusesTicket() {
        SocAlert alert = alert("waf", "waf_block", "high");
        alert.setOwnerId(5L);
        SocTicket ticket = ticket();
        SocResponsePlaybook playbook = playbook(30L, "waf", "waf_block", "medium", "active", 1);
        List<SocResponsePlaybookStep> steps = List.of(step(301L, false), step(302L, true));
        Fixture fixture = fixture(alert, ticket, List.of(playbook), steps);

        ResponsePlaybookService.ApplyPlaybookResult result = fixture.service().applyToAlert(10L, 30L, "测试应用剧本");

        assertThat(result.ticket().getId()).isEqualTo(20L);
        assertThat(result.tasks()).hasSize(2);
        assertThat(result.employeeTasks()).isEqualTo(1);
        assertThat(fixture.taskWrites()).isEqualTo(2);
        assertThat(fixture.matchWrites()).isEqualTo(1);
        assertThat(fixture.timelineWrites()).isEqualTo(1);
    }

    private static Fixture fixture(SocAlert alert,
                                   SocTicket ticket,
                                   List<SocResponsePlaybook> playbooks,
                                   List<SocResponsePlaybookStep> steps) {
        WriteCounter taskCounter = new WriteCounter();
        WriteCounter matchCounter = new WriteCounter();
        WriteCounter timelineCounter = new WriteCounter();
        WriteCounter externalEventCounter = new WriteCounter();
        List<SocTicketTask> existingTasks = new ArrayList<>();
        SocResponsePlaybookMapper playbookMapper = mapper(SocResponsePlaybookMapper.class, playbooks, null);
        SocResponsePlaybookStepMapper stepMapper = mapper(SocResponsePlaybookStepMapper.class, steps, null);
        SocTicketTaskMapper taskMapper = mapper(SocTicketTaskMapper.class, existingTasks, taskCounter);
        SocPlaybookMatchLogMapper matchLogMapper = mapper(SocPlaybookMatchLogMapper.class, List.of(), matchCounter);
        SocTicketTimelineMapper timelineMapper = mapper(SocTicketTimelineMapper.class, List.of(), timelineCounter);
        SocAlertMapper alertMapper = mapper(SocAlertMapper.class, List.of(alert), null);
        SocExternalEventMapper externalEventMapper = mapper(SocExternalEventMapper.class, List.of(), externalEventCounter);
        SocTicketMapper ticketMapper = (SocTicketMapper) Proxy.newProxyInstance(
                ResponsePlaybookServiceTest.class.getClassLoader(),
                new Class[]{SocTicketMapper.class},
                (_proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> ticket;
                    default -> defaultValue(method.getReturnType());
                }
        );
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
                return true;
            }
        };
        SocOperationService operationService = new SocOperationService(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper()
        ) {
            @Override
            public SocAlert alertDetail(Long id) {
                return alert;
            }

            @Override
            public SocTicket createTicket(Long alertId, AlertActionRequest request) {
                return ticket;
            }
        };
        ResponsePlaybookService service = new ResponsePlaybookService(
                playbookMapper, stepMapper, taskMapper, matchLogMapper, ticketMapper,
                timelineMapper, alertMapper, externalEventMapper, operationService, scope
        );
        return new Fixture(service, taskCounter, matchCounter, timelineCounter);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> mapperType, List<?> rows, WriteCounter counter) {
        return (T) Proxy.newProxyInstance(
                ResponsePlaybookServiceTest.class.getClassLoader(),
                new Class[]{mapperType},
                (_proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> rows.stream()
                            .filter(row -> idOf(row) != null && idOf(row).equals(((Number) args[0]).longValue()))
                            .findFirst()
                            .orElse(null);
                    case "selectList" -> rows;
                    case "insert" -> {
                        if (counter != null) {
                            counter.writes++;
                            if (args[0] instanceof SocTicketTask task) {
                                task.setId((long) counter.writes);
                            }
                        }
                        yield 1;
                    }
                    case "delete", "updateById" -> 1;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Long idOf(Object row) {
        if (row instanceof SocResponsePlaybook item) return item.getId();
        if (row instanceof SocResponsePlaybookStep item) return item.getId();
        if (row instanceof SocTicketTask item) return item.getId();
        if (row instanceof SocAlert item) return item.getId();
        return null;
    }

    private static SocAlert alert(String sourceType, String eventType, String severity) {
        SocAlert alert = new SocAlert();
        alert.setId(10L);
        alert.setSourceType(sourceType);
        alert.setEventType(eventType);
        alert.setSeverity(severity);
        alert.setRuleId("WAF-DEMO-1001");
        alert.setRuleDescription("Demo alert");
        alert.setOwnerId(1L);
        alert.setDeptId(12L);
        return alert;
    }

    private static SocTicket ticket() {
        SocTicket ticket = new SocTicket();
        ticket.setId(20L);
        ticket.setAlertId(10L);
        ticket.setStatus("待分派");
        ticket.setDeptId(12L);
        return ticket;
    }

    private static SocResponsePlaybook playbook(Long id, String sourceType, String eventType, String minSeverity, String status, int enabled) {
        SocResponsePlaybook playbook = new SocResponsePlaybook();
        playbook.setId(id);
        playbook.setPlaybookKey("PB-" + sourceType);
        playbook.setPlaybookName(sourceType + " playbook");
        playbook.setSourceType(sourceType);
        playbook.setEventType(eventType);
        playbook.setRuleIdPattern("*");
        playbook.setMinSeverity(minSeverity);
        playbook.setStatus(status);
        playbook.setEnabled(enabled);
        playbook.setDeleted(0);
        return playbook;
    }

    private static SocResponsePlaybookStep step(Long id, boolean employee) {
        SocResponsePlaybookStep step = new SocResponsePlaybookStep();
        step.setId(id);
        step.setStepKey("step-" + id);
        step.setStepName(employee ? "员工确认" : "分析员复核");
        step.setStepType(employee ? "employee_confirm" : "triage");
        step.setOwnerRole(employee ? "employee" : "analyst");
        step.setInstruction("人工核对处置证据");
        step.setExpectedEvidence("处置记录");
        step.setRequiresEmployee(employee ? 1 : 0);
        step.setSortOrder(employee ? 20 : 10);
        step.setEnabled(1);
        step.setDeleted(0);
        return step;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private static class WriteCounter {
        private int writes;
    }

    private record Fixture(ResponsePlaybookService service,
                           WriteCounter taskCounter,
                           WriteCounter matchCounter,
                           WriteCounter timelineCounter) {
        int taskWrites() {
            return taskCounter.writes;
        }

        int matchWrites() {
            return matchCounter.writes;
        }

        int timelineWrites() {
            return timelineCounter.writes;
        }
    }
}
