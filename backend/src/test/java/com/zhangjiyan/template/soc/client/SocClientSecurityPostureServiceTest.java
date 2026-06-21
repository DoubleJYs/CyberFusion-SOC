package com.zhangjiyan.template.soc.client;

import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.keeper.SocClientCheckup;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocClientSecurityPostureServiceTest {

    @Test
    void calculatesCoverageAndPendingMetricsFromVisibleRows() {
        SocAsset first = asset(1L, "prod-app-01", "10.20.1.15", 90, "critical");
        SocAsset second = asset(2L, "office-win-23", "10.30.5.23", 30, "low");
        SocClientCheckup checkup = checkup(10L, first, 86, "serious", LocalDateTime.now());
        SocTicketTask task = new SocTicketTask();
        task.setStatus("pending");
        SocExternalEvent localCheck = localCheck(100L, first, "terminal", "new");

        SocClientSecurityPostureService.ClientSecurityMetrics metrics =
                SocClientSecurityPostureService.metrics(List.of(first, second), Map.of(first.getIp(), checkup), List.of(task), List.of(localCheck));

        assertThat(metrics.totalAssets()).isEqualTo(2);
        assertThat(metrics.checkedAssets()).isEqualTo(1);
        assertThat(metrics.checkupCoverageRate()).isEqualTo(50);
        assertThat(metrics.seriousRiskAssets()).isEqualTo(1);
        assertThat(metrics.pendingEmployeeTasks()).isEqualTo(1);
        assertThat(metrics.waitingReviewRecords()).isEqualTo(1);
    }

    @Test
    void comparesLatestCheckupsForRiskDownAssets() {
        SocAsset asset = asset(1L, "prod-app-01", "10.20.1.15", 70, "high");
        SocClientCheckup latest = checkup(11L, asset, 45, "attention", LocalDateTime.now());
        SocClientCheckup previous = checkup(10L, asset, 90, "serious", LocalDateTime.now().minusDays(1));

        List<SocClientSecurityPostureService.ClientRiskDownAsset> rows =
                SocClientSecurityPostureService.riskDownAssets(Map.of(asset.getIp(), List.of(latest, previous)));

        assertThat(rows).singleElement()
                .satisfies(row -> {
                    assertThat(row.previousScore()).isEqualTo(90);
                    assertThat(row.currentScore()).isEqualTo(45);
                    assertThat(row.assetIp()).isEqualTo(asset.getIp());
                });
    }

    @Test
    void reviewRecordsDoNotExposeRawEventPayloads() {
        SocAsset asset = asset(1L, "prod-app-01", "10.20.1.15", 70, "high");
        SocExternalEvent event = localCheck(100L, asset, "terminal", "new");
        event.setRawEvent("{\"terminal_output\":[\"secret\"]}");
        event.setRuleName("本机检查记录");

        List<SocClientSecurityPostureService.ClientReviewRecord> rows =
                SocClientSecurityPostureService.reviewRecords(List.of(event));

        assertThat(rows).singleElement()
                .satisfies(row -> {
                    assertThat(row.summary()).isEqualTo("本机检查记录");
                    assertThat(row.summary()).doesNotContain("terminal_output");
                });
    }

    private static SocAsset asset(Long id, String hostname, String ip, int score, String riskLevel) {
        SocAsset asset = new SocAsset();
        asset.setId(id);
        asset.setHostname(hostname);
        asset.setIp(ip);
        asset.setOsType("Linux");
        asset.setRiskScore(score);
        asset.setRiskLevel(riskLevel);
        asset.setOpenAlertCount(2);
        return asset;
    }

    private static SocClientCheckup checkup(Long id, SocAsset asset, int score, String status, LocalDateTime checkedAt) {
        SocClientCheckup checkup = new SocClientCheckup();
        checkup.setId(id);
        checkup.setAssetId(asset.getId());
        checkup.setAssetName(asset.getHostname());
        checkup.setAssetIp(asset.getIp());
        checkup.setScore(score);
        checkup.setStatus(status);
        checkup.setCheckedAt(checkedAt);
        return checkup;
    }

    private static SocExternalEvent localCheck(Long id, SocAsset asset, String eventType, String status) {
        SocExternalEvent event = new SocExternalEvent();
        event.setId(id);
        event.setAssetName(asset.getHostname());
        event.setAssetIp(asset.getIp());
        event.setSourceType("osquery");
        event.setEventType(eventType);
        event.setSeverity("low");
        event.setStatus(status);
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
