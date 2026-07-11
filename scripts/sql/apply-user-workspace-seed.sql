-- User-host workspace seed for local validation.
-- Zhang Yan receives the current Mac-host records; the other three users receive
-- clearly labelled validation fixtures until remote devices are enrolled.

-- The script contains Chinese user names and validation labels. Force the
-- connection charset so direct mysql execution cannot persist mojibake.
SET NAMES utf8mb4;

-- MySQL 8 deployments differ on ADD COLUMN IF NOT EXISTS support, so use
-- information_schema guards to keep this migration idempotent everywhere.
SET @migration_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_ticket' AND column_name = 'owner_id') = 0,
  'ALTER TABLE soc_ticket ADD COLUMN owner_id BIGINT NULL AFTER alert_id', 'SELECT 1');
PREPARE migration_stmt FROM @migration_sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @migration_sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'soc_ticket' AND index_name = 'idx_soc_ticket_owner') = 0,
  'ALTER TABLE soc_ticket ADD INDEX idx_soc_ticket_owner (owner_id, dept_id)', 'SELECT 1');
PREPARE migration_stmt FROM @migration_sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @migration_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_report' AND column_name = 'owner_id') = 0,
  'ALTER TABLE soc_report ADD COLUMN owner_id BIGINT NULL AFTER report_type', 'SELECT 1');
PREPARE migration_stmt FROM @migration_sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @migration_sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_report' AND column_name = 'dept_id') = 0,
  'ALTER TABLE soc_report ADD COLUMN dept_id BIGINT NULL AFTER owner_id', 'SELECT 1');
PREPARE migration_stmt FROM @migration_sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @migration_sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'soc_report' AND index_name = 'idx_soc_report_owner') = 0,
  'ALTER TABLE soc_report ADD INDEX idx_soc_report_owner (owner_id, dept_id)', 'SELECT 1');
PREPARE migration_stmt FROM @migration_sql; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

INSERT INTO sys_user (username, password_hash, nickname, email, mobile, dept_id, post_id, status)
VALUES
  ('zhangyan', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '张彥', 'zhangyan@example.local', NULL, 12, 1, 1),
  ('songsong', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '松松', 'songsong@example.local', NULL, 12, 1, 1),
  ('laocao', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '老曹', 'laocao@example.local', NULL, 12, 1, 1),
  ('liuge', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '刘哥', 'liuge@example.local', NULL, 12, 1, 1)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), email = VALUES(email), dept_id = VALUES(dept_id), status = VALUES(status), deleted = 0;

SET @zhangyan_id := (SELECT id FROM sys_user WHERE username = 'zhangyan' AND deleted = 0 LIMIT 1);
SET @songsong_id := (SELECT id FROM sys_user WHERE username = 'songsong' AND deleted = 0 LIMIT 1);
SET @laocao_id := (SELECT id FROM sys_user WHERE username = 'laocao' AND deleted = 0 LIMIT 1);
SET @liuge_id := (SELECT id FROM sys_user WHERE username = 'liuge' AND deleted = 0 LIMIT 1);

DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
WHERE u.username IN ('zhangyan', 'songsong', 'laocao', 'liuge') AND ur.role_id <> 10;
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 10 FROM sys_user
WHERE username IN ('zhangyan', 'songsong', 'laocao', 'liuge') AND deleted = 0
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- Existing owner 1 data comes from the current local Mac host and must belong
-- to the ordinary user who owns that computer, not to the platform operator.
UPDATE soc_asset SET owner_id = @zhangyan_id, owner_name = '张彥', dept_id = 12 WHERE owner_id = 1 AND deleted = 0;
UPDATE soc_host_agent SET owner_id = @zhangyan_id, dept_id = 12 WHERE owner_id = 1 AND deleted = 0;
UPDATE soc_alert SET owner_id = @zhangyan_id, dept_id = 12 WHERE owner_id = 1 AND deleted = 0;
UPDATE soc_external_event SET owner_id = @zhangyan_id, dept_id = 12 WHERE owner_id = 1 AND deleted = 0;
UPDATE soc_file_integrity_event SET owner_id = @zhangyan_id, dept_id = 12 WHERE owner_id = 1 AND deleted = 0;
UPDATE soc_baseline_check SET owner_id = @zhangyan_id, dept_id = 12 WHERE owner_id = 1 AND deleted = 0;
UPDATE soc_incident_cluster SET owner_id = @zhangyan_id, dept_id = 12 WHERE owner_id = 1 AND deleted = 0;
UPDATE soc_asset SET owner_name = '张彥', dept_id = 12 WHERE owner_id = @zhangyan_id AND deleted = 0;

