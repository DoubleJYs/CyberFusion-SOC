package com.zhangjiyan.template.soc.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.soc.agent.HostAgentLocalInstallRequest;
import com.zhangjiyan.template.soc.agent.HostAgentLocalInstallService;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRuntimeResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.LocalAgentInstallContext;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.LocalAgentInstallResult;
import com.zhangjiyan.template.soc.agent.HostAgentRuntimeRequest;
import com.zhangjiyan.template.soc.agent.HostAgentRuntimeService;
import com.zhangjiyan.template.soc.agent.SocHostAgent;
import com.zhangjiyan.template.soc.agent.SocHostAgentMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheck;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheckMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEvent;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** Minimal user-facing protection status. It never exposes another user's host controls or token material. */
@Service
@RequiredArgsConstructor
public class ClientProtectionService {

    private final SocSecurityScope securityScope;
    private final SocAssetMapper assetMapper;
    private final SocHostAgentMapper agentMapper;
    private final SocExternalEventMapper eventMapper;
    private final SocFileIntegrityEventMapper fimMapper;
    private final SocBaselineCheckMapper baselineMapper;
    private final HostAgentLocalInstallService localInstallService;
    private final HostAgentRuntimeService runtimeService;

    public ClientProtectionStatus status(String assetIp) {
        SocAsset asset = resolveAsset(assetIp);
        if (asset == null) {
            return ClientProtectionStatus.empty();
        }
        SocHostAgent agent = agentFor(asset);
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long events24h = eventMapper.selectCount(scopedEventQuery(asset.getIp()).ge(SocExternalEvent::getEventTime, since));
        long fim24h = fimMapper.selectCount(scopedFimQuery(asset.getIp()).ge(SocFileIntegrityEvent::getEventTime, since));
        long baselineFailures = baselineMapper.selectCount(scopedBaselineQuery(asset.getIp())
                .in(SocBaselineCheck::getResult, List.of("failed", "fail", "error")));
        boolean controllable = runtimeService.isControllable(agent);
        return new ClientProtectionStatus(asset.getIp(), asset.getHostname(), asset.getOsType(),
                agent == null ? "not_installed" : agent.getStatus(),
                agent == null ? null : agent.getAgentName(),
                agent == null ? null : agent.getLastSeenAt(),
                controllable,
                agent == null ? 0 : safe(agent.getQueueDepth()),
                agent == null ? 0L : safe(agent.getQueueBytes()),
                events24h, fim24h, baselineFailures);
    }

    public LocalAgentInstallContext installContext(String assetIp) {
        SocAsset asset = requireLocalAsset(assetIp);
        LocalAgentInstallContext context = localInstallService.context();
        SocHostAgent agent = agentFor(asset);
        return new LocalAgentInstallContext(context.runtime(), context.hostname(), context.ipAddresses(),
                agent == null ? context.defaultAgentId() : agent.getAgentId(),
                agent == null ? context.defaultAgentName() : agent.getAgentName(), context.agentVersion(),
                context.apiBaseUrl(), context.projectRoot(), context.envRoot(), context.fimPath(), context.supported(), context.message());
    }

    public LocalAgentInstallResult install(ClientProtectionInstallRequest request, String clientIp) {
        SocAsset asset = requireLocalAsset(request.assetIp());
        LocalAgentInstallContext context = localInstallService.context();
        SocHostAgent agent = agentFor(asset);
        String agentId = agent == null ? context.defaultAgentId() : agent.getAgentId();
        String agentName = agent == null ? asset.getHostname() + " 本机保护" : agent.getAgentName();
        return localInstallService.installForOwner(new HostAgentLocalInstallRequest(
                        agentId, agentName, context.hostname(), request.agentVersion(), request.profile(),
                        context.ipAddresses(), request.fimPath(), request.interval()),
                clientIp, asset.getOwnerId(), asset.getDeptId());
    }

