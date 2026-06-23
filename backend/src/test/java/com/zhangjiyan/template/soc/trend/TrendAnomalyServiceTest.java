package com.zhangjiyan.template.soc.trend;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnomalyServiceTest {

    private static final LocalDateTime REFERENCE = LocalDateTime.of(2026, 6, 18, 10, 10);

    @Test
    void detectsVolumeSpikeWithExplainableReason() {
        List<TrendAnomalyService.TrendSignal> signals = List.of(
                signal("10.20.1.15", "waf", "waf_block", "WAF-DEMO-1001", "high", REFERENCE.minusMinutes(6)),
                signal("10.20.1.15", "waf", "waf_block", "WAF-DEMO-1001", "high", REFERENCE.minusMinutes(3)),
                signal("10.20.1.15", "waf", "waf_block", "WAF-DEMO-1001", "medium", REFERENCE.minusDays(3))
        );

        List<TrendAnomalyService.TrendAnomalyItem> result = TrendAnomalyService.detectForTest(
                signals, REFERENCE, new TrendAnomalyService.TrendAnomalyQuery(null, "waf", null, null, null, 5));

        assertThat(result).anySatisfy(item -> {
            assertThat(item.title()).contains("数量突增");
            assertThat(item.assetIp()).isEqualTo("10.20.1.15");
            assertThat(item.currentCount()).isEqualTo(2);
            assertThat(item.reason()).contains("7 天日均").contains("assetIp=10.20.1.15");
            assertThat(item.recommendation()).contains("复核");
        });
    }

    @Test
    void detectsCrossSourceRiseForDemoEvidenceChain() {
        List<TrendAnomalyService.TrendSignal> signals = List.of(
                signal("10.20.1.15", "waf", "waf_block", "WAF-DEMO-1001", "high", REFERENCE.minusMinutes(9)),
                signal("10.20.1.15", "zap", "zap_alert", "10021", "medium", REFERENCE.minusMinutes(8)),
                signal("10.20.1.15", "wazuh", "fim", "550", "high", REFERENCE.minusMinutes(7)),
                signal("10.20.1.15", "suricata", "alert", "26061801", "critical", REFERENCE.minusMinutes(6)),
                signal("10.20.1.15", "zeek", "conn", "zeek_conn", "low", REFERENCE.minusMinutes(5))
        );

        List<TrendAnomalyService.TrendAnomalyItem> result = TrendAnomalyService.detectForTest(
                signals, REFERENCE, new TrendAnomalyService.TrendAnomalyQuery(null, null, null, null, null, 5));

        assertThat(result).anySatisfy(item -> {
            assertThat(item.eventType()).isEqualTo("cross_source_rise");
            assertThat(item.sourceType()).isEqualTo("multi_source");
            assertThat(item.reason()).contains("waf").contains("suricata").contains("zeek");
            assertThat(item.anomalyScore()).isGreaterThanOrEqualTo(80);
        });
    }

    @Test
    void queryFiltersByAssetIp() {
        List<TrendAnomalyService.TrendSignal> signals = List.of(
                signal("10.20.1.15", "waf", "waf_block", "WAF-DEMO-1001", "high", REFERENCE.minusMinutes(4)),
                signal("10.20.1.15", "waf", "waf_block", "WAF-DEMO-1001", "high", REFERENCE.minusMinutes(3)),
                signal("10.20.1.20", "waf", "waf_block", "WAF-DEMO-1001", "high", REFERENCE.minusMinutes(2)),
                signal("10.20.1.20", "waf", "waf_block", "WAF-DEMO-1001", "high", REFERENCE.minusMinutes(1))
        );

        List<TrendAnomalyService.TrendAnomalyItem> result = TrendAnomalyService.detectForTest(
                signals, REFERENCE, new TrendAnomalyService.TrendAnomalyQuery("10.20.1.15", null, null, null, null, 10));

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(item -> "10.20.1.15".equals(item.assetIp()));
    }

    private static TrendAnomalyService.TrendSignal signal(String assetIp, String sourceType, String eventType,
                                                         String ruleId, String severity, LocalDateTime eventTime) {
        return new TrendAnomalyService.TrendSignal(assetIp, sourceType, eventType, ruleId, severity, eventTime,
                "external_event");
    }
}
