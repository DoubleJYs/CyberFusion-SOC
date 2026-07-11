package com.zhangjiyan.template.soc.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.agent.SocHostAgent;
import com.zhangjiyan.template.soc.agent.SocHostAgentMapper;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheck;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheckMapper;
import com.zhangjiyan.template.soc.correlation.SocIncidentCluster;
import com.zhangjiyan.template.soc.correlation.SocIncidentClusterMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEvent;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEventMapper;
import com.zhangjiyan.template.soc.report.SocReport;
import com.zhangjiyan.template.soc.report.SocReportMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import com.zhangjiyan.template.system.role.SysUserRole;
import com.zhangjiyan.template.system.role.SysUserRoleMapper;
import com.zhangjiyan.template.system.user.SysUser;
import com.zhangjiyan.template.system.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Aggregates operational data by the user who owns the protected host. */
@Service
@RequiredArgsConstructor
public class SocUserWorkspaceService {

    private static final long EMPLOYEE_ROLE_ID = 10L;
    private static final List<String> NON_WORKSPACE_SOURCES = List.of("demo", "mock", "local-demo-client", "fixture");

    private final SocSecurityScope securityScope;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SocAssetMapper assetMapper;
    private final SocHostAgentMapper agentMapper;
    private final SocAlertMapper alertMapper;
    private final SocIncidentClusterMapper incidentMapper;
    private final SocFileIntegrityEventMapper fimMapper;
    private final SocVulnerabilityMapper vulnerabilityMapper;
    private final SocBaselineCheckMapper baselineMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocTicketMapper ticketMapper;
    private final SocReportMapper reportMapper;

