package com.zhangjiyan.template.common.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StateMachine {

    private final List<StateTransitionRule> rules = new ArrayList<>();

    public StateMachine allow(String fromStatus, String toStatus, String action) {
        return allow(fromStatus, toStatus, action, false, null);
    }

    public StateMachine allow(String fromStatus, String toStatus, String action, boolean reasonRequired, String description) {
        rules.add(new StateTransitionRule(fromStatus, toStatus, action, reasonRequired, description));
        return this;
    }

    public boolean canTransit(String fromStatus, String toStatus, String action) {
        return match(fromStatus, toStatus, action) != null;
    }

    public StateTransitionResult validate(String fromStatus, String toStatus, String action, String reason) {
        StateTransitionRule rule = match(fromStatus, toStatus, action);
        if (rule == null) {
            throw new StateTransitionException("非法状态流转: " + fromStatus + " -> " + toStatus);
        }
        if (rule.reasonRequired() && (reason == null || reason.isBlank())) {
            throw new StateTransitionException("状态流转必须填写原因");
        }
        return new StateTransitionResult(true, fromStatus, toStatus, action, "状态流转允许");
    }

    public List<StateTransitionRule> rules() {
        return List.copyOf(rules);
    }

    private StateTransitionRule match(String fromStatus, String toStatus, String action) {
        return rules.stream()
                .filter(rule -> Objects.equals(rule.fromStatus(), fromStatus)
                        && Objects.equals(rule.toStatus(), toStatus)
                        && (rule.action() == null || Objects.equals(rule.action(), action)))
                .findFirst()
                .orElse(null);
    }
}
