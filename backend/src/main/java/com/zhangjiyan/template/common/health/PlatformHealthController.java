package com.zhangjiyan.template.common.health;

import com.zhangjiyan.template.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/health")
public class PlatformHealthController {

    private static final List<String> REQUIRED_TABLES = List.of(
            "sys_user",
            "sys_menu",
            "sys_role_menu",
            "soc_asset",
            "soc_external_event",
            "soc_alert",
            "soc_ticket",
            "soc_report",
            "soc_local_check_command",
            "soc_event_adapter_profile",
            "soc_correlation_rule",
            "soc_incident_cluster",
            "soc_incident_evidence",
            "soc_fim_watch_path"
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;

    @GetMapping("/liveness")
    public ApiResult<HealthResponse> liveness() {
        return ApiResult.ok(new HealthResponse("UP", OffsetDateTime.now().toString(), appVersion(), activeProfiles(), Map.of(
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
        dependencies.put("schema", schemaHealth());
        dependencies.put("seed", seedHealth());
        dependencies.put("redis", redisHealth());
        String status = dependencies.values().stream().allMatch(item -> "UP".equals(item.status())) ? "UP" : "DOWN";
        return ApiResult.ok(new HealthResponse(status, OffsetDateTime.now().toString(), appVersion(), activeProfiles(), dependencies));
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

    private DependencyHealth schemaHealth() {
        long started = System.nanoTime();
        try {
            List<String> existing = jdbcTemplate.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name IN (%s)
                    """.formatted("'"+ String.join("','", REQUIRED_TABLES) + "'"), String.class);
            List<String> missing = REQUIRED_TABLES.stream()
                    .filter(table -> !existing.contains(table))
                    .toList();
            if (missing.isEmpty()) {
                return new DependencyHealth("UP", elapsedMillis(started), "required tables present: " + REQUIRED_TABLES.size());
            }
            return new DependencyHealth("DOWN", elapsedMillis(started), "missing required tables: " + String.join(", ", missing));
        } catch (Exception ex) {
            return new DependencyHealth("DOWN", elapsedMillis(started), safeMessage(ex));
        }
    }

    private DependencyHealth seedHealth() {
        long started = System.nanoTime();
        try {
            Integer adminCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys_user
                    WHERE username = 'admin'
                      AND status = 1
                    """, Integer.class);
            Integer menuCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys_menu
                    WHERE path IN ('/soc/policies', '/soc/incidents', '/soc/reports')
                      AND status = 1
                    """, Integer.class);
            Integer permissionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys_menu
                    WHERE permission IN ('soc:policy:list', 'soc:incident:list', 'soc:correlation-rule:list')
                      AND status = 1
                    """, Integer.class);
            if (value(adminCount) > 0 && value(menuCount) >= 3 && value(permissionCount) >= 3) {
                return new DependencyHealth("UP", elapsedMillis(started), "admin user, SOC menus, and policy/incident permissions present");
            }
            return new DependencyHealth(
                    "DOWN",
                    elapsedMillis(started),
                    "seed incomplete: admin=" + value(adminCount) + ", menu=" + value(menuCount) + "/3, permission=" + value(permissionCount) + "/3"
            );
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

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private String appVersion() {
        Package appPackage = PlatformHealthController.class.getPackage();
        String implementationVersion = appPackage == null ? null : appPackage.getImplementationVersion();
        if (implementationVersion == null || implementationVersion.isBlank()) {
            return "dev";
        }
        return implementationVersion;
    }

    private String activeProfiles() {
        String profiles = String.join(",", Arrays.stream(environment.getActiveProfiles()).toList());
        if (profiles.isBlank()) {
            return "default";
        }
        return profiles;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getClass().getSimpleName();
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return message;
        }
        return message + ": " + ex.getMessage().replaceAll("(?i)(password|token|secret)=([^\\s&]+)", "$1=***");
    }

    public record HealthResponse(String status, String checkedAt, String version, String profile, Map<String, DependencyHealth> dependencies) {
    }

    public record DependencyHealth(String status, long latencyMs, String message) {
    }
}