-- Prior validation records are partitioned to two test users. They remain
-- explicitly marked by their existing non-host source, never as real devices.
UPDATE soc_asset SET owner_id = @songsong_id, owner_name = '松松', dept_id = 12 WHERE owner_id = 4 AND deleted = 0;
UPDATE soc_host_agent SET owner_id = @songsong_id, dept_id = 12 WHERE owner_id = 4 AND deleted = 0;
UPDATE soc_alert SET owner_id = @songsong_id, dept_id = 12 WHERE owner_id = 4 AND deleted = 0;
UPDATE soc_external_event SET owner_id = @songsong_id, dept_id = 12 WHERE owner_id = 4 AND deleted = 0;
UPDATE soc_file_integrity_event SET owner_id = @songsong_id, dept_id = 12 WHERE owner_id = 4 AND deleted = 0;
UPDATE soc_baseline_check SET owner_id = @songsong_id, dept_id = 12 WHERE owner_id = 4 AND deleted = 0;
UPDATE soc_incident_cluster SET owner_id = @songsong_id, dept_id = 12 WHERE owner_id = 4 AND deleted = 0;

UPDATE soc_asset SET owner_id = @laocao_id, owner_name = '老曹', dept_id = 12 WHERE owner_id = 5 AND deleted = 0;
UPDATE soc_host_agent SET owner_id = @laocao_id, dept_id = 12 WHERE owner_id = 5 AND deleted = 0;
UPDATE soc_alert SET owner_id = @laocao_id, dept_id = 12 WHERE owner_id = 5 AND deleted = 0;
UPDATE soc_external_event SET owner_id = @laocao_id, dept_id = 12 WHERE owner_id = 5 AND deleted = 0;
UPDATE soc_file_integrity_event SET owner_id = @laocao_id, dept_id = 12 WHERE owner_id = 5 AND deleted = 0;
UPDATE soc_baseline_check SET owner_id = @laocao_id, dept_id = 12 WHERE owner_id = 5 AND deleted = 0;
UPDATE soc_incident_cluster SET owner_id = @laocao_id, dept_id = 12 WHERE owner_id = 5 AND deleted = 0;

-- Historical demo/mock rows are retained solely as clearly labelled validation
-- fixtures. They must remain visible through the normal user workspaces so the
-- card counts and each business page describe the same data boundary.
UPDATE soc_asset
SET source_type = 'validation-fixture', owner_name = CASE owner_id
  WHEN @songsong_id THEN '松松' WHEN @laocao_id THEN '老曹' ELSE owner_name END
WHERE owner_id IN (@songsong_id, @laocao_id) AND deleted = 0;
UPDATE soc_alert
SET source_type = 'validation-fixture',
    evidence_summary = CONCAT('预置验证数据：', COALESCE(evidence_summary, rule_description, '用于用户工作区验证。'))
WHERE owner_id IN (@songsong_id, @laocao_id) AND deleted = 0;
UPDATE soc_external_event
SET source_type = 'validation-fixture',
    normalized_event = JSON_SET(COALESCE(normalized_event, JSON_OBJECT()), '$.source', 'validation-fixture', '$.validation', TRUE)
WHERE owner_id IN (@songsong_id, @laocao_id) AND deleted = 0;
UPDATE soc_file_integrity_event SET source_type = 'validation-fixture'
WHERE owner_id IN (@songsong_id, @laocao_id) AND deleted = 0;
UPDATE soc_baseline_check SET source_type = 'validation-fixture'
WHERE owner_id IN (@songsong_id, @laocao_id) AND deleted = 0;
UPDATE soc_incident_cluster
SET source_summary = 'validation-fixture', source_types = 'validation-fixture',
    summary = CONCAT('预置验证数据：', COALESCE(summary, '用于用户工作区验证。'))
WHERE owner_id IN (@songsong_id, @laocao_id) AND deleted = 0;

-- Keep each fixture event attached to an asset that belongs to the same user.
UPDATE soc_alert
SET asset_name = 'office-win-23', asset_ip = '10.30.5.23'
WHERE owner_id = @songsong_id AND asset_ip IN ('10.20.1.15', '10.20.8.21');
UPDATE soc_alert
SET asset_name = 'mac-build-02', asset_ip = '10.40.2.9'
WHERE owner_id = @songsong_id AND asset_ip = '10.30.5.23' AND alert_uid = 'SURICATA-20260527-0001';
UPDATE soc_alert
SET asset_name = 'prod-app-01', asset_ip = '10.20.1.15'
WHERE owner_id = @laocao_id AND asset_ip = '10.30.5.23';
UPDATE soc_alert
SET asset_name = 'finance-db-01', asset_ip = '10.20.8.21'
WHERE owner_id = @laocao_id AND asset_ip = '10.40.2.9';
UPDATE soc_external_event
SET asset_name = 'office-win-23', asset_ip = '10.30.5.23', dest_ip = '10.30.5.23'
WHERE owner_id = @songsong_id AND asset_ip IN ('10.20.1.15', '10.20.8.21');

