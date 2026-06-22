package com.zhangjiyan.template.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.config.SecurityHardeningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @Test
    void shouldRejectRequestsBeyondConfiguredWindow() throws Exception {
        SecurityHardeningProperties properties = new SecurityHardeningProperties();
        properties.getRateLimit().setAuthRequestsPerMinute(2);
        RateLimitFilter filter = new RateLimitFilter(properties, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-27T15:30:00Z"), ZoneOffset.UTC));

        MockHttpServletRequest first = loginRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());

        MockHttpServletRequest second = loginRequest();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        MockHttpServletRequest third = loginRequest();
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(third, thirdResponse, new MockFilterChain());

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
        assertThat(thirdResponse.getStatus()).isEqualTo(429);
        assertThat(thirdResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(thirdResponse.getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(thirdResponse.getContentAsString()).contains("秒后重试");
    }

    @Test
    void shouldSkipPreflightRequests() throws Exception {
        SecurityHardeningProperties properties = new SecurityHardeningProperties();
        properties.getRateLimit().setAuthRequestsPerMinute(0);
        RateLimitFilter filter = new RateLimitFilter(properties, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-27T15:30:00Z"), ZoneOffset.UTC));

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldNotLetCurrentUserRequestsExhaustLoginBucket() throws Exception {
        SecurityHardeningProperties properties = new SecurityHardeningProperties();
        properties.getRateLimit().setAuthRequestsPerMinute(1);
        properties.getRateLimit().setRequestsPerMinute(10);
        RateLimitFilter filter = new RateLimitFilter(properties, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-27T15:30:00Z"), ZoneOffset.UTC));

        MockHttpServletRequest me = new MockHttpServletRequest("GET", "/api/auth/me");
        me.setContextPath("/api");
        me.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse meResponse = new MockHttpServletResponse();
        filter.doFilter(me, meResponse, new MockFilterChain());

        MockHttpServletResponse firstLoginResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest(), firstLoginResponse, new MockFilterChain());

        MockHttpServletResponse secondLoginResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest(), secondLoginResponse, new MockFilterChain());

        assertThat(meResponse.getStatus()).isEqualTo(200);
        assertThat(firstLoginResponse.getStatus()).isEqualTo(200);
        assertThat(secondLoginResponse.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContextPath("/api");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
