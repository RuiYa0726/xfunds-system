-- ============================================================
-- 交易数据生成脚本（3000条）
-- 使用Python生成，避免MySQL存储过程语法问题
-- ============================================================

-- 先清理数据
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE fx_option_trade;
TRUNCATE TABLE fx_swap_trade;
TRUNCATE TABLE fx_forward_trade;
TRUNCATE TABLE fx_spot_trade;
TRUNCATE TABLE fx_trade_master;
TRUNCATE TABLE fx_trade_lifecycle;
SET FOREIGN_KEY_CHECKS = 1;