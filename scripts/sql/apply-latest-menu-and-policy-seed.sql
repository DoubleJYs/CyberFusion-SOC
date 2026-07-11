-- CyberFusion P4.5 visibility menu/role seed patch.
-- Purpose: make latest SOC menu and permission entries available in
-- already-initialized databases without deleting business data.
--
-- For full runtime refresh on an existing local database, run this after:
--   mysql cyberfusion_soc < sql/schema.sql
--   mysql cyberfusion_soc < sql/data.sql
--
-- This patch avoids business-data DELETE/TRUNCATE and is safe to rerun.
-- It only removes stale role-menu grants for simple user roles so employee
-- accounts do not retain legacy SOC/system menus in already-initialized DBs.
-- Safe to rerun on MySQL 8.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS soc_detection_rule_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_type VARCHAR(32) NOT NULL,
  rule_id VARCHAR(160) NOT NULL,
  rule_name VARCHAR(255) NOT NULL,
  detection_category VARCHAR(32) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  detection_summary VARCHAR(1000) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  enabled TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  approved_by BIGINT NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_detection_rule_policy_source_rule (source_type, rule_id, deleted),
  KEY idx_soc_detection_rule_policy_status (status, enabled),
  KEY idx_soc_detection_rule_policy_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Alert noise was retired as a standalone expert-view page. Preserve the alert
-- history and whitelist data, but remove the obsolete menu and button grants.
DELETE FROM sys_role_menu WHERE menu_id IN (2010, 2410, 2411);
DELETE FROM sys_menu WHERE id IN (2010, 2410, 2411);

INSERT INTO sys_role (id, role_code, role_name, data_scope, status)
VALUES
  (7, 'super_admin', '超级管理员兼容角色', 'all', 1),
  (8, 'security_engineer', '安全工程师', 'all', 1),
  (9, 'analyst', '运营分析员', 'dept_tree', 1),
  (10, 'employee', '员工安全管家用户', 'self', 1),
  (11, 'customer', '客户演示用户', 'self', 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), data_scope = VALUES(data_scope), status = VALUES(status);

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
  (7, 1, 7),
  (8, 3, 8),
  (9, 4, 9),
  (10, 5, 10),
  (11, 2, 11)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, type, permission, sort, visible, status)
