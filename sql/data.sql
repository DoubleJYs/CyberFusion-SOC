SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
USE cyberfusion_soc;

SET @add_dept_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sys_user ADD COLUMN dept_id BIGINT NULL AFTER mobile',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_user'
    AND column_name = 'dept_id'
);
PREPARE stmt FROM @add_dept_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_post_id = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sys_user ADD COLUMN post_id BIGINT NULL AFTER dept_id',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_user'
    AND column_name = 'post_id'
);
PREPARE stmt FROM @add_post_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_role_data_scope = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sys_role ADD COLUMN data_scope VARCHAR(32) NOT NULL DEFAULT ''self'' AFTER role_name',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_role'
    AND column_name = 'data_scope'
);
PREPARE stmt FROM @add_role_data_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_role_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  dept_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_role_dept (role_id, dept_id),
  KEY idx_sys_role_dept_dept_id (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_external_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_uid VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL COMMENT 'wazuh, zeek, suricata, trivy, misp, zap, sigma, shuffle and optional sources',
  event_type VARCHAR(64) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  rule_id VARCHAR(64) NULL,
  rule_name VARCHAR(255) NULL,
  src_ip VARCHAR(64) NULL,
  dest_ip VARCHAR(64) NULL,
  asset_name VARCHAR(128) NULL,
  asset_ip VARCHAR(64) NULL,
  batch_id VARCHAR(128) NULL,
  demo_case_id VARCHAR(128) NULL,
  target_url VARCHAR(500) NULL,
  action VARCHAR(64) NULL,
  request_id VARCHAR(128) NULL,
  correlation_key VARCHAR(255) NULL,
  ioc VARCHAR(255) NULL,
  raw_event JSON NULL,
  normalized_event JSON NULL,
  alert_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'new',
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  event_time DATETIME NOT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_external_event_uid (event_uid),
  KEY idx_soc_external_source_type (source_type, event_type),
  KEY idx_soc_external_severity_status (severity, status),
  KEY idx_soc_external_asset (asset_ip, dest_ip),
  KEY idx_soc_external_correlation (correlation_key),
  KEY idx_soc_external_batch_demo (batch_id, demo_case_id),
  KEY idx_soc_external_ioc (ioc),
  KEY idx_soc_external_alert (alert_id),
  KEY idx_soc_external_scope (owner_id, dept_id),
  KEY idx_soc_external_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @add_external_batch_id = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_external_event ADD COLUMN batch_id VARCHAR(128) NULL AFTER asset_ip', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_external_event' AND column_name = 'batch_id');
PREPARE stmt FROM @add_external_batch_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_external_demo_case_id = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_external_event ADD COLUMN demo_case_id VARCHAR(128) NULL AFTER batch_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_external_event' AND column_name = 'demo_case_id');
PREPARE stmt FROM @add_external_demo_case_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_external_target_url = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_external_event ADD COLUMN target_url VARCHAR(500) NULL AFTER demo_case_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_external_event' AND column_name = 'target_url');
PREPARE stmt FROM @add_external_target_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_external_action = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_external_event ADD COLUMN action VARCHAR(64) NULL AFTER target_url', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_external_event' AND column_name = 'action');
PREPARE stmt FROM @add_external_action;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_external_request_id = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_external_event ADD COLUMN request_id VARCHAR(128) NULL AFTER action', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_external_event' AND column_name = 'request_id');
PREPARE stmt FROM @add_external_request_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_external_correlation_key = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_external_event ADD COLUMN correlation_key VARCHAR(255) NULL AFTER request_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_external_event' AND column_name = 'correlation_key');
PREPARE stmt FROM @add_external_correlation_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_alert_event_type = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_alert ADD COLUMN event_type VARCHAR(64) NULL AFTER source_ip', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_alert' AND column_name = 'event_type');
PREPARE stmt FROM @add_alert_event_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_alert_target_url = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_alert ADD COLUMN target_url VARCHAR(500) NULL AFTER event_type', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_alert' AND column_name = 'target_url');
PREPARE stmt FROM @add_alert_target_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_alert_action = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_alert ADD COLUMN action VARCHAR(64) NULL AFTER target_url', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_alert' AND column_name = 'action');
PREPARE stmt FROM @add_alert_action;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_alert_evidence_summary = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_alert ADD COLUMN evidence_summary VARCHAR(1000) NULL AFTER action', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_alert' AND column_name = 'evidence_summary');
PREPARE stmt FROM @add_alert_evidence_summary;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_alert_batch_id = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_alert ADD COLUMN batch_id VARCHAR(128) NULL AFTER evidence_summary', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_alert' AND column_name = 'batch_id');
PREPARE stmt FROM @add_alert_batch_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_alert_demo_case_id = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_alert ADD COLUMN demo_case_id VARCHAR(128) NULL AFTER batch_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_alert' AND column_name = 'demo_case_id');
PREPARE stmt FROM @add_alert_demo_case_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_alert_correlation_key = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_alert ADD COLUMN correlation_key VARCHAR(255) NULL AFTER demo_case_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_alert' AND column_name = 'correlation_key');
PREPARE stmt FROM @add_alert_correlation_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS soc_correlation_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(128) NULL,
  rule_key VARCHAR(128) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  version INT NOT NULL DEFAULT 1,
  rule_type VARCHAR(64) NOT NULL,
  time_window_minutes INT NULL,
  min_score INT NULL,
  min_count INT NULL,
  group_by_fields_json JSON NULL,
  source_types_json JSON NULL,
  event_types_json JSON NULL,
  group_by_json JSON NULL,
  threshold INT NOT NULL DEFAULT 2,
  timeframe_seconds INT NOT NULL DEFAULT 1800,
  sequence_json JSON NULL,
  severity_min VARCHAR(32) NULL,
  severity_floor VARCHAR(32) NULL,
  weights_json JSON NULL,
  safety_note VARCHAR(500) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  approved_by BIGINT NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_correlation_rule_key (rule_key, deleted),
  KEY idx_soc_correlation_rule_status (status, enabled),
  KEY idx_soc_correlation_rule_type (rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_incident_cluster (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cluster_no VARCHAR(128) NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary VARCHAR(1000) NULL,
  recommendation VARCHAR(1000) NULL,
  severity VARCHAR(32) NOT NULL DEFAULT 'medium',
  status VARCHAR(32) NOT NULL DEFAULT 'open',
  score INT NOT NULL DEFAULT 0,
  correlation_key VARCHAR(255) NOT NULL,
  asset_id BIGINT NULL,
  asset_ip VARCHAR(64) NULL,
  hostname VARCHAR(128) NULL,
  primary_asset_ip VARCHAR(64) NULL,
  primary_hostname VARCHAR(128) NULL,
  batch_id VARCHAR(128) NULL,
  demo_case_id VARCHAR(128) NULL,
  source_summary VARCHAR(255) NULL,
  source_types VARCHAR(255) NULL,
  evidence_count INT NOT NULL DEFAULT 0,
  event_count INT NOT NULL DEFAULT 0,
  alert_count INT NOT NULL DEFAULT 0,
  vulnerability_count INT NOT NULL DEFAULT 0,
  first_seen_at DATETIME NULL,
  last_seen_at DATETIME NULL,
  rule_id BIGINT NULL,
  rule_key VARCHAR(128) NULL,
  ticket_id BIGINT NULL,
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  closed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_incident_cluster_no (cluster_no),
  UNIQUE KEY uk_soc_incident_correlation (correlation_key),
  KEY idx_soc_incident_status (status, severity),
  KEY idx_soc_incident_asset (primary_asset_ip),
  KEY idx_soc_incident_batch_demo (batch_id, demo_case_id),
  KEY idx_soc_incident_scope (owner_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_incident_evidence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cluster_id BIGINT NOT NULL,
  evidence_type VARCHAR(32) NOT NULL,
  evidence_id BIGINT NOT NULL,
  evidence_uid VARCHAR(128) NULL,
  source_type VARCHAR(32) NULL,
  event_type VARCHAR(64) NULL,
  severity VARCHAR(32) NULL,
  rule_id VARCHAR(128) NULL,
  asset_ip VARCHAR(64) NULL,
  hostname VARCHAR(128) NULL,
  target_url VARCHAR(500) NULL,
  batch_id VARCHAR(128) NULL,
  demo_case_id VARCHAR(128) NULL,
  event_time DATETIME NULL,
  relation_score INT NOT NULL DEFAULT 0,
  relation_reason VARCHAR(1000) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_incident_evidence (cluster_id, evidence_type, evidence_id),
  KEY idx_soc_incident_evidence_lookup (evidence_type, evidence_id),
  KEY idx_soc_incident_evidence_asset (asset_ip),
  KEY idx_soc_incident_evidence_batch_demo (batch_id, demo_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @add_correlation_rule_code = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_correlation_rule ADD COLUMN rule_code VARCHAR(128) NULL AFTER id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_correlation_rule' AND column_name = 'rule_code');
PREPARE stmt FROM @add_correlation_rule_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_correlation_time_window = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_correlation_rule ADD COLUMN time_window_minutes INT NULL AFTER rule_type', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_correlation_rule' AND column_name = 'time_window_minutes');
PREPARE stmt FROM @add_correlation_time_window;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_correlation_min_score = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_correlation_rule ADD COLUMN min_score INT NULL AFTER time_window_minutes', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_correlation_rule' AND column_name = 'min_score');
PREPARE stmt FROM @add_correlation_min_score;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_correlation_min_count = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_correlation_rule ADD COLUMN min_count INT NULL AFTER min_score', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_correlation_rule' AND column_name = 'min_count');
PREPARE stmt FROM @add_correlation_min_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_correlation_group_by_fields = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_correlation_rule ADD COLUMN group_by_fields_json JSON NULL AFTER min_count', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_correlation_rule' AND column_name = 'group_by_fields_json');
PREPARE stmt FROM @add_correlation_group_by_fields;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_correlation_severity_min = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_correlation_rule ADD COLUMN severity_min VARCHAR(32) NULL AFTER sequence_json', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_correlation_rule' AND column_name = 'severity_min');
PREPARE stmt FROM @add_correlation_severity_min;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_correlation_weights = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_correlation_rule ADD COLUMN weights_json JSON NULL AFTER severity_floor', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_correlation_rule' AND column_name = 'weights_json');
PREPARE stmt FROM @add_correlation_weights;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_incident_recommendation = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_incident_cluster ADD COLUMN recommendation VARCHAR(1000) NULL AFTER summary', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_incident_cluster' AND column_name = 'recommendation');
PREPARE stmt FROM @add_incident_recommendation;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_incident_asset_id = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_incident_cluster ADD COLUMN asset_id BIGINT NULL AFTER correlation_key', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_incident_cluster' AND column_name = 'asset_id');
PREPARE stmt FROM @add_incident_asset_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_incident_asset_ip = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_incident_cluster ADD COLUMN asset_ip VARCHAR(64) NULL AFTER asset_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_incident_cluster' AND column_name = 'asset_ip');
PREPARE stmt FROM @add_incident_asset_ip;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_incident_hostname = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_incident_cluster ADD COLUMN hostname VARCHAR(128) NULL AFTER asset_ip', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_incident_cluster' AND column_name = 'hostname');
PREPARE stmt FROM @add_incident_hostname;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_incident_source_summary = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_incident_cluster ADD COLUMN source_summary VARCHAR(255) NULL AFTER demo_case_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_incident_cluster' AND column_name = 'source_summary');
PREPARE stmt FROM @add_incident_source_summary;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_incident_evidence_count = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE soc_incident_cluster ADD COLUMN evidence_count INT NOT NULL DEFAULT 0 AFTER source_types', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'soc_incident_cluster' AND column_name = 'evidence_count');
PREPARE stmt FROM @add_incident_evidence_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, leader, phone, sort, status)
VALUES
  (1, 0, '总部', 'HQ', '管理员', NULL, 10, 1),
  (2, 1, '产品研发部', 'RD', '演示负责人', NULL, 20, 1),
  (3, 1, '运营支持部', 'OPS', '运营负责人', NULL, 30, 1)
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), dept_name = VALUES(dept_name), leader = VALUES(leader), phone = VALUES(phone), sort = VALUES(sort), status = VALUES(status);

