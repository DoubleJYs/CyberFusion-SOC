package com.zhangjiyan.template.soc.policy.adapter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventAdapterPolicyServiceTest {

    @Test
    void previewsActiveAdapterWithoutWritingDatabase() throws Exception {
        Fixture fixture = fixture(profile("waf", "active", 1),
                List.of(field("eventType", "eventType", "direct", "waf_detect"),
                        field("severity", "severity", "lowercase", "medium"),
                        field("ruleId", "ruleId", "direct", "WAF-DEMO"),
                        field("assetIp", "assetIp", "direct", null),
                        field("requestId", "requestId", "direct", null)),
                List.of(severity("high", "high")),
                List.of(rule("*", "medium", "[\"source\",\"eventType\",\"ruleId\",\"assetIp\",\"requestId\"]", "WAF 证据：{ruleId}")));

        Optional<EventAdapterPolicyService.AdapterPreviewResult> result = fixture.service().previewActive(
                "waf",
                new ObjectMapper().readTree("""
                        {"eventType":"waf_block","severity":"HIGH","ruleId":"WAF-1","assetIp":"10.20.1.15","requestId":"req-1"}
                        """)
        );

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo("high");
        assertThat(result.get().dedupKey()).contains("waf_block", "WAF-1", "10.20.1.15");
        assertThat(result.get().willCreateAlert()).isTrue();
        assertThat(fixture.fieldMapper().writes()).isZero();
        assertThat(fixture.severityMapper().writes()).isZero();
        assertThat(fixture.alertRuleMapper().writes()).isZero();
    }

    @Test
    void disabledAdapterDoesNotBecomeActivePreview() throws Exception {
        Fixture fixture = fixture(profile("waf", "disabled", 0),
                List.of(field("eventType", "eventType", "direct", "waf_detect")),
                List.of(),
                List.of(rule("*", "medium", "[\"source\",\"eventType\"]", null)));

        Optional<EventAdapterPolicyService.AdapterPreviewResult> result = fixture.service().previewActive(
                "waf",
                new ObjectMapper().readTree("{\"eventType\":\"waf_block\"}")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void listsAdaptersWithoutSourceTypeFilter() {
        Fixture fixture = fixture(profile("waf", "active", 1),
                List.of(),
                List.of(),
                List.of());

        assertThat(fixture.service().page(1, 20, null, "active", null).records())
                .extracting(SocEventAdapterProfile::getSourceType)
                .containsExactly("waf");
    }

    @Test
    void rejectsInvalidSourceFieldPathBeforePublish() {
        Fixture fixture = fixture(profile("waf", "draft", 1),
                List.of(field("$.payload;drop", "eventType", "direct", "waf_detect")),
                List.of(),
                List.of(rule("*", "medium", "[\"source\",\"eventType\"]", null)));

        assertThatThrownBy(() -> fixture.service().publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法 source_field_path");
    }

    @Test
    void rejectsExpressionStyleAlertTemplateBeforePublish() {
        Fixture fixture = fixture(profile("waf", "draft", 1),
                List.of(field("eventType", "eventType", "direct", "waf_detect")),
                List.of(),
                List.of(rule("*", "medium", "[\"source\",\"eventType\"]", "${ruleName}")));

        assertThatThrownBy(() -> fixture.service().publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("alert_name_template");
    }

    private static Fixture fixture(SocEventAdapterProfile profile,
                                   List<SocEventFieldMapping> fields,
                                   List<SocEventSeverityMapping> severities,
                                   List<SocEventAlertLinkRule> alertRules) {
        CountingMapper<SocEventAdapterProfileMapper> profileMapper = profileMapper(profile);
        CountingMapper<SocEventFieldMappingMapper> fieldMapper = listMapper(SocEventFieldMappingMapper.class, fields);
        CountingMapper<SocEventSeverityMappingMapper> severityMapper = listMapper(SocEventSeverityMappingMapper.class, severities);
        CountingMapper<SocEventAlertLinkRuleMapper> alertRuleMapper = listMapper(SocEventAlertLinkRuleMapper.class, alertRules);
        SocSecurityScope scope = new SocSecurityScope(null, null, null, null, null) {
            @Override
            public Long currentUserId() {
                return 1L;
            }
        };
        EventAdapterPolicyService service = new EventAdapterPolicyService(
                profileMapper.mapper(),
                fieldMapper.mapper(),
                severityMapper.mapper(),
                alertRuleMapper.mapper(),
                new ObjectMapper(),
                scope
        );
        return new Fixture(service, fieldMapper, severityMapper, alertRuleMapper);
    }

    @SuppressWarnings("unchecked")
    private static CountingMapper<SocEventAdapterProfileMapper> profileMapper(SocEventAdapterProfile profile) {
        Counter counter = new Counter();
        SocEventAdapterProfileMapper mapper = (SocEventAdapterProfileMapper) Proxy.newProxyInstance(
                EventAdapterPolicyServiceTest.class.getClassLoader(),
                new Class[]{SocEventAdapterProfileMapper.class},
                (_proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> profile;
                    case "selectOne" -> "active".equals(profile.getStatus()) && profile.getEnabled() == 1 ? profile : null;
                    case "selectPage" -> {
                        Page<SocEventAdapterProfile> page = (Page<SocEventAdapterProfile>) args[0];
                        page.setRecords(List.of(profile));
                        page.setTotal(1);
                        yield page;
                    }
                    case "insert", "updateById", "delete" -> {
                        counter.writes++;
                        yield 1;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
        return new CountingMapper<>(mapper, counter);
    }

    @SuppressWarnings("unchecked")
    private static <T> CountingMapper<T> listMapper(Class<T> mapperType, List<?> rows) {
        Counter counter = new Counter();
        T mapper = (T) Proxy.newProxyInstance(
                EventAdapterPolicyServiceTest.class.getClassLoader(),
                new Class[]{mapperType},
                (_proxy, method, args) -> switch (method.getName()) {
                    case "selectList" -> rows;
                    case "insert", "updateById", "delete" -> {
                        counter.writes++;
                        yield 1;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
        return new CountingMapper<>(mapper, counter);
    }

    private static SocEventAdapterProfile profile(String sourceType, String status, int enabled) {
        SocEventAdapterProfile profile = new SocEventAdapterProfile();
        profile.setId(1L);
        profile.setSourceType(sourceType);
        profile.setDisplayName(sourceType + " adapter");
        profile.setStatus(status);
        profile.setEnabled(enabled);
        profile.setVersion(1);
        profile.setSortOrder(10);
        profile.setDeleted(0);
        return profile;
    }

    private static SocEventFieldMapping field(String sourceFieldPath, String normalizedField, String transform, String defaultValue) {
        SocEventFieldMapping field = new SocEventFieldMapping();
        field.setAdapterId(1L);
        field.setSourceFieldPath(sourceFieldPath);
        field.setNormalizedField(normalizedField);
        field.setTransformType(transform);
        field.setDefaultValue(defaultValue);
        field.setRequired(0);
        field.setEnabled(1);
        field.setSortOrder(10);
        return field;
    }

    private static SocEventSeverityMapping severity(String sourceValue, String normalizedSeverity) {
        SocEventSeverityMapping severity = new SocEventSeverityMapping();
        severity.setAdapterId(1L);
        severity.setSourceValue(sourceValue);
        severity.setNormalizedSeverity(normalizedSeverity);
        severity.setRiskScore(80);
        severity.setEnabled(1);
        return severity;
    }

    private static SocEventAlertLinkRule rule(String eventType, String minSeverity, String dedupKeyFieldsJson, String template) {
        SocEventAlertLinkRule rule = new SocEventAlertLinkRule();
        rule.setAdapterId(1L);
        rule.setEventType(eventType);
        rule.setMinSeverity(minSeverity);
        rule.setLinkAlertsDefault(1);
        rule.setAlertRuleIdField("ruleId");
        rule.setAlertNameTemplate(template);
        rule.setDedupKeyFieldsJson(dedupKeyFieldsJson);
        rule.setEnabled(1);
        return rule;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private record Fixture(EventAdapterPolicyService service,
                           CountingMapper<SocEventFieldMappingMapper> fieldMapper,
                           CountingMapper<SocEventSeverityMappingMapper> severityMapper,
                           CountingMapper<SocEventAlertLinkRuleMapper> alertRuleMapper) {
    }

    private record CountingMapper<T>(T mapper, Counter counter) {
        int writes() {
            return counter.writes;
        }
    }

    private static class Counter {
        private int writes;
    }
}
