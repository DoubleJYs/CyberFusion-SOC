INSERT INTO soc_asset
  (hostname, ip, os_type, risk_score, risk_level, source_type, dept_id, dept_name, owner_id, owner_name, open_alert_count, last_seen_at, deleted)
VALUES
  ('prod-app-01', '10.20.1.15', 'Linux', 100, 'critical', 'demo', 12, '基础设施运维组', 5, '运维人员', 7, NOW(), 0),
  ('finance-db-01', '10.20.8.21', 'Linux', 69, 'high', 'demo', 12, '基础设施运维组', 5, '运维人员', 4, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0),
  ('office-win-23', '10.30.5.23', 'Windows', 51, 'medium', 'demo', 11, '安全分析组', 4, '安全分析员', 2, DATE_SUB(NOW(), INTERVAL 5 HOUR), 0),
  ('mac-build-02', '10.40.2.9', 'macOS', 0, 'low', 'demo', 11, '安全分析组', 4, '安全分析员', 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 0)
ON DUPLICATE KEY UPDATE
  hostname = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(hostname), hostname),
  os_type = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(os_type), os_type),
  risk_score = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(risk_score), risk_score),
  risk_level = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(risk_level), risk_level),
  source_type = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(source_type), source_type),
  dept_id = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(dept_id), dept_id),
  dept_name = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(dept_name), dept_name),
  owner_id = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(owner_id), owner_id),
  owner_name = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(owner_name), owner_name),
  open_alert_count = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(open_alert_count), open_alert_count),
  last_seen_at = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), VALUES(last_seen_at), last_seen_at),
  deleted = IF(source_type IN ('demo', 'mock', 'local-demo-client') OR hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'), 0, deleted);

INSERT INTO soc_alert
  (alert_uid, source_type, level, severity, rule_id, rule_description, asset_name, asset_ip, source_ip, event_type,
   action, evidence_summary, batch_id, demo_case_id, status, tactic, raw_ref, event_time, owner_id, dept_id, deleted)
VALUES
  ('MOCK-20260527-0001', 'demo', 15, 'critical', '5715', '多次认证失败后出现成功登录，疑似暴力破解成功', 'prod-app-01', '10.20.1.15', '198.51.100.23', 'auth_bruteforce', 'review', '演示数据：认证失败和成功登录证据用于说明告警入口。', 'DEMO-SEED-BASELINE', 'legacy-auth-risk', 'new', 'Credential Access', 'wazuh-alerts-4.x-2026.05.27/1/batch/DEMO-SEED-BASELINE', NOW(), 4, 11, 0),
  ('MOCK-20260527-0002', 'demo', 12, 'high', '5502', '关键系统配置文件发生变更，需要复核变更单', 'finance-db-01', '10.20.8.21', '10.10.4.12', 'fim_change', 'review', '演示数据：配置文件变更和内部来源形成变更复核样例。', 'DEMO-SEED-BASELINE', 'legacy-config-change', 'acknowledged', 'Defense Evasion', 'wazuh-alerts-4.x-2026.05.27/2/batch/DEMO-SEED-BASELINE', DATE_SUB(NOW(), INTERVAL 2 HOUR), 4, 11, 0),
  ('MOCK-20260526-0003', 'demo', 8, 'medium', '100201', '终端出现异常 PowerShell 命令行行为', 'office-win-23', '10.30.5.23', '10.30.5.23', 'endpoint_script', 'ticket', '演示数据：终端命令行样例已转工单。', 'DEMO-SEED-BASELINE', 'legacy-endpoint-command', 'ticketed', 'Execution', 'wazuh-alerts-4.x-2026.05.26/3/batch/DEMO-SEED-BASELINE', DATE_SUB(NOW(), INTERVAL 1 DAY), 5, 12, 0),
  ('MOCK-20260525-0004', 'demo', 3, 'low', '530', '系统日志轮转记录异常但未触发风险升级', 'mac-build-02', '10.40.2.9', '10.40.2.9', 'log_rotation', 'close', '演示数据：低危维护噪声样例已关闭。', 'DEMO-SEED-BASELINE', 'legacy-maintenance-noise', 'closed', 'Collection', 'wazuh-alerts-4.x-2026.05.25/4/batch/DEMO-SEED-BASELINE', DATE_SUB(NOW(), INTERVAL 2 DAY), 5, 12, 0),
  ('MOCK-20260527-0005', 'demo', 12, 'high', '5502', '关键系统配置文件发生变更，需要复核变更单', 'finance-db-01', '10.20.8.21', '10.10.4.12', 'fim_change', 'review', '演示数据：同一规则重复命中用于降噪和聚合展示。', 'DEMO-SEED-BASELINE', 'legacy-config-repeat', 'new', 'Defense Evasion', 'wazuh-alerts-4.x-2026.05.27/5/batch/DEMO-SEED-BASELINE', DATE_SUB(NOW(), INTERVAL 90 MINUTE), 4, 11, 0),
  ('SURICATA-20260527-0001', 'demo', 12, 'high', 'ET-SCAN-001', 'Suricata 检测到面向生产应用的异常端口扫描流量', 'prod-app-01', '10.20.1.15', '203.0.113.44', 'ids_scan', 'review', '演示数据：网络侧 IDS 告警用于事件簇关联。', 'DEMO-SEED-BASELINE', 'legacy-network-scan', 'new', 'Discovery', 'suricata/eve.json/1/batch/DEMO-SEED-BASELINE', DATE_SUB(NOW(), INTERVAL 35 MINUTE), 4, 11, 0),
  ('SURICATA-20260527-0002', 'demo', 10, 'medium', 'ET-POLICY-HTTP', 'Suricata 检测到异常 HTTP User-Agent 访问行为', 'office-win-23', '10.30.5.23', '198.51.100.77', 'http_anomaly', 'review', '演示数据：HTTP 异常访问样例用于证据中心说明。', 'DEMO-SEED-BASELINE', 'legacy-http-anomaly', 'acknowledged', 'Command and Control', 'suricata/eve.json/2/batch/DEMO-SEED-BASELINE', DATE_SUB(NOW(), INTERVAL 70 MINUTE), 4, 11, 0)
ON DUPLICATE KEY UPDATE
  source_type = VALUES(source_type),
  level = VALUES(level),
  severity = VALUES(severity),
  rule_id = VALUES(rule_id),
  rule_description = VALUES(rule_description),
  asset_name = VALUES(asset_name),
  asset_ip = VALUES(asset_ip),
  source_ip = VALUES(source_ip),
  event_type = VALUES(event_type),
  action = VALUES(action),
  evidence_summary = VALUES(evidence_summary),
  batch_id = VALUES(batch_id),
  demo_case_id = VALUES(demo_case_id),
  status = VALUES(status),
  tactic = VALUES(tactic),
  raw_ref = VALUES(raw_ref),
  event_time = VALUES(event_time),
  owner_id = VALUES(owner_id),
  dept_id = VALUES(dept_id),
  deleted = 0;

INSERT INTO soc_external_event
  (event_uid, source_type, event_type, severity, rule_id, rule_name, src_ip, dest_ip, asset_name, asset_ip,
   batch_id, demo_case_id, action, correlation_key, ioc, raw_event, normalized_event, alert_id, status, owner_id, dept_id, event_time, deleted)
VALUES
  ('EXT-SURICATA-20260527-0001', 'suricata', 'ids_alert', 'high', 'ET-SCAN-001', 'ET SCAN Suspicious inbound port scan', '203.0.113.44', '10.20.1.15', 'prod-app-01', '10.20.1.15', 'DEMO-SEED-BASELINE', 'legacy-network-scan', 'detect', 'DEMO-SEED-BASELINE|10.20.1.15|ET-SCAN-001', '203.0.113.44', JSON_OBJECT('batchId', 'DEMO-SEED-BASELINE', 'demoCaseId', 'legacy-network-scan', 'event_type', 'alert', 'proto', 'TCP', 'src_ip', '203.0.113.44', 'dest_ip', '10.20.1.15', 'dest_port', 443, 'signature', 'ET SCAN Suspicious inbound port scan'), JSON_OBJECT('source', 'suricata', 'batchId', 'DEMO-SEED-BASELINE', 'demoCaseId', 'legacy-network-scan', 'event_type', 'ids_alert', 'severity', 'high', 'asset', 'prod-app-01', 'ioc', '203.0.113.44'), (SELECT id FROM soc_alert WHERE alert_uid = 'SURICATA-20260527-0001' LIMIT 1), 'new', 4, 11, DATE_SUB(NOW(), INTERVAL 35 MINUTE), 0),
  ('EXT-SURICATA-20260527-0002', 'suricata', 'http_anomaly', 'medium', 'ET-POLICY-HTTP', 'ET POLICY Unusual HTTP user agent', '198.51.100.77', '10.30.5.23', 'office-win-23', '10.30.5.23', 'DEMO-SEED-BASELINE', 'legacy-http-anomaly', 'review', 'DEMO-SEED-BASELINE|10.30.5.23|ET-POLICY-HTTP', '198.51.100.77', JSON_OBJECT('batchId', 'DEMO-SEED-BASELINE', 'demoCaseId', 'legacy-http-anomaly', 'event_type', 'http', 'src_ip', '198.51.100.77', 'dest_ip', '10.30.5.23', 'hostname', 'intranet.example.local', 'http_user_agent', 'unusual-client'), JSON_OBJECT('source', 'suricata', 'batchId', 'DEMO-SEED-BASELINE', 'demoCaseId', 'legacy-http-anomaly', 'event_type', 'http_anomaly', 'severity', 'medium', 'asset', 'office-win-23', 'ioc', '198.51.100.77'), (SELECT id FROM soc_alert WHERE alert_uid = 'SURICATA-20260527-0002' LIMIT 1), 'reviewing', 4, 11, DATE_SUB(NOW(), INTERVAL 70 MINUTE), 0)
ON DUPLICATE KEY UPDATE
  source_type = VALUES(source_type),
  event_type = VALUES(event_type),
  severity = VALUES(severity),
  rule_id = VALUES(rule_id),
  rule_name = VALUES(rule_name),
  src_ip = VALUES(src_ip),
  dest_ip = VALUES(dest_ip),
  asset_name = VALUES(asset_name),
  asset_ip = VALUES(asset_ip),
  batch_id = VALUES(batch_id),
  demo_case_id = VALUES(demo_case_id),
  action = VALUES(action),
  correlation_key = VALUES(correlation_key),
  ioc = VALUES(ioc),
  raw_event = VALUES(raw_event),
  normalized_event = VALUES(normalized_event),
  alert_id = VALUES(alert_id),
  status = VALUES(status),
  owner_id = VALUES(owner_id),
  dept_id = VALUES(dept_id),
  event_time = VALUES(event_time),
  deleted = 0;

INSERT INTO soc_ticket
  (ticket_no, alert_id, title, severity, status, assignee_id, assignee_name, reviewer_id, review_conclusion, resolution, dept_id, due_at, closed_at, deleted)
VALUES
  ('INC-202605260001', (SELECT id FROM soc_alert WHERE alert_uid = 'MOCK-20260526-0003' LIMIT 1), 'MEDIUM 告警处置：异常 PowerShell 命令行行为（演示数据）', 'medium', '处理中', 5, '运维人员', NULL, NULL, '已隔离终端并导出进程列表，等待安全复核。', 12, DATE_ADD(NOW(), INTERVAL 1 DAY), NULL, 0),
  ('INC-202605250001', (SELECT id FROM soc_alert WHERE alert_uid = 'MOCK-20260525-0004' LIMIT 1), 'LOW 告警处置：系统日志轮转异常（演示数据）', 'low', '已关闭', 5, '运维人员', 6, '确认为维护任务触发，允许关闭。', '已补充维护窗口记录并关闭告警。', 12, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 6 HOUR), 0)