    public AgentRuntimeResult control(String assetIp, String action) {
        SocAsset asset = requireLocalAsset(assetIp);
        SocHostAgent agent = agentFor(asset);
        if (agent == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前电脑尚未安装 Agent。");
        }
        return runtimeService.control(agent.getId(), new HostAgentRuntimeRequest(action));
    }

    private SocAsset resolveAsset(String assetIp) {
        LambdaQueryWrapper<SocAsset> query = new LambdaQueryWrapper<SocAsset>().eq(SocAsset::getDeleted, 0);
        if (assetIp != null && !assetIp.isBlank()) {
            query.eq(SocAsset::getIp, assetIp.trim());
        }
        securityScope.applyDataScope(query, SocAsset::getOwnerId, SocAsset::getDeptId);
        return assetMapper.selectList(query.orderByDesc(SocAsset::getLastSeenAt)).stream().findFirst().orElse(null);
    }

    private SocAsset requireLocalAsset(String assetIp) {
        SocAsset asset = resolveAsset(assetIp);
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前账号没有该电脑的访问权限。");
        }
        LocalAgentInstallContext context = localInstallService.context();
        boolean sameHost = asset.getHostname() != null && asset.getHostname().equalsIgnoreCase(context.hostname());
        boolean sameAddress = asset.getIp() != null && context.ipAddresses().contains(asset.getIp());
        if (!sameHost && !sameAddress) {
            throw new BusinessException("页面安装仅支持当前后端所在主机，远程电脑请在该电脑上打开我的电脑安全助手。");
        }
        return asset;
    }

    private SocHostAgent agentFor(SocAsset asset) {
        LambdaQueryWrapper<SocHostAgent> agentQuery = new LambdaQueryWrapper<SocHostAgent>()
                .eq(SocHostAgent::getDeleted, 0)
                .and(w -> w.eq(SocHostAgent::getHostname, asset.getHostname()).or().eq(SocHostAgent::getLastIp, asset.getIp()))
                .orderByDesc(SocHostAgent::getLastSeenAt);
        securityScope.applyDataScope(agentQuery, SocHostAgent::getOwnerId, SocHostAgent::getDeptId);
        return agentMapper.selectList(agentQuery).stream().findFirst().orElse(null);
    }

    private LambdaQueryWrapper<SocExternalEvent> scopedEventQuery(String assetIp) {
        LambdaQueryWrapper<SocExternalEvent> query = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0).eq(SocExternalEvent::getAssetIp, assetIp);
        securityScope.applyDataScope(query, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        return query;
    }

    private LambdaQueryWrapper<SocFileIntegrityEvent> scopedFimQuery(String assetIp) {
        LambdaQueryWrapper<SocFileIntegrityEvent> query = new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .eq(SocFileIntegrityEvent::getDeleted, 0).eq(SocFileIntegrityEvent::getAssetIp, assetIp);
        securityScope.applyDataScope(query, SocFileIntegrityEvent::getOwnerId, SocFileIntegrityEvent::getDeptId);
        return query;
    }

    private LambdaQueryWrapper<SocBaselineCheck> scopedBaselineQuery(String assetIp) {
        LambdaQueryWrapper<SocBaselineCheck> query = new LambdaQueryWrapper<SocBaselineCheck>()
                .eq(SocBaselineCheck::getDeleted, 0).eq(SocBaselineCheck::getAssetIp, assetIp);
        securityScope.applyDataScope(query, SocBaselineCheck::getOwnerId, SocBaselineCheck::getDeptId);
        return query;
    }

    private int safe(Integer value) { return value == null ? 0 : value; }
    private long safe(Long value) { return value == null ? 0L : value; }

    public record ClientProtectionStatus(String assetIp, String hostname, String osType, String agentStatus,
                                         String agentName, LocalDateTime lastHeartbeatAt, boolean agentControllable,
                                         int queueDepth,
                                         long queueBytes, long event24hCount, long fim24hCount,
                                         long baselineFailureCount) {
        static ClientProtectionStatus empty() {
            return new ClientProtectionStatus(null, null, null, "no_device", null, null, false, 0, 0L, 0, 0, 0);
        }
    }
}
