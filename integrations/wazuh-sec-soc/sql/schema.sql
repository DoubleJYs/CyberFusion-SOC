CREATE DATABASE IF NOT EXISTS sec_wazuh_soc DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE sec_wazuh_soc;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  email VARCHAR(128) NULL,
  mobile VARCHAR(32) NULL,
  dept_id BIGINT NULL,
  post_id BIGINT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_status (status),
  KEY idx_sys_user_dept_id (dept_id),
  KEY idx_sys_user_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  dept_name VARCHAR(64) NOT NULL,
  dept_code VARCHAR(64) NOT NULL,
  leader VARCHAR(64) NULL,
  phone VARCHAR(32) NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_dept_code (dept_code),
  KEY idx_sys_dept_parent_sort (parent_id, sort),
  KEY idx_sys_dept_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_code VARCHAR(64) NOT NULL,
  post_name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_post_code (post_code),
  KEY idx_sys_post_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  data_scope VARCHAR(32) NOT NULL DEFAULT 'self' COMMENT 'self,dept,dept_tree,all,custom',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_user_role (user_id, role_id),
  KEY idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(64) NOT NULL,
  path VARCHAR(128) NULL,
  component VARCHAR(128) NULL,
  icon VARCHAR(64) NULL,
  type VARCHAR(16) NOT NULL COMMENT 'directory, menu, button',
  permission VARCHAR(128) NULL,
  sort INT NOT NULL DEFAULT 0,
  visible TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_sys_menu_parent_sort (parent_id, sort),
  KEY idx_sys_menu_permission (permission),
  KEY idx_sys_menu_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_role_menu (role_id, menu_id),
  KEY idx_sys_role_menu_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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

