DELETE item FROM soc_algorithm_evaluation_item item
JOIN soc_algorithm_evaluation evaluation ON item.evaluation_id = evaluation.id
WHERE evaluation.batch_id LIKE 'DEMO-%';

DELETE FROM soc_algorithm_evaluation
WHERE batch_id LIKE 'DEMO-%';

DELETE match_log FROM soc_playbook_match_log match_log
LEFT JOIN soc_alert alert ON match_log.alert_id = alert.id
LEFT JOIN soc_ticket ticket ON match_log.ticket_id = ticket.id
WHERE alert.batch_id LIKE 'DEMO-%'
   OR alert.demo_case_id IS NOT NULL
   OR alert.source_type IN ('demo', 'mock', 'local-demo-client')
   OR alert.alert_uid IN (
     'MOCK-20260527-0001','MOCK-20260527-0002','MOCK-20260526-0003','MOCK-20260525-0004',
     'MOCK-20260527-0005','SURICATA-20260527-0001','SURICATA-20260527-0002'
   )
   OR ticket.ticket_no IN ('INC-202605260001','INC-202605250001')
   OR ticket.title LIKE '%DEMO-%'
   OR ticket.title LIKE '%演示数据%'
   OR ticket.title LIKE '%mac-incident-host%'
   OR ticket.title LIKE '%win-incident-host%'
   OR ticket.title LIKE '%198.18.%'
   OR ticket.title LIKE '%198.19.%';

DELETE task FROM soc_ticket_task task
LEFT JOIN soc_alert alert ON task.alert_id = alert.id
LEFT JOIN soc_ticket ticket ON task.ticket_id = ticket.id
WHERE alert.batch_id LIKE 'DEMO-%'
   OR alert.demo_case_id IS NOT NULL
   OR alert.source_type IN ('demo', 'mock', 'local-demo-client')
   OR ticket.ticket_no IN ('INC-202605260001','INC-202605250001')
   OR ticket.title LIKE '%DEMO-%'
   OR ticket.title LIKE '%演示数据%'
   OR ticket.title LIKE '%mac-incident-host%'
   OR ticket.title LIKE '%win-incident-host%'
   OR ticket.title LIKE '%198.18.%'
   OR ticket.title LIKE '%198.19.%'
   OR task.task_key LIKE '%DEMO-%';

DELETE timeline FROM soc_ticket_timeline timeline
JOIN soc_ticket ticket ON timeline.ticket_id = ticket.id
WHERE ticket.ticket_no IN ('INC-202605260001','INC-202605250001')
   OR ticket.title LIKE '%DEMO-%'
   OR ticket.title LIKE '%演示数据%'
   OR ticket.title LIKE '%mac-incident-host%'
   OR ticket.title LIKE '%win-incident-host%'
   OR ticket.title LIKE '%198.18.%'
   OR ticket.title LIKE '%198.19.%';

