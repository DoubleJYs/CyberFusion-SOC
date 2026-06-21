package com.zhangjiyan.template.common.health;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformHealthControllerTest {

    @Test
    void readinessStatusShouldBeDownWhenAnyDependencyIsDown() {
        PlatformHealthController.HealthResponse response = new PlatformHealthController.HealthResponse(
                "DOWN",
                "2026-05-27T16:00:00Z",
                Map.of(
                        "database", new PlatformHealthController.DependencyHealth("UP", 3, null),
                        "redis", new PlatformHealthController.DependencyHealth("DOWN", 12, "ping failed")
                )
        );

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.dependencies()).containsKeys("database", "redis");
        assertThat(response.dependencies().get("redis").message()).isEqualTo("ping failed");
    }
}
