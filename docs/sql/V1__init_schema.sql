-- 智识云 AI 知识库平台 - MySQL 5.7 初始化脚本
-- 兼容: MySQL 5.7.x
-- 字符集: utf8mb4

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `zhishiyun` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `zhishiyun`;

-- ----------------------------
-- 用户与认证
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `emp_no` VARCHAR(32) DEFAULT NULL COMMENT '工号',
  `name` VARCHAR(64) NOT NULL COMMENT '姓名',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '企业邮箱',
  `mobile` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt密码',
  `dept_name` VARCHAR(64) DEFAULT NULL COMMENT '部门',
  `role_code` VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE' COMMENT 'EMPLOYEE/KB_ADMIN',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0待审核 1正常 2禁用',
  `sso_subject` VARCHAR(128) DEFAULT NULL COMMENT 'Azure AD oid',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_email` (`email`),
  UNIQUE KEY `uk_sys_user_mobile` (`mobile`),
  KEY `idx_sys_user_emp_no` (`emp_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS `sys_refresh_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `token_hash` VARCHAR(128) NOT NULL COMMENT 'refresh token hash',
  `expire_at` DATETIME NOT NULL,
  `remember_me` TINYINT NOT NULL DEFAULT 0,
  `revoked` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_user` (`user_id`),
  KEY `idx_refresh_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='刷新令牌';

CREATE TABLE IF NOT EXISTS `user_preference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `notify_kb_update` TINYINT NOT NULL DEFAULT 1,
  `notify_mention` TINYINT NOT NULL DEFAULT 1,
  `theme_mode` VARCHAR(16) NOT NULL DEFAULT 'system' COMMENT 'light/dark/system',
  `default_kb_scopes` VARCHAR(128) NOT NULL DEFAULT '["hr","product"]',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_preference_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好';

-- ----------------------------
-- 知识库与文档
-- ----------------------------
CREATE TABLE IF NOT EXISTS `kb_library` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(32) NOT NULL COMMENT 'product/hr/tech/support',
  `name` VARCHAR(64) NOT NULL,
  `description` VARCHAR(512) DEFAULT NULL,
  `tags` VARCHAR(256) DEFAULT NULL,
  `doc_count` INT NOT NULL DEFAULT 0,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_library_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

CREATE TABLE IF NOT EXISTS `kb_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `library_id` BIGINT NOT NULL,
  `library_code` VARCHAR(32) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `file_type` VARCHAR(16) NOT NULL COMMENT 'pdf/word/excel/ppt/image',
  `category` VARCHAR(16) DEFAULT 'manual' COMMENT 'faq/policy/manual',
  `storage_key` VARCHAR(512) NOT NULL COMMENT '对象存储路径',
  `pages` INT NOT NULL DEFAULT 0,
  `summary` TEXT,
  `status` VARCHAR(16) NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING/PARSING/READY/FAILED',
  `view_count` INT NOT NULL DEFAULT 0,
  `created_by` BIGINT DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_doc_library_category` (`library_id`, `category`),
  KEY `idx_kb_doc_updated` (`updated_at`),
  KEY `idx_kb_doc_status` (`status`),
  FULLTEXT KEY `ft_kb_doc_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档';

CREATE TABLE IF NOT EXISTS `kb_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `doc_id` BIGINT NOT NULL,
  `library_id` BIGINT NOT NULL,
  `library_code` VARCHAR(32) NOT NULL,
  `page_no` INT NOT NULL DEFAULT 1,
  `chunk_index` INT NOT NULL DEFAULT 0,
  `content` MEDIUMTEXT NOT NULL,
  `token_est` INT NOT NULL DEFAULT 0,
  `milvus_pk` VARCHAR(128) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_chunk_doc_page_idx` (`doc_id`, `page_no`, `chunk_index`),
  KEY `idx_kb_chunk_doc_page` (`doc_id`, `page_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块';

CREATE TABLE IF NOT EXISTS `kb_acl` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `dept_code` VARCHAR(64) DEFAULT NULL,
  `library_id` BIGINT NOT NULL,
  `library_code` VARCHAR(32) NOT NULL,
  `perm` VARCHAR(16) NOT NULL DEFAULT 'READ',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_acl_user` (`user_id`),
  KEY `idx_kb_acl_dept` (`dept_code`),
  KEY `idx_kb_acl_library` (`library_id`),
  UNIQUE KEY `uk_kb_acl_user_library` (`user_id`, `library_id`),
  UNIQUE KEY `uk_kb_acl_dept_library` (`dept_code`, `library_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库访问控制';

