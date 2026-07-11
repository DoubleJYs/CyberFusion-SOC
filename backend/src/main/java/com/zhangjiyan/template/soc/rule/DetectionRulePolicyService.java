package com.zhangjiyan.template.soc.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DetectionRulePolicyService {

    private final SocDetectionRulePolicyMapper mapper;
    private final SocSecurityScope securityScope;

    public PageResult<SocDetectionRulePolicy> page(long pageNum, long pageSize, String sourceType,
                                                    String status, String keyword) {
        LambdaQueryWrapper<SocDetectionRulePolicy> wrapper = baseWrapper()
                .eq(hasText(sourceType), SocDetectionRulePolicy::getSourceType, normalizeSource(sourceType))
                .eq(hasText(status), SocDetectionRulePolicy::getStatus, status)
                .and(hasText(keyword), nested -> nested.like(SocDetectionRulePolicy::getRuleId, keyword)
                        .or().like(SocDetectionRulePolicy::getRuleName, keyword)
                        .or().like(SocDetectionRulePolicy::getDetectionSummary, keyword))
                .orderByDesc(SocDetectionRulePolicy::getUpdatedAt)
                .orderByAsc(SocDetectionRulePolicy::getSourceType, SocDetectionRulePolicy::getRuleId);
        return PageResult.from(mapper.selectPage(new Page<>(Math.max(1, pageNum), Math.max(1, pageSize)), wrapper));
    }

    public List<SocDetectionRulePolicy> directoryPolicies() {
        return mapper.selectList(baseWrapper()
                .orderByDesc(SocDetectionRulePolicy::getUpdatedAt)
                .orderByAsc(SocDetectionRulePolicy::getSourceType, SocDetectionRulePolicy::getRuleId));
    }

    public Map<String, SocDetectionRulePolicy> directoryPolicyMap() {
        Map<String, SocDetectionRulePolicy> policies = new LinkedHashMap<>();
        directoryPolicies().forEach(policy -> policies.put(ruleKey(policy.getSourceType(), policy.getRuleId()), policy));
        return policies;
    }

    @Transactional
    public SocDetectionRulePolicy create(DetectionRulePolicyRequest request) {
        ensureUnique(null, request.sourceType(), request.ruleId());
        SocDetectionRulePolicy policy = new SocDetectionRulePolicy();
        apply(policy, request, false);
        policy.setVersion(1);
        policy.setCreatedBy(securityScope.currentUserId());
        policy.setUpdatedBy(securityScope.currentUserId());
        mapper.insert(policy);
        return policy;
    }

    @Transactional
    public SocDetectionRulePolicy update(Long id, DetectionRulePolicyRequest request) {
        SocDetectionRulePolicy policy = requirePolicy(id);
        ensureUnique(id, request.sourceType(), request.ruleId());
        apply(policy, request, true);
        policy.setVersion((policy.getVersion() == null ? 1 : policy.getVersion()) + 1);
        policy.setUpdatedBy(securityScope.currentUserId());
        mapper.updateById(policy);
        return policy;
    }

    @Transactional
    public SocDetectionRulePolicy publish(Long id) {
        SocDetectionRulePolicy policy = requirePolicy(id);
        policy.setStatus("active");
        policy.setEnabled(1);
        policy.setApprovedBy(securityScope.currentUserId());
        policy.setApprovedAt(LocalDateTime.now());
        policy.setUpdatedBy(securityScope.currentUserId());
        policy.setVersion((policy.getVersion() == null ? 1 : policy.getVersion()) + 1);
        mapper.updateById(policy);
        return policy;
    }

    @Transactional
    public SocDetectionRulePolicy disable(Long id) {
        SocDetectionRulePolicy policy = requirePolicy(id);
        policy.setStatus("disabled");
        policy.setEnabled(0);
        policy.setUpdatedBy(securityScope.currentUserId());
        policy.setVersion((policy.getVersion() == null ? 1 : policy.getVersion()) + 1);
        mapper.updateById(policy);
        return policy;
    }

    public AlertPromotion alertPromotion(String sourceType, String ruleId, String sourceSeverity) {
        SocDetectionRulePolicy policy = mapper.selectOne(baseWrapper()
                .eq(SocDetectionRulePolicy::getSourceType, normalizeSource(sourceType))
                .eq(SocDetectionRulePolicy::getRuleId, normalizedRuleId(ruleId))
                .last("LIMIT 1"));
        if (policy == null || "draft".equals(policy.getStatus())) {
            return new AlertPromotion(true, sourceSeverity);
        }
        if (!"active".equals(policy.getStatus()) || !Objects.equals(policy.getEnabled(), 1)) {
            return new AlertPromotion(false, sourceSeverity);
        }
        return new AlertPromotion(true, policy.getSeverity());
    }

    private void apply(SocDetectionRulePolicy policy, DetectionRulePolicyRequest request, boolean keepPublishedState) {
        policy.setSourceType(normalizeSource(request.sourceType()));
        policy.setRuleId(normalizedRuleId(request.ruleId()));
        policy.setRuleName(request.ruleName().trim());
        policy.setDetectionCategory(request.detectionCategory());
        policy.setSeverity(request.severity());
        policy.setDetectionSummary(trimToNull(request.detectionSummary()));
        policy.setStatus(statusOrDefault(request.status(), keepPublishedState ? policy.getStatus() : "draft"));
        policy.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        policy.setDeleted(0);
    }

    private void ensureUnique(Long id, String sourceType, String ruleId) {
        SocDetectionRulePolicy existing = mapper.selectOne(baseWrapper()
                .eq(SocDetectionRulePolicy::getSourceType, normalizeSource(sourceType))
                .eq(SocDetectionRulePolicy::getRuleId, normalizedRuleId(ruleId))
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), id)) {
            throw new BusinessException("该来源和规则 ID 已存在配置");
        }
    }

    private SocDetectionRulePolicy requirePolicy(Long id) {
        SocDetectionRulePolicy policy = mapper.selectById(id);
        if (policy == null || Objects.equals(policy.getDeleted(), 1)) {
            throw new BusinessException("检测规则配置不存在");
        }
        return policy;
    }

    private LambdaQueryWrapper<SocDetectionRulePolicy> baseWrapper() {
        return new LambdaQueryWrapper<SocDetectionRulePolicy>().eq(SocDetectionRulePolicy::getDeleted, 0);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeSource(String sourceType) {
        return sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizedRuleId(String ruleId) {
        return ruleId == null ? "" : ruleId.trim();
    }

    private static String ruleKey(String sourceType, String ruleId) {
        return normalizeSource(sourceType) + "|" + normalizedRuleId(ruleId);
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String statusOrDefault(String status, String fallback) {
        return hasText(status) ? status : fallback;
    }

    public record AlertPromotion(boolean enabled, String severity) {
    }
}
