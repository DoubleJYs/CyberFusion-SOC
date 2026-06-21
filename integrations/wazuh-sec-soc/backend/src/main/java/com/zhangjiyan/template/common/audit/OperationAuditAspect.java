package com.zhangjiyan.template.common.audit;

import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.common.web.ClientInfoUtils;
import com.zhangjiyan.template.system.log.SysOperationLog;
import com.zhangjiyan.template.system.log.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Locale;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationAuditAspect {

    private static final int MAX_ACTION_LENGTH = 64;
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final SysOperationLogMapper operationLogMapper;

    @Around("@within(restController) && execution(public * com.zhangjiyan.template..*Controller.*(..))")
    public Object audit(ProceedingJoinPoint joinPoint, RestController restController) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null || shouldSkip(request)) {
            return joinPoint.proceed();
        }

        long startedAt = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            saveLog(joinPoint, request, "SUCCESS", "耗时 " + (System.currentTimeMillis() - startedAt) + " ms");
            return result;
        } catch (Throwable throwable) {
            saveLog(joinPoint, request, "FAIL", throwable.getMessage());
            throw throwable;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return path.startsWith(request.getContextPath() + "/auth/")
                || path.startsWith(request.getContextPath() + "/v3/api-docs")
                || path.startsWith(request.getContextPath() + "/swagger-ui");
    }

    private void saveLog(ProceedingJoinPoint joinPoint, HttpServletRequest request, String status, String message) {
        try {
            SysOperationLog operationLog = new SysOperationLog();
            operationLog.setUsername(SecurityUtils.currentUsername());
            operationLog.setAction(action(joinPoint));
            operationLog.setMethod(request.getMethod());
            operationLog.setPath(request.getRequestURI());
            operationLog.setIp(ClientInfoUtils.ip(request));
            operationLog.setUserAgent(ClientInfoUtils.userAgent(request));
            operationLog.setStatus(status);
            operationLog.setMessage(limit(message, MAX_MESSAGE_LENGTH));
            operationLogMapper.insert(operationLog);
        } catch (Exception ex) {
            log.warn("Failed to write operation audit log", ex);
        }
    }

    private String action(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationAudit annotation = method.getAnnotation(OperationAudit.class);
        if (annotation != null && !annotation.value().isBlank()) {
            return limit(annotation.value(), MAX_ACTION_LENGTH);
        }
        String controller = joinPoint.getTarget().getClass().getSimpleName()
                .replace("Controller", "")
                .replaceFirst("^Sys", "");
        return limit(camelToUpperSnake(controller) + "." + method.getName().toUpperCase(Locale.ROOT), MAX_ACTION_LENGTH);
    }

    private String camelToUpperSnake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