DELETE action FROM soc_client_recommendation_action action
WHERE action.asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
  AND (
    action.recommendation_key LIKE '%DEMO-%'
    OR action.asset_name IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02')
    OR action.related_id IN (
      SELECT id FROM soc_alert
      WHERE batch_id LIKE 'DEMO-%'
         OR demo_case_id IS NOT NULL
         OR source_type IN ('demo', 'mock', 'local-demo-client')
    )
    OR action.related_id IN (
      SELECT id FROM soc_vulnerability
      WHERE source_type IN ('demo', 'mock')
         OR cve_id LIKE 'CVE-2026-DEMO-RANGE-%'
         OR (cve_id IN ('CVE-2024-3094','CVE-2023-38408','CVE-2024-6387','CVE-2022-22965')
             AND asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9'))
    )
  );

DELETE item FROM soc_client_checkup_item item
JOIN soc_client_checkup checkup ON item.checkup_id = checkup.id
WHERE checkup.asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
  AND (
    checkup.checkup_no LIKE '%DEMO-%'
    OR checkup.asset_name IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02')
    OR checkup.summary LIKE '%演示%'
  );

DELETE FROM soc_client_checkup
WHERE asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
  AND (
    checkup_no LIKE '%DEMO-%'
    OR asset_name IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02')
    OR summary LIKE '%演示%'
  );

DELETE factor FROM soc_asset_risk_factor factor
JOIN soc_asset_risk_snapshot snapshot ON factor.snapshot_id = snapshot.id
WHERE snapshot.asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
  AND (
    snapshot.hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02')
    OR snapshot.recommendation_summary LIKE '%演示%'
  );

DELETE FROM soc_asset_risk_snapshot
WHERE asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
  AND (
    hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02')
    OR recommendation_summary LIKE '%演示%'
  );

DELETE evidence FROM soc_incident_evidence evidence
LEFT JOIN soc_incident_cluster cluster ON evidence.cluster_id = cluster.id
WHERE evidence.batch_id LIKE 'DEMO-%'
   OR evidence.batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
   OR evidence.demo_case_id IS NOT NULL
   OR evidence.evidence_uid LIKE '%DEMO-%'
   OR evidence.evidence_uid LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
   OR cluster.batch_id LIKE 'DEMO-%'
   OR cluster.batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
   OR cluster.demo_case_id IS NOT NULL
   OR cluster.correlation_key LIKE '%DEMO-%'
   OR cluster.correlation_key LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
   OR cluster.title LIKE '%HOST-AGENT-INCIDENT-SMOKE-%';

DELETE FROM soc_incident_cluster
WHERE batch_id LIKE 'DEMO-%'
   OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
   OR demo_case_id IS NOT NULL
   OR correlation_key LIKE '%DEMO-%'
   OR correlation_key LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
   OR title LIKE '%HOST-AGENT-INCIDENT-SMOKE-%';

DELETE FROM soc_notification_log
WHERE event_type = 'demo_range_batch_imported'
   OR title LIKE '%DEMO-%'
   OR content LIKE '%DEMO-%'
   OR content LIKE '%演示数据%'
   OR (title = '高危告警已转工单'
       AND content = '关键系统配置文件发生变更，已进入工单处置流程。'
       AND target = 'soc-team@example.local');

DELETE FROM soc_report
WHERE report_no = 'RPT-DAILY-202605270001'
   OR report_no LIKE 'RPT-VALIDATION-%'
   OR title LIKE '%DEMO-%'
   OR summary LIKE '%DEMO-%';

DELETE FROM soc_alert_whitelist
WHERE (asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
       AND (reason LIKE '%演示%' OR rule_id IN ('530','5502','WAF-DEMO-1001','WAF-DEMO-2001')))
   OR (source_ip IN ('10.10.4.12','203.0.113.80','203.0.113.81') AND reason LIKE '%演示%');

DELETE FROM soc_ticket
WHERE ticket_no IN ('INC-202605260001','INC-202605250001')
   OR title LIKE '%DEMO-%'
   OR title LIKE '%演示数据%'
   OR title LIKE '%mac-incident-host%'
   OR title LIKE '%win-incident-host%'
   OR title LIKE '%198.18.%'
   OR title LIKE '%198.19.%'
   OR alert_id IN (
     SELECT id FROM soc_alert
     WHERE batch_id LIKE 'DEMO-%'
        OR demo_case_id IS NOT NULL
        OR source_type IN ('demo', 'mock', 'local-demo-client')
   );

DELETE FROM soc_external_event
WHERE batch_id LIKE 'DEMO-%'
   OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
   OR demo_case_id IS NOT NULL
   OR source_type IN ('demo', 'mock', 'local-demo-client')
   OR event_uid IN ('EXT-SURICATA-20260527-0001','EXT-SURICATA-20260527-0002')
   OR event_uid LIKE '%HOST-AGENT-INCIDENT-SMOKE-%';

DELETE FROM soc_alert
WHERE batch_id LIKE 'DEMO-%'
   OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
   OR demo_case_id IS NOT NULL
   OR source_type IN ('demo', 'mock', 'local-demo-client')
   OR alert_uid IN (
     'MOCK-20260527-0001','MOCK-20260527-0002','MOCK-20260526-0003','MOCK-20260525-0004',
     'MOCK-20260527-0005','SURICATA-20260527-0001','SURICATA-20260527-0002'
   )
   OR raw_ref LIKE '%DEMO-%'
   OR raw_ref LIKE '%HOST-AGENT-INCIDENT-SMOKE-%';

DELETE FROM soc_vulnerability
WHERE source_type IN ('demo', 'mock')
   OR cve_id LIKE 'CVE-2026-DEMO-RANGE-%'
   OR (cve_id IN ('CVE-2024-3094','CVE-2023-38408','CVE-2024-6387','CVE-2022-22965')
       AND asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9'));

DELETE FROM soc_baseline_check
WHERE source_type IN ('demo', 'mock')
   OR (check_code IN ('SSH_ROOT_LOGIN','PASSWORD_MAX_DAYS','FIREWALL_DEFAULT_DENY','SENSITIVE_FILE_PERMISSION','UNNEEDED_SERVICE')
       AND asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9'));

DELETE FROM soc_file_integrity_event
WHERE source_type IN ('demo', 'mock')
   OR event_uid IN ('FIM-20260527-0001','FIM-20260527-0002','FIM-20260526-0003','FIM-20260525-0004');

DELETE FROM soc_asset
WHERE source_type IN ('demo', 'mock', 'local-demo-client')
   OR (
     source_type IN ('macos-agent', 'windows-agent', 'host-agent')
     AND hostname IN ('mac-incident-host', 'win-incident-host')
     AND (ip LIKE '198.18.%' OR ip LIKE '198.19.%')
   )
   OR (
     ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
     AND hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02')
   );

DELETE FROM soc_ingest_reject_log
WHERE batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
   OR agent_id IN ('incident-macos-agent', 'incident-windows-agent');

DELETE FROM soc_ingest_batch
WHERE batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
   OR agent_id IN ('incident-macos-agent', 'incident-windows-agent');

DELETE FROM soc_host_agent
WHERE agent_id IN ('incident-macos-agent', 'incident-windows-agent')
   OR CAST(labels_json AS CHAR) LIKE '%incident-chain%';

DELETE FROM soc_playbook_match_log
WHERE alert_id IN (
  SELECT id FROM soc_alert
  WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
    AND (
      asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
      OR asset_ip LIKE '192.0.2.%'
      OR asset_ip LIKE '198.18.%'
      OR asset_ip LIKE '198.19.%'
      OR alert_uid LIKE '%FIXTURE%'
      OR raw_ref LIKE '%fixture%'
      OR rule_description LIKE '%fixture%'
      OR rule_description LIKE '%Fixture%'
    )
)
   OR ticket_id IN (
     SELECT id FROM soc_ticket
     WHERE title LIKE '%mac-dev-host%'
        OR title LIKE '%win-docker-host%'
        OR title LIKE '%mac-incident-host%'
        OR title LIKE '%win-incident-host%'
        OR title LIKE '%192.0.2.%'
        OR title LIKE '%198.18.%'
        OR title LIKE '%198.19.%'
   );

DELETE FROM soc_ticket_task
WHERE ticket_id IN (
  SELECT id FROM soc_ticket
  WHERE title LIKE '%mac-dev-host%'
     OR title LIKE '%win-docker-host%'
     OR title LIKE '%mac-incident-host%'
     OR title LIKE '%win-incident-host%'
     OR title LIKE '%192.0.2.%'
     OR title LIKE '%198.18.%'
     OR title LIKE '%198.19.%'
)
   OR alert_id IN (
     SELECT id FROM soc_alert
     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
       AND (
         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
         OR asset_ip LIKE '192.0.2.%'
         OR asset_ip LIKE '198.18.%'
         OR asset_ip LIKE '198.19.%'
       )
   );

DELETE FROM soc_ticket_timeline
WHERE ticket_id IN (
  SELECT id FROM soc_ticket
  WHERE title LIKE '%mac-dev-host%'
     OR title LIKE '%win-docker-host%'
     OR title LIKE '%mac-incident-host%'
     OR title LIKE '%win-incident-host%'
     OR title LIKE '%192.0.2.%'
     OR title LIKE '%198.18.%'
     OR title LIKE '%198.19.%'
);

DELETE FROM soc_incident_evidence
WHERE asset_ip LIKE '192.0.2.%'
   OR asset_ip LIKE '198.18.%'
   OR asset_ip LIKE '198.19.%'
   OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
   OR evidence_uid LIKE '%FIXTURE%'
   OR cluster_id IN (
     SELECT id FROM soc_incident_cluster
     WHERE hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
        OR primary_hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
        OR asset_ip LIKE '192.0.2.%'
        OR primary_asset_ip LIKE '192.0.2.%'
        OR asset_ip LIKE '198.18.%'
        OR primary_asset_ip LIKE '198.18.%'
        OR asset_ip LIKE '198.19.%'
        OR primary_asset_ip LIKE '198.19.%'
        OR title LIKE '%fixture%'
        OR title LIKE '%Fixture%'
        OR summary LIKE '%fixture%'
   )
   OR (evidence_type = 'external_event' AND evidence_id IN (
     SELECT id FROM soc_external_event
     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
       AND (
         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
         OR asset_ip LIKE '192.0.2.%'
         OR asset_ip LIKE '198.18.%'
         OR asset_ip LIKE '198.19.%'
         OR event_uid LIKE '%FIXTURE%'
         OR CAST(raw_event AS CHAR) LIKE '%"fixture": true%'
         OR CAST(normalized_event AS CHAR) LIKE '%"fixture": true%'
       )
   ))
   OR (evidence_type = 'alert' AND evidence_id IN (
     SELECT id FROM soc_alert
     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
       AND (
         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
         OR asset_ip LIKE '192.0.2.%'
         OR asset_ip LIKE '198.18.%'
         OR asset_ip LIKE '198.19.%'
         OR alert_uid LIKE '%FIXTURE%'
       )
   ));

DELETE FROM soc_ticket
WHERE title LIKE '%mac-dev-host%'
   OR title LIKE '%win-docker-host%'
   OR title LIKE '%mac-incident-host%'
   OR title LIKE '%win-incident-host%'
   OR title LIKE '%192.0.2.%'
   OR title LIKE '%198.18.%'
   OR title LIKE '%198.19.%'
   OR alert_id IN (
     SELECT id FROM soc_alert
     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
       AND (
         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
         OR asset_ip LIKE '192.0.2.%'
         OR asset_ip LIKE '198.18.%'
         OR asset_ip LIKE '198.19.%'
       )
   );

DELETE FROM soc_incident_cluster
WHERE hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
   OR primary_hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
   OR asset_ip LIKE '192.0.2.%'
   OR primary_asset_ip LIKE '192.0.2.%'
   OR asset_ip LIKE '198.18.%'
   OR primary_asset_ip LIKE '198.18.%'
   OR asset_ip LIKE '198.19.%'
   OR primary_asset_ip LIKE '198.19.%'
   OR title LIKE '%fixture%'
   OR title LIKE '%Fixture%'
   OR summary LIKE '%fixture%';

DELETE factor FROM soc_asset_risk_factor factor
JOIN soc_asset_risk_snapshot snapshot ON factor.snapshot_id = snapshot.id
WHERE snapshot.asset_ip LIKE '192.0.2.%'
   OR snapshot.asset_ip LIKE '198.18.%'
   OR snapshot.asset_ip LIKE '198.19.%'
   OR snapshot.hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host');

DELETE FROM soc_asset_risk_snapshot
WHERE asset_ip LIKE '192.0.2.%'
   OR asset_ip LIKE '198.18.%'
   OR asset_ip LIKE '198.19.%'
   OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host');

DELETE FROM soc_external_event
WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
  AND (
    asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
    OR asset_ip LIKE '192.0.2.%'
    OR asset_ip LIKE '198.18.%'
    OR asset_ip LIKE '198.19.%'
    OR batch_id LIKE 'HOST-%fixture-agent-%'
    OR event_uid LIKE '%FIXTURE%'
    OR CAST(raw_event AS CHAR) LIKE '%"fixture": true%'
    OR CAST(normalized_event AS CHAR) LIKE '%"fixture": true%'
  );

DELETE FROM soc_alert
WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
  AND (
    asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
    OR asset_ip LIKE '192.0.2.%'
    OR asset_ip LIKE '198.18.%'
    OR asset_ip LIKE '198.19.%'
    OR batch_id LIKE 'HOST-%fixture-agent-%'
    OR alert_uid LIKE '%FIXTURE%'
    OR raw_ref LIKE '%fixture%'
    OR rule_description LIKE '%fixture%'
    OR rule_description LIKE '%Fixture%'
    OR evidence_summary LIKE '%fixture%'
  );

DELETE FROM soc_file_integrity_event
WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
  AND (
    hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
    OR asset_ip LIKE '192.0.2.%'
    OR asset_ip LIKE '198.18.%'
    OR asset_ip LIKE '198.19.%'
    OR event_uid LIKE '%FIXTURE%'
    OR rule_name LIKE '%Fixture%'
    OR rule_name LIKE '%fixture%'
  );

DELETE FROM soc_baseline_check
WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
  AND (
    asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
    OR asset_ip LIKE '192.0.2.%'
    OR asset_ip LIKE '198.18.%'
    OR asset_ip LIKE '198.19.%'
    OR check_code LIKE '%fixture%'
    OR check_code LIKE '%FIXTURE%'
    OR check_item LIKE '%Fixture%'
    OR check_item LIKE '%fixture%'
  );

DELETE FROM soc_asset
WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
  AND (
    hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
    OR ip LIKE '192.0.2.%'
    OR ip LIKE '198.18.%'
    OR ip LIKE '198.19.%'
  );

DELETE FROM soc_ingest_reject_log
WHERE agent_id LIKE '%fixture-agent%'
   OR agent_id LIKE 'queue-replay-macos-agent-%'
   OR batch_id LIKE 'HOST-%fixture-agent-%';

DELETE FROM soc_ingest_batch
WHERE agent_id LIKE '%fixture-agent%'
   OR agent_id LIKE 'queue-replay-macos-agent-%'
   OR batch_id LIKE 'HOST-%fixture-agent-%';

DELETE FROM soc_host_agent
WHERE agent_id LIKE '%fixture-agent%'
   OR agent_id LIKE 'queue-replay-macos-agent-%'
   OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
   OR CAST(ip_addresses_json AS CHAR) LIKE '%192.0.2.%'
   OR CAST(ip_addresses_json AS CHAR) LIKE '%198.18.%'
   OR CAST(ip_addresses_json AS CHAR) LIKE '%198.19.%'
   OR CAST(labels_json AS CHAR) LIKE '%"fixture": "true"%'
   OR CAST(labels_json AS CHAR) LIKE '%"fixture":"true"%'
   OR CAST(labels_json AS CHAR) LIKE '%queue-replay%';
