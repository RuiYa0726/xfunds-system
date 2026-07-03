-- 为 fx_swap_trade 表添加缺失的字段
-- 这些字段在实体类和 MyBatis 映射中存在，但数据库表中缺失

-- 近端成本汇率
ALTER TABLE fx_swap_trade ADD COLUMN IF NOT EXISTS near_leg_cost_rate DECIMAL(18, 8) COMMENT '近端成本汇率';

-- 近端分行收益点
ALTER TABLE fx_swap_trade ADD COLUMN IF NOT EXISTS near_leg_branch_profit_point DECIMAL(18, 8) COMMENT '近端分行收益点';

-- 远端成本汇率
ALTER TABLE fx_swap_trade ADD COLUMN IF NOT EXISTS far_leg_cost_rate DECIMAL(18, 8) COMMENT '远端成本汇率';

-- 远端分行收益点
ALTER TABLE fx_swap_trade ADD COLUMN IF NOT EXISTS far_leg_branch_profit_point DECIMAL(18, 8) COMMENT '远端分行收益点';

-- 为 fx_trade_master 表添加轧差相关字段（提前违约功能需要）
ALTER TABLE fx_trade_master ADD COLUMN IF NOT EXISTS netting_currency VARCHAR(10) COMMENT '轧差货币';
ALTER TABLE fx_trade_master ADD COLUMN IF NOT EXISTS netting_account VARCHAR(50) COMMENT '轧差账户';
ALTER TABLE fx_trade_master ADD COLUMN IF NOT EXISTS netting_amount DECIMAL(20, 2) COMMENT '轧差金额';
