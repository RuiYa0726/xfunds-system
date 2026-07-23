-- 删除旧的金额相关别名
DELETE FROM report_field_alias WHERE field_code = 'notional_amount' AND alias_word IN ('金额', '交易金额', '成交金额');

-- 添加正确的金额别名映射到counter_amount
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('counter_amount', '金额', 'SYNONYM'),
('counter_amount', '交易金额', 'SYNONYM'),
('counter_amount', '成交金额', 'SYNONYM');

-- 更新counter_amount的标准别名（如果存在则不重复插入）
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('counter_amount', '对手金额', 'STANDARD') ON DUPLICATE KEY UPDATE alias_word='对手金额';

SELECT '金额别名配置更新完成' as result;