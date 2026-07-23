-- 迁移脚本：为远期交易表增加期限字段，并补全现有数据

-- 1. 为 fx_forward_trade 表增加 term 字段
ALTER TABLE fx_forward_trade ADD COLUMN term VARCHAR(10) COMMENT '期限：1D/SW/1M/2M/3M/6M/1Y' AFTER forward_point;

-- 2. 更新现有远期交易数据的期限字段
-- 期限规则：根据起息日(value_date)和到期日(maturity_date)计算
-- 1D: 1天, SW: 2天, 1M: 约30天, 2M: 约60天, 3M: 约90天, 6M: 约180天, 1Y: 约365天
UPDATE fx_forward_trade ft
JOIN fx_trade_master tm ON ft.trade_id = tm.trade_id
SET ft.term = CASE
    WHEN DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) <= 1 THEN '1D'
    WHEN DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) <= 2 THEN 'SW'
    WHEN DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) <= 35 THEN '1M'
    WHEN DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) <= 65 THEN '2M'
    WHEN DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) <= 95 THEN '3M'
    WHEN DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) <= 185 THEN '6M'
    WHEN DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) <= 370 THEN '1Y'
    ELSE '>1Y'
END
WHERE ft.term IS NULL OR ft.term = '';

-- 3. 查看更新结果
SELECT ft.trade_id, tm.currency_pair, ft.maturity_date, tm.value_date, 
       DATEDIFF(ft.maturity_date, COALESCE(tm.value_date, tm.trade_date)) AS days,
       ft.term
FROM fx_forward_trade ft
JOIN fx_trade_master tm ON ft.trade_id = tm.trade_id
ORDER BY tm.currency_pair, ft.term;