ON DUPLICATE KEY UPDATE
  alert_id = VALUES(alert_id),
  title = VALUES(title),
  severity = VALUES(severity),
  status = VALUES(status),
  assignee_id = VALUES(assignee_id),
  assignee_name = VALUES(assignee_name),
  reviewer_id = VALUES(reviewer_id),
  review_conclusion = VALUES(review_conclusion),
  resolution = VALUES(resolution),
  dept_id = VALUES(dept_id),
  due_at = VALUES(due_at),
  closed_at = VALUES(closed_at),
  deleted = 0;

UPDATE soc_alert alert
JOIN soc_ticket ticket ON ticket.ticket_no = 'INC-202605260001'
SET alert.ticket_id = ticket.id
WHERE alert.alert_uid = 'MOCK-20260526-0003';

UPDATE soc_alert alert
JOIN soc_ticket ticket ON ticket.ticket_no = 'INC-202605250001'
SET alert.ticket_id = ticket.id
WHERE alert.alert_uid = 'MOCK-20260525-0004';

INSERT INTO soc_ticket_timeline (ticket_id, action, from_status, to_status, operator_name, remark, created_at, deleted)
SELECT id, '转工单', NULL, '待分派', 'analyst', '演示数据：异常命令行需运维核查进程和账号来源。', DATE_SUB(NOW(), INTERVAL 1 DAY), 0
FROM soc_ticket WHERE ticket_no = 'INC-202605260001';

