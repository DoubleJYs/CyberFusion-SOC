package com.zhangjiyan.template.soc.ticket;

import com.zhangjiyan.template.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class TicketStateMachine {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "待分派", Set.of("处理中"),
            "处理中", Set.of("待复核"),
            "待复核", Set.of("处理中", "已关闭"),
            "已关闭", Set.of("已归档"),
            "已归档", Set.of()
    );

    public void validate(String currentStatus, String targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            throw new BusinessException("工单状态不能为空");
        }
        if (currentStatus.equals(targetStatus)) {
            return;
        }
        if (!ALLOWED.getOrDefault(currentStatus, Set.of()).contains(targetStatus)) {
            throw new BusinessException("工单状态不能从 " + currentStatus + " 流转到 " + targetStatus);
        }
    }
}