CREATE TABLE IF NOT EXISTS sys_dict_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_name VARCHAR(64) NOT NULL,
  dict_code VARCHAR(64) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_dict_type_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_dict_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_type_id BIGINT NOT NULL,
  dict_label VARCHAR(64) NOT NULL,
  dict_value VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_sys_dict_data_type_sort (dict_type_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(128) NOT NULL,
  config_name VARCHAR(128) NOT NULL,
  config_value VARCHAR(1000) NOT NULL,
  value_type VARCHAR(32) NOT NULL DEFAULT 'string' COMMENT 'string, number, boolean, json',
  group_code VARCHAR(64) NOT NULL DEFAULT 'system',
  editable TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_config_key (config_key),
  KEY idx_sys_config_group (group_code),
  KEY idx_sys_config_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notice_title VARCHAR(128) NOT NULL,
  notice_type VARCHAR(32) NOT NULL,
  notice_content VARCHAR(4000) NOT NULL,
  pinned TINYINT NOT NULL DEFAULT 0,
  publish_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expire_at DATETIME NULL,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_sys_notice_type (notice_type),
  KEY idx_sys_notice_status_time (status, publish_at, expire_at),
  KEY idx_sys_notice_pinned (pinned)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL,
  message VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_sys_login_log_created_at (created_at),
  KEY idx_sys_login_log_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NULL,
  action VARCHAR(64) NOT NULL,
  method VARCHAR(16) NOT NULL,
  path VARCHAR(255) NOT NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL,
  message VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_sys_operation_log_created_at (created_at),
  KEY idx_sys_operation_log_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_refresh_token (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_refresh_token_hash (token_hash),
  KEY idx_sys_refresh_token_user (user_id),
  KEY idx_sys_refresh_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  file_ext VARCHAR(32) NOT NULL,
  content_type VARCHAR(128) NULL,
  file_size BIGINT NOT NULL,
  storage_type VARCHAR(32) NOT NULL DEFAULT 'local',
  storage_path VARCHAR(500) NOT NULL,
  access_url VARCHAR(500) NULL,
  md5 VARCHAR(64) NULL,
  biz_type VARCHAR(64) NULL,
  uploader_id BIGINT NULL,
  uploader_name VARCHAR(64) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sys_file_biz_type (biz_type),
  KEY idx_sys_file_uploader_id (uploader_id),
  KEY idx_sys_file_md5 (md5),
  KEY idx_sys_file_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type VARCHAR(64) NOT NULL,
  biz_id VARCHAR(64) NOT NULL,
  file_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sys_attachment_biz (biz_type, biz_id),
  KEY idx_sys_attachment_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_import_export_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_no VARCHAR(64) NOT NULL,
  task_type VARCHAR(32) NOT NULL COMMENT 'IMPORT or EXPORT',
  template_code VARCHAR(128) NOT NULL,
  file_id BIGINT NULL,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  error_summary VARCHAR(1000) NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_import_export_log_task_no (task_no),
  KEY idx_sys_import_export_log_template_code (template_code),
  KEY idx_sys_import_export_log_status (status),
  KEY idx_sys_import_export_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_biz_sequence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sequence_code VARCHAR(64) NOT NULL,
  sequence_name VARCHAR(128) NOT NULL,
  prefix VARCHAR(32) NOT NULL DEFAULT '',
  date_pattern VARCHAR(32) NOT NULL DEFAULT 'yyyyMMdd',
  current_value BIGINT NOT NULL DEFAULT 0,
  step INT NOT NULL DEFAULT 1,
  length INT NOT NULL DEFAULT 4,
  reset_policy VARCHAR(32) NOT NULL DEFAULT 'DAILY',
  last_reset_date DATE NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_biz_sequence_code (sequence_code),
  KEY idx_sys_biz_sequence_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_biz_flow_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type VARCHAR(64) NOT NULL,
  biz_id VARCHAR(64) NOT NULL,
  biz_no VARCHAR(64) NULL,
  from_status VARCHAR(64) NULL,
  to_status VARCHAR(64) NULL,
  action VARCHAR(64) NOT NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  reason VARCHAR(255) NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sys_biz_flow_log_biz (biz_type, biz_id),
  KEY idx_sys_biz_flow_log_biz_no (biz_no),
  KEY idx_sys_biz_flow_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_alert (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alert_uid VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'mock',
  level INT NOT NULL DEFAULT 0,
  severity VARCHAR(32) NOT NULL,
  rule_id VARCHAR(64) NOT NULL,
  rule_description VARCHAR(500) NOT NULL,
  asset_name VARCHAR(128) NOT NULL,
  asset_ip VARCHAR(64) NOT NULL,
  source_ip VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'new',
  tactic VARCHAR(128) NULL,
  raw_ref VARCHAR(255) NULL,
  event_time DATETIME NOT NULL,
  ticket_id BIGINT NULL,
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  acknowledged_at DATETIME NULL,
  closed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_alert_uid (alert_uid),
  KEY idx_soc_alert_event_time (event_time),
  KEY idx_soc_alert_severity_status (severity, status),
  KEY idx_soc_alert_asset_ip (asset_ip),
  KEY idx_soc_alert_owner_dept (owner_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hostname VARCHAR(128) NOT NULL,
  ip VARCHAR(64) NOT NULL,
  os_type VARCHAR(64) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'mock',
  dept_id BIGINT NULL,
  dept_name VARCHAR(64) NULL,
  owner_id BIGINT NULL,
  owner_name VARCHAR(64) NULL,
  open_alert_count INT NOT NULL DEFAULT 0,
  last_seen_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_asset_ip (ip),
  KEY idx_soc_asset_risk (risk_level),
  KEY idx_soc_asset_dept_owner (dept_id, owner_id),
  KEY idx_soc_asset_last_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_ticket (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_no VARCHAR(64) NOT NULL,
  alert_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  assignee_id BIGINT NULL,
  assignee_name VARCHAR(64) NULL,
  reviewer_id BIGINT NULL,
  review_conclusion VARCHAR(500) NULL,
  resolution VARCHAR(1000) NULL,
  dept_id BIGINT NULL,
  due_at DATETIME NULL,
  closed_at DATETIME NULL,
  archived_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_ticket_no (ticket_no),
  KEY idx_soc_ticket_alert_id (alert_id),
  KEY idx_soc_ticket_status (status),
  KEY idx_soc_ticket_assignee (assignee_id),
  KEY idx_soc_ticket_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_ticket_timeline (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NULL,
  operator_name VARCHAR(64) NULL,
  remark VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_soc_ticket_timeline_ticket (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_no VARCHAR(64) NOT NULL,
  report_type VARCHAR(32) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  title VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  recommendation VARCHAR(1000) NOT NULL,
  generated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_report_no (report_no),
  KEY idx_soc_report_period (report_type, period_start, period_end),
  KEY idx_soc_report_generated_at (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_wazuh_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_name VARCHAR(128) NOT NULL,
  manager_url VARCHAR(255) NULL,
  indexer_url VARCHAR(255) NULL,
  dashboard_url VARCHAR(255) NULL,
  auth_mode VARCHAR(32) NOT NULL DEFAULT 'env',
  enabled TINYINT NOT NULL DEFAULT 1,
  last_checked_at DATETIME NULL,
  last_status VARCHAR(64) NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_soc_wazuh_config_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_sync_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  schedule_cron VARCHAR(64) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  last_status VARCHAR(64) NULL,
  last_run_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_sync_task_code (task_code),
  KEY idx_soc_sync_task_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_notification_channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_name VARCHAR(128) NOT NULL,
  channel_type VARCHAR(32) NOT NULL,
  target VARCHAR(255) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  min_severity VARCHAR(32) NOT NULL DEFAULT 'medium',
  trigger_event VARCHAR(64) NOT NULL DEFAULT '*',
  send_mode VARCHAR(32) NOT NULL DEFAULT 'dry_run',
  last_status VARCHAR(64) NULL,
  last_sent_at DATETIME NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_soc_notification_channel_enabled (enabled),
  KEY idx_soc_notification_channel_type_event (channel_type, trigger_event)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_notification_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_id BIGINT NULL,
  channel_type VARCHAR(32) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  severity VARCHAR(32) NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  target VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message VARCHAR(500) NULL,
  sent_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_soc_notification_log_event (event_type, created_at),
  KEY idx_soc_notification_log_biz (biz_type, biz_id),
  KEY idx_soc_notification_log_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_alert_whitelist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_name VARCHAR(128) NOT NULL,
  rule_id VARCHAR(64) NULL,
  asset_ip VARCHAR(64) NULL,
  source_ip VARCHAR(64) NULL,
  severity VARCHAR(32) NULL,
  reason VARCHAR(500) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  match_count INT NOT NULL DEFAULT 0,
  last_matched_at DATETIME NULL,
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  expires_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_soc_alert_whitelist_enabled (enabled),
  KEY idx_soc_alert_whitelist_rule_asset (rule_id, asset_ip),
  KEY idx_soc_alert_whitelist_scope (owner_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_external_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_uid VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL COMMENT 'suricata, zeek, misp, opencti',
  event_type VARCHAR(64) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  rule_id VARCHAR(64) NULL,
  rule_name VARCHAR(255) NULL,
  src_ip VARCHAR(64) NULL,
  dest_ip VARCHAR(64) NULL,
  asset_name VARCHAR(128) NULL,
  asset_ip VARCHAR(64) NULL,
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
  KEY idx_soc_external_ioc (ioc),
  KEY idx_soc_external_alert (alert_id),
  KEY idx_soc_external_scope (owner_id, dept_id),
  KEY idx_soc_external_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_vulnerability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cve_id VARCHAR(64) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  asset_name VARCHAR(128) NOT NULL,
  asset_ip VARCHAR(64) NOT NULL,
  software_name VARCHAR(128) NOT NULL,
  software_version VARCHAR(128) NULL,
  fix_suggestion VARCHAR(1000) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'open',
  source_type VARCHAR(32) NOT NULL DEFAULT 'mock',
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  detected_at DATETIME NOT NULL,
  fixed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_vuln_cve_asset (cve_id, asset_ip, software_name),
  KEY idx_soc_vuln_severity_status (severity, status),
  KEY idx_soc_vuln_asset (asset_ip),
  KEY idx_soc_vuln_owner_dept (owner_id, dept_id),
  KEY idx_soc_vuln_detected_at (detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_baseline_check (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  check_code VARCHAR(64) NOT NULL,
  category VARCHAR(64) NOT NULL,
  check_item VARCHAR(255) NOT NULL,
  asset_name VARCHAR(128) NOT NULL,
  asset_ip VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  pass_rate INT NOT NULL DEFAULT 0,
  remediation VARCHAR(1000) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'failed',
  source_type VARCHAR(32) NOT NULL DEFAULT 'mock',
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  checked_at DATETIME NOT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_baseline_check_asset (check_code, asset_ip),
  KEY idx_soc_baseline_category_result (category, result),
  KEY idx_soc_baseline_status (status),
  KEY idx_soc_baseline_owner_dept (owner_id, dept_id),
  KEY idx_soc_baseline_checked_at (checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS soc_file_integrity_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_uid VARCHAR(128) NOT NULL,
  action VARCHAR(32) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  hostname VARCHAR(128) NOT NULL,
  asset_ip VARCHAR(64) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  rule_name VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'new',
  source_type VARCHAR(32) NOT NULL DEFAULT 'mock',
  owner_id BIGINT NULL,
  dept_id BIGINT NULL,
  event_time DATETIME NOT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_soc_fim_event_uid (event_uid),
  KEY idx_soc_fim_action_status (action, status),
  KEY idx_soc_fim_asset_path (asset_ip, file_path(128)),
  KEY idx_soc_fim_owner_dept (owner_id, dept_id),
  KEY idx_soc_fim_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
