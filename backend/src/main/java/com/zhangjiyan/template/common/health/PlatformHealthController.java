package com.zhangjiyan.template.common.health;

import com.zhangjiyan.template.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/health")
public class PlatformHealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/liveness")
    public ApiResult<HealthResponse> liveness() {
        return ApiResult.ok(new HealthResponse("UP", OffsetDateTime.now().toString(), Map.of(
                "application", new DependencyHealth("UP", 0, null)
        )));
    }

    @GetMapping
    public ApiResult<HealthResponse> health() {
        return readiness();
    }

    @GetMapping("/readiness")
    public ApiResult<HealthResponse> readiness() {
        Map<String, DependencyHealth> dependencies = new LinkedHashMap<>();
        dependencies.put("database", databaseHealth());
        dependencies.put("redis", redisHealth());
        String status = dependencies.values().stream().allMatch(item -> "UP".equals(item.status())) ? "UP" : "DOWN";
        return ApiResult.ok(new HealthResponse(status, OffsetDateTime.now().toString(), dependencies));
    }

    private DependencyHealth databaseHealth() {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(1);
            return new DependencyHealth(valid ? "UP" : "DOWN", elapsedMillis(started), valid ? null : "connection invalid");
        } catch (Exception ex) {
            return new DependencyHealth("DOWN", elapsedMillis(started), safeMessage(ex));
        }
    }

    private DependencyHealth redisHealth() {
        long started = System.nanoTime();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            boolean valid = "PONG".equalsIgnoreCase(pong);
            return new DependencyHealth(valid ? "UP" : "DOWN", elapsedMillis(started), valid ? null : "ping failed");
        } catch (Exception ex) {
            return new DependencyHealth("DOWN", elapsedMillis(started), safeMessage(ex));
        }
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getClass().getSimpleName();
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return message;
        }
        return message + ": " + ex.getMessage().replaceAll("(?i)(password|token|secret)=([^\\s&]+)", "$1=***");
    }

    public record HealthResponse(String status, String checkedAt, Map<String, DependencyHealth> dependencies) {
    }

    public record DependencyHealth(String status, long latencyMs, String message) {
    }
}