INSERT INTO sys_post (id, post_code, post_name, sort, status, remark)
VALUES
  (1, 'admin', '系统管理员', 10, 1, 'SOC 平台内置管理岗位'),
  (2, 'developer', '开发工程师', 20, 1, '用于技术类项目派生'),
  (3, 'operator', '运营专员', 30, 1, '用于运营后台演示')
ON DUPLICATE KEY UPDATE post_name = VALUES(post_name), sort = VALUES(sort), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_user (id, username, password_hash, nickname, email, mobile, dept_id, post_id, status)
VALUES
  (1, 'admin', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '管理员', 'admin@example.local', NULL, 1, 1, 1),
  (2, 'demo', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '演示用户', 'demo@example.local', NULL, 2, 3, 1)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = VALUES(status);

INSERT INTO sys_role (id, role_code, role_name, data_scope, status)
VALUES
  (1, 'admin', '超级管理员', 'all', 1),
  (2, 'user', '普通用户', 'self', 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), data_scope = VALUES(data_scope), status = VALUES(status);

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES (1, 1, 1), (2, 2, 2)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, type, permission, sort, visible, status)
VALUES
  (1, 0, '仪表盘', '/dashboard', 'dashboard/DashboardView', 'DataLine', 'menu', 'dashboard:view', 10, 1, 1),
  (2, 0, '系统管理', '/system', NULL, 'Setting', 'directory', 'system:view', 20, 1, 1),
  (3, 2, '用户管理', '/system/user', 'system/user/UserView', 'User', 'menu', 'system:user:view', 10, 1, 1),
  (4, 2, '部门管理', '/system/dept', 'system/org/DeptView', 'OfficeBuilding', 'menu', 'system:dept:view', 20, 1, 1),
  (5, 2, '岗位管理', '/system/post', 'system/org/PostView', 'Postcard', 'menu', 'system:post:view', 30, 1, 1),
  (6, 2, '角色管理', '/system/role', 'system/role/RoleView', 'UserFilled', 'menu', 'system:role:view', 40, 1, 1),
  (7, 2, '菜单管理', '/system/menu', 'system/menu/MenuView', 'Menu', 'menu', 'system:menu:view', 50, 1, 1),
  (8, 2, '字典管理', '/system/dict', 'system/dict/DictView', 'Tickets', 'menu', 'system:dict:view', 60, 1, 1),
  (9, 2, '系统日志', '/system/log', 'system/log/LogView', 'Document', 'menu', 'system:log:view', 70, 1, 1),
  (10, 2, '文件管理', '/system/file', 'system/file/FileView', 'FolderOpened', 'menu', 'system:file:list', 80, 1, 1),
  (11, 2, '导入导出日志', '/system/excel/logs', 'system/excel/ImportExportLogView', 'UploadFilled', 'menu', 'system:excel:log', 90, 1, 1),
  (12, 2, '编号规则', '/system/workflow/biz-sequence', 'system/workflow/BizSequenceView', 'Connection', 'menu', 'system:sequence:list', 100, 1, 1),
  (13, 2, '流程日志', '/system/workflow/biz-flow-log', 'system/workflow/BizFlowLogView', 'Memo', 'menu', 'system:flowlog:list', 110, 1, 1),
  (14, 2, '参数配置', '/system/config', 'system/config/ConfigView', 'Tools', 'menu', 'system:config:view', 120, 1, 1),
  (15, 2, '通知公告', '/system/notice', 'system/notice/NoticeView', 'Bell', 'menu', 'system:notice:view', 115, 1, 1),
  (101, 3, '用户新增', NULL, NULL, NULL, 'button', 'system:user:create', 11, 0, 1),
  (102, 3, '用户编辑', NULL, NULL, NULL, 'button', 'system:user:update', 12, 0, 1),
  (103, 3, '用户重置密码', NULL, NULL, NULL, 'button', 'system:user:reset-password', 13, 0, 1),
  (104, 3, '用户分配角色', NULL, NULL, NULL, 'button', 'system:user:assign-role', 14, 0, 1),
  (201, 4, '部门新增', NULL, NULL, NULL, 'button', 'system:dept:create', 11, 0, 1),
  (202, 4, '部门编辑', NULL, NULL, NULL, 'button', 'system:dept:update', 12, 0, 1),
  (203, 4, '部门删除', NULL, NULL, NULL, 'button', 'system:dept:delete', 13, 0, 1),
  (301, 5, '岗位新增', NULL, NULL, NULL, 'button', 'system:post:create', 11, 0, 1),
  (302, 5, '岗位编辑', NULL, NULL, NULL, 'button', 'system:post:update', 12, 0, 1),
  (303, 5, '岗位删除', NULL, NULL, NULL, 'button', 'system:post:delete', 13, 0, 1),
  (401, 6, '角色新增', NULL, NULL, NULL, 'button', 'system:role:create', 11, 0, 1),
  (402, 6, '角色编辑', NULL, NULL, NULL, 'button', 'system:role:update', 12, 0, 1),
  (403, 6, '角色分配菜单', NULL, NULL, NULL, 'button', 'system:role:assign-menu', 13, 0, 1),
  (501, 7, '菜单新增', NULL, NULL, NULL, 'button', 'system:menu:create', 11, 0, 1),
  (502, 7, '菜单编辑', NULL, NULL, NULL, 'button', 'system:menu:update', 12, 0, 1),
  (503, 7, '菜单删除', NULL, NULL, NULL, 'button', 'system:menu:delete', 13, 0, 1),
  (601, 8, '字典新增', NULL, NULL, NULL, 'button', 'system:dict:create', 11, 0, 1),
  (602, 8, '字典编辑', NULL, NULL, NULL, 'button', 'system:dict:update', 12, 0, 1),
  (603, 8, '字典删除', NULL, NULL, NULL, 'button', 'system:dict:delete', 13, 0, 1),
  (701, 10, '文件上传', NULL, NULL, NULL, 'button', 'system:file:upload', 11, 0, 1),
  (702, 10, '文件下载', NULL, NULL, NULL, 'button', 'system:file:download', 12, 0, 1),
  (703, 10, '文件删除', NULL, NULL, NULL, 'button', 'system:file:delete', 13, 0, 1),
  (801, 11, '模板下载', NULL, NULL, NULL, 'button', 'system:excel:template', 11, 0, 1),
  (802, 11, 'Excel 导入', NULL, NULL, NULL, 'button', 'system:excel:import', 12, 0, 1),
  (803, 11, 'Excel 导出', NULL, NULL, NULL, 'button', 'system:excel:export', 13, 0, 1),
  (901, 12, '编号规则新增', NULL, NULL, NULL, 'button', 'system:sequence:create', 11, 0, 1),
  (902, 12, '编号规则编辑', NULL, NULL, NULL, 'button', 'system:sequence:update', 12, 0, 1),
  (903, 12, '业务编号生成', NULL, NULL, NULL, 'button', 'system:sequence:generate', 13, 0, 1),
  (1001, 13, '流程日志新增', NULL, NULL, NULL, 'button', 'system:flowlog:create', 11, 0, 1),
  (1101, 14, '参数新增', NULL, NULL, NULL, 'button', 'system:config:create', 11, 0, 1),
  (1102, 14, '参数编辑', NULL, NULL, NULL, 'button', 'system:config:update', 12, 0, 1),
  (1103, 14, '参数删除', NULL, NULL, NULL, 'button', 'system:config:delete', 13, 0, 1),
  (1201, 15, '公告新增', NULL, NULL, NULL, 'button', 'system:notice:create', 11, 0, 1),
  (1202, 15, '公告编辑', NULL, NULL, NULL, 'button', 'system:notice:update', 12, 0, 1),
  (1203, 15, '公告删除', NULL, NULL, NULL, 'button', 'system:notice:delete', 13, 0, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), permission = VALUES(permission), type = VALUES(type), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (2, 1), (2, 3), (2, 6)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_type (id, dict_name, dict_code, status)
VALUES
  (1, '系统状态', 'sys_status', 1),
  (2, '日志状态', 'log_status', 1),
  (3, '菜单类型', 'menu_type', 1),
  (4, '公告类型', 'notice_type', 1)
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), status = VALUES(status);

INSERT INTO sys_dict_data (id, dict_type_id, dict_label, dict_value, sort_order, status)
VALUES
  (1, 1, '启用', '1', 10, 1),
  (2, 1, '停用', '0', 20, 1),
  (3, 2, '成功', 'SUCCESS', 10, 1),
  (4, 2, '失败', 'FAIL', 20, 1),
  (5, 3, '目录', 'directory', 10, 1),
  (6, 3, '菜单', 'menu', 20, 1),
  (7, 3, '按钮', 'button', 30, 1),
  (8, 4, '系统公告', 'system', 10, 1),
  (9, 4, '维护通知', 'maintenance', 20, 1),
  (10, 4, '版本发布', 'release', 30, 1)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), status = VALUES(status);

INSERT INTO sys_config (id, config_key, config_name, config_value, value_type, group_code, editable, status, remark)
VALUES
  (1, 'site.title', '站点标题', 'CyberFusion SOC', 'string', 'site', 1, 1, '前端页面标题和浏览器标题的默认展示文案'),
  (2, 'security.password.minLength', '密码最小长度', '8', 'number', 'security', 1, 1, '本地演示账号和新增用户密码的基础长度提示'),
  (3, 'file.upload.maxSizeMb', '上传文件大小 MB', '20', 'number', 'file', 0, 1, '与 application.yml 中 app.file.max-size-mb 保持一致'),
  (4, 'feature.demoMode', '演示模式', 'true', 'boolean', 'feature', 1, 1, '用于项目派生时控制演示数据或演示入口')
ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_value = VALUES(config_value), value_type = VALUES(value_type), group_code = VALUES(group_code), editable = VALUES(editable), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_notice (id, notice_title, notice_type, notice_content, pinned, publish_at, expire_at, status, remark)
VALUES
  (1, '企业安全监测平台初始化完成', 'system', '当前平台已内置登录认证、RBAC、SOC 告警、资产、工单、报表和审计日志基础能力。', 1, NOW(), NULL, 1, 'SOC 默认公告'),
  (2, '本地开发环境维护窗口', 'maintenance', '本地 Docker MySQL、Redis、Adminer 等服务统一放在 Environment 目录管理，避免运行数据进入源码仓库。', 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1, '开发环境提示'),
  (3, 'v2 基础能力发布', 'release', '新增参数配置、部门岗位和通知公告模块，方便派生项目快速落地常见后台管理能力。', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, 1, '版本发布演示')
