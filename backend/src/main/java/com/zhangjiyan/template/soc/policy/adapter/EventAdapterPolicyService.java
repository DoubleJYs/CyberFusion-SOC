package com.zhangjiyan.template.soc.policy.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EventAdapterPolicyService {

    private static final Set<String> SUPPORTED_SOURCES = Set.of("waf", "zap", "trivy", "wazuh", "suricata", "zeek");
    private static final Set<String> TRANSFORMS = Set.of("direct", "string", "number", "timestamp", "lowercase", "uppercase", "join", "first_non_empty");
    private static final Set<String> SEVERITIES = Set.of("critical", "high", "medium", "low", "info");
    private static final Pattern FIELD_PATH = Pattern.compile("[A-Za-z0-9_@#.-]+(,[A-Za-z0-9_@#.-]+)*");
    private static final Pattern NORMALIZED_FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern TEMPLATE_FIELD = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final SocEventAdapterProfileMapper profileMapper;
    private final SocEventFieldMappingMapper fieldMappingMapper;
    private final SocEventSeverityMappingMapper severityMappingMapper;
    private final SocEventAlertLinkRuleMapper alertLinkRuleMapper;
    private final ObjectMapper objectMapper;
    private final SocSecurityScope securityScope;

    public PageResult<SocEventAdapterProfile> page(long pageNum, long pageSize, String sourceType, String status, String keyword) {
        LambdaQueryWrapper<SocEventAdapterProfile> wrapper = baseProfileWrapper();
        if (hasText(sourceType)) {
            wrapper.eq(SocEventAdapterProfile::getSourceType, normalizeSourceType(sourceType));
        }
        if (hasText(status)) {
            wrapper.eq(SocEventAdapterProfile::getStatus, status);
        }
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(SocEventAdapterProfile::getSourceType, keyword)
                    .or().like(SocEventAdapterProfile::getDisplayName, keyword)
                    .or().like(SocEventAdapterProfile::getDescription, keyword));
        }
        wrapper.orderByAsc(SocEventAdapterProfile::getSortOrder, SocEventAdapterProfile::getId);
        return PageResult.from(profileMapper.selectPage(new Page<>(Math.max(1, pageNum), Math.max(1, pageSize)), wrapper));
    }

    public SocEventAdapterProfile detail(Long id) {
        return requireProfile(id);
    }

    @Transactional
    public SocEventAdapterProfile create(EventAdapterProfileRequest request) {
        SocEventAdapterProfile profile = new SocEventAdapterProfile();
        applyProfile(profile, request, false);
        profile.setVersion(1);
        profile.setCreatedBy(securityScope.currentUserId());
        profile.setUpdatedBy(securityScope.currentUserId());
        profileMapper.insert(profile);
        return profile;
    }

    @Transactional
    public SocEventAdapterProfile update(Long id, EventAdapterProfileRequest request) {
        SocEventAdapterProfile profile = requireProfile(id);
        applyProfile(profile, request, true);
        profile.setVersion((profile.getVersion() == null ? 1 : profile.getVersion()) + 1);
        profile.setUpdatedBy(securityScope.currentUserId());
        profileMapper.updateById(profile);
        return profile;
    }

    @Transactional
    public SocEventAdapterProfile publish(Long id) {
        AdapterValidationResult validation = validateExisting(id);
        if (!validation.passed()) {
            throw new BusinessException(validation.message() + ": " + String.join("; ", validation.errors()));
        }
        SocEventAdapterProfile profile = requireProfile(id);
        profile.setStatus("active");
        profile.setEnabled(1);
        profile.setApprovedBy(securityScope.currentUserId());
        profile.setApprovedAt(LocalDateTime.now());
        profile.setUpdatedBy(securityScope.currentUserId());
        profile.setVersion((profile.getVersion() == null ? 1 : profile.getVersion()) + 1);
        profileMapper.updateById(profile);
        return profile;
    }

    @Transactional
    public SocEventAdapterProfile disable(Long id) {
        SocEventAdapterProfile profile = requireProfile(id);
        profile.setStatus("disabled");
        profile.setEnabled(0);
        profile.setUpdatedBy(securityScope.currentUserId());
        profileMapper.updateById(profile);
        return profile;
    }

    public AdapterValidationResult validateExisting(Long id) {
        SocEventAdapterProfile profile = requireProfile(id);
        AdapterBundle bundle = mappings(id);
        List<String> errors = validateBundle(profile, bundle);
        return new AdapterValidationResult(errors.isEmpty(), errors.isEmpty() ? "事件适配映射校验通过" : "事件适配映射校验失败", errors);
    }

    public AdapterBundle mappings(Long adapterId) {
        requireProfile(adapterId);
        List<SocEventFieldMapping> fieldMappings = fieldMappingMapper.selectList(new LambdaQueryWrapper<SocEventFieldMapping>()
                .eq(SocEventFieldMapping::getAdapterId, adapterId)
                .orderByAsc(SocEventFieldMapping::getSortOrder, SocEventFieldMapping::getId));
        List<SocEventSeverityMapping> severityMappings = severityMappingMapper.selectList(new LambdaQueryWrapper<SocEventSeverityMapping>()
                .eq(SocEventSeverityMapping::getAdapterId, adapterId)
                .orderByAsc(SocEventSeverityMapping::getId));
        List<SocEventAlertLinkRule> alertLinkRules = alertLinkRuleMapper.selectList(new LambdaQueryWrapper<SocEventAlertLinkRule>()
                .eq(SocEventAlertLinkRule::getAdapterId, adapterId)
                .orderByAsc(SocEventAlertLinkRule::getId));
        return new AdapterBundle(nonNull(fieldMappings), nonNull(severityMappings), nonNull(alertLinkRules));
    }

    @Transactional
    public AdapterBundle updateMappings(Long adapterId, EventAdapterMappingsRequest request) {
        requireProfile(adapterId);
        fieldMappingMapper.delete(new QueryWrapper<SocEventFieldMapping>().eq("adapter_id", adapterId));
        severityMappingMapper.delete(new QueryWrapper<SocEventSeverityMapping>().eq("adapter_id", adapterId));
        alertLinkRuleMapper.delete(new QueryWrapper<SocEventAlertLinkRule>().eq("adapter_id", adapterId));
        if (request.fieldMappings() != null) {
            for (EventAdapterMappingsRequest.FieldMappingRequest item : request.fieldMappings()) {
                SocEventFieldMapping row = new SocEventFieldMapping();
                row.setAdapterId(adapterId);
                row.setSourceFieldPath(item.sourceFieldPath());
                row.setNormalizedField(item.normalizedField());
                row.setRequired(Boolean.TRUE.equals(item.required()) ? 1 : 0);
                row.setTransformType(item.transformType());
                row.setDefaultValue(item.defaultValue());
                row.setExampleValue(item.exampleValue());
                row.setSortOrder(item.sortOrder() == null ? 100 : item.sortOrder());
                row.setEnabled(Boolean.FALSE.equals(item.enabled()) ? 0 : 1);
                fieldMappingMapper.insert(row);
            }
        }
        if (request.severityMappings() != null) {
            for (EventAdapterMappingsRequest.SeverityMappingRequest item : request.severityMappings()) {
                SocEventSeverityMapping row = new SocEventSeverityMapping();
                row.setAdapterId(adapterId);
                row.setSourceValue(item.sourceValue());
                row.setNormalizedSeverity(item.normalizedSeverity());
                row.setRiskScore(item.riskScore() == null ? severityWeight(item.normalizedSeverity()) * 20 : item.riskScore());
                row.setEnabled(Boolean.FALSE.equals(item.enabled()) ? 0 : 1);
                severityMappingMapper.insert(row);
            }
        }
        if (request.alertLinkRules() != null) {
            for (EventAdapterMappingsRequest.AlertLinkRuleRequest item : request.alertLinkRules()) {
                SocEventAlertLinkRule row = new SocEventAlertLinkRule();
                row.setAdapterId(adapterId);
                row.setEventType(item.eventType());
                row.setMinSeverity(item.minSeverity());
                row.setLinkAlertsDefault(Boolean.FALSE.equals(item.linkAlertsDefault()) ? 0 : 1);
                row.setAlertRuleIdField(item.alertRuleIdField());
                row.setAlertNameTemplate(item.alertNameTemplate());
                row.setDedupKeyFieldsJson(item.dedupKeyFieldsJson());
                row.setEnabled(Boolean.FALSE.equals(item.enabled()) ? 0 : 1);
                alertLinkRuleMapper.insert(row);
            }
        }
        return mappings(adapterId);
    }

    public AdapterPreviewResult preview(Long adapterId, EventAdapterPreviewRequest request) {
        SocEventAdapterProfile profile = requireProfile(adapterId);
        try {
            return preview(profile, objectMapper.readTree(request.payload()));
        } catch (JsonProcessingException ex) {
            return new AdapterPreviewResult(Map.of(), "medium", "", false, List.of("payload 不是合法 JSON"));
        }
    }

    public Optional<AdapterPreviewResult> previewActive(String sourceType, JsonNode raw) {
        SocEventAdapterProfile profile = activeProfile(sourceType);
        if (profile == null) {
            return Optional.empty();
        }
        AdapterPreviewResult result = preview(profile, raw);
        return result.validationErrors().isEmpty() ? Optional.of(result) : Optional.empty();
    }

    public boolean hasActive(String sourceType) {
        return activeProfile(sourceType) != null;
    }

    public AdapterPreviewResult preview(SocEventAdapterProfile profile, JsonNode raw) {
        AdapterBundle bundle = mappings(profile.getId());
        List<String> errors = validateBundle(profile, bundle);
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (SocEventFieldMapping mapping : bundle.fieldMappings().stream().filter(this::enabled).sorted(Comparator.comparing(this::sortOrder)).toList()) {
            Object value = transformValue(raw, mapping);
            if (value == null || Objects.toString(value, "").isBlank()) {
                if (mapping.getRequired() != null && mapping.getRequired() == 1) {
                    errors.add("required field missing: " + mapping.getSourceFieldPath());
                }
                if (hasText(mapping.getDefaultValue())) {
                    value = mapping.getDefaultValue();
                }
            }
            if (value != null) {
                normalized.put(mapping.getNormalizedField(), value);
            }
        }
        normalized.putIfAbsent("source", profile.getSourceType());
        String severityValue = Objects.toString(normalized.getOrDefault("severity", ""), "");
        String severity = normalizeSeverity(severityValue, "medium");
        severity = mapSeverity(bundle.severityMappings(), severity);
        normalized.put("severity", severity);
        String dedupKey = dedupKey(bundle.alertLinkRules(), normalized, raw);
        boolean willCreateAlert = willCreateAlert(bundle.alertLinkRules(), normalized, severity);
        return new AdapterPreviewResult(normalized, severity, dedupKey, willCreateAlert, errors);
    }

    private void applyProfile(SocEventAdapterProfile profile, EventAdapterProfileRequest request, boolean keepStatus) {
        profile.setSourceType(normalizeSourceType(request.sourceType()));
        profile.setDisplayName(request.displayName().trim());
        profile.setDescription(trimToNull(request.description()));
        profile.setStatus(statusOrDefault(request.status(), keepStatus ? profile.getStatus() : "draft"));
        profile.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        profile.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        profile.setSampleFile(trimToNull(request.sampleFile()));
        profile.setDeleted(0);
    }

    private List<String> validateBundle(SocEventAdapterProfile profile, AdapterBundle bundle) {
        List<String> errors = new ArrayList<>();
        try {
            normalizeSourceType(profile.getSourceType());
        } catch (BusinessException ex) {
            errors.add(ex.getMessage());
        }
        if (bundle.fieldMappings().isEmpty()) {
            errors.add("至少需要一条字段映射");
        }
        for (SocEventFieldMapping mapping : bundle.fieldMappings()) {
            if (!FIELD_PATH.matcher(Objects.toString(mapping.getSourceFieldPath(), "")).matches()) {
                errors.add("非法 source_field_path: " + mapping.getSourceFieldPath());
            }
            if (!NORMALIZED_FIELD.matcher(Objects.toString(mapping.getNormalizedField(), "")).matches()) {
                errors.add("非法 normalized_field: " + mapping.getNormalizedField());
            }
            if (!TRANSFORMS.contains(Objects.toString(mapping.getTransformType(), ""))) {
                errors.add("非法 transform_type: " + mapping.getTransformType());
            }
        }
        for (SocEventSeverityMapping mapping : bundle.severityMappings()) {
            if (!SEVERITIES.contains(Objects.toString(mapping.getNormalizedSeverity(), ""))) {
                errors.add("非法 normalized_severity: " + mapping.getNormalizedSeverity());
            }
        }
        for (SocEventAlertLinkRule rule : bundle.alertLinkRules()) {
            if (!SEVERITIES.contains(Objects.toString(rule.getMinSeverity(), ""))) {
                errors.add("非法 min_severity: " + rule.getMinSeverity());
            }
            validateDedupFields(rule.getDedupKeyFieldsJson(), errors);
            validateTemplate(rule.getAlertNameTemplate(), errors);
        }
        return errors;
    }

    private void validateDedupFields(String value, List<String> errors) {
        try {
            List<String> fields = objectMapper.readValue(value, STRING_LIST);
            if (fields.isEmpty()) {
                errors.add("dedup_key_fields_json 不能为空");
            }
            for (String field : fields) {
                if (!NORMALIZED_FIELD.matcher(field).matches()) {
                    errors.add("非法 dedup 字段: " + field);
                }
            }
        } catch (Exception ex) {
            errors.add("dedup_key_fields_json 必须是字段名数组");
        }
    }

    private void validateTemplate(String template, List<String> errors) {
        if (!hasText(template)) {
            return;
        }
        String stripped = TEMPLATE_FIELD.matcher(template).replaceAll("");
        if (stripped.contains("{") || stripped.contains("}") || stripped.contains("$") || stripped.contains("(") || stripped.contains(")") || stripped.contains(";")) {
            errors.add("alert_name_template 只允许 {fieldName} 占位符");
        }
    }

    private Object transformValue(JsonNode raw, SocEventFieldMapping mapping) {
        String transform = Objects.toString(mapping.getTransformType(), "direct");
        String value = "first_non_empty".equals(transform)
                ? firstNonEmpty(raw, mapping.getSourceFieldPath())
                : textAt(raw, mapping.getSourceFieldPath());
        if (!hasText(value)) {
            value = mapping.getDefaultValue();
        }
        if (!hasText(value)) {
            return null;
        }
        return switch (transform) {
            case "lowercase" -> value.toLowerCase(Locale.ROOT);
            case "uppercase" -> value.toUpperCase(Locale.ROOT);
            case "number" -> parseNumber(value);
            case "join" -> value.replace(',', ' ');
            default -> value;
        };
    }

    private String firstNonEmpty(JsonNode raw, String paths) {
        for (String path : paths.split(",")) {
            String value = textAt(raw, path.trim());
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String textAt(JsonNode raw, String path) {
        if (raw.has(path)) {
            return raw.path(path).asText(null);
        }
        JsonNode current = raw;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.path(part);
        }
        return current == null || current.isMissingNode() || current.isNull() ? null : current.asText(null);
    }

    private Number parseNumber(String value) {
        try {
            return value.contains(".") ? Double.parseDouble(value) : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String mapSeverity(List<SocEventSeverityMapping> mappings, String sourceValue) {
        String normalized = normalizeSeverity(sourceValue, "medium");
        for (SocEventSeverityMapping mapping : mappings) {
            if (enabled(mapping) && Objects.toString(mapping.getSourceValue(), "").equalsIgnoreCase(sourceValue)) {
                return normalizeSeverity(mapping.getNormalizedSeverity(), normalized);
            }
        }
        return normalized;
    }

    private String dedupKey(List<SocEventAlertLinkRule> rules, Map<String, Object> normalized, JsonNode raw) {
        SocEventAlertLinkRule rule = firstEnabledRule(rules, Objects.toString(normalized.get("eventType"), Objects.toString(normalized.get("event_type"), "")));
        if (rule == null) {
            return "";
        }
        try {
            List<String> fields = objectMapper.readValue(rule.getDedupKeyFieldsJson(), STRING_LIST);
            List<String> parts = new ArrayList<>();
            for (String field : fields) {
                parts.add(Objects.toString(normalized.getOrDefault(field, textAt(raw, field)), ""));
            }
            return String.join("|", parts);
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean willCreateAlert(List<SocEventAlertLinkRule> rules, Map<String, Object> normalized, String severity) {
        SocEventAlertLinkRule rule = firstEnabledRule(rules, Objects.toString(normalized.get("eventType"), Objects.toString(normalized.get("event_type"), "")));
        return rule != null
                && Objects.equals(rule.getLinkAlertsDefault(), 1)
                && severityWeight(severity) >= severityWeight(rule.getMinSeverity());
    }

    private SocEventAlertLinkRule firstEnabledRule(List<SocEventAlertLinkRule> rules, String eventType) {
        return rules.stream()
                .filter(this::enabled)
                .filter(rule -> "*".equals(rule.getEventType()) || Objects.toString(rule.getEventType(), "").equalsIgnoreCase(eventType))
                .findFirst()
                .orElse(null);
    }

    private SocEventAdapterProfile activeProfile(String sourceType) {
        return profileMapper.selectOne(baseProfileWrapper()
                .eq(SocEventAdapterProfile::getSourceType, normalizeSourceType(sourceType))
                .eq(SocEventAdapterProfile::getStatus, "active")
                .eq(SocEventAdapterProfile::getEnabled, 1)
                .last("LIMIT 1"));
    }

    private SocEventAdapterProfile requireProfile(Long id) {
        SocEventAdapterProfile profile = profileMapper.selectById(id);
        if (profile == null || Objects.equals(profile.getDeleted(), 1)) {
            throw new BusinessException("事件适配器不存在");
        }
        return profile;
    }

    private LambdaQueryWrapper<SocEventAdapterProfile> baseProfileWrapper() {
        return new LambdaQueryWrapper<SocEventAdapterProfile>().eq(SocEventAdapterProfile::getDeleted, 0);
    }

    private String normalizeSourceType(String sourceType) {
        String value = Objects.toString(sourceType, "").trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SOURCES.contains(value)) {
            throw new BusinessException("本阶段仅支持 waf、zap、trivy、wazuh、suricata、zeek");
        }
        return value;
    }

    private String statusOrDefault(String status, String fallback) {
        String value = hasText(status) ? status : fallback;
        if (!Set.of("draft", "active", "disabled").contains(value)) {
            throw new BusinessException("状态必须是 draft、active 或 disabled");
        }
        return value;
    }

    private String normalizeSeverity(String value, String fallback) {
        String normalized = Objects.toString(value, fallback).trim().toLowerCase(Locale.ROOT);
        if ("3".equals(normalized)) return "high";
        if ("2".equals(normalized)) return "medium";
        if ("1".equals(normalized)) return "high";
        if (normalized.contains("critical")) return "critical";
        if (normalized.contains("high")) return "high";
        if (normalized.contains("medium")) return "medium";
        if (normalized.contains("low")) return "low";
        if (normalized.contains("info")) return "info";
        return fallback;
    }

    private int severityWeight(String severity) {
        return switch (normalizeSeverity(severity, "low")) {
            case "critical" -> 5;
            case "high" -> 4;
            case "medium" -> 3;
            case "low" -> 2;
            default -> 1;
        };
    }

    private int sortOrder(SocEventFieldMapping mapping) {
        return mapping.getSortOrder() == null ? 100 : mapping.getSortOrder();
    }

    private boolean enabled(SocEventFieldMapping mapping) {
        return mapping.getEnabled() == null || mapping.getEnabled() == 1;
    }

    private boolean enabled(SocEventSeverityMapping mapping) {
        return mapping.getEnabled() == null || mapping.getEnabled() == 1;
    }

    private boolean enabled(SocEventAlertLinkRule rule) {
        return rule.getEnabled() == null || rule.getEnabled() == 1;
    }

    private <T> List<T> nonNull(List<T> items) {
        return items == null ? List.of() : items;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AdapterBundle(List<SocEventFieldMapping> fieldMappings,
                                List<SocEventSeverityMapping> severityMappings,
                                List<SocEventAlertLinkRule> alertLinkRules) {
    }

    public record AdapterValidationResult(boolean passed, String message, List<String> errors) {
    }

    public record AdapterPreviewResult(Map<String, Object> normalizedEvent, String severity,
                                       String dedupKey, boolean willCreateAlert, List<String> validationErrors) {
    }
}