CREATE TABLE IF NOT EXISTS `kb_page_vision` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `doc_id` BIGINT NOT NULL,
  `page_no` INT NOT NULL,
  `need_vision` TINYINT NOT NULL DEFAULT 0,
  `vision_status` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/DONE/FAILED',
  `vision_text` MEDIUMTEXT,
  `vision_summary` TEXT,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vision_doc_page` (`doc_id`, `page_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vision增强页';

-- ----------------------------
-- 会话与问答
-- ----------------------------
CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `scope` VARCHAR(128) NOT NULL DEFAULT 'hr' COMMENT 'all或json/逗号',
  `share_token` VARCHAR(128) DEFAULT NULL,
  `last_question` VARCHAR(512) DEFAULT NULL,
  `rating` DECIMAL(3,2) DEFAULT NULL,
  `message_count` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_session_share_token` (`share_token`),
  KEY `idx_chat_session_user_updated_deleted` (`user_id`, `updated_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话';

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT NOT NULL,
  `role` VARCHAR(16) NOT NULL COMMENT 'user/assistant/system',
  `content` MEDIUMTEXT NOT NULL,
  `thinking_content` MEDIUMTEXT NULL COMMENT '识别/思考过程',
  `elapsed_ms` INT DEFAULT NULL,
  `answer_status` VARCHAR(16) DEFAULT NULL COMMENT 'OK/NO_ANSWER/ERROR',
  `model_name` VARCHAR(64) DEFAULT NULL,
  `prompt_tokens` INT DEFAULT NULL,
  `completion_tokens` INT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_message_session_created` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息';

CREATE TABLE IF NOT EXISTS `chat_citation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `message_id` BIGINT NOT NULL,
  `cite_index` INT NOT NULL,
  `doc_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `page_no` INT NOT NULL DEFAULT 1,
  `library_name` VARCHAR(64) NOT NULL,
  `library_code` VARCHAR(32) NOT NULL,
  `excerpt` TEXT,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_citation_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引用来源';

-- ----------------------------
-- 反馈/收藏/统计
-- ----------------------------
CREATE TABLE IF NOT EXISTS `feedback_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `message_id` BIGINT NOT NULL,
  `session_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(16) NOT NULL COMMENT 'HELPFUL/UNHELPFUL/RATING',
  `issue_type` VARCHAR(32) DEFAULT NULL COMMENT 'INACCURATE/WRONG_DOC/MISSING_KNOWLEDGE/INCOMPLETE/OTHER',
  `comment` TEXT,
  `correct_answer` TEXT,
  `rating_score` INT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_message_user` (`message_id`, `user_id`),
  KEY `idx_feedback_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈记录';

CREATE TABLE IF NOT EXISTS `fav_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `doc_id` BIGINT NOT NULL,
  `page_no` INT NOT NULL DEFAULT 1,
  `saved_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fav_doc_user_doc` (`user_id`, `doc_id`),
  KEY `idx_fav_doc_user_saved` (`user_id`, `saved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏文档';

CREATE TABLE IF NOT EXISTS `fav_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `message_id` BIGINT NOT NULL,
  `summary` VARCHAR(512) DEFAULT NULL,
  `source_text` VARCHAR(512) DEFAULT NULL,
  `topic` VARCHAR(255) DEFAULT NULL,
  `context_json` JSON DEFAULT NULL,
  `saved_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fav_answer_user_msg` (`user_id`, `message_id`),
  KEY `idx_fav_answer_user_saved` (`user_id`, `saved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏回答';

CREATE TABLE IF NOT EXISTS `usage_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `event_type` VARCHAR(32) NOT NULL COMMENT 'ASK/OPEN_SOURCE/READ_COMPLETE/FAVORITE/FEEDBACK/SEARCH',
  `library_code` VARCHAR(32) DEFAULT NULL,
  `ref_id` VARCHAR(64) DEFAULT NULL,
  `extra_json` JSON DEFAULT NULL,
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_usage_user_time_type` (`user_id`, `event_time`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='埋点事件';

CREATE TABLE IF NOT EXISTS `stats_daily_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `stat_date` DATE NOT NULL,
  `ask_count` INT NOT NULL DEFAULT 0,
  `open_source_count` INT NOT NULL DEFAULT 0,
  `read_complete_count` INT NOT NULL DEFAULT 0,
  `favorite_count` INT NOT NULL DEFAULT 0,
  `helpful_count` INT NOT NULL DEFAULT 0,
  `unhelpful_count` INT NOT NULL DEFAULT 0,
  `rating_avg` DECIMAL(4,2) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stats_user_date` (`user_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户日统计';

-- ----------------------------
-- 帮助与联系人
-- ----------------------------
CREATE TABLE IF NOT EXISTS `help_faq` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `question` VARCHAR(512) NOT NULL,
  `answer` TEXT NOT NULL,
  `locale` VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
  `sort_no` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_help_faq_locale_sort` (`locale`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帮助FAQ';

CREATE TABLE IF NOT EXISTS `biz_contact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `library_code` VARCHAR(32) NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `title` VARCHAR(64) NOT NULL,
  `wecom` VARCHAR(128) DEFAULT NULL,
  `ext_no` VARCHAR(32) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_biz_contact_library` (`library_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务联系人';

-- ----------------------------
-- 审计与任务
-- ----------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `action` VARCHAR(64) NOT NULL,
  `target_type` VARCHAR(64) DEFAULT NULL,
  `target_id` VARCHAR(64) DEFAULT NULL,
  `detail` TEXT,
  `ip` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

CREATE TABLE IF NOT EXISTS `ingest_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `doc_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  `progress` INT NOT NULL DEFAULT 0,
  `error_msg` VARCHAR(1024) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ingest_doc` (`doc_id`),
  KEY `idx_ingest_status_updated` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库任务';

-- ----------------------------
-- 基础种子数据
-- ----------------------------

INSERT INTO `kb_library` (`id`, `code`, `name`, `description`, `tags`, `doc_count`)
VALUES
  (1, 'product', '产品知识库', '产品规格、FAQ、竞品对比等', '#FAQ #规格 #对比', 0),
  (2, 'hr', '人事制度库', '员工手册、考勤、报销与休假制度', '#制度 #手册 #FAQ', 0),
  (3, 'tech', '技术文档库', '接口说明、架构设计、排障手册', '#手册 #FAQ', 0),
  (4, 'support', '售后FAQ', '常见故障、售后流程、服务话术', '#FAQ #流程', 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `tags` = VALUES(`tags`);

-- 默认测试用户: zhangming@company.com / 密码: password
INSERT INTO `sys_user` (`id`, `emp_no`, `name`, `email`, `mobile`, `password_hash`, `dept_name`, `role_code`, `status`)
VALUES
  (1001, '100234', '张明', 'zhangming@company.com', '13800000001', '$2a$10$7EqJtq98hPqEX7fNZaFWoOePaWxn96p36F6E4E6xPD58Ll35H5T8y', '研发部', 'EMPLOYEE', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `dept_name` = VALUES(`dept_name`),
  `role_code` = VALUES(`role_code`),
  `status` = VALUES(`status`);

INSERT INTO `user_preference` (`user_id`, `notify_kb_update`, `notify_mention`, `theme_mode`, `default_kb_scopes`)
VALUES
  (1001, 1, 1, 'system', '["hr","product"]')
ON DUPLICATE KEY UPDATE
  `notify_kb_update` = VALUES(`notify_kb_update`),
  `notify_mention` = VALUES(`notify_mention`),
  `theme_mode` = VALUES(`theme_mode`),
  `default_kb_scopes` = VALUES(`default_kb_scopes`);

INSERT INTO `kb_acl` (`user_id`, `library_id`, `library_code`, `perm`)
VALUES
  (1001, 1, 'product', 'READ'),
  (1001, 2, 'hr', 'READ'),
  (1001, 3, 'tech', 'READ'),
  (1001, 4, 'support', 'READ')
ON DUPLICATE KEY UPDATE
  `perm` = VALUES(`perm`);

INSERT INTO `biz_contact` (`library_code`, `name`, `title`, `wecom`, `ext_no`)
VALUES
  ('hr', '李晓雯', '人力资源 BP', 'lixiaowen_hr', '8012'),
  ('product', '王婷', '知识运营', 'wangting_ops', '8066')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `title` = VALUES(`title`),
  `wecom` = VALUES(`wecom`),
  `ext_no` = VALUES(`ext_no`);

INSERT INTO `help_faq` (`question`, `answer`, `locale`, `sort_no`)
VALUES
  ('如何开始提问？', '打开“智能问答”页，在底部输入框直接提问。也可先选择知识库范围，让回答更聚焦。', 'zh-CN', 1),
  ('答案里的来源是什么？', '每个回答会附带来源文档与原文摘录。点击“查看原文”可跳转到对应页并高亮关键段落。', 'zh-CN', 2),
  ('为什么有些知识搜不到？', '平台只会检索你有权限访问的知识库。可尝试换个问法，或切换到“全部知识库”。', 'zh-CN', 3),
  ('如何收藏常用内容？', '在原文阅读页可收藏文档；对优质回答也可收藏。之后在“我的收藏”里快速查阅。', 'zh-CN', 4),
  ('反馈“没帮助”会怎样？', '你的反馈会用于优化检索与回答质量，不会影响个人绩效，也不会公开你的身份信息。', 'zh-CN', 5);

SET FOREIGN_KEY_CHECKS = 1;