ON DUPLICATE KEY UPDATE notice_title = VALUES(notice_title), notice_type = VALUES(notice_type), notice_content = VALUES(notice_content), pinned = VALUES(pinned), publish_at = VALUES(publish_at), expire_at = VALUES(expire_at), status = VALUES(status), remark = VALUES(remark);

INSERT INTO sys_login_log (username, ip, user_agent, status, message, created_at)
VALUES
  ('admin', '127.0.0.1', 'seed', 'SUCCESS', '演示登录成功', NOW()),
  ('demo', '127.0.0.1', 'seed', 'SUCCESS', '演示用户登录', DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO sys_operation_log (username, action, method, path, ip, user_agent, status, message, created_at)
VALUES
  ('admin', 'LOGIN', 'POST', '/api/auth/login', '127.0.0.1', 'seed', 'SUCCESS', '管理员登录', NOW()),
  ('admin', 'USER_PAGE', 'GET', '/api/system/users', '127.0.0.1', 'seed', 'SUCCESS', '查询用户列表', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  ('admin', 'DASHBOARD_OVERVIEW', 'GET', '/api/dashboard/overview', '127.0.0.1', 'seed', 'SUCCESS', '查看系统概览', DATE_SUB(NOW(), INTERVAL 2 DAY));

INSERT INTO sys_biz_sequence (sequence_code, sequence_name, prefix, date_pattern, current_value, step, length, reset_policy, last_reset_date, enabled, remark)
VALUES
  ('ORDER_DEMO', '订单号演示规则', 'ORD', 'yyyyMMdd', 0, 1, 4, 'DAILY', CURDATE(), 1, 'SOC 演示编号规则，不绑定订单模块'),
  ('APPOINTMENT_DEMO', '预约号演示规则', 'APT', 'yyyyMMdd', 0, 1, 4, 'DAILY', CURDATE(), 1, 'SOC 演示编号规则，不实现预约模块'),
  ('WORK_ORDER_DEMO', '工单号演示规则', 'WO', 'yyyyMMdd', 0, 1, 4, 'DAILY', CURDATE(), 1, 'SOC 演示编号规则，不实现工单模块')
ON DUPLICATE KEY UPDATE sequence_name = VALUES(sequence_name), prefix = VALUES(prefix), date_pattern = VALUES(date_pattern), step = VALUES(step), length = VALUES(length), reset_policy = VALUES(reset_policy), enabled = VALUES(enabled), remark = VALUES(remark);

INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, leader, sort, status)
VALUES
  (10, 0, '安全运营中心', 'SOC', 'SecOps Lead', 10, 1),
  (11, 10, '安全分析组', 'SOC-ANALYST', 'Analyst Lead', 20, 1),
  (12, 10, '基础设施运维组', 'SOC-OPS', 'Ops Lead', 30, 1),
  (13, 10, '审计合规组', 'SOC-AUDIT', 'Audit Lead', 40, 1)
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name), parent_id = VALUES(parent_id), leader = VALUES(leader), sort = VALUES(sort), status = VALUES(status);

INSERT INTO sys_role (id, role_code, role_name, data_scope, status)
VALUES
  (3, 'security_admin', '安全管理员', 'all', 1),
  (4, 'security_analyst', '安全分析员', 'dept_tree', 1),
  (5, 'ops', '运维人员', 'self', 1),
  (6, 'auditor', '只读审计员', 'all', 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), data_scope = VALUES(data_scope), status = VALUES(status);

INSERT INTO sys_role_dept (role_id, dept_id)
VALUES
  (4, 11),
  (5, 12)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_user (id, username, password_hash, nickname, email, mobile, dept_id, post_id, status)