INSERT INTO soc_ticket_timeline (ticket_id, action, from_status, to_status, operator_name, remark, created_at, deleted)
SELECT id, '状态流转', '待分派', '处理中', 'operator', '演示数据：已接单处理。', DATE_SUB(NOW(), INTERVAL 20 HOUR), 0
FROM soc_ticket WHERE ticket_no = 'INC-202605260001';

INSERT INTO soc_ticket_timeline (ticket_id, action, from_status, to_status, operator_name, remark, created_at, deleted)
SELECT id, '转工单', NULL, '待分派', 'analyst', '演示数据：日志轮转异常，核对维护计划。', DATE_SUB(NOW(), INTERVAL 2 DAY), 0
FROM soc_ticket WHERE ticket_no = 'INC-202605250001';

INSERT INTO soc_ticket_timeline (ticket_id, action, from_status, to_status, operator_name, remark, created_at, deleted)
SELECT id, '状态流转', '待复核', '已关闭', 'auditor', '演示数据：复核通过。', DATE_SUB(NOW(), INTERVAL 6 HOUR), 0
FROM soc_ticket WHERE ticket_no = 'INC-202605250001';

INSERT INTO soc_report
  (report_no, report_type, period_start, period_end, title, status, summary, recommendation, generated_at, deleted)