VALUES
  (2013, 2020, '安全验证中心', '/soc/demo-range', 'soc/DemoRangeView', 'Operation', 'menu', 'soc:demo-range:view', 18, 1, 1),
  (2019, 2020, '每日处理', '/soc/daily-recommendations', 'soc/DailyRecommendationView', 'Calendar', 'menu', 'soc:recommendation:view', 19, 1, 1),
  (2014, 2020, '检测内容规则设置', '/soc/rules', 'soc/RuleCenterView', 'List', 'menu', 'soc:rules:view', 25, 1, 1),
  (2015, 2020, '策略与规则中心', '/soc/policies', 'soc/PolicyCenterView', 'SetUp', 'menu', 'soc:policy:list', 28, 1, 1),
  (2018, 0, 'Agent 中心', '/soc/agents', NULL, 'Connection', 'directory', 'soc:agent:view', 6, 1, 1),
  (2024, 2018, 'Agent 安装命令设置与建立', '/soc/agents/install', 'soc/HostAgentInstallView', 'SetUp', 'menu', 'soc:agent:register', 10, 1, 1),
  (2025, 2018, 'Agent 管理', '/soc/agents', 'soc/HostAgentView', 'Connection', 'menu', 'soc:agent:view', 20, 1, 1),
  (2414, 2013, '导入演示数据', NULL, NULL, NULL, 'button', 'soc:demo-range:import', 11, 0, 1),
  (2415, 2015, '策略新增', NULL, NULL, NULL, 'button', 'soc:policy:create', 11, 0, 1),
  (2416, 2015, '策略编辑', NULL, NULL, NULL, 'button', 'soc:policy:update', 12, 0, 1),
  (2417, 2015, '策略发布', NULL, NULL, NULL, 'button', 'soc:policy:publish', 13, 0, 1),
  (2418, 2015, '策略停用', NULL, NULL, NULL, 'button', 'soc:policy:disable', 14, 0, 1),
  (2419, 2015, '策略审计', NULL, NULL, NULL, 'button', 'soc:policy:audit', 15, 0, 1),
  (2420, 2015, '应用处置剧本', NULL, NULL, NULL, 'button', 'soc:playbook:apply', 16, 0, 1),
  (2421, 2004, '处置任务流转', NULL, NULL, NULL, 'button', 'soc:ticket-task:update', 16, 0, 1),
  (2422, 2015, '风险策略查看', NULL, NULL, NULL, 'button', 'soc:risk-policy:list', 21, 0, 1),
  (2423, 2015, '风险策略新增', NULL, NULL, NULL, 'button', 'soc:risk-policy:create', 22, 0, 1),
  (2424, 2015, '风险策略编辑', NULL, NULL, NULL, 'button', 'soc:risk-policy:update', 23, 0, 1),
  (2425, 2015, '风险策略发布', NULL, NULL, NULL, 'button', 'soc:risk-policy:publish', 24, 0, 1),
  (2426, 2015, '风险策略停用', NULL, NULL, NULL, 'button', 'soc:risk-policy:disable', 25, 0, 1),
  (2427, 2003, '资产风险重算', NULL, NULL, NULL, 'button', 'soc:risk-score:recalculate', 12, 0, 1),
  (2428, 2003, '资产风险画像', NULL, NULL, NULL, 'button', 'soc:risk-score:view', 13, 0, 1),
  (2439, 2015, '算法治理查看', NULL, NULL, NULL, 'button', 'soc:algorithm:view', 41, 0, 1),
  (2440, 2015, '算法回放评估', NULL, NULL, NULL, 'button', 'soc:algorithm:replay', 42, 0, 1),
  (2441, 2015, '算法评估记录', NULL, NULL, NULL, 'button', 'soc:algorithm:evaluation', 43, 0, 1),
  (2450, 2013, '清除演示数据', NULL, NULL, NULL, 'button', 'soc:demo-range:clear', 12, 0, 1),
  (2451, 2025, 'Agent 管理查看', NULL, NULL, NULL, 'button', 'soc:agent:view', 21, 0, 1),
  (2452, 2024, 'Agent 注册', NULL, NULL, NULL, 'button', 'soc:agent:register', 22, 0, 1),
  (2453, 2025, 'Agent 启停', NULL, NULL, NULL, 'button', 'soc:agent:manage', 23, 0, 1),
  (2600, 0, '员工端', '/client', NULL, 'Monitor', 'directory', 'client:view', 90, 1, 1),
  (2601, 2600, '我的电脑', '/client/workbench', 'client/ClientWorkbenchView', 'Monitor', 'menu', 'client:workbench:view', 10, 1, 1),
  (2602, 2600, '我的待办', '/client/tasks', 'client/ClientOperationsView', 'Tickets', 'menu', 'client:tasks:view', 20, 1, 1),
  (2603, 2600, '提交日志', '/client/data-report', 'client/ClientDataReportView', 'DocumentChecked', 'menu', 'client:data-report:view', 30, 1, 1),
  (2604, 2600, '安全工具', '/client/local-range', 'client/ClientLocalRangeView', 'Tools', 'menu', 'client:local-range:view', 40, 1, 1),
  (2605, 2600, '安全日志', '/client/security-logs', 'client/ClientSecurityLogsView', 'Document', 'menu', 'client:security-logs:view', 50, 1, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  name = VALUES(name),
  path = VALUES(path),
  component = VALUES(component),
  icon = VALUES(icon),
  type = VALUES(type),
  permission = VALUES(permission),
  sort = VALUES(sort),
  visible = VALUES(visible),
  status = VALUES(status);

-- admin and security_admin can maintain policy, adapter, and playbook entries.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (2013, 2019, 2014, 2015, 2018, 2024, 2025, 2414, 2450, 2415, 2416, 2417, 2418, 2419, 2420, 2421, 2422, 2423, 2424, 2425, 2426, 2427, 2428, 2439, 2440, 2441, 2451, 2452, 2453)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 7, id FROM sys_menu
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu WHERE id IN (2013, 2019, 2014, 2015, 2018, 2024, 2025, 2414, 2450, 2415, 2416, 2417, 2418, 2419, 2420, 2421, 2422, 2423, 2424, 2425, 2426, 2427, 2428, 2439, 2440, 2441, 2451, 2452, 2453)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM (
  SELECT 1 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (2018, 2024, 2025, 2451, 2452, 2453)
  UNION ALL
  SELECT 3 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (2018, 2024, 2025, 2451, 2452, 2453)
  UNION ALL
  SELECT 4 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (2018, 2025, 2451)
  UNION ALL
  SELECT 7 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (2018, 2024, 2025, 2451, 2452, 2453)
  UNION ALL
  SELECT 8 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (2018, 2024, 2025, 2451, 2452, 2453)
  UNION ALL
  SELECT 9 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (2018, 2025, 2451)
) AS latest_agent_permission_seed
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 8, menu_id FROM sys_role_menu WHERE role_id = 3
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- security_analyst can operate the demo/alert/ticket/report path and apply playbooks,
-- but cannot publish policy or adapter definitions.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (2013, 2019, 2014, 2018, 2025, 2414, 2420, 2421, 2427, 2428, 2451)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (2441)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 9, menu_id FROM sys_role_menu WHERE role_id = 4
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM (
  SELECT 1 AS role_id, id AS menu_id FROM sys_menu WHERE id BETWEEN 2600 AND 2605
  UNION ALL
  SELECT 3 AS role_id, id AS menu_id FROM sys_menu WHERE id BETWEEN 2600 AND 2605
  UNION ALL
  SELECT 5 AS role_id, id AS menu_id FROM sys_menu WHERE id BETWEEN 2600 AND 2605
  UNION ALL
  SELECT 7 AS role_id, id AS menu_id FROM sys_menu WHERE id BETWEEN 2600 AND 2605
  UNION ALL
  SELECT 10 AS role_id, id AS menu_id FROM sys_menu WHERE id BETWEEN 2600 AND 2605
) AS latest_client_menu_seed
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

DELETE FROM sys_role_menu
WHERE role_id IN (2, 5, 10, 11)
  AND menu_id NOT BETWEEN 2600 AND 2605;

INSERT INTO soc_correlation_rule
  (id, rule_code, rule_key, rule_name, description, enabled, status, version, rule_type, time_window_minutes, min_score, min_count, group_by_fields_json, source_types_json, event_types_json, group_by_json, threshold, timeframe_seconds, sequence_json, severity_min, severity_floor, weights_json, safety_note, approved_by, approved_at)
VALUES
  (7, 'host_agent_event_chain', 'host_agent_event_chain', 'Host Agent 主机事件链路', '将 Mac 和 Windows Host Agent 上报的同资产主机事件与自动生成告警聚合为真实主机安全事件簇。', 1, 'active', 1, 'event_count', 30, 50, 2, JSON_ARRAY('assetIp'), JSON_ARRAY('macos-agent', 'windows-agent', 'host-agent'), NULL, JSON_ARRAY('assetIp'), 2, 1800, NULL, 'medium', 'medium', JSON_OBJECT('sameAssetIp', 30, 'withinTimeWindow', 15, 'highSeverity', 10, 'linkedAlertOrVulnerability', 10), '仅聚合 Host Agent 已上报的结构化主机事件和统一告警，不下发主机命令。', 1, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
  rule_code = VALUES(rule_code),
  rule_key = VALUES(rule_key),
  rule_name = VALUES(rule_name),
  description = VALUES(description),
  enabled = VALUES(enabled),
  status = VALUES(status),
  version = VALUES(version),
  rule_type = VALUES(rule_type),
  time_window_minutes = VALUES(time_window_minutes),
  min_score = VALUES(min_score),
  min_count = VALUES(min_count),
  group_by_fields_json = VALUES(group_by_fields_json),
  source_types_json = VALUES(source_types_json),
  event_types_json = VALUES(event_types_json),
  group_by_json = VALUES(group_by_json),
  threshold = VALUES(threshold),
  timeframe_seconds = VALUES(timeframe_seconds),
  sequence_json = VALUES(sequence_json),
  severity_min = VALUES(severity_min),
  severity_floor = VALUES(severity_floor),
  weights_json = VALUES(weights_json),
  safety_note = VALUES(safety_note);

CREATE TABLE IF NOT EXISTS soc_host_agent (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  agent_id VARCHAR(128) NOT NULL,
  agent_name VARCHAR(128) NULL,
  hostname VARCHAR(128) NOT NULL,
  os_type VARCHAR(32) NOT NULL,
  os_version VARCHAR(128) NULL,
  architecture VARCHAR(64) NULL,
  agent_version VARCHAR(64) NOT NULL,
  ip_addresses_json JSON NULL,
  mac_addresses_json JSON NULL,
  labels_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'offline',
  enabled TINYINT NOT NULL DEFAULT 1,
  token_hash VARCHAR(255) NOT NULL,
  last_ip VARCHAR(64) NULL,
  queue_depth INT NOT NULL DEFAULT 0,
  queue_bytes BIGINT NOT NULL DEFAULT 0,
  collected_count BIGINT NOT NULL DEFAULT 0,
  sent_count BIGINT NOT NULL DEFAULT 0,
  failed_count BIGINT NOT NULL DEFAULT 0,
  first_seen_at DATETIME NOT NULL,
  last_seen_at DATETIME NULL,
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_host_agent_id (agent_id),
  KEY idx_soc_host_agent_os_status (os_type, status),
  KEY idx_soc_host_agent_last_seen (last_seen_at),
  KEY idx_soc_host_agent_scope (owner_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @host_agent_enabled_column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'soc_host_agent'
    AND COLUMN_NAME = 'enabled'
);

CREATE TABLE IF NOT EXISTS soc_fim_watch_path (
  id BIGINT NOT NULL AUTO_INCREMENT,
  display_name VARCHAR(128) NOT NULL,
  host_name VARCHAR(128) NOT NULL,
  os_type VARCHAR(16) NOT NULL,
  watch_path VARCHAR(500) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  is_recursive TINYINT NOT NULL DEFAULT 1,
  max_entries INT NOT NULL DEFAULT 500,
  status VARCHAR(16) NOT NULL DEFAULT 'draft',
  enabled TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  approved_by BIGINT NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_soc_fim_watch_host_path (host_name, watch_path, deleted),
  KEY idx_soc_fim_watch_target (os_type, host_name, status, enabled),
  KEY idx_soc_fim_watch_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Host Agent FIM authorized watch paths';
SET @host_agent_enabled_ddl := IF(@host_agent_enabled_column_exists = 0,
  'ALTER TABLE soc_host_agent ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER status',
  'SELECT 1'
);
PREPARE host_agent_enabled_stmt FROM @host_agent_enabled_ddl;
EXECUTE host_agent_enabled_stmt;
DEALLOCATE PREPARE host_agent_enabled_stmt;

CREATE TABLE IF NOT EXISTS soc_ingest_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id VARCHAR(128) NOT NULL,
  agent_id VARCHAR(128) NOT NULL,
  agent_db_id BIGINT NULL,
  source_os VARCHAR(32) NULL,
  ingest_type VARCHAR(32) NOT NULL,
  item_count INT NOT NULL DEFAULT 0,
  accepted_count INT NOT NULL DEFAULT 0,
  duplicate_count INT NOT NULL DEFAULT 0,
  rejected_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'accepted',
  started_at DATETIME NOT NULL,
  finished_at DATETIME NULL,
  error_message VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_soc_ingest_batch_id (batch_id),
  KEY idx_soc_ingest_batch_agent (agent_id, created_at),
  KEY idx_soc_ingest_batch_type_status (ingest_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_ingest_reject_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id VARCHAR(128) NULL,
  agent_id VARCHAR(128) NULL,
  event_uid VARCHAR(128) NULL,
  ingest_type VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  payload_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_soc_ingest_reject_agent (agent_id, created_at),
  KEY idx_soc_ingest_reject_batch (batch_id),
  KEY idx_soc_ingest_reject_reason (reason_code, ingest_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_algorithm_evaluation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evaluation_no VARCHAR(64) NOT NULL,
  algorithm_type VARCHAR(64) NOT NULL,
  policy_id BIGINT NULL,
  policy_version INT NULL,
  batch_id VARCHAR(128) NULL,
  time_range_start DATETIME NULL,
  time_range_end DATETIME NULL,
  input_count INT NOT NULL DEFAULT 0,
  output_count INT NOT NULL DEFAULT 0,
  diff_summary_json JSON NULL,
  result_summary VARCHAR(1000) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_soc_algorithm_evaluation_no (evaluation_no),
  KEY idx_soc_algorithm_evaluation_type (algorithm_type, created_at),
  KEY idx_soc_algorithm_evaluation_batch (batch_id, created_at),
  KEY idx_soc_algorithm_evaluation_operator (created_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_algorithm_evaluation_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evaluation_id BIGINT NOT NULL,
  item_type VARCHAR(64) NOT NULL,
  item_name VARCHAR(255) NOT NULL,
  preview_result_json JSON NULL,
  reason VARCHAR(1000) NOT NULL,
  sort_order INT NOT NULL DEFAULT 100,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_soc_algorithm_eval_item_eval (evaluation_id, sort_order),
  KEY idx_soc_algorithm_eval_item_type (item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @risk_score_column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'soc_asset'
    AND COLUMN_NAME = 'risk_score'
);
SET @risk_score_ddl := IF(@risk_score_column_exists = 0,
  'ALTER TABLE soc_asset ADD COLUMN risk_score INT NOT NULL DEFAULT 0 AFTER risk_level',
  'SELECT 1'
);
PREPARE risk_score_stmt FROM @risk_score_ddl;
EXECUTE risk_score_stmt;
DEALLOCATE PREPARE risk_score_stmt;

CREATE TABLE IF NOT EXISTS soc_risk_scoring_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code VARCHAR(80) NOT NULL,
  policy_name VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  enabled TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 1,
  critical_asset_weight INT NOT NULL DEFAULT 10,
  internet_exposed_weight INT NOT NULL DEFAULT 10,
  critical_alert_weight INT NOT NULL DEFAULT 25,
  high_alert_weight INT NOT NULL DEFAULT 15,
  medium_alert_weight INT NOT NULL DEFAULT 8,
  critical_vulnerability_weight INT NOT NULL DEFAULT 25,
  high_vulnerability_weight INT NOT NULL DEFAULT 15,
  baseline_failed_weight INT NOT NULL DEFAULT 8,
  fim_unreviewed_weight INT NOT NULL DEFAULT 6,
  external_event_weight INT NOT NULL DEFAULT 6,
  overdue_ticket_weight INT NOT NULL DEFAULT 10,
  open_playbook_task_weight INT NOT NULL DEFAULT 6,
  employee_pending_task_weight INT NOT NULL DEFAULT 8,
  closed_ticket_reduce_weight INT NOT NULL DEFAULT 8,
  completed_playbook_reduce_weight INT NOT NULL DEFAULT 5,
  max_score INT NOT NULL DEFAULT 100,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  approved_by BIGINT NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_risk_scoring_policy_code (policy_code, deleted),
  KEY idx_soc_risk_scoring_policy_status (status, enabled),
  KEY idx_soc_risk_scoring_policy_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_asset_risk_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_id BIGINT NOT NULL,
  asset_ip VARCHAR(64) NOT NULL,
  hostname VARCHAR(128) NOT NULL,
  score INT NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  policy_id BIGINT NULL,
  calculated_at DATETIME NOT NULL,
  factor_summary_json JSON NOT NULL,
  recommendation_summary VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_soc_asset_risk_snapshot_asset (asset_id, calculated_at),
  KEY idx_soc_asset_risk_snapshot_ip (asset_ip, calculated_at),
  KEY idx_soc_asset_risk_snapshot_score (score, risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_asset_risk_factor (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  factor_type VARCHAR(64) NOT NULL,
  factor_name VARCHAR(128) NOT NULL,
  factor_score INT NOT NULL DEFAULT 0,
  factor_count INT NOT NULL DEFAULT 0,
  related_biz_type VARCHAR(64) NULL,
  related_biz_id BIGINT NULL,
  explanation VARCHAR(1000) NOT NULL,
  recommendation VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_soc_asset_risk_factor_snapshot (snapshot_id),
  KEY idx_soc_asset_risk_factor_asset (asset_id, factor_type),
  KEY idx_soc_asset_risk_factor_related (related_biz_type, related_biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO soc_risk_scoring_policy
  (id, policy_code, policy_name, description, status, enabled, version,
   critical_asset_weight, internet_exposed_weight, critical_alert_weight, high_alert_weight, medium_alert_weight,
   critical_vulnerability_weight, high_vulnerability_weight, baseline_failed_weight, fim_unreviewed_weight,
   external_event_weight, overdue_ticket_weight, open_playbook_task_weight, employee_pending_task_weight,
   closed_ticket_reduce_weight, completed_playbook_reduce_weight, max_score,
   created_by, updated_by, approved_by, approved_at)
VALUES
  (501, 'DEFAULT_ASSET_RISK_V1', '默认资产风险评分策略', '用规则权重解释资产风险来源，只用于排序、展示和处置建议，不执行自动修复。', 'active', 1, 1,
   10, 10, 25, 15, 8,
   25, 15, 8, 6,
   6, 10, 6, 8,
   8, 5, 100,
   1, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
  policy_name = VALUES(policy_name),
  description = VALUES(description),
  status = VALUES(status),
  enabled = VALUES(enabled),
  critical_alert_weight = VALUES(critical_alert_weight),
  critical_vulnerability_weight = VALUES(critical_vulnerability_weight),
  updated_by = VALUES(updated_by),
  approved_by = VALUES(approved_by),
  approved_at = VALUES(approved_at);
