package com.zhangjiyan.template.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.security")
public class SecurityHardeningProperties {
    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Cors {
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("Authorization", "Content-Type", "X-Requested-With"));
        private List<String> exposedHeaders = new ArrayList<>(List.of("Authorization"));
        private boolean allowCredentials = true;
        private long maxAgeSeconds = 3600;
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int requestsPerMinute = 120;
        private int authRequestsPerMinute = 20;
        private int maxTrackedClients = 5000;
        private List<String> excludedPaths = new ArrayList<>(List.of(
                "/v3/api-docs",
                "/swagger-ui",
                "/swagger-ui.html"
        ));
    }
}
