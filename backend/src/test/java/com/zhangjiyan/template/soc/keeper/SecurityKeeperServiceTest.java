package com.zhangjiyan.template.soc.keeper;

import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.risk.RiskScoringService;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityKeeperServiceTest {

    @Test
    void mapsScoreToEmployeeFriendlyStatus() {
        assertThat(SecurityKeeperService.statusFromScore(20)).isEqualTo("safe");
        assertThat(SecurityKeeperService.statusFromScore(40)).isEqualTo("attention");
        assertThat(SecurityKeeperService.statusFromScore(80)).isEqualTo("serious");
    }

    @Test
    void aggregatesExistingSocDataIntoCheckupRiskItemsWithoutRawOutputs() {
        List<SecurityKeeperService.CheckupRiskItem> items = SecurityKeeperService.buildRiskItems(profile(),
                new RiskScoringService.CalculationInput(0, 1, 0, 0, 1, 0, 2, 0, 0, 4, 3, 0, 0, 1));

        assertThat(items).extracting(SecurityKeeperService.CheckupRiskItem::itemType)
                .contains("alerts", "vulnerabilities", "file_changes", "ticket_tasks", "employee_tasks", "local_checks");
        assertThat(items).filteredOn(item -> "alerts".equals(item.itemType()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.count()).isEqualTo(1);
                    assertThat(item.summary()).contains("未关闭安全提醒");
                    assertThat(item.summary()).doesNotContain("terminal_output");
                });
        assertThat(items).filteredOn(item -> "local_checks".equals(item.itemType()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.count()).isEqualTo(1);
                    assertThat(item.summary()).contains("本机检查记录");
                });
    }

    private static SocOperationService.ClientDeviceProfile profile() {
        SocAsset asset = new SocAsset();
        asset.setId(15L);
        asset.setHostname("prod-app-01");
        asset.setIp("10.20.1.15");
        asset.setOsType("Linux");
        asset.setOwnerId(6L);
        asset.setDeptId(2L);

        SocAlert alert = new SocAlert();
        alert.setStatus("new");
        alert.setSeverity("high");

        SocExternalEvent terminal = new SocExternalEvent();
        terminal.setSourceType("osquery");
        terminal.setEventType("terminal");
        terminal.setEventTime(LocalDateTime.now());

        return new SocOperationService.ClientDeviceProfile(asset,
                new SocOperationService.ClientDeviceMetrics(86, 1, 1, 0, 2, 1, "演示体检"),
                List.of(alert), List.of(vulnerability()), List.of(), List.of(), List.of(terminal), List.of());
    }

    private static SocVulnerability vulnerability() {
        SocVulnerability vulnerability = new SocVulnerability();
        vulnerability.setStatus("open");
        vulnerability.setSeverity("high");
        return vulnerability;
    }
}
