package com.zhangjiyan.template.common.workflow;

import com.zhangjiyan.template.common.exception.BusinessException;

public class StateTransitionException extends BusinessException {
    public StateTransitionException(String message) {
        super(message);
    }
}
