-- CyberFusion P4.5 visibility menu/role seed patch.
-- Purpose: make latest SOC menu and permission entries available in
-- already-initialized databases without deleting business data.
--
-- For full runtime refresh on an existing local database, run this after:
--   mysql cyberfusion_soc < sql/schema.sql
--   mysql cyberfusion_soc < sql/data.sql
--
-- This patch intentionally avoids DELETE/TRUNCATE and is safe to rerun.
-- Safe to rerun on MySQL 8.

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, type, permission, sort, visible, status)
VALUES
  (2013, 2020, '安全验证中心', '/soc/demo-range', 'soc/DemoRangeView', 'Operation', 'menu', 'soc:demo-range:view', 18, 1, 1),
  (2014, 2020, '检测规则中心', '/soc/rules', 'soc/RuleCenterView', 'List', 'menu', 'soc:rules:view', 25, 1, 1),
  (2015, 2020, '策略与规则中心', '/soc/policies', 'soc/PolicyCenterView', 'SetUp', 'menu', 'soc:policy:list', 28, 1, 1),
  (2414, 2013, '导入演示批次', NULL, NULL, NULL, 'button', 'soc:demo-range:import', 11, 0, 1),
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
  (2428, 2003, '资产风险画像', NULL, NULL, NULL, 'button', 'soc:risk-score:view', 13, 0, 1)
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
SELECT 1, id FROM sys_menu WHERE id IN (2013, 2014, 2015, 2414, 2415, 2416, 2417, 2418, 2419, 2420, 2421, 2422, 2423, 2424, 2425, 2426, 2427, 2428)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu WHERE id IN (2013, 2014, 2015, 2414, 2415, 2416, 2417, 2418, 2419, 2420, 2421, 2422, 2423, 2424, 2425, 2426, 2427, 2428)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- security_analyst can operate the demo/alert/ticket/report path and apply playbooks,
-- but cannot publish policy or adapter definitions.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (2013, 2014, 2414, 2420, 2421, 2427, 2428)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

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
