package com.zhangjiyan.template.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.config.SecurityHardeningProperties;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityHardeningProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(SecurityHardeningProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.getRateLimit().isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizedPath(request);
        return properties.getRateLimit().getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = normalizedPath(request);
        String clientKey = clientIp(request) + ":" + request.getMethod() + ":" + pathGroup(path);
        long nowMinute = clock.millis() / 60_000;
        int limit = path.startsWith("/auth/") ? properties.getRateLimit().getAuthRequestsPerMinute() : properties.getRateLimit().getRequestsPerMinute();
        Window window = windows.compute(clientKey, (ignored, current) -> {
            if (current == null || current.minute != nowMinute) {
                return new Window(nowMinute, 1);
            }
            current.count++;
            return current;
        });
        cleanupIfNeeded(nowMinute);
        if (window.count > limit) {
            writeRateLimit(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private String pathGroup(String path) {
        if (path.startsWith("/auth/")) {
            return "/auth";
        }
        int secondSlash = path.indexOf('/', 1);
        if (secondSlash < 0) {
            return path;
        }
        int thirdSlash = path.indexOf('/', secondSlash + 1);
        return thirdSlash < 0 ? path : path.substring(0, thirdSlash);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void cleanupIfNeeded(long nowMinute) {
        if (windows.size() <= properties.getRateLimit().getMaxTrackedClients()) {
            return;
        }
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Window> entry = iterator.next();
            if (entry.getValue().minute < nowMinute) {
                iterator.remove();
            }
        }
    }

    private void writeRateLimit(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResult.fail(ResultCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试")
        ));
    }

    private static class Window {
        private final long minute;
        private int count;

        private Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
