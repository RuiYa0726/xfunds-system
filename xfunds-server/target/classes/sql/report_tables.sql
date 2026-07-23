-- ============================================================
-- 报表助手 · 自由查询模块 数据库表结构
-- 依据：代客外汇交易系统_报表助手_自由查询完整实现方案 V1.0
-- ============================================================

-- 1. 字段元数据表（白名单核心）
DROP TABLE IF EXISTS `report_field_meta`;
CREATE TABLE `report_field_meta` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `field_code` VARCHAR(50) NOT NULL COMMENT '字段编码（程序内部唯一）',
  `column_expr` VARCHAR(200) NOT NULL COMMENT 'SQL 列表达式（如 t.trade_date）',
  `display_name_cn` VARCHAR(100) NOT NULL COMMENT '中文显示名',
  `data_type` VARCHAR(20) NOT NULL COMMENT 'STRING/DECIMAL/DATE/DATETIME',
  `is_dimension` CHAR(1) DEFAULT 'N' COMMENT '是否维度（GROUP BY 用）',
  `is_metric` CHAR(1) DEFAULT 'N' COMMENT '是否指标（SUM/AVG/COUNT）',
  `is_filterable` CHAR(1) DEFAULT 'N' COMMENT '是否可过滤（WHERE 用）',
  `default_agg` VARCHAR(20) DEFAULT NULL COMMENT '默认聚合函数：SUM/AVG/COUNT/MAX/MIN',
  `source_table` VARCHAR(50) DEFAULT NULL COMMENT '来源表',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '前端分组：交易信息/币种金额/汇率/客户/机构/损益/期权',
  `sort_order` INT DEFAULT 0 COMMENT '展示排序',
  `enabled` CHAR(1) DEFAULT 'Y' COMMENT '是否启用',
  `sensitive_level` INT DEFAULT 0 COMMENT '敏感等级 0普通 1机密 2绝密',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_code` (`field_code`),
  INDEX `idx_source_table` (`source_table`),
  INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表字段元数据表（白名单）';

-- 2. 角色字段权限表
DROP TABLE IF EXISTS `report_role_field_permission`;
CREATE TABLE `report_role_field_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `field_code` VARCHAR(50) NOT NULL COMMENT '字段编码',
  `can_view` CHAR(1) DEFAULT 'Y' COMMENT '是否允许查看',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_field` (`role_id`, `field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色字段权限表';

-- 3. 字段别名表（辅助自然语言解析）
DROP TABLE IF EXISTS `report_field_alias`;
CREATE TABLE `report_field_alias` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `field_code` VARCHAR(50) NOT NULL COMMENT '字段编码',
  `alias_word` VARCHAR(100) NOT NULL COMMENT '别名/同义词',
  `alias_type` VARCHAR(20) DEFAULT 'SYNONYM' COMMENT '类型：STANDARD标准名/SYNONYM同义词',
  PRIMARY KEY (`id`),
  INDEX `idx_field_code` (`field_code`),
  INDEX `idx_alias_word` (`alias_word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段别名表';

-- 4. 查询审计日志表
DROP TABLE IF EXISTS `report_query_audit_log`;
CREATE TABLE `report_query_audit_log` (
  `log_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT COMMENT '操作人ID',
  `username` VARCHAR(50) COMMENT '操作人用户名',
  `org_code` VARCHAR(20) COMMENT '操作人机构',
  `query_fields` TEXT COMMENT '查询字段列表(JSON)',
  `filter_conditions` TEXT COMMENT '过滤条件(JSON)',
  `row_count` INT COMMENT '返回行数',
  `query_source` VARCHAR(20) COMMENT '来源：NLP自然语言/SELECT勾选',
  `duration_ms` BIGINT COMMENT '执行耗时(毫秒)',
  `ip_address` VARCHAR(50) COMMENT '请求IP',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  INDEX `idx_user_created` (`user_id`, `created_at`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表查询审计日志表';