VALUES
  ('RPT-DAILY-202605270001', 'daily', CURDATE(), CURDATE(), 'CyberFusion 综合安全日报（演示数据）', 'generated', 'DEMO-SEED-BASELINE：演示数据中发现 2 条待处理高风险告警，工单闭环正常。', '优先处置 critical/high 演示告警，复核认证失败来源和配置文件变更。', NOW(), 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  summary = VALUES(summary),
  recommendation = VALUES(recommendation),
  generated_at = VALUES(generated_at),
  deleted = 0;

INSERT INTO soc_notification_log
  (channel_id, channel_type, event_type, severity, biz_type, biz_id, title, content, target, status, sent_at, deleted)
VALUES
  (1, 'email', 'alert_ticketed', 'high', 'alert', (SELECT id FROM soc_alert WHERE alert_uid = 'MOCK-20260527-0002' LIMIT 1), '高危告警已转工单（演示数据）', 'DEMO-SEED-BASELINE：关键系统配置文件发生变更，已进入工单处置流程。', 'soc-team@example.local', 'DRY_RUN', DATE_SUB(NOW(), INTERVAL 90 MINUTE), 0);

INSERT INTO soc_alert_whitelist
  (rule_name, rule_id, asset_ip, source_ip, severity, reason, enabled, match_count, last_matched_at, owner_id, dept_id, expires_at, deleted)
VALUES
  ('维护窗口内日志轮转噪声', '530', '10.40.2.9', NULL, 'low', '演示数据：构建机维护窗口触发的日志轮转记录，保留审计但默认降噪。', 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 4, 11, DATE_ADD(CURDATE(), INTERVAL 30 DAY), 0),
  ('内部变更单配置调整', '5502', '10.20.8.21', '10.10.4.12', 'high', '演示数据：已登记变更来源，仅在相同资产、来源和规则下做降噪标记。', 1, 2, DATE_SUB(NOW(), INTERVAL 90 MINUTE), 5, 12, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 0);

INSERT INTO soc_vulnerability
  (cve_id, severity, asset_name, asset_ip, software_name, software_version, fix_suggestion, status, source_type, owner_id, dept_id, detected_at, fixed_at, deleted)
VALUES
  ('CVE-2024-3094', 'critical', 'prod-app-01', '10.20.1.15', 'xz-utils', '5.6.1', '演示数据：立即回滚到可信版本并核查供应链来源、包仓库和登录痕迹。', 'open', 'demo', 5, 12, DATE_SUB(NOW(), INTERVAL 3 HOUR), NULL, 0),
  ('CVE-2023-38408', 'high', 'finance-db-01', '10.20.8.21', 'OpenSSH', '8.9p1', '演示数据：升级 OpenSSH，禁用 agent forwarding，并复核堡垒机访问策略。', 'fixing', 'demo', 5, 12, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 0),
  ('CVE-2024-6387', 'high', 'office-win-23', '10.30.5.23', 'OpenSSH for Windows', '9.2', '演示数据：应用厂商安全补丁，限制管理端口来源并复核登录失败日志。', 'reviewing', 'demo', 4, 11, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, 0),
  ('CVE-2022-22965', 'medium', 'mac-build-02', '10.40.2.9', 'Spring Framework', '5.3.17', '演示数据：升级 Spring Framework 并复核构建机对外暴露面。', 'fixed', 'demo', 4, 11, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0)
ON DUPLICATE KEY UPDATE
  severity = VALUES(severity),
  software_version = VALUES(software_version),
  fix_suggestion = VALUES(fix_suggestion),
  status = VALUES(status),
  source_type = VALUES(source_type),
  owner_id = VALUES(owner_id),
  dept_id = VALUES(dept_id),
  detected_at = VALUES(detected_at),
  fixed_at = VALUES(fixed_at),
  deleted = 0;

INSERT INTO soc_baseline_check
  (check_code, category, check_item, asset_name, asset_ip, result, severity, pass_rate, remediation, status, source_type, owner_id, dept_id, checked_at, reviewed_at, deleted)
VALUES
  ('SSH_ROOT_LOGIN', 'SSH', '禁止 root 直接 SSH 登录', 'prod-app-01', '10.20.1.15', 'failed', 'high', 62, '演示数据：设置 PermitRootLogin no，并通过 sudo 审计管理员操作。', 'failed', 'demo', 5, 12, DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL, 0),
  ('PASSWORD_MAX_DAYS', 'PASSWORD', '密码最长有效期不超过 90 天', 'finance-db-01', '10.20.8.21', 'failed', 'medium', 74, '演示数据：调整 PASS_MAX_DAYS 并对数据库运维账号执行轮换。', 'remediating', 'demo', 5, 12, DATE_SUB(NOW(), INTERVAL 4 HOUR), NULL, 0),
  ('FIREWALL_DEFAULT_DENY', 'FIREWALL', '主机防火墙默认拒绝入站访问', 'office-win-23', '10.30.5.23', 'passed', 'low', 96, '演示数据：保持默认拒绝策略，按变更单开放必要端口。', 'passed', 'demo', 4, 11, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), 0),
  ('SENSITIVE_FILE_PERMISSION', 'FILE_PERMISSION', '敏感配置文件权限不高于 600', 'mac-build-02', '10.40.2.9', 'failed', 'medium', 68, '演示数据：收敛构建密钥和配置文件权限，复核构建脚本输出。', 'reviewing', 'demo', 4, 11, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 0),
  ('UNNEEDED_SERVICE', 'SERVICE', '关闭不必要系统服务', 'prod-app-01', '10.20.1.15', 'failed', 'medium', 71, '演示数据：关闭未登记的调试服务并补充资产暴露面记录。', 'failed', 'demo', 5, 12, DATE_SUB(NOW(), INTERVAL 8 HOUR), NULL, 0)
