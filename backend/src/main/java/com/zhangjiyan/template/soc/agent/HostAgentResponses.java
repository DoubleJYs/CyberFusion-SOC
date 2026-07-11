package com.zhangjiyan.template.soc.agent;

import java.time.LocalDateTime;
import java.util.List;

public final class HostAgentResponses {

    private HostAgentResponses() {
    }

    public record AgentRegistrationResult(
            String agentId,
            String agentToken,
            String status,
            LocalDateTime registeredAt,
            String message
    ) {
    }

    public record AgentHeartbeatResult(
            String agentId,
            String status,
            LocalDateTime acceptedAt,
            String message
    ) {
    }

    public record AgentRuntimeResult(
            String agentId,
            String action,
            String runtimeStatus,
            boolean enabled,
            boolean commandExecuted,
            String message,
            LocalDateTime operatedAt
    ) {
    }

    public record IngestResult(
            String batchId,
            String agentId,
            String ingestType,
            int accepted,
            int duplicates,
            int rejected,
            String message
    ) {
    }

    public record AgentOverview(
            AgentRuntimeEnvironment runtime,
            long totalAgents,
            long onlineAgents,
            long offlineAgents,
            long macosAgents,
            long windowsAgents,
            long linuxAgents,
            long realAssetCount,
            long events24h,
            long fim24h,
            long failedBaselines,
            long batches24h,
            long rejects24h,
            List<AgentSourceHealth> sources,
            List<SocHostAgent> agents,
            List<RecentHostEvent> recentEvents
    ) {
    }

    /**
     * Runtime facts for the backend process that is serving the Agent console.
     * Agent overview data is scoped to this operating-system family.
     */
    public record AgentRuntimeEnvironment(
            String osType,
            String label,
            String osName,
            String osVersion,
            String architecture
    ) {
    }

    public record LocalAgentInstallContext(
            AgentRuntimeEnvironment runtime,
            String hostname,
            List<String> ipAddresses,
            String defaultAgentId,
            String defaultAgentName,
            String agentVersion,
            String apiBaseUrl,
            String projectRoot,
            String envRoot,
            String fimPath,
            boolean supported,
            String message
    ) {
    }

    public record LocalAgentInstallStage(
            String label,
            String status,
            String detail
    ) {
    }

    public record LocalAgentInstallResult(
            String agentId,
            boolean installed,
            boolean verified,
            String runtimeStatus,
            List<LocalAgentInstallStage> stages,
            String message
    ) {
    }

    public record AgentDetail(
            SocHostAgent agent,
            AgentSourceHealth source,
            List<AgentBatchItem> recentBatches,
            List<RecentHostEvent> recentEvents,
            List<AgentRejectItem> recentRejects
    ) {
    }

    public record AgentSourceHealth(
            String sourceType,
            String label,
            long agentCount,
            long onlineCount,
            long assetCount,
            long eventCount24h,
            long fimCount24h,
            long failedBaselineCount,
            String status
    ) {
    }

    public record RecentHostEvent(
            Long id,
            String eventUid,
            String sourceAgentId,
            String sourceAgentName,
            String sourceType,
            String eventType,
            String severity,
            String ruleName,
            String assetName,
            String assetIp,
            String batchId,
            String status,
            LocalDateTime eventTime
    ) {
    }

    public record AgentBatchItem(
            Long id,
            String batchId,
            String agentId,
            String sourceAgentName,
            String ingestType,
            Integer itemCount,
            Integer acceptedCount,
            Integer duplicateCount,
            Integer rejectedCount,
            String status,
            LocalDateTime finishedAt,
            String errorMessage
    ) {
    }

    public record AgentRejectItem(
            Long id,
            String batchId,
            String agentId,
            String sourceAgentName,
            String ingestType,
            String eventUid,
            String reasonCode,
            String reason,
            LocalDateTime createdAt
    ) {
    }
}