UPDATE soc_ticket t
JOIN soc_alert a ON a.id = t.alert_id
SET t.owner_id = a.owner_id, t.dept_id = a.dept_id
WHERE t.deleted = 0;

-- Liu Ge has no enrolled remote host. These are deliberate local validation
-- fixtures and are identified as such in the source field and labels.
INSERT INTO soc_asset (hostname, ip, os_type, risk_score, risk_level, source_type, dept_id, dept_name, owner_id, owner_name, open_alert_count, last_seen_at)
VALUES ('liuge-validation-host', '198.51.100.40', 'macOS', 48, 'medium', 'validation-fixture', 12, '基础设施运维组', @liuge_id, '刘哥', 1, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE hostname = VALUES(hostname), risk_score = VALUES(risk_score), risk_level = VALUES(risk_level), source_type = VALUES(source_type), owner_id = VALUES(owner_id), owner_name = VALUES(owner_name), dept_id = VALUES(dept_id), dept_name = VALUES(dept_name), open_alert_count = VALUES(open_alert_count), last_seen_at = VALUES(last_seen_at), deleted = 0;

INSERT INTO soc_host_agent (agent_id, agent_name, hostname, os_type, os_version, architecture, agent_version, ip_addresses_json, labels_json, status, enabled, token_hash, last_ip, queue_depth, queue_bytes, collected_count, sent_count, failed_count, first_seen_at, last_seen_at, owner_id, dept_id)
VALUES ('validation-liuge-macos', '刘哥 - 本机保护验证', 'liuge-validation-host', 'macos', 'validation fixture', 'arm64', '0.1.0-validation', JSON_ARRAY('198.51.100.40'), JSON_ARRAY('validation-fixture', 'not-enrolled'), 'offline', 0, 'validation-no-token', '198.51.100.40', 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, NULL, @liuge_id, 12)
ON DUPLICATE KEY UPDATE agent_name = VALUES(agent_name), hostname = VALUES(hostname), status = VALUES(status), enabled = VALUES(enabled), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), deleted = 0;

INSERT INTO soc_alert (alert_uid, source_type, level, severity, rule_id, rule_description, asset_name, asset_ip, event_type, action, evidence_summary, batch_id, correlation_key, status, event_time, owner_id, dept_id)
VALUES ('VALIDATION-LIUGE-ALERT-001', 'validation-fixture', 5, 'medium', 'LOCAL-VALIDATION-001', '本机保护预置验证记录，不代表远程真实主机告警。', 'liuge-validation-host', '198.51.100.40', 'local_protection_validation', 'review', '预置验证数据：用于演示用户工作区隔离和处置流程。', 'VALIDATION-LIUGE', 'validation-liuge-001', 'new', CURRENT_TIMESTAMP, @liuge_id, 12)
ON DUPLICATE KEY UPDATE source_type = VALUES(source_type), level = VALUES(level), severity = VALUES(severity), rule_id = VALUES(rule_id), rule_description = VALUES(rule_description), asset_name = VALUES(asset_name), asset_ip = VALUES(asset_ip), event_type = VALUES(event_type), action = VALUES(action), evidence_summary = VALUES(evidence_summary), batch_id = VALUES(batch_id), correlation_key = VALUES(correlation_key), status = VALUES(status), event_time = VALUES(event_time), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), deleted = 0;

INSERT INTO soc_external_event (event_uid, source_type, event_type, severity, rule_id, rule_name, asset_name, asset_ip, batch_id, correlation_key, raw_event, normalized_event, status, owner_id, dept_id, event_time)
VALUES ('VALIDATION-LIUGE-EVENT-001', 'validation-fixture', 'local_protection_validation', 'medium', 'LOCAL-VALIDATION-001', '用户工作区预置验证记录', 'liuge-validation-host', '198.51.100.40', 'VALIDATION-LIUGE', 'validation-liuge-001', JSON_OBJECT('validation', true), JSON_OBJECT('source', 'validation-fixture'), 'new', @liuge_id, 12, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE source_type = VALUES(source_type), event_type = VALUES(event_type), severity = VALUES(severity), rule_id = VALUES(rule_id), rule_name = VALUES(rule_name), asset_name = VALUES(asset_name), asset_ip = VALUES(asset_ip), batch_id = VALUES(batch_id), correlation_key = VALUES(correlation_key), raw_event = VALUES(raw_event), normalized_event = VALUES(normalized_event), status = VALUES(status), event_time = VALUES(event_time), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), deleted = 0;

