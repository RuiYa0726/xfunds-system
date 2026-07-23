-- 删除旧的分析指标字段
DELETE FROM report_field_meta WHERE category = '分析指标';

-- 删除旧的分析字段别名
DELETE FROM report_field_alias WHERE field_code LIKE 'yoy_%' OR field_code LIKE 'mom_%';

-- 重新插入新的分析指标字段（统一使用counter_amount）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('yoy_current_counter', 'CURRENT_COUNTER', '本期业务量(CNY)(同比)', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 80),
('yoy_last_counter', 'LAST_YEAR_COUNTER', '去年同期业务量(CNY)', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 81),
('yoy_rate_counter', 'YOY_RATE_COUNTER', '同比增长率(%)', 'DECIMAL', 'N', 'Y', 'N', NULL, 'fx_trade_master', '分析指标', 82),
('yoy_current_profit', 'CURRENT_PROFIT', '本期收益点(同比)', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 83),
('yoy_last_profit', 'LAST_YEAR_PROFIT', '去年同期收益点', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 84),
('yoy_rate_profit', 'YOY_RATE_PROFIT', '同比增长率(收益点)(%)', 'DECIMAL', 'N', 'Y', 'N', NULL, 'fx_trade_master', '分析指标', 85),
('mom_current_counter', 'CURRENT_COUNTER', '本期业务量(CNY)(环比)', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 90),
('mom_last_counter', 'LAST_PERIOD_COUNTER', '上期业务量(CNY)', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 91),
('mom_rate_counter', 'MOM_RATE_COUNTER', '环比增长率(%)', 'DECIMAL', 'N', 'Y', 'N', NULL, 'fx_trade_master', '分析指标', 92),
('mom_current_profit', 'CURRENT_PROFIT', '本期收益点(环比)', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 93),
('mom_last_profit', 'LAST_PERIOD_PROFIT', '上期收益点', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_trade_master', '分析指标', 94),
('mom_rate_profit', 'MOM_RATE_PROFIT', '环比增长率(收益点)(%)', 'DECIMAL', 'N', 'Y', 'N', NULL, 'fx_trade_master', '分析指标', 95);

-- 重新插入分析字段别名
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('yoy_rate_counter', '同比增长率', 'STANDARD'),
('yoy_rate_counter', '同比增长', 'SYNONYM'),
('yoy_rate_counter', '同比', 'SYNONYM'),
('mom_rate_counter', '环比增长率', 'STANDARD'),
('mom_rate_counter', '环比增长', 'SYNONYM'),
('mom_rate_counter', '环比', 'SYNONYM'),
('yoy_current_counter', '本期业务量', 'STANDARD'),
('yoy_current_counter', '当期业务量', 'SYNONYM'),
('yoy_last_counter', '去年同期业务量', 'STANDARD'),
('yoy_last_counter', '同期业务量', 'SYNONYM'),
('mom_current_counter', '本期业务量', 'STANDARD'),
('mom_current_counter', '当期业务量', 'SYNONYM'),
('mom_last_counter', '上期业务量', 'STANDARD'),
('mom_last_counter', '上一期业务量', 'SYNONYM');

-- 更新counter_amount字段的显示名，添加币种信息
UPDATE report_field_meta SET display_name_cn = '交易金额(CNY)' WHERE field_code = 'counter_amount';

-- 更新notional_amount字段的显示名，明确是外币
UPDATE report_field_meta SET display_name_cn = '名义金额(原币种)' WHERE field_code = 'notional_amount';

SELECT '分析指标字段更新完成' as result;