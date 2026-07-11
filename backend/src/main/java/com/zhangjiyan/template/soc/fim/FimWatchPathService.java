package com.zhangjiyan.template.soc.fim;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.agent.SocHostAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FimWatchPathService {

    private final SocFimWatchPathMapper mapper;
    private final SocSecurityScope securityScope;

    public PageResult<SocFimWatchPath> page(long pageNum, long pageSize, String osType, String hostName, String status, String keyword) {
        LambdaQueryWrapper<SocFimWatchPath> wrapper = baseWrapper()
                .eq(hasText(osType), SocFimWatchPath::getOsType, normalizeOs(osType))
                .eq(hasText(hostName), SocFimWatchPath::getHostName, trimToEmpty(hostName))
                .eq(hasText(status), SocFimWatchPath::getStatus, status)
                .and(hasText(keyword), nested -> nested.like(SocFimWatchPath::getDisplayName, keyword)
                        .or().like(SocFimWatchPath::getWatchPath, keyword)
                        .or().like(SocFimWatchPath::getHostName, keyword))
                .orderByDesc(SocFimWatchPath::getUpdatedAt)
                .orderByAsc(SocFimWatchPath::getWatchPath);
        return PageResult.from(mapper.selectPage(new Page<>(Math.max(1, pageNum), Math.max(1, pageSize)), wrapper));
    }

    @Transactional
    public SocFimWatchPath create(FimWatchPathRequest request) {
        ensureUnique(null, request.hostName(), request.watchPath());
        SocFimWatchPath item = new SocFimWatchPath();
        apply(item, request, false);
        item.setVersion(1);
        item.setCreatedBy(securityScope.currentUserId());
        item.setUpdatedBy(securityScope.currentUserId());
        mapper.insert(item);
        return item;
    }

    @Transactional
    public SocFimWatchPath update(Long id, FimWatchPathRequest request) {
        SocFimWatchPath item = requireItem(id);
        ensureUnique(id, request.hostName(), request.watchPath());
        apply(item, request, true);
        item.setVersion((item.getVersion() == null ? 1 : item.getVersion()) + 1);
        item.setUpdatedBy(securityScope.currentUserId());
        mapper.updateById(item);
        return item;
    }

    @Transactional
    public SocFimWatchPath publish(Long id) {
        SocFimWatchPath item = requireItem(id);
        item.setStatus("active");
        item.setEnabled(1);
        item.setApprovedBy(securityScope.currentUserId());
        item.setApprovedAt(LocalDateTime.now());
        item.setUpdatedBy(securityScope.currentUserId());
        item.setVersion((item.getVersion() == null ? 1 : item.getVersion()) + 1);
        mapper.updateById(item);
        return item;
    }

    @Transactional
    public SocFimWatchPath disable(Long id) {
        SocFimWatchPath item = requireItem(id);
        item.setStatus("disabled");
        item.setEnabled(0);
        item.setUpdatedBy(securityScope.currentUserId());
        item.setVersion((item.getVersion() == null ? 1 : item.getVersion()) + 1);
        mapper.updateById(item);
        return item;
    }

    public List<AgentWatchPath> activeFor(SocHostAgent agent, String osType) {
        String normalizedOs = normalizeOs(osType);
        if (!normalizedOs.equals(normalizeOs(agent.getOsType()))) {
            throw new BusinessException("Agent operating system does not match requested FIM policy");
        }
        return mapper.selectList(baseWrapper()
                        .eq(SocFimWatchPath::getOsType, normalizedOs)
                        .eq(SocFimWatchPath::getHostName, agent.getHostname())
                        .eq(SocFimWatchPath::getStatus, "active")
                        .eq(SocFimWatchPath::getEnabled, 1)
                        .orderByAsc(SocFimWatchPath::getWatchPath))
                .stream()
                .map(item -> new AgentWatchPath(item.getId(), item.getDisplayName(), item.getWatchPath(), item.getPurpose(),
                        Integer.valueOf(1).equals(item.getRecursive()), boundedMaxEntries(item.getMaxEntries()), item.getVersion()))
                .toList();
    }

    private void apply(SocFimWatchPath item, FimWatchPathRequest request, boolean keepPublishedState) {
        String path = normalizeAndValidatePath(request.watchPath(), request.osType());
        item.setDisplayName(request.displayName().trim());
        item.setHostName(request.hostName().trim());
        item.setOsType(normalizeOs(request.osType()));
        item.setWatchPath(path);
        item.setPurpose(request.purpose());
        item.setRecursive(Boolean.FALSE.equals(request.recursive()) ? 0 : 1);
        item.setMaxEntries(boundedMaxEntries(request.maxEntries()));
        item.setStatus(hasText(request.status()) ? request.status() : (keepPublishedState ? item.getStatus() : "draft"));
        item.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        item.setDeleted(0);
    }

    private void ensureUnique(Long id, String hostName, String watchPath) {
        SocFimWatchPath existing = mapper.selectOne(baseWrapper()
                .eq(SocFimWatchPath::getHostName, hostName.trim())
                .eq(SocFimWatchPath::getWatchPath, normalizePathSeparators(watchPath))
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), id)) {
            throw new BusinessException("该主机目录已存在文件监控授权");
        }
    }

    private SocFimWatchPath requireItem(Long id) {
        SocFimWatchPath item = mapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BusinessException("文件监控授权不存在");
        }
        return item;
    }

    private static String normalizeAndValidatePath(String value, String osType) {
        String path = normalizePathSeparators(value);
        if (path.isBlank() || path.contains("..") || path.contains("*") || path.contains("?")) {
            throw new BusinessException("监控目录必须是明确的绝对路径，不能包含路径穿越或通配符");
        }
        boolean windows = "windows".equals(normalizeOs(osType));
        boolean absolute = windows ? path.matches("^[A-Za-z]:/.*") : path.startsWith("/");
        if (!absolute || isRootPath(path, windows)) {
            throw new BusinessException("不允许授权文件系统根目录；请仅选择需要审计的具体目录");
        }
        return path;
    }

    private static boolean isRootPath(String path, boolean windows) {
        return "/".equals(path) || (windows && path.matches("^[A-Za-z]:/$"));
    }

    private static String normalizePathSeparators(String path) {
        return path == null ? "" : path.trim().replace('\\', '/').replaceAll("/+$", "");
    }

    private static int boundedMaxEntries(Integer value) {
        return Math.min(2000, Math.max(1, value == null ? 500 : value));
    }

    private LambdaQueryWrapper<SocFimWatchPath> baseWrapper() {
        return new LambdaQueryWrapper<SocFimWatchPath>().eq(SocFimWatchPath::getDeleted, 0);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeOs(String osType) {
        return osType == null ? "" : osType.trim().toLowerCase(Locale.ROOT);
    }

    public record AgentWatchPath(Long id, String displayName, String watchPath, String purpose,
                                 boolean recursive, int maxEntries, Integer version) {
    }
}