ON DUPLICATE KEY UPDATE
  category = VALUES(category),
  check_item = VALUES(check_item),
  result = VALUES(result),
  severity = VALUES(severity),
  pass_rate = VALUES(pass_rate),
  remediation = VALUES(remediation),
  status = VALUES(status),
  source_type = VALUES(source_type),
  owner_id = VALUES(owner_id),
  dept_id = VALUES(dept_id),
  checked_at = VALUES(checked_at),
  reviewed_at = VALUES(reviewed_at),
  deleted = 0;

INSERT INTO soc_file_integrity_event
  (event_uid, action, severity, hostname, asset_ip, file_path, rule_name, status, source_type, owner_id, dept_id, event_time, reviewed_at, deleted)
VALUES
  ('FIM-20260527-0001', 'modified', 'high', 'finance-db-01', '10.20.8.21', '/etc/ssh/sshd_config', '关键 SSH 配置变更', 'new', 'demo', 5, 12, DATE_SUB(NOW(), INTERVAL 35 MINUTE), NULL, 0),
  ('FIM-20260527-0002', 'permission', 'medium', 'prod-app-01', '10.20.1.15', '/etc/shadow', '敏感账号文件权限变化', 'reviewing', 'demo', 5, 12, DATE_SUB(NOW(), INTERVAL 90 MINUTE), NULL, 0),
  ('FIM-20260526-0003', 'created', 'medium', 'office-win-23', '10.30.5.23', 'C:\\ProgramData\\Startup\\debug.ps1', '启动目录新增脚本', 'confirmed', 'demo', 4, 11, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 16 HOUR), 0),
  ('FIM-20260525-0004', 'deleted', 'low', 'mac-build-02', '10.40.2.9', '/usr/local/build/tmp/token.cache', '构建缓存文件删除', 'ignored', 'demo', 4, 11, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0)
ON DUPLICATE KEY UPDATE
  action = VALUES(action),
  severity = VALUES(severity),
  hostname = VALUES(hostname),
  asset_ip = VALUES(asset_ip),
  file_path = VALUES(file_path),
  rule_name = VALUES(rule_name),
  status = VALUES(status),
  source_type = VALUES(source_type),
  owner_id = VALUES(owner_id),
  dept_id = VALUES(dept_id),
  event_time = VALUES(event_time),
  reviewed_at = VALUES(reviewed_at),
  deleted = 0;
