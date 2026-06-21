USE sec_wazuh_soc;

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
  (1, 'site.title', '站点标题', 'Sec Wazuh SOC', 'string', 'site', 1, 1, '前端页面标题和浏览器标题的默认展示文案'),
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
  (2001, 2000, '安全总览', '/soc/dashboard', 'soc/DashboardView', 'DataAnalysis', 'menu', 'soc:dashboard:view', 10, 1, 1),
  (2002, 2000, '告警中心', '/soc/alerts', 'soc/AlertCenterView', 'WarningFilled', 'menu', 'soc:alert:view', 20, 1, 1),
  (2010, 2000, '告警降噪', '/soc/alert-noise', 'soc/AlertNoiseView', 'Filter', 'menu', 'soc:alert-noise:view', 25, 1, 1),
  (2003, 2000, '资产视图', '/soc/assets', 'soc/AssetView', 'Cpu', 'menu', 'soc:asset:view', 30, 1, 1),
  (2007, 2000, '漏洞中心', '/soc/vulnerabilities', 'soc/VulnerabilityView', 'Aim', 'menu', 'soc:vulnerability:view', 35, 1, 1),
  (2008, 2000, '基线核查', '/soc/baselines', 'soc/BaselineView', 'Checked', 'menu', 'soc:baseline:view', 36, 1, 1),
  (2009, 2000, '文件完整性', '/soc/fim', 'soc/FileIntegrityView', 'Files', 'menu', 'soc:fim:view', 37, 1, 1),
  (2011, 2000, '外部事件', '/soc/external-events', 'soc/ExternalEventView', 'Connection', 'menu', 'soc:external-event:view', 38, 1, 1),
  (2004, 2000, '工单中心', '/soc/tickets', 'soc/TicketView', 'Tickets', 'menu', 'soc:ticket:view', 40, 1, 1),
  (2005, 2000, '报表中心', '/soc/reports', 'soc/ReportView', 'DocumentChecked', 'menu', 'soc:report:view', 50, 1, 1),
  (2006, 2000, '系统配置', '/soc/settings', 'soc/SettingsView', 'Tools', 'menu', 'soc:settings:view', 60, 1, 1),
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
  (2413, 2011, 'Suricata 事件导入', NULL, NULL, NULL, 'button', 'soc:external-event:import', 12, 0, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), permission = VALUES(permission), type = VALUES(type), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 2000 AND 2499
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu WHERE id BETWEEN 2000 AND 2499
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu WHERE id IN (2000, 2001, 2002, 2003, 2004, 2007, 2008, 2009, 2010, 2011, 2101, 2102, 2103, 2104, 2105, 2201, 2402, 2403, 2404, 2410, 2411, 2412, 2413)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, id FROM sys_menu WHERE id IN (2000, 2003, 2004, 2007, 2008, 2009, 2011, 2201, 2402, 2403, 2404)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, id FROM sys_menu WHERE id IN (2000, 2001, 2002, 2003, 2004, 2005, 2007, 2008, 2009, 2010, 2011)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

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
  (1, 'RPT-DAILY-202605270001', 'daily', CURDATE(), CURDATE(), '企业安全监测日报', 'generated', '今日模拟数据中发现 2 条待处理高风险告警，工单闭环正常。', '优先处置 critical/high 告警，复核认证失败来源和配置文件变更。', NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), summary = VALUES(summary), recommendation = VALUES(recommendation), generated_at = VALUES(generated_at);

INSERT INTO soc_wazuh_config (id, config_name, manager_url, indexer_url, dashboard_url, auth_mode, enabled, last_status, remark)
VALUES
  (1, '本地 Wazuh 连接', '${WAZUH_MANAGER_URL}', '${WAZUH_INDEXER_URL}', '${WAZUH_DASHBOARD_URL}', 'env', 1, 'PENDING', '凭据仅从运行环境读取，源码不保存真实密码或证书。')
ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), auth_mode = VALUES(auth_mode), enabled = VALUES(enabled), last_status = VALUES(last_status), remark = VALUES(remark);

INSERT INTO soc_sync_task (id, task_code, task_name, source_type, schedule_cron, enabled, last_status, last_run_at)
VALUES
  (1, 'SYNC_WAZUH_ALERTS', '同步 Wazuh 告警索引', 'wazuh-indexer', '0 */5 * * * ?', 0, 'P0 使用模拟数据', NULL),
  (2, 'IMPORT_SECURITY_LOGS', '导入安全日志样本', 'import', '0 0 * * * ?', 1, 'READY', NOW())
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
