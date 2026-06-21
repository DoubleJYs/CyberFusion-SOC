package com.zhangjiyan.template.soc.settings;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.notification.SocNotificationChannel;
import com.zhangjiyan.template.soc.notification.SocNotificationLog;
import com.zhangjiyan.template.soc.notification.SocNotificationService;
import com.zhangjiyan.template.soc.wazuh.WazuhClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "SOC 系统配置", description = "Wazuh 连接配置、同步任务和连接检查")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/settings")
public class SocSettingsController {

    private final SocOperationService service;
    private final WazuhClient wazuhClient;
    private final SocNotificationService notificationService;

    @Operation(summary = "Wazuh 连接配置列表")
    @GetMapping("/wazuh-configs")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:settings:view')")
    public ApiResult<List<SocWazuhConfig>> wazuhConfigs() {
        return ApiResult.ok(service.wazuhConfigs());
    }

    @Operation(summary = "同步任务列表")
    @GetMapping("/sync-tasks")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:settings:view')")
    public ApiResult<List<SocSyncTask>> syncTasks() {
        return ApiResult.ok(service.syncTasks());
    }

    @Operation(summary = "Wazuh 连接检查")
    @GetMapping("/wazuh-health")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:settings:wazuh')")
    public ApiResult<Map<String, Object>> wazuhHealth() {
        return ApiResult.ok(Map.of(
                "configured", wazuhClient.configured(),
                "manager", wazuhClient.managerHealth(),
                "indexer", wazuhClient.indexerHealth()
        ));
    }

    @Operation(summary = "通知通道列表")
    @GetMapping("/notification-channels")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:settings:view')")
    public ApiResult<List<SocNotificationChannel>> notificationChannels() {
        return ApiResult.ok(notificationService.channels());
    }

    @Operation(summary = "通知发送日志")
    @GetMapping("/notification-logs")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:settings:view')")
    public ApiResult<PageResult<SocNotificationLog>> notificationLogs(@Valid SocPageRequest request) {
        return ApiResult.ok(notificationService.logs(request));
    }

    @Operation(summary = "测试通知通道")
    @PostMapping("/notification-channels/{id}/test")
    @OperationAudit("SOC_NOTIFICATION.TEST")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:settings:notify-test')")
    public ApiResult<SocNotificationLog> testNotification(@PathVariable Long id) {
        return ApiResult.ok(notificationService.test(id));
    }
}