VALUES
  (3, 'secadmin', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '安全管理员', 'secadmin@example.local', NULL, 10, 1, 1),
  (4, 'analyst', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '安全分析员', 'analyst@example.local', NULL, 11, 1, 1),
  (5, 'operator', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '运维人员', 'operator@example.local', NULL, 12, 1, 1),
  (6, 'auditor', '$2a$10$YHt2b5R57EgMvgQePeSZLOsBEtPWCy3hoDJh.yqDbFYsS7B24e2f2', '只读审计员', 'auditor@example.local', NULL, 13, 1, 1)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), email = VALUES(email), mobile = VALUES(mobile), dept_id = VALUES(dept_id), status = VALUES(status);

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES (3, 3, 3), (4, 4, 4), (5, 5, 5), (6, 6, 6)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, type, permission, sort, visible, status)
VALUES
  (2000, 0, '安全运营', '/soc', NULL, 'Monitor', 'directory', 'soc:view', 5, 1, 1),
  (2020, 2000, '监控处置', NULL, NULL, 'Odometer', 'directory', 'soc:operate:view', 10, 1, 1),
  (2021, 2000, '资产暴露面', NULL, NULL, 'Cpu', 'directory', 'soc:exposure:view', 20, 1, 1),
  (2022, 2000, '接入自动化', NULL, NULL, 'Connection', 'directory', 'soc:integration:view', 30, 1, 1),
  (2023, 2000, '管理配置', NULL, NULL, 'Setting', 'directory', 'soc:governance:view', 40, 1, 1),
  (2001, 2020, '安全总览', '/soc/dashboard', 'soc/DashboardView', 'DataAnalysis', 'menu', 'soc:dashboard:view', 10, 1, 1),
  (2012, 2020, '产品能力', '/soc/capabilities', 'soc/CapabilityView', 'Grid', 'menu', 'soc:dashboard:view', 15, 1, 1),
  (2013, 2020, '安全验证中心', '/soc/demo-range', 'soc/DemoRangeView', 'Operation', 'menu', 'soc:demo-range:view', 18, 1, 1),
  (2002, 2020, '告警中心', '/soc/alerts', 'soc/AlertCenterView', 'WarningFilled', 'menu', 'soc:alert:view', 20, 1, 1),
  (2017, 2020, '安全事件簇', '/soc/incidents', 'soc/IncidentClusterView', 'Share', 'menu', 'soc:incident:list', 22, 1, 1),
  (2014, 2020, '检测规则中心', '/soc/rules', 'soc/RuleCenterView', 'List', 'menu', 'soc:rules:view', 25, 1, 1),
  (2015, 2020, '策略与规则中心', '/soc/policies', 'soc/PolicyCenterView', 'SetUp', 'menu', 'soc:policy:list', 28, 1, 1),
  (2010, 2020, '告警降噪', '/soc/alert-noise', 'soc/AlertNoiseView', 'Filter', 'menu', 'soc:alert-noise:view', 30, 1, 1),
  (2003, 2021, '资产视图', '/soc/assets', 'soc/AssetView', 'Cpu', 'menu', 'soc:asset:view', 10, 1, 1),
  (2016, 2021, '员工终端安全态势', '/soc/client-security', 'soc/ClientSecurityPostureView', 'Monitor', 'menu', 'soc:client-security:view', 15, 1, 1),
  (2007, 2021, '漏洞中心', '/soc/vulnerabilities', 'soc/VulnerabilityView', 'Aim', 'menu', 'soc:vulnerability:view', 20, 1, 1),
  (2008, 2021, '基线核查', '/soc/baselines', 'soc/BaselineView', 'Checked', 'menu', 'soc:baseline:view', 30, 1, 1),
  (2009, 2021, '文件完整性', '/soc/fim', 'soc/FileIntegrityView', 'Files', 'menu', 'soc:fim:view', 40, 1, 1),
  (2011, 2022, '外部事件', '/soc/external-events', 'soc/ExternalEventView', 'Connection', 'menu', 'soc:external-event:view', 10, 1, 1),
  (2004, 2022, '工单中心', '/soc/tickets', 'soc/TicketView', 'Tickets', 'menu', 'soc:ticket:view', 20, 1, 1),
  (2005, 2022, '报表中心', '/soc/reports', 'soc/ReportView', 'DocumentChecked', 'menu', 'soc:report:view', 30, 1, 1),
  (2006, 2023, '系统配置', '/soc/settings', 'soc/SettingsView', 'Tools', 'menu', 'soc:settings:view', 10, 1, 1),
  (2101, 2002, '确认告警', NULL, NULL, NULL, 'button', 'soc:alert:ack', 11, 0, 1),
  (2102, 2002, '标记误报', NULL, NULL, NULL, 'button', 'soc:alert:false-positive', 12, 0, 1),
  (2103, 2002, '忽略告警', NULL, NULL, NULL, 'button', 'soc:alert:ignore', 13, 0, 1),
  (2104, 2002, '关闭告警', NULL, NULL, NULL, 'button', 'soc:alert:close', 14, 0, 1),
  (2105, 2002, '转工单', NULL, NULL, NULL, 'button', 'soc:alert:ticket', 15, 0, 1),
  (2201, 2004, '工单流转', NULL, NULL, NULL, 'button', 'soc:ticket:transition', 11, 0, 1),
  (2301, 2005, '生成报表', NULL, NULL, NULL, 'button', 'soc:report:generate', 11, 0, 1),
  (2302, 2005, '导出报表', NULL, NULL, NULL, 'button', 'soc:report:export', 12, 0, 1),
  (2401, 2006, 'Wazuh 连接检查', NULL, NULL, NULL, 'button', 'soc:settings:wazuh', 11, 0, 1),
  (2402, 2007, '漏洞状态流转', NULL, NULL, NULL, 'button', 'soc:vulnerability:status', 11, 0, 1),
  (2403, 2008, '基线状态流转', NULL, NULL, NULL, 'button', 'soc:baseline:status', 11, 0, 1),
  (2404, 2009, '文件完整性状态流转', NULL, NULL, NULL, 'button', 'soc:fim:status', 11, 0, 1),
  (2405, 2006, '测试通知通道', NULL, NULL, NULL, 'button', 'soc:settings:notify-test', 12, 0, 1),
  (2410, 2010, '白名单启停', NULL, NULL, NULL, 'button', 'soc:alert-noise:status', 11, 0, 1),
  (2411, 2010, '白名单保存', NULL, NULL, NULL, 'button', 'soc:alert-noise:save', 12, 0, 1),
  (2412, 2011, '外部事件状态流转', NULL, NULL, NULL, 'button', 'soc:external-event:status', 11, 0, 1),
  (2413, 2011, '安全数据导入', NULL, NULL, NULL, 'button', 'soc:external-event:import', 12, 0, 1),
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
  (2428, 2003, '资产风险画像', NULL, NULL, NULL, 'button', 'soc:risk-score:view', 13, 0, 1),
  (2430, 2017, '事件簇详情', NULL, NULL, NULL, 'button', 'soc:incident:view', 11, 0, 1),
  (2431, 2017, '执行关联', NULL, NULL, NULL, 'button', 'soc:incident:correlate', 12, 0, 1),
  (2432, 2017, '事件簇转工单', NULL, NULL, NULL, 'button', 'soc:incident:ticket', 13, 0, 1),
  (2433, 2017, '关闭事件簇', NULL, NULL, NULL, 'button', 'soc:incident:close', 14, 0, 1),
  (2434, 2015, '关联规则查看', NULL, NULL, NULL, 'button', 'soc:correlation-rule:list', 31, 0, 1),
  (2435, 2015, '关联规则新增', NULL, NULL, NULL, 'button', 'soc:correlation-rule:create', 32, 0, 1),
  (2436, 2015, '关联规则编辑', NULL, NULL, NULL, 'button', 'soc:correlation-rule:update', 33, 0, 1),
  (2437, 2015, '关联规则发布', NULL, NULL, NULL, 'button', 'soc:correlation-rule:publish', 34, 0, 1),
  (2438, 2015, '关联规则停用', NULL, NULL, NULL, 'button', 'soc:correlation-rule:disable', 35, 0, 1)
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), permission = VALUES(permission), type = VALUES(type), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 2000 AND 2499
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu WHERE id BETWEEN 2000 AND 2499
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (2000, 2020, 2021, 2022, 2001, 2012, 2013, 2014, 2002, 2017, 2003, 2016, 2004, 2005, 2007, 2008, 2009, 2010, 2011, 2101, 2102, 2103, 2104, 2105, 2201, 2301, 2402, 2403, 2404, 2410, 2411, 2412, 2413, 2414, 2430, 2431, 2432, 2433, 2434)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, id FROM sys_menu WHERE id IN (2000, 2021, 2022, 2003, 2004, 2007, 2008, 2009, 2011, 2201, 2402, 2403, 2404)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, id FROM sys_menu WHERE id IN (2000, 2020, 2021, 2022, 2001, 2012, 2013, 2014, 2002, 2003, 2004, 2005, 2007, 2008, 2009, 2010, 2011)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (2420, 2421)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (2422, 2423, 2424, 2425, 2426, 2427, 2428)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu WHERE id IN (2430, 2431, 2432, 2433, 2434, 2435, 2436, 2437, 2438)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO soc_correlation_rule (id, rule_code, rule_key, rule_name, description, enabled, status, version, rule_type, time_window_minutes, min_score, min_count, group_by_fields_json, source_types_json, event_types_json, group_by_json, threshold, timeframe_seconds, sequence_json, severity_min, severity_floor, weights_json, safety_note, approved_by, approved_at)
VALUES
  (1, 'waf_zap_wazuh_chain', 'waf_zap_wazuh_chain', 'WAF/ZAP/Wazuh 多源验证链路', '将同一资产、同一批次内 WAF、ZAP 和 Wazuh 证据聚合为安全事件簇。', 1, 'active', 1, 'cross_source_chain', 30, 60, 3, JSON_ARRAY('assetIp', 'batchId', 'demoCaseId'), JSON_ARRAY('waf', 'zap', 'wazuh'), NULL, JSON_ARRAY('assetIp', 'batchId', 'demoCaseId'), 3, 1800, JSON_ARRAY('waf', 'zap', 'wazuh'), 'medium', 'medium', JSON_OBJECT('sameAssetIp', 30, 'sameHostname', 20, 'sameBatchOrDemo', 25, 'sameRuleId', 15, 'sameTargetUrl', 10, 'withinTimeWindow', 15, 'crossSource', 20, 'highSeverity', 10, 'linkedAlertOrVulnerability', 10), '仅做离线证据关联，不执行扫描、脚本或外部查询。', 1, CURRENT_TIMESTAMP),
  (2, 'same_asset_event_count', 'same_asset_event_count', '同资产事件计数', '同一资产在时间窗口内出现多个安全事件时形成事件簇。', 1, 'active', 1, 'event_count', 30, 50, 3, JSON_ARRAY('assetIp'), JSON_ARRAY('waf', 'zap', 'wazuh', 'suricata', 'zeek', 'sigma'), NULL, JSON_ARRAY('assetIp'), 3, 1800, NULL, 'medium', 'medium', JSON_OBJECT('sameAssetIp', 30, 'withinTimeWindow', 15, 'highSeverity', 10), '仅按结构化字段计数，不执行表达式或脚本。', 1, CURRENT_TIMESTAMP),
  (3, 'wazuh_frequency_window', 'wazuh_frequency_window', 'Wazuh 频率窗口', '同一 Wazuh 规则在同一资产短时间多次命中时形成事件簇。', 1, 'active', 1, 'frequency', 15, 50, 3, JSON_ARRAY('assetIp', 'ruleId'), JSON_ARRAY('wazuh'), NULL, JSON_ARRAY('assetIp', 'ruleId'), 3, 900, NULL, 'medium', 'medium', JSON_OBJECT('sameAssetIp', 30, 'sameRuleId', 15, 'withinTimeWindow', 15), '仅按 ruleId 和 assetIp 聚合，不下发主机命令。', 1, CURRENT_TIMESTAMP),
  (4, 'demo_batch_chain', 'demo_batch_chain', '演示批次顺序链路', '按演示批次中的 WAF、ZAP、Trivy、主机和网络证据顺序构建验证链路。', 1, 'active', 1, 'temporal_ordered', 60, 50, 2, JSON_ARRAY('batchId', 'demoCaseId', 'assetIp'), JSON_ARRAY('waf', 'zap', 'trivy', 'wazuh', 'suricata', 'zeek'), NULL, JSON_ARRAY('batchId', 'demoCaseId', 'assetIp'), 2, 3600, JSON_ARRAY('waf', 'zap', 'trivy'), 'low', 'low', JSON_OBJECT('sameBatchOrDemo', 25, 'withinTimeWindow', 15, 'crossSource', 20, 'linkedAlertOrVulnerability', 10), '只读取已导入证据，不触发真实扫描。', 1, CURRENT_TIMESTAMP),
  (5, 'same_asset_value_count', 'same_asset_value_count', '同资产不同规则数量', '同一资产在时间窗口内命中多个不同规则或目标 URL 时形成事件簇。', 1, 'active', 1, 'value_count', 30, 50, 2, JSON_ARRAY('assetIp', 'batchId', 'demoCaseId'), JSON_ARRAY('waf', 'zap', 'suricata', 'zeek', 'sigma'), NULL, JSON_ARRAY('assetIp', 'batchId', 'demoCaseId'), 2, 1800, NULL, 'medium', 'medium', JSON_OBJECT('sameAssetIp', 30, 'sameBatchOrDemo', 25, 'sameTargetUrl', 10, 'withinTimeWindow', 15), '仅统计结构化字段的不同值，不执行脚本、查询或扫描。', 1, CURRENT_TIMESTAMP),
  (6, 'network_ids_chain', 'network_ids_chain', '网络 IDS 证据链路', '将 Suricata、Zeek 和相关主机证据按资产与批次聚合为网络检测事件簇。', 1, 'active', 1, 'cross_source_chain', 30, 55, 2, JSON_ARRAY('assetIp', 'batchId', 'demoCaseId'), JSON_ARRAY('suricata', 'zeek', 'wazuh'), NULL, JSON_ARRAY('assetIp', 'batchId', 'demoCaseId'), 2, 1800, JSON_ARRAY('suricata', 'zeek'), 'medium', 'medium', JSON_OBJECT('sameAssetIp', 30, 'sameBatchOrDemo', 25, 'withinTimeWindow', 15, 'crossSource', 20), '只聚合已入库网络检测证据，不连接 IDS 或执行抓包。', 1, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE rule_code = VALUES(rule_code), rule_key = VALUES(rule_key), rule_name = VALUES(rule_name), rule_type = VALUES(rule_type), time_window_minutes = VALUES(time_window_minutes), min_score = VALUES(min_score), min_count = VALUES(min_count), group_by_fields_json = VALUES(group_by_fields_json), source_types_json = VALUES(source_types_json), group_by_json = VALUES(group_by_json), threshold = VALUES(threshold), timeframe_seconds = VALUES(timeframe_seconds), sequence_json = VALUES(sequence_json), severity_min = VALUES(severity_min), severity_floor = VALUES(severity_floor), weights_json = VALUES(weights_json), enabled = VALUES(enabled), status = VALUES(status), version = VALUES(version), description = VALUES(description), safety_note = VALUES(safety_note);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu WHERE id IN (2422, 2423, 2424, 2425, 2426, 2427, 2428)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (2427, 2428)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

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
  critical_asset_weight = VALUES(critical_asset_weight),
  internet_exposed_weight = VALUES(internet_exposed_weight),
  critical_alert_weight = VALUES(critical_alert_weight),
  high_alert_weight = VALUES(high_alert_weight),
  medium_alert_weight = VALUES(medium_alert_weight),
  critical_vulnerability_weight = VALUES(critical_vulnerability_weight),
  high_vulnerability_weight = VALUES(high_vulnerability_weight),
  baseline_failed_weight = VALUES(baseline_failed_weight),
  fim_unreviewed_weight = VALUES(fim_unreviewed_weight),
  external_event_weight = VALUES(external_event_weight),
  overdue_ticket_weight = VALUES(overdue_ticket_weight),
  open_playbook_task_weight = VALUES(open_playbook_task_weight),
  employee_pending_task_weight = VALUES(employee_pending_task_weight),
  closed_ticket_reduce_weight = VALUES(closed_ticket_reduce_weight),
  completed_playbook_reduce_weight = VALUES(completed_playbook_reduce_weight),
  max_score = VALUES(max_score),
  updated_by = VALUES(updated_by),
  approved_by = VALUES(approved_by),
  approved_at = VALUES(approved_at);

INSERT INTO soc_local_check_command
  (command_key, display_name, os_type, category, description, command_argv_json, timeout_seconds, output_limit_kb, enabled, status, version, sort_order, safety_note, created_by, updated_by, approved_by, approved_at)
VALUES
  ('identity', '检查当前登录身份', 'Linux', 'identity', '确认当前登录用户和权限组。', JSON_ARRAY('id'), 2, 8, 1, 'active', 1, 10, '只读身份观察，不使用 shell，不访问外部目标。', 1, 1, 1, NOW()),
  ('network', '检查网络连接', 'Linux', 'network', '查看当前电脑网络连接状态。', JSON_ARRAY('ss', '-tunap'), 2, 8, 1, 'active', 1, 20, '只读网络连接观察，不进行端口扫描或公网访问。', 1, 1, 1, NOW()),
  ('process', '检查正在运行的程序', 'Linux', 'process', '查看正在运行的程序列表。', JSON_ARRAY('ps', '-axo', 'pid,comm'), 2, 8, 1, 'active', 1, 30, '只读进程快照，不结束进程。', 1, 1, 1, NOW()),
  ('startup', '检查开机启动项', 'Linux', 'startup', '查看用户服务启动项。', JSON_ARRAY('systemctl', '--user', 'list-units', '--type=service', '--no-pager', '--no-legend'), 2, 8, 1, 'active', 1, 40, '只读服务列表，不修改服务状态。', 1, 1, 1, NOW()),
  ('hostname', '核对电脑名称', 'Linux', 'host', '核对电脑名称是否和安全团队记录一致。', JSON_ARRAY('hostname'), 2, 8, 1, 'active', 1, 50, '只读主机名观察。', 1, 1, 1, NOW()),
  ('identity', '检查当前登录身份', 'macOS', 'identity', '确认当前登录用户和权限组。', JSON_ARRAY('id'), 2, 8, 1, 'active', 1, 10, '只读身份观察，不使用 shell，不访问外部目标。', 1, 1, 1, NOW()),
  ('network', '检查网络连接', 'macOS', 'network', '查看当前电脑网络连接状态。', JSON_ARRAY('lsof', '-i', '-n', '-P'), 2, 8, 1, 'active', 1, 20, '只读网络连接观察，不进行端口扫描或公网访问。', 1, 1, 1, NOW()),
  ('process', '检查正在运行的程序', 'macOS', 'process', '查看正在运行的程序列表。', JSON_ARRAY('ps', '-axo', 'pid,comm'), 2, 8, 1, 'active', 1, 30, '只读进程快照，不结束进程。', 1, 1, 1, NOW()),
  ('startup', '检查开机启动项', 'macOS', 'startup', '查看用户启动项。', JSON_ARRAY('launchctl', 'list'), 2, 8, 1, 'active', 1, 40, '只读启动项列表，不修改启动项。', 1, 1, 1, NOW()),
  ('hostname', '核对电脑名称', 'macOS', 'host', '核对电脑名称是否和安全团队记录一致。', JSON_ARRAY('hostname'), 2, 8, 1, 'active', 1, 50, '只读主机名观察。', 1, 1, 1, NOW()),
  ('identity', '检查当前登录身份', 'Windows', 'identity', '确认当前登录用户和权限组。', JSON_ARRAY('whoami', '/groups'), 2, 8, 1, 'active', 1, 10, '只读身份观察，不使用 cmd /c 或 PowerShell。', 1, 1, 1, NOW()),
  ('network', '检查网络连接', 'Windows', 'network', '查看当前电脑网络连接状态。', JSON_ARRAY('netstat', '-ano'), 2, 8, 1, 'active', 1, 20, '只读网络连接观察，不进行端口扫描或公网访问。', 1, 1, 1, NOW()),
  ('process', '检查正在运行的程序', 'Windows', 'process', '查看正在运行的程序列表。', JSON_ARRAY('tasklist', '/fo', 'table'), 2, 8, 1, 'active', 1, 30, '只读进程快照，不结束进程。', 1, 1, 1, NOW()),
  ('startup', '检查开机启动项', 'Windows', 'startup', '查看用户启动项。', JSON_ARRAY('reg', 'query', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'), 2, 8, 1, 'active', 1, 40, '只读注册表启动项查询，不修改注册表。', 1, 1, 1, NOW()),
  ('hostname', '核对电脑名称', 'Windows', 'host', '核对电脑名称是否和安全团队记录一致。', JSON_ARRAY('hostname'), 2, 8, 1, 'active', 1, 50, '只读主机名观察。', 1, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  category = VALUES(category),
  description = VALUES(description),
  command_argv_json = VALUES(command_argv_json),
  timeout_seconds = VALUES(timeout_seconds),
  output_limit_kb = VALUES(output_limit_kb),
  enabled = VALUES(enabled),
  status = VALUES(status),
  sort_order = VALUES(sort_order),
  safety_note = VALUES(safety_note),
  updated_by = VALUES(updated_by),
  approved_by = VALUES(approved_by),
  approved_at = VALUES(approved_at);

INSERT INTO soc_event_adapter_profile
  (id, source_type, display_name, description, status, enabled, version, sort_order, sample_file, created_by, updated_by, approved_by, approved_at)
VALUES
  (301, 'waf', 'WAF / 网关审计日志', '归一化 WAF 阻断、检测、上传策略和 API 滥用事件。', 'active', 1, 1, 10, 'integrations/samples/waf-demo-events.jsonl', 1, 1, 1, NOW()),
  (302, 'zap', 'ZAP Baseline 风险结果', '归一化离线 ZAP Baseline Web 风险发现。', 'active', 1, 1, 20, 'integrations/samples/zap-baseline.json', 1, 1, 1, NOW()),
  (303, 'trivy', 'Trivy 漏洞 JSON', '归一化 Trivy 漏洞字段用于预览，导入仍进入漏洞中心。', 'active', 1, 1, 30, 'integrations/samples/trivy.json', 1, 1, 1, NOW()),
  (304, 'wazuh', 'Wazuh 主机安全事件', '归一化 Wazuh rule、agent 和 data 字段。', 'active', 1, 1, 40, 'integrations/samples/wazuh.jsonl', 1, 1, 1, NOW()),
  (305, 'suricata', 'Suricata IDS 事件', '归一化 Suricata EVE alert 事件。', 'active', 1, 1, 50, 'integrations/samples/suricata-eve.jsonl', 1, 1, 1, NOW()),
  (306, 'zeek', 'Zeek 网络连接事件', '归一化 Zeek conn JSON 连接元数据。', 'active', 1, 1, 60, 'integrations/samples/zeek-conn.jsonl', 1, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  description = VALUES(description),
  status = VALUES(status),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order),
  sample_file = VALUES(sample_file),
  updated_by = VALUES(updated_by),
  approved_by = VALUES(approved_by),
  approved_at = VALUES(approved_at);

INSERT INTO soc_response_playbook
  (id, playbook_key, playbook_name, source_type, event_type, rule_id_pattern, min_severity, match_expression, description, status, enabled, version, sort_order, safety_note, created_by, updated_by, approved_by, approved_at)
VALUES
  (401, 'PB-WAF-BLOCK', 'WAF 阻断事件处置剧本', 'waf', 'waf_block,upload_block,api_abuse_block', '*', 'medium', 'sourceType=waf 且事件为阻断类', '用于把 Web 网关阻断证据转为人工复核、业务确认和报告记录。', 'active', 1, 1, 10, '只生成处置任务，不执行任何攻击、扫描、脚本或自动修复。', 1, 1, 1, NOW()),
  (402, 'PB-ZAP-WEB-RISK', 'Web 风险发现处置剧本', 'zap', 'web_app_finding,passive_findings', '*', 'low', 'sourceType=zap 且为离线 Baseline 发现', '用于复核离线 Web 风险发现并形成修复建议。', 'active', 1, 1, 20, '只处理离线报告和人工复核记录，不运行真实扫描。', 1, 1, 1, NOW()),
  (403, 'PB-TRIVY-VULN', '组件漏洞处置剧本', 'trivy', 'dependency_vulnerability,vulnerability', '*', 'medium', 'sourceType=trivy 或漏洞中心关联组件风险', '用于把组件漏洞发现转为版本核对、修复安排和验证记录。', 'active', 1, 1, 30, '只生成修复建议任务，不安装软件、不自动变更依赖。', 1, 1, 1, NOW()),
  (404, 'PB-WAZUH-FIM', '主机文件变更处置剧本', 'wazuh', 'fim_change,file_integrity', '*', 'medium', 'sourceType=wazuh 且为文件完整性记录', '用于复核主机侧文件变更证据并收集员工确认。', 'active', 1, 1, 40, '只要求人工核对和证据提交，不修改主机文件。', 1, 1, 1, NOW()),
  (405, 'PB-NETWORK-IDS', '网络检测事件处置剧本', 'suricata,zeek', 'ids_detect,network_connection,alert', '*', 'medium', 'sourceType 为 Suricata 或 Zeek 的网络检测证据', '用于把网络侧检测证据转为资产核实和告警闭环。', 'active', 1, 1, 50, '只做证据核实和人工处置建议，不进行网络扫描或阻断下发。', 1, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
  playbook_name = VALUES(playbook_name),
  source_type = VALUES(source_type),
  event_type = VALUES(event_type),
  rule_id_pattern = VALUES(rule_id_pattern),
  min_severity = VALUES(min_severity),
  match_expression = VALUES(match_expression),
  description = VALUES(description),
  status = VALUES(status),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order),
  safety_note = VALUES(safety_note),
  updated_by = VALUES(updated_by),
  approved_by = VALUES(approved_by),
  approved_at = VALUES(approved_at);

INSERT INTO soc_response_playbook_step
  (id, playbook_id, step_key, step_name, step_type, owner_role, instruction, expected_evidence, requires_employee, sort_order, enabled)
VALUES
  (40101, 401, 'triage', '确认网关阻断证据', 'triage', 'analyst', '核对告警中的 targetUrl、action、ruleId 和 evidenceSummary，确认属于授权演示批次或业务资产。', '告警详情截图或规则说明记录。', 0, 10, 1),
  (40102, 401, 'owner_confirm', '业务负责人确认访问影响', 'employee_confirm', 'employee', '请确认该业务路径是否属于正常访问范围，并补充是否影响当前工作。', '员工确认文字或业务负责人说明。', 1, 20, 1),
  (40103, 401, 'report', '记录验证结论', 'report', 'analyst', '在工单中记录阻断证据、处置结论和后续优化建议。', '工单结论和安全验证报告编号。', 0, 30, 1),
  (40201, 402, 'triage', '复核 Web 风险发现', 'triage', 'analyst', '核对离线 Baseline 发现是否与当前资产、路径和业务场景一致。', '风险发现摘要和影响路径。', 0, 10, 1),
  (40202, 402, 'manual_fix_plan', '制定人工修复建议', 'remediate_manual', 'analyst', '整理安全响应头、输入校验或配置项的人工修复建议，并交由业务团队排期。', '修复建议和负责人。', 0, 20, 1),
  (40301, 403, 'version_check', '核对组件版本', 'triage', 'analyst', '确认漏洞中心记录中的组件名称、版本和影响资产。', '漏洞详情和版本核对记录。', 0, 10, 1),
  (40302, 403, 'fix_schedule', '安排依赖修复', 'remediate_manual', 'analyst', '创建人工修复安排，说明建议升级版本和验证窗口。', '修复计划、负责人和预计完成时间。', 0, 20, 1),
  (40401, 404, 'fim_review', '复核文件变更记录', 'triage', 'analyst', '核对文件路径、变更时间和资产负责人，判断是否为授权维护动作。', '文件变更摘要和维护窗口说明。', 0, 10, 1),
  (40402, 404, 'employee_confirm', '员工确认本机变更', 'employee_confirm', 'employee', '请确认近期是否进行过相关配置变更，并提交简短说明。', '员工确认说明。', 1, 20, 1),
  (40501, 405, 'network_triage', '核实网络检测证据', 'triage', 'analyst', '核对源地址、目标资产、规则名称和事件时间，确认是否需要持续观察。', '网络检测摘要。', 0, 10, 1),
  (40502, 405, 'asset_context', '补充资产上下文', 'verify', 'analyst', '关联资产风险、近期告警和漏洞记录，形成处置优先级建议。', '资产上下文说明和优先级。', 0, 20, 1)
ON DUPLICATE KEY UPDATE
  step_name = VALUES(step_name),
  step_type = VALUES(step_type),
  owner_role = VALUES(owner_role),
  instruction = VALUES(instruction),
  expected_evidence = VALUES(expected_evidence),
  requires_employee = VALUES(requires_employee),
  sort_order = VALUES(sort_order),
  enabled = VALUES(enabled);

INSERT INTO soc_event_field_mapping
  (id, adapter_id, source_field_path, normalized_field, required, transform_type, default_value, example_value, sort_order, enabled)
VALUES
  (3001, 301, 'eventType,event_type', 'eventType', 0, 'first_non_empty', 'waf_detect', 'waf_block', 10, 1),
  (3002, 301, 'severity', 'severity', 0, 'lowercase', 'medium', 'high', 20, 1),
  (3003, 301, 'assetIp,asset_ip,dest_ip,dst_ip', 'assetIp', 0, 'first_non_empty', NULL, '10.20.1.15', 30, 1),
  (3004, 301, 'sourceIp,srcIp,clientIp,src_ip,source_ip', 'srcIp', 0, 'first_non_empty', NULL, '203.0.113.80', 40, 1),
  (3005, 301, 'targetUrl,target_url,url,request_uri', 'targetUrl', 0, 'first_non_empty', 'local-waf-demo', '/admin', 50, 1),
  (3006, 301, 'httpMethod,http_method,method', 'httpMethod', 0, 'first_non_empty', 'GET', 'POST', 60, 1),
  (3007, 301, 'httpStatus,http_status,status', 'httpStatus', 0, 'first_non_empty', '200', '403', 70, 1),
  (3008, 301, 'action', 'action', 0, 'lowercase', 'detect', 'block', 80, 1),
  (3009, 301, 'ruleId,rule_id', 'ruleId', 0, 'first_non_empty', 'WAF-DEMO', 'WAF-DEMO-1001', 90, 1),
  (3010, 301, 'ruleName,rule_name,message', 'ruleName', 0, 'first_non_empty', 'WAF policy event', 'Restricted admin route', 100, 1),
  (3011, 301, 'requestId,request_id,id', 'requestId', 0, 'first_non_empty', NULL, 'req-0001', 110, 1),
  (3012, 301, 'demoCaseId,demo_case_id', 'demoCaseId', 0, 'first_non_empty', NULL, 'DEMO-ACCESS-001', 120, 1),
  (3013, 301, 'batchId,demoBatchId,batch_id', 'batchId', 0, 'first_non_empty', NULL, 'DEMO-RANGE-OFFLINE-V1', 130, 1),
  (3014, 301, 'evidenceSummary,evidence_summary,summary', 'evidenceSummary', 0, 'first_non_empty', NULL, 'WAF blocked request', 140, 1),
  (3021, 302, 'pluginid,pluginId', 'ruleId', 0, 'first_non_empty', 'ZAP', '10021', 10, 1),
  (3022, 302, 'name,alert', 'ruleName', 0, 'first_non_empty', 'ZAP finding', 'Missing security header', 20, 1),
  (3023, 302, 'riskdesc,risk,riskcode', 'severity', 0, 'first_non_empty', 'low', 'Medium', 30, 1),
  (3024, 302, 'url,targetUrl', 'targetUrl', 0, 'first_non_empty', 'web-target', 'https://demo.internal.local/login', 40, 1),
  (3025, 302, 'batchId,demoBatchId', 'batchId', 0, 'first_non_empty', NULL, 'DEMO-RANGE-OFFLINE-V1', 50, 1),
  (3026, 302, 'demoCaseId,demo_case_id', 'demoCaseId', 0, 'first_non_empty', NULL, 'DEMO-HEADER-001', 60, 1),
  (3027, 302, 'evidenceSummary,evidence_summary,desc', 'evidenceSummary', 0, 'first_non_empty', NULL, 'Offline ZAP-style finding', 70, 1),
  (3028, 302, 'eventType,event_type', 'eventType', 0, 'first_non_empty', 'web_app_finding', 'web_app_finding', 80, 1),
  (3031, 303, 'VulnerabilityID,id', 'ruleId', 0, 'first_non_empty', 'TRIVY', 'CVE-2026-DEMO-0001', 10, 1),
  (3032, 303, 'Title,title', 'ruleName', 0, 'first_non_empty', 'Trivy vulnerability', 'Demo dependency risk', 20, 1),
  (3033, 303, 'Severity,severity', 'severity', 0, 'lowercase', 'medium', 'HIGH', 30, 1),
  (3034, 303, 'Target,assetName', 'assetName', 0, 'first_non_empty', 'trivy-target', 'prod-app-01', 40, 1),
  (3035, 303, 'PkgName,packageName', 'packageName', 0, 'first_non_empty', 'unknown-package', 'demo-lib', 50, 1),
  (3036, 303, 'batchId,demoBatchId', 'batchId', 0, 'first_non_empty', NULL, 'DEMO-RANGE-OFFLINE-V1', 60, 1),
  (3037, 303, 'eventType,event_type', 'eventType', 0, 'first_non_empty', 'dependency_vulnerability', 'dependency_vulnerability', 70, 1),
  (3041, 304, 'rule.id', 'ruleId', 0, 'direct', 'WAZUH', '550', 10, 1),
  (3042, 304, 'rule.description', 'ruleName', 0, 'direct', 'Wazuh alert', 'Protected file changed', 20, 1),
  (3043, 304, 'rule.level', 'severity', 0, 'direct', 'medium', '9', 30, 1),
  (3044, 304, 'agent.ip,dest_ip,dst_ip', 'assetIp', 0, 'first_non_empty', NULL, '10.20.1.15', 40, 1),
  (3045, 304, 'data.srcip,data.src_ip,src_ip', 'srcIp', 0, 'first_non_empty', NULL, '127.0.0.1', 50, 1),
  (3046, 304, 'id', 'requestId', 0, 'direct', NULL, 'wazuh-0001', 60, 1),
  (3047, 304, 'batchId,demoBatchId', 'batchId', 0, 'first_non_empty', NULL, 'DEMO-RANGE-OFFLINE-V1', 70, 1),
  (3048, 304, 'demoCaseId,demo_case_id', 'demoCaseId', 0, 'first_non_empty', NULL, 'DEMO-FIM-001', 80, 1),
  (3049, 304, 'evidenceSummary,evidence_summary', 'evidenceSummary', 0, 'first_non_empty', NULL, 'Wazuh-style FIM event', 90, 1),
  (3050, 304, 'eventType,event_type', 'eventType', 0, 'first_non_empty', 'xdr_alert', 'xdr_alert', 100, 1),
  (3051, 305, 'alert.signature_id', 'ruleId', 0, 'direct', 'SURICATA', '26061801', 10, 1),
  (3052, 305, 'alert.signature', 'ruleName', 0, 'direct', 'Suricata alert', 'Demo IDS policy match', 20, 1),
  (3053, 305, 'alert.severity', 'severity', 0, 'direct', 'medium', '1', 30, 1),
  (3054, 305, 'src_ip', 'srcIp', 0, 'direct', NULL, '203.0.113.90', 40, 1),
  (3055, 305, 'dest_ip', 'assetIp', 0, 'direct', NULL, '10.20.1.15', 50, 1),
  (3056, 305, 'event_type', 'eventType', 0, 'direct', 'ids_alert', 'alert', 60, 1),
  (3057, 305, 'flow_id', 'requestId', 0, 'direct', NULL, '860001', 70, 1),
  (3058, 305, 'batchId,demoBatchId', 'batchId', 0, 'first_non_empty', NULL, 'DEMO-RANGE-OFFLINE-V1', 80, 1),
  (3059, 305, 'demoCaseId,demo_case_id', 'demoCaseId', 0, 'first_non_empty', NULL, 'DEMO-NETWORK-001', 90, 1),
  (3060, 305, 'evidenceSummary,evidence_summary', 'evidenceSummary', 0, 'first_non_empty', NULL, 'Offline IDS evidence', 100, 1),
  (3061, 306, 'uid', 'ruleId', 0, 'direct', 'ZEEK', 'C1uid', 10, 1),
  (3062, 306, 'service,proto', 'ruleName', 0, 'first_non_empty', 'Zeek connection', 'https', 20, 1),
  (3063, 306, 'severity', 'severity', 0, 'lowercase', 'medium', 'medium', 30, 1),
  (3064, 306, 'id.orig_h,src_ip', 'srcIp', 0, 'first_non_empty', NULL, '203.0.113.91', 40, 1),
  (3065, 306, 'id.resp_h,dest_ip', 'assetIp', 0, 'first_non_empty', NULL, '10.20.1.15', 50, 1),
  (3066, 306, 'eventType,event_type', 'eventType', 0, 'first_non_empty', 'network_connection', 'network_connection', 60, 1),
  (3067, 306, 'batchId,demoBatchId', 'batchId', 0, 'first_non_empty', NULL, 'DEMO-RANGE-OFFLINE-V1', 70, 1),
  (3068, 306, 'demoCaseId,demo_case_id', 'demoCaseId', 0, 'first_non_empty', NULL, 'DEMO-NETWORK-001', 80, 1),
  (3069, 306, 'evidenceSummary,evidence_summary', 'evidenceSummary', 0, 'first_non_empty', NULL, 'Offline Zeek metadata', 90, 1)
ON DUPLICATE KEY UPDATE
  adapter_id = VALUES(adapter_id),
  source_field_path = VALUES(source_field_path),
  normalized_field = VALUES(normalized_field),
  required = VALUES(required),
  transform_type = VALUES(transform_type),
  default_value = VALUES(default_value),
  example_value = VALUES(example_value),
  sort_order = VALUES(sort_order),
  enabled = VALUES(enabled);

INSERT INTO soc_event_severity_mapping
  (id, adapter_id, source_value, normalized_severity, risk_score, enabled)
VALUES
  (3101, 301, 'critical', 'critical', 100, 1), (3102, 301, 'high', 'high', 80, 1), (3103, 301, 'medium', 'medium', 60, 1), (3104, 301, 'low', 'low', 30, 1), (3105, 301, 'info', 'info', 10, 1),
  (3111, 302, 'High', 'high', 80, 1), (3112, 302, 'Medium', 'medium', 60, 1), (3113, 302, 'Low', 'low', 30, 1), (3114, 302, 'Informational', 'info', 10, 1),
  (3121, 303, 'CRITICAL', 'critical', 100, 1), (3122, 303, 'HIGH', 'high', 80, 1), (3123, 303, 'MEDIUM', 'medium', 60, 1), (3124, 303, 'LOW', 'low', 30, 1),
  (3131, 304, '15', 'critical', 100, 1), (3132, 304, '12', 'critical', 100, 1), (3133, 304, '9', 'high', 80, 1), (3134, 304, '5', 'medium', 60, 1), (3135, 304, '3', 'low', 30, 1),
  (3141, 305, '1', 'high', 80, 1), (3142, 305, '2', 'medium', 60, 1), (3143, 305, '3', 'low', 30, 1),
  (3151, 306, 'high', 'high', 80, 1), (3152, 306, 'medium', 'medium', 60, 1), (3153, 306, 'low', 'low', 30, 1)
ON DUPLICATE KEY UPDATE
  adapter_id = VALUES(adapter_id),
  source_value = VALUES(source_value),
  normalized_severity = VALUES(normalized_severity),
  risk_score = VALUES(risk_score),
  enabled = VALUES(enabled);

INSERT INTO soc_event_alert_link_rule
  (id, adapter_id, event_type, min_severity, link_alerts_default, alert_rule_id_field, alert_name_template, dedup_key_fields_json, enabled)
VALUES
  (3201, 301, '*', 'medium', 1, 'ruleId', 'WAF 证据：{ruleName}', JSON_ARRAY('source', 'eventType', 'ruleId', 'assetIp', 'requestId'), 1),
  (3202, 302, '*', 'medium', 1, 'ruleId', 'Web 风险：{ruleName}', JSON_ARRAY('source', 'ruleId', 'targetUrl', 'batchId'), 1),
  (3203, 303, '*', 'high', 1, 'ruleId', '组件漏洞：{ruleName}', JSON_ARRAY('source', 'ruleId', 'assetName', 'packageName'), 1),
  (3204, 304, '*', 'medium', 1, 'ruleId', '主机安全事件：{ruleName}', JSON_ARRAY('source', 'ruleId', 'assetIp', 'requestId'), 1),
  (3205, 305, '*', 'medium', 1, 'ruleId', '网络检测事件：{ruleName}', JSON_ARRAY('source', 'ruleId', 'assetIp', 'srcIp', 'requestId'), 1),
  (3206, 306, '*', 'medium', 1, 'ruleId', '网络连接证据：{ruleName}', JSON_ARRAY('source', 'ruleId', 'assetIp', 'srcIp', 'batchId'), 1)
ON DUPLICATE KEY UPDATE
  adapter_id = VALUES(adapter_id),
  event_type = VALUES(event_type),
  min_severity = VALUES(min_severity),
  link_alerts_default = VALUES(link_alerts_default),
  alert_rule_id_field = VALUES(alert_rule_id_field),
  alert_name_template = VALUES(alert_name_template),
  dedup_key_fields_json = VALUES(dedup_key_fields_json),
  enabled = VALUES(enabled);

INSERT INTO soc_asset (id, hostname, ip, os_type, risk_level, source_type, dept_id, dept_name, owner_id, owner_name, open_alert_count, last_seen_at)
VALUES
  (1, 'prod-app-01', '10.20.1.15', 'Linux', 'critical', 'mock', 12, '基础设施运维组', 5, '运维人员', 7, NOW()),
  (2, 'finance-db-01', '10.20.8.21', 'Linux', 'high', 'mock', 12, '基础设施运维组', 5, '运维人员', 4, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
  (3, 'office-win-23', '10.30.5.23', 'Windows', 'medium', 'mock', 11, '安全分析组', 4, '安全分析员', 2, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
  (4, 'mac-build-02', '10.40.2.9', 'macOS', 'low', 'mock', 11, '安全分析组', 4, '安全分析员', 1, DATE_SUB(NOW(), INTERVAL 1 DAY))
ON DUPLICATE KEY UPDATE hostname = VALUES(hostname), os_type = VALUES(os_type), risk_level = VALUES(risk_level), open_alert_count = VALUES(open_alert_count), last_seen_at = VALUES(last_seen_at);

INSERT INTO soc_alert (id, alert_uid, source_type, level, severity, rule_id, rule_description, asset_name, asset_ip, source_ip, status, tactic, raw_ref, event_time, owner_id, dept_id)
VALUES
  (1, 'MOCK-20260527-0001', 'mock', 15, 'critical', '5715', '多次认证失败后出现成功登录，疑似暴力破解成功', 'prod-app-01', '10.20.1.15', '198.51.100.23', 'new', 'Credential Access', 'wazuh-alerts-4.x-2026.05.27/1', NOW(), 4, 11),
  (2, 'MOCK-20260527-0002', 'mock', 12, 'high', '5502', '关键系统配置文件发生变更，需要复核变更单', 'finance-db-01', '10.20.8.21', '10.10.4.12', 'acknowledged', 'Defense Evasion', 'wazuh-alerts-4.x-2026.05.27/2', DATE_SUB(NOW(), INTERVAL 2 HOUR), 4, 11),
  (3, 'MOCK-20260526-0003', 'mock', 8, 'medium', '100201', '终端出现异常 PowerShell 命令行行为', 'office-win-23', '10.30.5.23', '10.30.5.23', 'ticketed', 'Execution', 'wazuh-alerts-4.x-2026.05.26/3', DATE_SUB(NOW(), INTERVAL 1 DAY), 5, 12),
  (4, 'MOCK-20260525-0004', 'mock', 3, 'low', '530', '系统日志轮转记录异常但未触发风险升级', 'mac-build-02', '10.40.2.9', '10.40.2.9', 'closed', 'Collection', 'wazuh-alerts-4.x-2026.05.25/4', DATE_SUB(NOW(), INTERVAL 2 DAY), 5, 12),
  (5, 'MOCK-20260527-0005', 'mock', 12, 'high', '5502', '关键系统配置文件发生变更，需要复核变更单', 'finance-db-01', '10.20.8.21', '10.10.4.12', 'new', 'Defense Evasion', 'wazuh-alerts-4.x-2026.05.27/5', DATE_SUB(NOW(), INTERVAL 90 MINUTE), 4, 11),
  (6, 'SURICATA-20260527-0001', 'suricata', 12, 'high', 'ET-SCAN-001', 'Suricata 检测到面向生产应用的异常端口扫描流量', 'prod-app-01', '10.20.1.15', '203.0.113.44', 'new', 'Discovery', 'suricata/eve.json/1', DATE_SUB(NOW(), INTERVAL 35 MINUTE), 4, 11),
  (7, 'SURICATA-20260527-0002', 'suricata', 10, 'medium', 'ET-POLICY-HTTP', 'Suricata 检测到异常 HTTP User-Agent 访问行为', 'office-win-23', '10.30.5.23', '198.51.100.77', 'acknowledged', 'Command and Control', 'suricata/eve.json/2', DATE_SUB(NOW(), INTERVAL 70 MINUTE), 4, 11)
ON DUPLICATE KEY UPDATE severity = VALUES(severity), rule_description = VALUES(rule_description), asset_name = VALUES(asset_name), status = VALUES(status), event_time = VALUES(event_time), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id);

INSERT INTO soc_external_event (id, event_uid, source_type, event_type, severity, rule_id, rule_name, src_ip, dest_ip, asset_name, asset_ip, ioc, raw_event, normalized_event, alert_id, status, owner_id, dept_id, event_time)
VALUES
  (1, 'EXT-SURICATA-20260527-0001', 'suricata', 'ids_alert', 'high', 'ET-SCAN-001', 'ET SCAN Suspicious inbound port scan', '203.0.113.44', '10.20.1.15', 'prod-app-01', '10.20.1.15', '203.0.113.44', JSON_OBJECT('event_type', 'alert', 'proto', 'TCP', 'src_ip', '203.0.113.44', 'dest_ip', '10.20.1.15', 'dest_port', 443, 'signature', 'ET SCAN Suspicious inbound port scan'), JSON_OBJECT('source', 'suricata', 'event_type', 'ids_alert', 'severity', 'high', 'asset', 'prod-app-01', 'ioc', '203.0.113.44'), 6, 'new', 4, 11, DATE_SUB(NOW(), INTERVAL 35 MINUTE)),
  (2, 'EXT-SURICATA-20260527-0002', 'suricata', 'http_anomaly', 'medium', 'ET-POLICY-HTTP', 'ET POLICY Unusual HTTP user agent', '198.51.100.77', '10.30.5.23', 'office-win-23', '10.30.5.23', '198.51.100.77', JSON_OBJECT('event_type', 'http', 'src_ip', '198.51.100.77', 'dest_ip', '10.30.5.23', 'hostname', 'intranet.example.local', 'http_user_agent', 'unusual-client'), JSON_OBJECT('source', 'suricata', 'event_type', 'http_anomaly', 'severity', 'medium', 'asset', 'office-win-23', 'ioc', '198.51.100.77'), 7, 'reviewing', 4, 11, DATE_SUB(NOW(), INTERVAL 70 MINUTE))
ON DUPLICATE KEY UPDATE source_type = VALUES(source_type), event_type = VALUES(event_type), severity = VALUES(severity), rule_id = VALUES(rule_id), rule_name = VALUES(rule_name), src_ip = VALUES(src_ip), dest_ip = VALUES(dest_ip), asset_name = VALUES(asset_name), asset_ip = VALUES(asset_ip), ioc = VALUES(ioc), raw_event = VALUES(raw_event), normalized_event = VALUES(normalized_event), alert_id = VALUES(alert_id), status = VALUES(status), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), event_time = VALUES(event_time);

INSERT INTO soc_ticket (id, ticket_no, alert_id, title, severity, status, assignee_id, assignee_name, reviewer_id, review_conclusion, resolution, dept_id, due_at, closed_at)
VALUES
  (1, 'INC-202605260001', 3, 'MEDIUM 告警处置：异常 PowerShell 命令行行为', 'medium', '处理中', 5, '运维人员', NULL, NULL, '已隔离终端并导出进程列表，等待安全复核。', 12, DATE_ADD(NOW(), INTERVAL 1 DAY), NULL),
  (2, 'INC-202605250001', 4, 'LOW 告警处置：系统日志轮转异常', 'low', '已关闭', 5, '运维人员', 6, '确认为维护任务触发，允许关闭。', '已补充维护窗口记录并关闭告警。', 12, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 6 HOUR))
ON DUPLICATE KEY UPDATE title = VALUES(title), status = VALUES(status), assignee_id = VALUES(assignee_id), assignee_name = VALUES(assignee_name), review_conclusion = VALUES(review_conclusion), resolution = VALUES(resolution);

UPDATE soc_alert SET ticket_id = 1 WHERE id = 3;
UPDATE soc_alert SET ticket_id = 2 WHERE id = 4;

INSERT INTO soc_ticket_timeline (id, ticket_id, action, from_status, to_status, operator_name, remark, created_at)
VALUES
  (1, 1, '转工单', NULL, '待分派', 'analyst', '异常命令行需运维核查进程和账号来源。', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (2, 1, '状态流转', '待分派', '处理中', 'operator', '已接单处理。', DATE_SUB(NOW(), INTERVAL 20 HOUR)),
  (3, 2, '转工单', NULL, '待分派', 'analyst', '日志轮转异常，核对维护计划。', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (4, 2, '状态流转', '待复核', '已关闭', 'auditor', '复核通过。', DATE_SUB(NOW(), INTERVAL 6 HOUR))
ON DUPLICATE KEY UPDATE action = VALUES(action), from_status = VALUES(from_status), to_status = VALUES(to_status), operator_name = VALUES(operator_name), remark = VALUES(remark);

INSERT INTO soc_report (id, report_no, report_type, period_start, period_end, title, status, summary, recommendation, generated_at)
VALUES
  (1, 'RPT-DAILY-202605270001', 'daily', CURDATE(), CURDATE(), 'CyberFusion 综合安全日报', 'generated', '今日模拟数据中发现 2 条待处理高风险告警，工单闭环正常。', '优先处置 critical/high 告警，复核认证失败来源和配置文件变更。', NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), summary = VALUES(summary), recommendation = VALUES(recommendation), generated_at = VALUES(generated_at);

INSERT INTO soc_wazuh_config (id, config_name, manager_url, indexer_url, dashboard_url, auth_mode, enabled, last_status, remark)
VALUES
  (1, '本地 Wazuh 连接', '${WAZUH_MANAGER_URL}', '${WAZUH_INDEXER_URL}', '${WAZUH_DASHBOARD_URL}', 'env', 1, 'PENDING', '凭据仅从运行环境读取，源码不保存真实密码或证书。')
ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), auth_mode = VALUES(auth_mode), enabled = VALUES(enabled), last_status = VALUES(last_status), remark = VALUES(remark);

INSERT INTO soc_sync_task (id, task_code, task_name, source_type, schedule_cron, enabled, last_status, last_run_at)
VALUES
  (1, 'SYNC_WAZUH_ALERTS', '同步 Wazuh 告警索引（可选实时）', 'wazuh-indexer', '0 */5 * * * ?', 0, 'P0 使用模拟数据', NULL),
  (2, 'IMPORT_ZEEK_LOGS', '导入 Zeek conn.log / JSON', 'zeek', 'manual', 1, 'READY', NOW()),
  (3, 'IMPORT_SURICATA_EVE', '导入 Suricata eve.json', 'suricata', 'manual', 1, 'READY', NOW()),
  (4, 'IMPORT_TRIVY_JSON', '导入 Trivy JSON 到漏洞中心', 'trivy', 'manual', 1, 'READY', NOW()),
  (5, 'IMPORT_MISP_IOC', '导入 MISP IOC 情报', 'misp', 'manual', 1, 'READY', NOW()),
  (6, 'IMPORT_ZAP_JSON', '导入 ZAP JSON Web 风险', 'zap', 'manual', 1, 'READY', NOW()),
  (7, 'IMPORT_SIGMA_RULES', '导入 Sigma 检测规则', 'sigma', 'manual', 1, 'P2 规则中心', NULL),
  (8, 'CYBERCHEF_ANALYZE', 'CyberChef 字段分析入口', 'cyberchef', 'on-demand', 1, 'READY', NOW()),
  (9, 'SHUFFLE_DRY_RUN', 'Shuffle demo workflow 通知', 'shuffle', 'on-demand', 1, 'DRY_RUN', NOW()),
  (10, 'IMPORT_OPTIONAL_EXTERNAL', '可选外部接入导入', 'import', 'manual', 0, 'P3 可选', NULL)
ON DUPLICATE KEY UPDATE task_name = VALUES(task_name), source_type = VALUES(source_type), schedule_cron = VALUES(schedule_cron), enabled = VALUES(enabled), last_status = VALUES(last_status), last_run_at = VALUES(last_run_at);

INSERT INTO soc_notification_channel (id, channel_name, channel_type, target, enabled, min_severity, trigger_event, send_mode, last_status, remark)
VALUES
  (1, 'SOC 邮件通知（演示）', 'email', 'soc-team@example.local', 1, 'medium', '*', 'dry_run', 'READY', '演示通道只写通知日志；真实 SMTP 主机、账号和密码必须由运行环境提供，不进入源码。')
ON DUPLICATE KEY UPDATE channel_name = VALUES(channel_name), channel_type = VALUES(channel_type), target = VALUES(target), enabled = VALUES(enabled), min_severity = VALUES(min_severity), trigger_event = VALUES(trigger_event), send_mode = VALUES(send_mode), last_status = VALUES(last_status), remark = VALUES(remark);

INSERT INTO soc_notification_log (id, channel_id, channel_type, event_type, severity, biz_type, biz_id, title, content, target, status, sent_at)
VALUES
  (1, 1, 'email', 'alert_ticketed', 'high', 'alert', 2, '高危告警已转工单', '关键系统配置文件发生变更，已进入工单处置流程。', 'soc-team@example.local', 'DRY_RUN', DATE_SUB(NOW(), INTERVAL 90 MINUTE))
ON DUPLICATE KEY UPDATE event_type = VALUES(event_type), severity = VALUES(severity), title = VALUES(title), content = VALUES(content), target = VALUES(target), status = VALUES(status), sent_at = VALUES(sent_at);

INSERT INTO soc_alert_whitelist (id, rule_name, rule_id, asset_ip, source_ip, severity, reason, enabled, match_count, last_matched_at, owner_id, dept_id, expires_at)
VALUES
  (1, '维护窗口内日志轮转噪声', '530', '10.40.2.9', NULL, 'low', '构建机维护窗口触发的日志轮转记录，保留审计但默认降噪。', 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 4, 11, DATE_ADD(CURDATE(), INTERVAL 30 DAY)),
  (2, '内部变更单配置调整', '5502', '10.20.8.21', '10.10.4.12', 'high', '已登记变更来源，仅在相同资产、来源和规则下做降噪标记。', 1, 2, DATE_SUB(NOW(), INTERVAL 90 MINUTE), 5, 12, DATE_ADD(CURDATE(), INTERVAL 7 DAY))
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name), rule_id = VALUES(rule_id), asset_ip = VALUES(asset_ip), source_ip = VALUES(source_ip), severity = VALUES(severity), reason = VALUES(reason), enabled = VALUES(enabled), match_count = VALUES(match_count), last_matched_at = VALUES(last_matched_at), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), expires_at = VALUES(expires_at);

INSERT INTO soc_vulnerability (id, cve_id, severity, asset_name, asset_ip, software_name, software_version, fix_suggestion, status, source_type, owner_id, dept_id, detected_at, fixed_at)
VALUES
  (1, 'CVE-2024-3094', 'critical', 'prod-app-01', '10.20.1.15', 'xz-utils', '5.6.1', '立即回滚到可信版本并核查供应链来源、包仓库和登录痕迹。', 'open', 'mock', 5, 12, DATE_SUB(NOW(), INTERVAL 3 HOUR), NULL),
  (2, 'CVE-2023-38408', 'high', 'finance-db-01', '10.20.8.21', 'OpenSSH', '8.9p1', '升级 OpenSSH，禁用 agent forwarding，并复核堡垒机访问策略。', 'fixing', 'mock', 5, 12, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
  (3, 'CVE-2024-6387', 'high', 'office-win-23', '10.30.5.23', 'OpenSSH for Windows', '9.2', '应用厂商安全补丁，限制管理端口来源并复核登录失败日志。', 'reviewing', 'import', 4, 11, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
  (4, 'CVE-2022-22965', 'medium', 'mac-build-02', '10.40.2.9', 'Spring Framework', '5.3.17', '升级 Spring Framework 并复核构建机对外暴露面。', 'fixed', 'import', 4, 11, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE severity = VALUES(severity), software_version = VALUES(software_version), fix_suggestion = VALUES(fix_suggestion), status = VALUES(status), source_type = VALUES(source_type), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), detected_at = VALUES(detected_at), fixed_at = VALUES(fixed_at);

INSERT INTO soc_baseline_check (id, check_code, category, check_item, asset_name, asset_ip, result, severity, pass_rate, remediation, status, source_type, owner_id, dept_id, checked_at, reviewed_at)
VALUES
  (1, 'SSH_ROOT_LOGIN', 'SSH', '禁止 root 直接 SSH 登录', 'prod-app-01', '10.20.1.15', 'failed', 'high', 62, '设置 PermitRootLogin no，并通过 sudo 审计管理员操作。', 'failed', 'mock', 5, 12, DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL),
  (2, 'PASSWORD_MAX_DAYS', 'PASSWORD', '密码最长有效期不超过 90 天', 'finance-db-01', '10.20.8.21', 'failed', 'medium', 74, '调整 PASS_MAX_DAYS 并对数据库运维账号执行轮换。', 'remediating', 'mock', 5, 12, DATE_SUB(NOW(), INTERVAL 4 HOUR), NULL),
  (3, 'FIREWALL_DEFAULT_DENY', 'FIREWALL', '主机防火墙默认拒绝入站访问', 'office-win-23', '10.30.5.23', 'passed', 'low', 96, '保持默认拒绝策略，按变更单开放必要端口。', 'passed', 'import', 4, 11, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR)),
  (4, 'SENSITIVE_FILE_PERMISSION', 'FILE_PERMISSION', '敏感配置文件权限不高于 600', 'mac-build-02', '10.40.2.9', 'failed', 'medium', 68, '收敛构建密钥和配置文件权限，复核构建脚本输出。', 'reviewing', 'import', 4, 11, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
  (5, 'UNNEEDED_SERVICE', 'SERVICE', '关闭不必要系统服务', 'prod-app-01', '10.20.1.15', 'failed', 'medium', 71, '关闭未登记的调试服务并补充资产暴露面记录。', 'failed', 'mock', 5, 12, DATE_SUB(NOW(), INTERVAL 8 HOUR), NULL)
ON DUPLICATE KEY UPDATE category = VALUES(category), check_item = VALUES(check_item), result = VALUES(result), severity = VALUES(severity), pass_rate = VALUES(pass_rate), remediation = VALUES(remediation), status = VALUES(status), source_type = VALUES(source_type), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), checked_at = VALUES(checked_at), reviewed_at = VALUES(reviewed_at);

INSERT INTO soc_file_integrity_event (id, event_uid, action, severity, hostname, asset_ip, file_path, rule_name, status, source_type, owner_id, dept_id, event_time, reviewed_at)
VALUES
  (1, 'FIM-20260527-0001', 'modified', 'high', 'finance-db-01', '10.20.8.21', '/etc/ssh/sshd_config', '关键 SSH 配置变更', 'new', 'mock', 5, 12, DATE_SUB(NOW(), INTERVAL 35 MINUTE), NULL),
  (2, 'FIM-20260527-0002', 'permission', 'medium', 'prod-app-01', '10.20.1.15', '/etc/shadow', '敏感账号文件权限变化', 'reviewing', 'mock', 5, 12, DATE_SUB(NOW(), INTERVAL 90 MINUTE), NULL),
  (3, 'FIM-20260526-0003', 'created', 'medium', 'office-win-23', '10.30.5.23', 'C:\\ProgramData\\Startup\\debug.ps1', '启动目录新增脚本', 'confirmed', 'import', 4, 11, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 16 HOUR)),
  (4, 'FIM-20260525-0004', 'deleted', 'low', 'mac-build-02', '10.40.2.9', '/usr/local/build/tmp/token.cache', '构建缓存文件删除', 'ignored', 'import', 4, 11, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY))
ON DUPLICATE KEY UPDATE action = VALUES(action), severity = VALUES(severity), hostname = VALUES(hostname), asset_ip = VALUES(asset_ip), file_path = VALUES(file_path), rule_name = VALUES(rule_name), status = VALUES(status), source_type = VALUES(source_type), owner_id = VALUES(owner_id), dept_id = VALUES(dept_id), event_time = VALUES(event_time), reviewed_at = VALUES(reviewed_at);
