-- ============================================================
-- 报表助手 · 字段元数据 + 别名 初始化
-- 依据：V1.0 方案文档第四节
-- ============================================================

-- ===================== 字段元数据 =====================
-- 交易主表 fx_trade_master（t 别名）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('trade_id',              't.trade_id',              '交易ID',       'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 1),
('business_no',           't.business_no',           '业务编号',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '交易信息', 2),
('trade_type',            't.trade_type',            '交易类型',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '交易信息', 3),
('status',                't.status',                '交易状态',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '交易信息', 4),
('branch_code',           't.branch_code',           '分行编码',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '交易信息', 5),
('customer_id',           't.customer_id',           '客户ID',       'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '客户',     50),
('customer_name',         't.customer_name',         '客户名称',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '客户',     51),
('base_currency',         't.base_currency',         '基础币种',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '币种金额', 10),
('quote_currency',        't.quote_currency',        '报价币种',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '币种金额', 11),
('currency_pair',         't.currency_pair',         '货币对',       'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '币种金额', 12),
('notional_amount',       't.notional_amount',       '名义金额',     'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_trade_master', '币种金额', 13),
('counter_amount',        't.counter_amount',        '对手金额',     'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_trade_master', '币种金额', 14),
('trade_direction',       't.trade_direction',       '交易方向',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '交易信息', 6),
('trade_date',            't.trade_date',            '交易日',       'DATE',     'Y', 'N', 'Y', NULL,    'fx_trade_master', '交易信息', 7),
('value_date',            't.value_date',            '起息日',       'DATE',     'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 8),
('maturity_date',         't.maturity_date',         '到期日',       'DATE',     'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 9),
('spot_rate',             't.spot_rate',             '即期汇率',     'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_trade_master', '汇率',     20),
('customer_rate',         't.customer_rate',         '客户汇率',     'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_trade_master', '汇率',     21),
('cost_rate',             't.cost_rate',             '成本汇率',     'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_trade_master', '汇率',     22),
('branch_profit_point',   't.branch_profit_point',   '分行收益点',   'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_trade_master', '损益',     30),
('delivery_type',         't.delivery_type',         '交割类型',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_trade_master', '交易信息', 15),
('settlement_method',     't.settlement_method',     '交割方式',     'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 16),
('special_trade_type',    't.special_trade_type',    '特殊交易类型', 'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 17),
('original_trade_type',   't.original_trade_type',   '原交易类型',   'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 18),
('netting_currency',      't.netting_currency',      '轧差货币',     'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '币种金额', 19),
('netting_amount',        't.netting_amount',        '轧差金额',     'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_trade_master', '币种金额', 23),
('purpose_code',          't.purpose_code',          '用途编码',     'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 19),
('fx_purpose_code',       't.fx_purpose_code',       '外汇用途编码', 'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 20),
('rcpmis_report_flag',    't.rcpmis_report_flag',    'RCPMIS上报标志','STRING',  'Y', 'N', 'N', NULL,    'fx_trade_master', '交易信息', 21),
('maker_id',              't.maker_id',              '经办人ID',     'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '机构',     60),
('checker_id',            't.checker_id',            '复核人ID',     'STRING',   'Y', 'N', 'N', NULL,    'fx_trade_master', '机构',     61);

-- 即期子表 fx_spot_trade（st 别名）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('spot_margin_amount',    'st.margin_amount',        '即期保证金金额', 'DECIMAL', 'N', 'Y', 'N', 'SUM', 'fx_spot_trade', '损益', 31);

-- 远期子表 fx_forward_trade（ft 别名）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('forward_rate',          'ft.forward_rate',         '远期汇率',     'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_forward_trade', '汇率',     23),
('forward_point',         'ft.forward_point',        '远期点',       'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_forward_trade', '汇率',     24),
('forward_term',          'ft.term',                 '远期期限',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_forward_trade', '交易信息', 25),
('forward_margin_amount', 'ft.margin_amount',        '远期保证金金额','DECIMAL', 'N', 'Y', 'N', 'SUM',   'fx_forward_trade', '损益',     32),
('is_rolled_over',        'ft.is_rolled_over',       '是否展期',     'STRING',   'Y', 'N', 'N', NULL,    'fx_forward_trade', '交易信息', 22),
('early_delivery_flag',   'ft.early_delivery_flag',  '提前交割标志', 'STRING',   'Y', 'N', 'N', NULL,    'fx_forward_trade', '交易信息', 23),
('early_default_flag',    'ft.early_default_flag',   '提前违约标志', 'STRING',   'Y', 'N', 'N', NULL,    'fx_forward_trade', '交易信息', 24);

-- 掉期子表 fx_swap_trade（sw 别名）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('swap_type',             'sw.swap_type',            '掉期类型',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_swap_trade', '交易信息', 25),
('swap_term',             'sw.term',                 '掉期期限',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_swap_trade', '交易信息', 27),
('near_leg_amount',       'sw.near_leg_amount',      '近端金额',     'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_swap_trade', '币种金额', 24),
('far_leg_amount',        'sw.far_leg_amount',       '远端金额',     'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_swap_trade', '币种金额', 25),
('near_leg_rate',         'sw.near_leg_rate',        '近端汇率',     'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_swap_trade', '汇率',     26),
('far_leg_rate',          'sw.far_leg_rate',         '远端汇率',     'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_swap_trade', '汇率',     27),
('swap_point',            'sw.swap_point',           '掉期点',       'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_swap_trade', '汇率',     28),
('is_pure_swap',          'sw.is_pure_swap',         '是否纯掉期',   'STRING',   'Y', 'N', 'N', NULL,    'fx_swap_trade', '交易信息', 26);

-- 期权子表 fx_option_trade（ot 别名）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('option_type',           'ot.option_type',          '期权类型',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_option_trade', '期权',     70),
('strike_price',          'ot.strike_price',         '执行价',       'DECIMAL',  'N', 'Y', 'N', 'AVG',   'fx_option_trade', '期权',     71),
('premium_amount',        'ot.premium_amount',       '期权费金额',   'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_option_trade', '期权',     72),
('premium_currency',      'ot.premium_currency',     '期权费币种',   'STRING',   'Y', 'N', 'N', NULL,    'fx_option_trade', '期权',     73),
('exercise_flag',         'ot.exercise_flag',        '是否行权',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_option_trade', '期权',     74),
('close_pnl',             'ot.close_pnl',            '平仓盈亏',     'DECIMAL',  'N', 'Y', 'N', 'SUM',   'fx_option_trade', '损益',     33);

-- 客户表 fx_customer（c 别名）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('cust_customer_type',    'c.customer_type',         '客户类型',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_customer', '客户',     52),
('cust_corp_type',        'c.corp_type',             '企业类型',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_customer', '客户',     53),
('cust_credit_level',     'c.credit_level',          '信用等级',     'STRING',   'Y', 'N', 'Y', NULL,    'fx_customer', '客户',     54),
('cust_risk_level',       'c.risk_level',            '风评等级',     'STRING',   'Y', 'N', 'N', NULL,    'fx_customer', '客户',     55),
('cust_contact_person',   'c.contact_person',        '联系人',       'STRING',   'Y', 'N', 'N', NULL,    'fx_customer', '客户',     56);

-- 机构表 fx_org（o 别名）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('org_name',              'o.org_name',              '机构名称',     'STRING',   'Y', 'N', 'N', NULL,    'fx_org', '机构',     62),
('org_level',             'o.org_level',             '机构层级',     'STRING',   'Y', 'N', 'N', NULL,    'fx_org', '机构',     63);

-- 经办人姓名（关联 fx_user u）
INSERT INTO report_field_meta (field_code, column_expr, display_name_cn, data_type, is_dimension, is_metric, is_filterable, default_agg, source_table, category, sort_order) VALUES
('maker_name',            'u.real_name',             '经办人',       'STRING',   'Y', 'N', 'N', NULL,    'fx_user', '机构',     64);

-- ===================== 字段别名（辅助自然语言解析） =====================
-- 交易日
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('trade_date', '交易日', 'STANDARD'),
('trade_date', '交易日期', 'SYNONYM'),
('trade_date', '交易时间', 'SYNONYM'),
('trade_date', '日期', 'SYNONYM');

-- 客户名称
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('customer_name', '客户名称', 'STANDARD'),
('customer_name', '客户名', 'SYNONYM'),
('customer_name', '客户', 'SYNONYM'),
('customer_name', '客户姓名', 'SYNONYM');

-- 名义金额
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('notional_amount', '名义金额', 'STANDARD'),
('notional_amount', '本金', 'SYNONYM');

-- 对手金额（人民币）
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('counter_amount', '对手金额', 'STANDARD'),
('counter_amount', '交易金额', 'SYNONYM'),
('counter_amount', '成交金额', 'SYNONYM'),
('counter_amount', '金额', 'SYNONYM');

-- 即期汇率
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('spot_rate', '即期汇率', 'STANDARD'),
('spot_rate', '即期', 'SYNONYM');

-- 客户汇率
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('customer_rate', '客户汇率', 'STANDARD'),
('customer_rate', '成交汇率', 'SYNONYM');

-- 成本汇率
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('cost_rate', '成本汇率', 'STANDARD'),
('cost_rate', '成本', 'SYNONYM');

-- 分行收益点
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('branch_profit_point', '分行收益点', 'STANDARD'),
('branch_profit_point', '分行利润', 'SYNONYM'),
('branch_profit_point', '收益点', 'SYNONYM'),
('branch_profit_point', '利润', 'SYNONYM'),
('branch_profit_point', '收益', 'SYNONYM');

-- 远期汇率
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('forward_rate', '远期汇率', 'STANDARD'),
('forward_rate', '远期价格', 'SYNONYM');

-- 远期点
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('forward_point', '远期点', 'STANDARD'),
('forward_point', '掉期点', 'SYNONYM');

-- 远期期限
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('forward_term', '远期期限', 'STANDARD'),
('forward_term', '远期到期期限', 'SYNONYM'),
('forward_term', '远期期限分组', 'SYNONYM');

-- 掉期类型
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('swap_type', '掉期类型', 'STANDARD');

-- 掉期期限
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('swap_term', '掉期期限', 'STANDARD'),
('swap_term', '掉期期限分组', 'SYNONYM'),
('swap_term', '掉期到期期限', 'SYNONYM');

-- 期权类型
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('option_type', '期权类型', 'STANDARD'),
('option_type', '期权', 'SYNONYM');

-- 期权费
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('premium_amount', '期权费金额', 'STANDARD'),
('premium_amount', '期权费', 'SYNONYM'),
('premium_amount', '权利金', 'SYNONYM');

-- 执行价
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('strike_price', '执行价', 'STANDARD'),
('strike_price', '行权价', 'SYNONYM'),
('strike_price', '行权价格', 'SYNONYM');

-- 平仓盈亏
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('close_pnl', '平仓盈亏', 'STANDARD'),
('close_pnl', '盈亏', 'SYNONYM'),
('close_pnl', '盈亏金额', 'SYNONYM');

-- 交易类型
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('trade_type', '交易类型', 'STANDARD'),
('trade_type', '产品类型', 'SYNONYM'),
('trade_type', '品种', 'SYNONYM');

-- 交易状态
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('status', '交易状态', 'STANDARD'),
('status', '状态', 'SYNONYM');

-- 客户ID
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('customer_id', '客户ID', 'STANDARD'),
('customer_id', '客户号', 'SYNONYM'),
('customer_id', '客户编号', 'SYNONYM');

-- 业务编号
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('business_no', '业务编号', 'STANDARD'),
('business_no', '流水号', 'SYNONYM'),
('business_no', '编号', 'SYNONYM');

-- 货币对
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('currency_pair', '货币对', 'STANDARD'),
('currency_pair', '币种对', 'SYNONYM');

-- 基础币种
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('base_currency', '基础币种', 'STANDARD'),
('base_currency', '基础货币', 'SYNONYM');

-- 报价币种
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('quote_currency', '报价币种', 'STANDARD'),
('quote_currency', '报价货币', 'SYNONYM'),
('quote_currency', '对标币种', 'SYNONYM');

-- 交易方向
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('trade_direction', '交易方向', 'STANDARD'),
('trade_direction', '方向', 'SYNONYM'),
('trade_direction', '买卖方向', 'SYNONYM');

-- 到期日
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('maturity_date', '到期日', 'STANDARD'),
('maturity_date', '到期日期', 'SYNONYM'),
('maturity_date', '到期时间', 'SYNONYM');

-- 起息日
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('value_date', '起息日', 'STANDARD'),
('value_date', '交割日', 'SYNONYM'),
('value_date', '结算日', 'SYNONYM');

-- 分行编码
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('branch_code', '分行编码', 'STANDARD'),
('branch_code', '分行', 'SYNONYM'),
('branch_code', '分行代码', 'SYNONYM'),
('branch_code', '机构编码', 'SYNONYM');

-- 机构名称
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('org_name', '机构名称', 'STANDARD'),
('org_name', '机构', 'SYNONYM'),
('org_name', '分行名称', 'SYNONYM');

-- 经办人
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('maker_name', '经办人', 'STANDARD'),
('maker_name', '经办人姓名', 'SYNONYM'),
('maker_name', '操作员', 'SYNONYM');

-- 客户类型
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('cust_customer_type', '客户类型', 'STANDARD'),
('cust_customer_type', '客户类别', 'SYNONYM');

-- 企业类型
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('cust_corp_type', '企业类型', 'STANDARD'),
('cust_corp_type', '企业类别', 'SYNONYM');

-- 信用等级
INSERT INTO report_field_alias (field_code, alias_word, alias_type) VALUES
('cust_credit_level', '信用等级', 'STANDARD'),
('cust_credit_level', '信用评级', 'SYNONYM'),
('cust_credit_level', '评级', 'SYNONYM');

-- ===================== 角色权限（默认全部允许） =====================
-- 为所有现有角色（admin/普通角色）授权查看全部字段
-- 通过应用层默认策略：未配置权限记录时默认允许，配置过的按 can_view 控制
-- 这里不预置数据，由应用层默认策略处理