    public List<UserWorkspaceCard> cards() {
        Collection<Long> ownerIds = visibleOwnerIds();
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, ownerIds).eq(SysUser::getStatus, 1).eq(SysUser::getDeleted, 0)
                .orderByAsc(SysUser::getId));
        return users.stream().map(user -> cardFor(user, since))
                .filter(UserWorkspaceCard::hasManagedHost)
                .toList();
    }

    private Collection<Long> visibleOwnerIds() {
        if (!securityScope.canViewAllData()) {
            Long current = securityScope.currentUserId();
            return current == null ? List.of() : List.of(current);
        }
        Set<Long> employeeIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, EMPLOYEE_ROLE_ID).eq(SysUserRole::getDeleted, 0))
                .stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
        return employeeIds;
    }

    private UserWorkspaceCard cardFor(SysUser user, LocalDateTime since) {
        List<SocAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getOwnerId, user.getId()).eq(SocAsset::getDeleted, 0)
                .isNotNull(SocAsset::getSourceType).notIn(SocAsset::getSourceType, NON_WORKSPACE_SOURCES));
        List<SocHostAgent> agents = agentMapper.selectList(new LambdaQueryWrapper<SocHostAgent>()
                .eq(SocHostAgent::getOwnerId, user.getId()).eq(SocHostAgent::getDeleted, 0));
        long onlineAgents = agents.stream().filter(agent -> "online".equalsIgnoreCase(agent.getStatus())).count();
        long openAlerts = count(alertMapper.selectCount(new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getOwnerId, user.getId()).eq(SocAlert::getDeleted, 0)
                .isNotNull(SocAlert::getSourceType).notIn(SocAlert::getSourceType, NON_WORKSPACE_SOURCES)
                .notIn(SocAlert::getStatus, List.of("closed", "ignored"))));
        long highAlerts = count(alertMapper.selectCount(new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getOwnerId, user.getId()).eq(SocAlert::getDeleted, 0)
                .isNotNull(SocAlert::getSourceType).notIn(SocAlert::getSourceType, NON_WORKSPACE_SOURCES)
                .in(SocAlert::getSeverity, List.of("critical", "high", "严重", "高危"))
                .notIn(SocAlert::getStatus, List.of("closed", "ignored"))));
        long incidents = count(incidentMapper.selectCount(new LambdaQueryWrapper<SocIncidentCluster>()
                .eq(SocIncidentCluster::getOwnerId, user.getId()).eq(SocIncidentCluster::getDeleted, 0)
                .notIn(SocIncidentCluster::getStatus, List.of("closed", "archived"))));
        long highIncidents = count(incidentMapper.selectCount(new LambdaQueryWrapper<SocIncidentCluster>()
                .eq(SocIncidentCluster::getOwnerId, user.getId()).eq(SocIncidentCluster::getDeleted, 0)
                .in(SocIncidentCluster::getSeverity, List.of("critical", "high", "严重", "高危"))
                .notIn(SocIncidentCluster::getStatus, List.of("closed", "archived"))));
        long highRiskAssets = assets.stream().filter(asset -> List.of("critical", "high", "严重", "高危")
                .contains(asset.getRiskLevel())).count();
        long openVulnerabilities = count(vulnerabilityMapper.selectCount(new LambdaQueryWrapper<SocVulnerability>()
                .eq(SocVulnerability::getOwnerId, user.getId()).eq(SocVulnerability::getDeleted, 0)
                .notIn(SocVulnerability::getStatus, List.of("fixed", "closed", "ignored"))));
        long highVulnerabilities = count(vulnerabilityMapper.selectCount(new LambdaQueryWrapper<SocVulnerability>()
                .eq(SocVulnerability::getOwnerId, user.getId()).eq(SocVulnerability::getDeleted, 0)
                .in(SocVulnerability::getSeverity, List.of("critical", "high", "严重", "高危"))
                .notIn(SocVulnerability::getStatus, List.of("fixed", "closed", "ignored"))));
        long baselineCount = count(baselineMapper.selectCount(new LambdaQueryWrapper<SocBaselineCheck>()
                .eq(SocBaselineCheck::getOwnerId, user.getId()).eq(SocBaselineCheck::getDeleted, 0)));
        long failedBaselines = count(baselineMapper.selectCount(new LambdaQueryWrapper<SocBaselineCheck>()
                .eq(SocBaselineCheck::getOwnerId, user.getId()).eq(SocBaselineCheck::getDeleted, 0)
                .in(SocBaselineCheck::getResult, List.of("failed", "fail", "异常"))));
        long fimCount = count(fimMapper.selectCount(new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .eq(SocFileIntegrityEvent::getOwnerId, user.getId()).eq(SocFileIntegrityEvent::getDeleted, 0)
                .isNotNull(SocFileIntegrityEvent::getSourceType).notIn(SocFileIntegrityEvent::getSourceType, NON_WORKSPACE_SOURCES)));
        long fim24h = count(fimMapper.selectCount(new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .eq(SocFileIntegrityEvent::getOwnerId, user.getId()).eq(SocFileIntegrityEvent::getDeleted, 0)
                .isNotNull(SocFileIntegrityEvent::getSourceType).notIn(SocFileIntegrityEvent::getSourceType, NON_WORKSPACE_SOURCES)
                .ge(SocFileIntegrityEvent::getEventTime, since)));
        long externalEvents = count(externalEventMapper.selectCount(new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getOwnerId, user.getId()).eq(SocExternalEvent::getDeleted, 0)
                .isNotNull(SocExternalEvent::getSourceType).notIn(SocExternalEvent::getSourceType, NON_WORKSPACE_SOURCES)));
        long highExternalEvents = count(externalEventMapper.selectCount(new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getOwnerId, user.getId()).eq(SocExternalEvent::getDeleted, 0)
                .isNotNull(SocExternalEvent::getSourceType).notIn(SocExternalEvent::getSourceType, NON_WORKSPACE_SOURCES)
                .in(SocExternalEvent::getSeverity, List.of("critical", "high", "严重", "高危"))));
        long ticketCount = count(ticketMapper.selectCount(new LambdaQueryWrapper<SocTicket>()
                .eq(SocTicket::getOwnerId, user.getId()).eq(SocTicket::getDeleted, 0)));
        long openTickets = count(ticketMapper.selectCount(new LambdaQueryWrapper<SocTicket>()
                .eq(SocTicket::getOwnerId, user.getId()).eq(SocTicket::getDeleted, 0)
                .notIn(SocTicket::getStatus, List.of("closed", "archived"))));
        long overdueTickets = count(ticketMapper.selectCount(new LambdaQueryWrapper<SocTicket>()
                .eq(SocTicket::getOwnerId, user.getId()).eq(SocTicket::getDeleted, 0)
                .lt(SocTicket::getDueAt, LocalDateTime.now())
                .notIn(SocTicket::getStatus, List.of("closed", "archived"))));
        long reportCount = count(reportMapper.selectCount(new LambdaQueryWrapper<SocReport>()
                .eq(SocReport::getOwnerId, user.getId()).eq(SocReport::getDeleted, 0)));
        long reports24h = count(reportMapper.selectCount(new LambdaQueryWrapper<SocReport>()
                .eq(SocReport::getOwnerId, user.getId()).eq(SocReport::getDeleted, 0)
                .ge(SocReport::getGeneratedAt, since)));
        String dataMode = !assets.isEmpty() && assets.stream().allMatch(asset -> "validation-fixture".equalsIgnoreCase(asset.getSourceType()))
                ? "validation" : "real";
        return new UserWorkspaceCard(user.getId(), user.getUsername(), user.getNickname(), user.getDeptId(),
                dataMode, assets.size(), highRiskAssets, agents.size(), onlineAgents,
                openAlerts, highAlerts, incidents, highIncidents,
                openVulnerabilities, highVulnerabilities, baselineCount, failedBaselines,
                fimCount, fim24h, externalEvents, highExternalEvents,
                ticketCount, openTickets, overdueTickets, reportCount, reports24h);
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    public record UserWorkspaceCard(Long ownerId, String username, String nickname, Long deptId, String dataMode,
                                    long assetCount, long highRiskAssetCount, int agentCount, long onlineAgentCount,
                                    long openAlertCount, long highAlertCount,
                                    long openIncidentCount, long highIncidentCount,
                                    long openVulnerabilityCount, long highVulnerabilityCount,
                                    long baselineCount, long failedBaselineCount,
                                    long fimCount, long fim24hCount,
                                    long externalEventCount, long highExternalEventCount,
                                    long ticketCount, long openTicketCount, long overdueTicketCount,
                                    long reportCount, long reports24hCount) {
        boolean hasManagedHost() {
            return assetCount > 0 || agentCount > 0;
        }
    }
}
