-- 简单的 MySQL 迁移脚本
-- 请确保这些字段在您的数据库中还不存在

-- 为 fx_swap_trade 表添加缺失的字段
ALTER TABLE fx_swap_trade ADD COLUMN near_leg_cost_rate DECIMAL(18,8) COMMENT '近端成本汇率';
ALTER TABLE fx_swap_trade ADD COLUMN near_leg_branch_profit_point DECIMAL(18,8) COMMENT '近端分行收益点';
ALTER TABLE fx_swap_trade ADD COLUMN far_leg_cost_rate DECIMAL(18,8) COMMENT '远端成本汇率';
ALTER TABLE fx_swap_trade ADD COLUMN far_leg_branch_profit_point DECIMAL(18,8) COMMENT '远端分行收益点';

-- 为 fx_trade_master 表添加缺失的字段
ALTER TABLE fx_trade_master ADD COLUMN original_trade_type VARCHAR(30) COMMENT '原交易类型';
ALTER TABLE fx_trade_master ADD COLUMN netting_currency VARCHAR(10) COMMENT '轧差货币';
ALTER TABLE fx_trade_master ADD COLUMN netting_account VARCHAR(50) COMMENT '轧差账户';
ALTER TABLE fx_trade_master ADD COLUMN netting_amount DECIMAL(20,2) COMMENT '轧差金额';
ALTER TABLE fx_trade_master ADD COLUMN authorizer_id BIGINT COMMENT '授权人ID';
ALTER TABLE fx_trade_master ADD COLUMN authorize_time DATETIME COMMENT '授权时间';

-- 迁移完成
SELECT 'Database migration completed successfully!' AS message;