INSERT INTO soc_file_integrity_event (event_uid, action, severity, hostname, asset_ip, file_path, rule_name, status, source_type, owner_id, dept_id, event_time)
VALUES ('VALIDATION-LIUGE-FIM-001', 'modified', 'info', 'liuge-validation-host', '198.51.100.40', '/Users/example/.config/security.conf', '用户工作区预置文件变更', 'new', 'validation-fixture', @liuge_id, 12, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE action = VALUES(action), severity = VALUES(severity), hostname = VALUES(hostname), asset_ip = VALUES(asset_ip), file_path = VALUES(file_path), rule_name = VALUES(rule_name), status = VALUES(status), source_type = VALUES(source_type), event_time = VALUES(event_time), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), deleted = 0;

INSERT INTO soc_baseline_check (check_code, category, check_item, asset_name, asset_ip, result, severity, pass_rate, remediation, status, source_type, owner_id, dept_id, checked_at)
VALUES ('VALIDATION-LIUGE-PROTECTION', 'host', '本机保护预置验证', 'liuge-validation-host', '198.51.100.40', 'failed', 'medium', 85, '这是预置验证记录；远程主机接入后由真实检查结果替换。', 'failed', 'validation-fixture', @liuge_id, 12, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE category = VALUES(category), check_item = VALUES(check_item), asset_name = VALUES(asset_name), asset_ip = VALUES(asset_ip), result = VALUES(result), severity = VALUES(severity), pass_rate = VALUES(pass_rate), remediation = VALUES(remediation), status = VALUES(status), source_type = VALUES(source_type), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), checked_at = VALUES(checked_at), deleted = 0;

INSERT INTO soc_incident_cluster (cluster_no, title, summary, recommendation, severity, status, score, correlation_key, asset_ip, hostname, primary_asset_ip, primary_hostname, batch_id, source_summary, source_types, evidence_count, event_count, alert_count, vulnerability_count, first_seen_at, last_seen_at, owner_id, dept_id)
VALUES ('CL-VALIDATION-LIUGE-001', '刘哥本机保护预置验证', '用于验证用户卡片、数据权限和处置闭环，不代表远程真实安全事件。', '远程设备接入后执行真实采集并复核该预置记录。', 'medium', 'open', 48, 'validation-liuge-001', '198.51.100.40', 'liuge-validation-host', '198.51.100.40', 'liuge-validation-host', 'VALIDATION-LIUGE', 'validation-fixture', 'validation-fixture', 2, 1, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, @liuge_id, 12)
ON DUPLICATE KEY UPDATE title = VALUES(title), summary = VALUES(summary), recommendation = VALUES(recommendation), severity = VALUES(severity), status = VALUES(status), score = VALUES(score), asset_ip = VALUES(asset_ip), hostname = VALUES(hostname), primary_asset_ip = VALUES(primary_asset_ip), primary_hostname = VALUES(primary_hostname), batch_id = VALUES(batch_id), source_summary = VALUES(source_summary), source_types = VALUES(source_types), evidence_count = VALUES(evidence_count), event_count = VALUES(event_count), alert_count = VALUES(alert_count), vulnerability_count = VALUES(vulnerability_count), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), last_seen_at = VALUES(last_seen_at), deleted = 0;

-- One labelled report per fixture workspace proves that reports, exports and
-- previews follow the same owner boundary as assets, alerts and tickets.
INSERT INTO soc_report (report_no, report_type, owner_id, dept_id, period_start, period_end, title, status, summary, recommendation, generated_at)
VALUES
  ('RPT-VALIDATION-SONGSONG', 'weekly', @songsong_id, 12, CURDATE(), CURDATE(), '松松预置验证报告', 'generated', '预置验证数据：用于验证松松工作区的报表隔离。', '远程主机接入后以真实采集数据替换。', CURRENT_TIMESTAMP),
  ('RPT-VALIDATION-LAOCAO', 'weekly', @laocao_id, 12, CURDATE(), CURDATE(), '老曹预置验证报告', 'generated', '预置验证数据：用于验证老曹工作区的报表隔离。', '远程主机接入后以真实采集数据替换。', CURRENT_TIMESTAMP),
  ('RPT-VALIDATION-LIUGE', 'weekly', @liuge_id, 12, CURDATE(), CURDATE(), '刘哥预置验证报告', 'generated', '预置验证数据：用于验证刘哥工作区的报表隔离。', '远程主机接入后以真实采集数据替换。', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), title = VALUES(title), status = VALUES(status), summary = VALUES(summary), recommendation = VALUES(recommendation), generated_at = VALUES(generated_at), deleted = 0;
