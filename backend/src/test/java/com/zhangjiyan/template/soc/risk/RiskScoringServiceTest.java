package com.zhangjiyan.template.soc.risk;

import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.asset.SocAsset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskScoringServiceTest {

    private final RiskScoringService service = new RiskScoringService(
            null, null, null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void calculatesExplainableRiskFromAlertsVulnerabilitiesAndTasks() {
        RiskScoringService.CalculationResult result = service.calculate(defaultPolicy(), prodAsset(),
                new RiskScoringService.CalculationInput(
                        1, 1, 1,
                        1, 1,
                        1, 1, 1,
                        1, 1, 1,
                        0, 0, 1
                ));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.riskLevel()).isEqualTo("critical");
        assertThat(result.factors()).extracting(SocAssetRiskFactor::getFactorType)
                .contains("alert_critical", "vulnerability_critical", "ticket_overdue", "employee_pending");
        assertThat(result.recommendationSummary()).contains("告警详情", "组件版本");
        assertThat(result.summary()).containsEntry("criticalAlerts", 1L);
    }

    @Test
    void closedTicketsAndCompletedTasksReduceRiskButDoNotGoBelowZero() {
        RiskScoringService.CalculationResult result = service.calculate(defaultPolicy(), normalAsset(),
                new RiskScoringService.CalculationInput(
                        0, 0, 0,
                        0, 0,
                        0, 0, 0,
                        0, 0, 0,
                        3, 3, 0
                ));

        assertThat(result.score()).isZero();
        assertThat(result.riskLevel()).isEqualTo("low");
        assertThat(result.factors()).extracting(SocAssetRiskFactor::getFactorType)
                .contains("ticket_closed", "playbook_completed");
        assertThat(result.factors()).allMatch(factor -> factor.getFactorScore() <= 0);
    }

    @Test
    void customPolicyWeightsChangeScoreWithoutExecutableExpressions() {
        SocRiskScoringPolicy policy = defaultPolicy();
        policy.setCriticalAlertWeight(40);
        policy.setHighVulnerabilityWeight(20);
        policy.setCriticalAssetWeight(0);
        policy.setMaxScore(100);

        RiskScoringService.CalculationResult result = service.calculate(policy, normalAsset(),
                new RiskScoringService.CalculationInput(
                        1, 0, 0,
                        0, 1,
                        0, 0, 0,
                        0, 0, 0,
                        0, 0, 0
                ));

        assertThat(result.score()).isEqualTo(60);
        assertThat(result.riskLevel()).isEqualTo("high");
        assertThat(result.factors()).extracting(SocAssetRiskFactor::getFactorScore).contains(40, 20);
    }

    @Test
    void rejectsScriptOrScanSemanticsInPolicyText() {
        SocRiskScoringPolicy policy = defaultPolicy();
        policy.setDescription("run bash script");

        assertThatThrownBy(() -> service.calculate(policy, normalAsset(), emptyInput()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许脚本");
    }

    @Test
    void rejectsWeightsOutsideZeroToOneHundred() {
        SocRiskScoringPolicy policy = defaultPolicy();
        policy.setHighAlertWeight(101);

        assertThatThrownBy(() -> service.calculate(policy, normalAsset(), emptyInput()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0-100");
    }

    private static RiskScoringService.CalculationInput emptyInput() {
        return new RiskScoringService.CalculationInput(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static SocRiskScoringPolicy defaultPolicy() {
        return RiskScoringService.defaultPolicy();
    }

    private static SocAsset prodAsset() {
        SocAsset asset = normalAsset();
        asset.setHostname("prod-app-01");
        asset.setRiskLevel("critical");
        return asset;
    }

    private static SocAsset normalAsset() {
        SocAsset asset = new SocAsset();
        asset.setId(15L);
        asset.setHostname("dev-app-01");
        asset.setIp("10.20.1.15");
        asset.setRiskLevel("low");
        asset.setSourceType("demo");
        asset.setDeleted(0);
        return asset;
    }
}
