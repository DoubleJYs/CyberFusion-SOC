package com.zhangjiyan.template.common.workflow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class BizNoGenerator {

    private BizNoGenerator() {
    }

    public static String generate(BizNoRule rule, long nextValue, LocalDate date) {
        String datePart = "";
        if (rule.datePattern() != null && !rule.datePattern().isBlank()) {
            datePart = date.format(DateTimeFormatter.ofPattern(rule.datePattern()));
        }
        String sequencePart = String.format("%0" + rule.length() + "d", nextValue);
        return (rule.prefix() == null ? "" : rule.prefix()) + datePart + sequencePart;
    }
}
