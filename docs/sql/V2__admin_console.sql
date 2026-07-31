-- 智识云管理后台增量：角色/部门/全员可读/系统配置
-- 兼容 MySQL 5.7（可重复执行：已存在列则跳过）

USE `zhishiyun`;

SET @db := DATABASE();

-- dept_code
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='dept_code');
SET @sql := IF(@exists=0,
  'ALTER TABLE `sys_user` ADD COLUMN `dept_code` VARCHAR(64) DEFAULT NULL COMMENT ''部门编码'' AFTER `dept_name`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- last_login_at
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='last_login_at');
SET @sql := IF(@exists=0,
  'ALTER TABLE `sys_user` ADD COLUMN `last_login_at` DATETIME DEFAULT NULL COMMENT ''最近登录时间'' AFTER `sso_subject`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE `sys_user`
  MODIFY COLUMN `role_code` VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE' COMMENT 'EMPLOYEE/KB_ADMIN/SYS_ADMIN';

-- public_read
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_library' AND COLUMN_NAME='public_read');
SET @sql := IF(@exists=0,
  'ALTER TABLE `kb_library` ADD COLUMN `public_read` TINYINT NOT NULL DEFAULT 0 COMMENT ''1=全员可读'' AFTER `doc_count`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `kb_library` SET `public_read` = 1 WHERE `code` = 'hr';

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(64) NOT NULL,
  `config_value` MEDIUMTEXT NOT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- 管理员种子（密码由 AdminSeedRunner 按 Mock 约定写入 BCrypt）
INSERT INTO `sys_user` (`id`, `emp_no`, `name`, `email`, `mobile`, `password_hash`, `dept_name`, `dept_code`, `role_code`, `status`)
VALUES
  (2001, 'A0001', '系统管理员', 'admin@zhishiyun.com', '13900000001',
   '$2a$10$7EqJtq98hPqEX7fNZaFWoOePaWxn96p36F6E4E6xPD58Ll35H5T8y', '信息技术部', 'IT', 'SYS_ADMIN', 1),
  (2002, 'A0002', '知识管理员', 'kbadmin@zhishiyun.com', '13900000002',
   '$2a$10$7EqJtq98hPqEX7fNZaFWoOePaWxn96p36F6E4E6xPD58Ll35H5T8y', '知识运营', 'OPS', 'KB_ADMIN', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `dept_name` = VALUES(`dept_name`),
  `dept_code` = VALUES(`dept_code`),
  `role_code` = VALUES(`role_code`),
  `status` = VALUES(`status`);

INSERT INTO `user_preference` (`user_id`, `notify_kb_update`, `notify_mention`, `theme_mode`, `default_kb_scopes`)
VALUES
  (2001, 1, 1, 'system', '["hr","product","tech","support"]'),
  (2002, 1, 1, 'system', '["hr","product","tech","support"]')
ON DUPLICATE KEY UPDATE
  `default_kb_scopes` = VALUES(`default_kb_scopes`);

UPDATE `sys_user` SET `dept_code` = 'RD' WHERE `id` = 1001 AND (`dept_code` IS NULL OR `dept_code` = '');
