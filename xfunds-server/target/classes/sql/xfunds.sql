-- ============================================================
-- 银行外汇交易系统 数据库脚本
-- 数据库：xfunds
-- 字符集：utf8mb4
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS xfunds DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE xfunds;

-- ============================================================
-- 一、机构与权限相关表
-- ============================================================

-- 机构表
DROP TABLE IF EXISTS fx_org;
CREATE TABLE fx_org (
    org_code         VARCHAR(20)  NOT NULL COMMENT '机构编码',
    org_name         VARCHAR(100) NOT NULL COMMENT '机构名称',
    org_level        INT          NOT NULL COMMENT '机构层级：1总行 2分行 3支行',
    parent_org_code  VARCHAR(20)           COMMENT '上级机构编码',
    org_type         VARCHAR(20)           COMMENT '机构类型',
    is_trading_org   VARCHAR(2)   DEFAULT 'Y' COMMENT '是否为交易机构：Y/N',
    approval_limit   DECIMAL(20,2)         COMMENT '审批权限额度',
    fx_business_flag VARCHAR(2)   DEFAULT 'Y' COMMENT '外汇业务标志：Y/N',
    status           VARCHAR(2)   DEFAULT '1' COMMENT '状态：1启用 0停用',
    created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (org_code),
    KEY idx_parent_org_code (parent_org_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构表';

-- 角色表
DROP TABLE IF EXISTS fx_role;
CREATE TABLE fx_role (
    role_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_code  VARCHAR(50)  NOT NULL COMMENT '角色编码',
    role_name  VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(200)         COMMENT '描述',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户表
DROP TABLE IF EXISTS fx_user;
CREATE TABLE fx_user (
    user_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username   VARCHAR(50)  NOT NULL COMMENT '用户名',
    password   VARCHAR(100) NOT NULL COMMENT '密码',
    real_name  VARCHAR(50)           COMMENT '真实姓名',
    org_code   VARCHAR(20)           COMMENT '所属机构编码',
    status     VARCHAR(2)   DEFAULT '1' COMMENT '状态：1启用 0停用',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_username (username),
    KEY idx_org_code (org_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户角色关联表
DROP TABLE IF EXISTS fx_user_role;
CREATE TABLE fx_user_role (
    user_id BIGINT      NOT NULL COMMENT '用户ID',
    role_id BIGINT      NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ============================================================
-- 二、客户相关表
-- ============================================================

-- 客户表
DROP TABLE IF EXISTS fx_customer;
CREATE TABLE fx_customer (
    customer_id    VARCHAR(50)  NOT NULL COMMENT '客户ID',
    customer_name  VARCHAR(100) NOT NULL COMMENT '客户名称',
    customer_type  VARCHAR(20)           COMMENT '客户类型',
    id_type        VARCHAR(20)           COMMENT '证件类型',
    id_no          VARCHAR(50)           COMMENT '证件号码',
    org_code       VARCHAR(20)           COMMENT '所属机构编码',
    credit_level   VARCHAR(20)           COMMENT '信用等级',
    status         VARCHAR(2)   DEFAULT '1' COMMENT '状态：1启用 0停用',
    contact_person VARCHAR(50)           COMMENT '联系人',
    contact_phone  VARCHAR(30)           COMMENT '联系电话',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (customer_id),
    KEY idx_org_code (org_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- 客户账户表
DROP TABLE IF EXISTS fx_customer_account;
CREATE TABLE fx_customer_account (
    account_id    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '账户ID',
    customer_id   VARCHAR(50)   NOT NULL COMMENT '客户ID',
    account_no    VARCHAR(50)   NOT NULL COMMENT '账号',
    currency      VARCHAR(10)   NOT NULL COMMENT '币种',
    account_type  VARCHAR(10)            COMMENT '账户类型：SPOT现汇 CASH现钞',
    balance       DECIMAL(20,2) DEFAULT 0 COMMENT '账户余额',
    frozen_amount DECIMAL(20,2) DEFAULT 0 COMMENT '冻结金额',
    status        VARCHAR(2)    DEFAULT '1' COMMENT '状态：1启用 0停用',
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_no (account_no),
    KEY idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户账户表';

-- 客户余额表
DROP TABLE IF EXISTS fx_customer_balance;
CREATE TABLE fx_customer_balance (
    balance_id     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '余额ID',
    customer_id    VARCHAR(50)   NOT NULL COMMENT '客户ID',
    currency       VARCHAR(10)   NOT NULL COMMENT '币种',
    balance_amount DECIMAL(20,2) DEFAULT 0 COMMENT '余额金额',
    frozen_amount  DECIMAL(20,2) DEFAULT 0 COMMENT '冻结金额',
    updated_at     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (balance_id),
    KEY idx_customer_currency (customer_id, currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户余额表';

-- ============================================================
-- 三、报价表
-- ============================================================

-- 报价表
DROP TABLE IF EXISTS fx_quote;
CREATE TABLE fx_quote (
    quote_id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '报价ID',
    quote_type         VARCHAR(20)   NOT NULL COMMENT '报价类型：SPOT/FORWARD/SWAP',
    currency_pair      VARCHAR(20)   NOT NULL COMMENT '货币对',
    base_currency      VARCHAR(10)   NOT NULL COMMENT '基础货币',
    quote_currency     VARCHAR(10)   NOT NULL COMMENT '报价货币',
    term               VARCHAR(20)            COMMENT '期限（远期/掉期如 1M/3M）',
    maturity_date      DATE                   COMMENT '到期日',
    market_mid_rate    DECIMAL(18,8)          COMMENT '市场中间价',
    total_buy_rate     DECIMAL(18,8)          COMMENT '总行买入价',
    total_sell_rate    DECIMAL(18,8)          COMMENT '总行卖出价',
    branch_customer_buy_rate  DECIMAL(18,8)  COMMENT '分/客买价（分行买入价=客户卖出价）',
    branch_customer_sell_rate DECIMAL(18,8)  COMMENT '分/客卖价（分行卖出价=客户买入价）',
    forward_point      DECIMAL(18,8)          COMMENT '远期点',
    swap_point         DECIMAL(18,8)          COMMENT '掉期点',
    status             VARCHAR(20)   DEFAULT 'ACTIVE' COMMENT '状态',
    effective_time     DATETIME               COMMENT '生效时间',
    published_by       BIGINT                 COMMENT '发布人',
    created_at         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (quote_id),
    KEY idx_quote_type_pair (quote_type, currency_pair),
    KEY idx_currency_pair (currency_pair)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价表';

-- ============================================================
-- 四、交易主表与子表
-- ============================================================

-- 交易主表
DROP TABLE IF EXISTS fx_trade_master;
CREATE TABLE fx_trade_master (
    trade_id            VARCHAR(50)   NOT NULL COMMENT '交易ID',
    business_no         VARCHAR(50)   NOT NULL COMMENT '业务编号',
    trade_type          VARCHAR(20)   NOT NULL COMMENT '交易类型：SPOT/FORWARD/SWAP/OPTION',
    status              VARCHAR(30)   NOT NULL COMMENT '交易状态',
    branch_code         VARCHAR(20)            COMMENT '分行编码',
    customer_id         VARCHAR(50)            COMMENT '客户ID',
    customer_name       VARCHAR(100)           COMMENT '客户名称',
    base_currency       VARCHAR(10)            COMMENT '基础货币',
    quote_currency      VARCHAR(10)            COMMENT '报价货币',
    currency_pair       VARCHAR(20)            COMMENT '货币对',
    notional_amount     DECIMAL(20,2)          COMMENT '名义金额',
    counter_amount      DECIMAL(20,2)          COMMENT '对手金额',
    trade_direction     VARCHAR(10)            COMMENT '交易方向：BUY/SELL',
    value_date          DATE                   COMMENT '起息日',
    trade_date          DATE                   COMMENT '交易日',
    maturity_date       DATE                   COMMENT '到期日',
    delivery_type       VARCHAR(10)            COMMENT '交割类型：T0/T1/T2',
    settlement_method   VARCHAR(10)            COMMENT '交割方式：FULL/NET',
    spot_rate           DECIMAL(18,8)          COMMENT '即期汇率',
    customer_rate       DECIMAL(18,8)          COMMENT '客户汇率',
    cost_rate           DECIMAL(18,8)          COMMENT '成本汇率',
    branch_profit_point DECIMAL(18,8)          COMMENT '分行利润点',
    special_trade_type  VARCHAR(30)            COMMENT '特殊交易类型',
    original_trade_type VARCHAR(30)            COMMENT '原交易类型',
    original_trade_id   VARCHAR(50)            COMMENT '原交易ID',
    related_trade_id    VARCHAR(50)            COMMENT '关联交易ID',
    maker_id            BIGINT                 COMMENT '经办人ID',
    checker_id          BIGINT                 COMMENT '复核人ID',
    authorizer_id       BIGINT                 COMMENT '授权人ID',
    make_time           DATETIME               COMMENT '经办时间',
    check_time          DATETIME               COMMENT '复核时间',
    authorize_time      DATETIME               COMMENT '授权时间',
    purpose_code        VARCHAR(50)            COMMENT '用途编码',
    fx_purpose_code     VARCHAR(50)            COMMENT '外汇用途编码',
    rcpmis_report_flag  VARCHAR(2)             COMMENT 'RCPMIS上报标志',
    rcpmis_report_time  DATETIME               COMMENT 'RCPMIS上报时间',
    netting_currency    VARCHAR(10)            COMMENT '轧差货币',
    netting_account     VARCHAR(50)            COMMENT '轧差账户',
    netting_amount      DECIMAL(20,2)          COMMENT '轧差金额',
    version             INT           DEFAULT 0 COMMENT '版本号',
    created_at          DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (trade_id),
    UNIQUE KEY uk_business_no (business_no),
    KEY idx_trade_type (trade_type),
    KEY idx_status (status),
    KEY idx_customer_id (customer_id),
    KEY idx_branch_code (branch_code),
    KEY idx_original_trade_id (original_trade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易主表';

-- 即期交易子表
DROP TABLE IF EXISTS fx_spot_trade;
CREATE TABLE fx_spot_trade (
    trade_id          VARCHAR(50)   NOT NULL COMMENT '交易ID',
    settlement_type   VARCHAR(10)            COMMENT '交割类型：T0/T1/T2',
    spot_rate         DECIMAL(18,8)          COMMENT '即期汇率',
    customer_rate     DECIMAL(18,8)          COMMENT '客户汇率',
    cost_rate         DECIMAL(18,8)          COMMENT '成本汇率',
    currency1_account VARCHAR(50)            COMMENT '货币1账户',
    currency2_account VARCHAR(50)            COMMENT '货币2账户',
    margin_account_id VARCHAR(50)            COMMENT '保证金账户ID',
    margin_amount     DECIMAL(20,2)          COMMENT '保证金金额',
    amount            DECIMAL(20,2)          COMMENT '金额',
    PRIMARY KEY (trade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='即期交易子表';

-- 远期交易子表
DROP TABLE IF EXISTS fx_forward_trade;
CREATE TABLE fx_forward_trade (
    trade_id              VARCHAR(50)   NOT NULL COMMENT '交易ID',
    maturity_date         DATE                   COMMENT '到期日',
    forward_rate          DECIMAL(18,8)          COMMENT '远期汇率',
    forward_point         DECIMAL(18,8)          COMMENT '远期点',
    settlement_method     VARCHAR(10)            COMMENT '交割方式：FULL/NET',
    net_settlement_amount DECIMAL(20,2)          COMMENT '差额交割金额',
    margin_account_id     VARCHAR(50)            COMMENT '保证金账户ID',
    margin_amount         DECIMAL(20,2)          COMMENT '保证金金额',
    amount                DECIMAL(20,2)          COMMENT '金额',
    is_rolled_over        VARCHAR(2)             COMMENT '是否展期：Y/N',
    original_trade_id     VARCHAR(50)            COMMENT '原交易ID',
    early_delivery_flag   VARCHAR(2)             COMMENT '提前交割标志：Y/N',
    early_default_flag    VARCHAR(2)             COMMENT '提前违约标志：Y/N',
    PRIMARY KEY (trade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='远期交易子表';

-- 掉期交易子表
DROP TABLE IF EXISTS fx_swap_trade;
CREATE TABLE fx_swap_trade (
    trade_id          VARCHAR(50)   NOT NULL COMMENT '交易ID',
    swap_type         VARCHAR(10)            COMMENT '掉期类型：S_B近卖远买/B_S近买远卖',
    term              VARCHAR(10)            COMMENT '掉期期限：ON/TN/SN/SW/1M',
    near_leg_direction VARCHAR(10)           COMMENT '近端方向',
    near_leg_amount   DECIMAL(20,2)          COMMENT '近端金额',
    near_leg_rate     DECIMAL(18,8)          COMMENT '近端汇率',
    near_leg_cost_rate DECIMAL(18,8)         COMMENT '近端成本汇率',
    near_leg_customer_rate DECIMAL(18,8)     COMMENT '近端客户汇率',
    near_leg_branch_profit_point DECIMAL(18,8) COMMENT '近端分行收益点',
    near_leg_value_date DATE                 COMMENT '近端起息日',
    near_leg_account  VARCHAR(50)            COMMENT '近端账户',
    near_leg_currency1_account VARCHAR(50)   COMMENT '近端币种1账户',
    near_leg_currency2_account VARCHAR(50)   COMMENT '近端币种2账户',
    near_leg_settlement_method VARCHAR(10)   COMMENT '近端交割方式：FULL/NET',
    far_leg_direction VARCHAR(10)            COMMENT '远端方向',
    far_leg_amount    DECIMAL(20,2)          COMMENT '远端金额',
    far_leg_rate      DECIMAL(18,8)          COMMENT '远端汇率',
    far_leg_cost_rate DECIMAL(18,8)          COMMENT '远端成本汇率',
    far_leg_customer_rate DECIMAL(18,8)      COMMENT '远端客户汇率',
    far_leg_branch_profit_point DECIMAL(18,8) COMMENT '远端分行收益点',
    far_leg_value_date DATE                  COMMENT '远端起息日',
    far_leg_account   VARCHAR(50)            COMMENT '远端账户',
    far_leg_currency1_account VARCHAR(50)    COMMENT '远端币种1账户',
    far_leg_currency2_account VARCHAR(50)    COMMENT '远端币种2账户',
    far_leg_settlement_method VARCHAR(10)    COMMENT '远端交割方式：FULL/NET',
    swap_point        DECIMAL(18,8)          COMMENT '掉期点',
    near_spot_rate    DECIMAL(18,8)          COMMENT '近端即期汇率',
    is_pure_swap      VARCHAR(2)             COMMENT '是否纯掉期：Y/N',
    margin_account_id VARCHAR(50)            COMMENT '保证金账户ID',
    margin_amount     DECIMAL(20,2)          COMMENT '保证金金额',
    PRIMARY KEY (trade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='掉期交易子表';

-- 期权交易子表
DROP TABLE IF EXISTS fx_option_trade;
CREATE TABLE fx_option_trade (
    trade_id              VARCHAR(50)   NOT NULL COMMENT '交易ID',
    option_type           VARCHAR(10)            COMMENT '期权类型：CALL/PUT',
    option_style          VARCHAR(10)            COMMENT '行权方式：AMERICAN/EUROPEAN',
    strike_price          DECIMAL(18,8)          COMMENT '执行价',
    premium_amount        DECIMAL(20,2)          COMMENT '期权费金额',
    premium_currency      VARCHAR(10)            COMMENT '期权费币种',
    premium_value_date    DATE                   COMMENT '期权费起息日',
    premium_paid_flag     VARCHAR(2)             COMMENT '期权费是否已支付：Y/N',
    premium_account_id    VARCHAR(50)            COMMENT '期权费账户ID',
    maturity_date         DATE                   COMMENT '到期日',
    exercise_date         DATE                   COMMENT '行权日',
    exercise_flag         VARCHAR(2)             COMMENT '是否行权标志：Y/N',
    abandon_flag          VARCHAR(2)             COMMENT '是否放弃标志：Y/N',
    settlement_method     VARCHAR(10)            COMMENT '交割方式：FULL/NET',
    buyer_seller          VARCHAR(10)            COMMENT '买卖方向',
    currency1_account     VARCHAR(50)            COMMENT '货币1账户',
    currency2_account     VARCHAR(50)            COMMENT '货币2账户',
    notional_amount       DECIMAL(20,2)          COMMENT '名义金额',
    currency1_amount      DECIMAL(20,2)          COMMENT '货币1金额',
    currency2_amount      DECIMAL(20,2)          COMMENT '货币2金额',
    observation_start_date DATE                  COMMENT '观察开始日',
    observation_end_date  DATE                   COMMENT '观察结束日',
    exercise_time_point   DATETIME               COMMENT '行权时间点',
    days                  INT                    COMMENT '天数',
    auto_exercise_flag    VARCHAR(2)             COMMENT '是否自动行权：Y/N',
    close_date            DATE                   COMMENT '平仓日',
    close_premium         DECIMAL(20,2)          COMMENT '平仓期权费',
    close_pnl             DECIMAL(20,2)          COMMENT '平仓盈亏',
    closed_amount         DECIMAL(20,2)          COMMENT '已平仓金额',
    remaining_amount      DECIMAL(20,2)          COMMENT '剩余金额',
    reference_rate        DECIMAL(18,8)          COMMENT '参考汇率',
    PRIMARY KEY (trade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期权交易子表';

-- ============================================================
-- 五、保证金相关表
-- ============================================================

-- 保证金账户表
DROP TABLE IF EXISTS fx_margin_account;
CREATE TABLE fx_margin_account (
    margin_account_id VARCHAR(50)   NOT NULL COMMENT '保证金账户ID',
    customer_id       VARCHAR(50)   NOT NULL COMMENT '客户ID',
    currency          VARCHAR(10)   NOT NULL COMMENT '币种',
    balance           DECIMAL(20,2) DEFAULT 0 COMMENT '账户余额',
    frozen_amount     DECIMAL(20,2) DEFAULT 0 COMMENT '冻结金额',
    occupied_amount   DECIMAL(20,2) DEFAULT 0 COMMENT '占用金额',
    status            VARCHAR(2)    DEFAULT '1' COMMENT '状态：1启用 0停用',
    created_at        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (margin_account_id),
    KEY idx_customer_currency (customer_id, currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保证金账户表';

-- 保证金交易流水表
DROP TABLE IF EXISTS fx_margin_txn;
CREATE TABLE fx_margin_txn (
    txn_id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    margin_account_id VARCHAR(50)   NOT NULL COMMENT '保证金账户ID',
    trade_id          VARCHAR(50)            COMMENT '交易ID',
    txn_type          VARCHAR(20)            COMMENT '交易类型：SUPPLEMENT补充/RELEASE释放/DEDUCT扣减/FREEZE冻结',
    amount            DECIMAL(20,2)          COMMENT '金额',
    balance_after     DECIMAL(20,2)          COMMENT '交易后余额',
    operator_id       BIGINT                 COMMENT '操作人ID',
    operate_time      DATETIME               COMMENT '操作时间',
    remark            VARCHAR(200)           COMMENT '备注',
    PRIMARY KEY (txn_id),
    KEY idx_margin_account_id (margin_account_id),
    KEY idx_trade_id (trade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保证金交易流水表';

-- ============================================================
-- 六、生命周期与审批相关表
-- ============================================================

-- 交易生命周期事件表
DROP TABLE IF EXISTS fx_trade_lifecycle;
CREATE TABLE fx_trade_lifecycle (
    event_id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    trade_id          VARCHAR(50)   NOT NULL COMMENT '交易ID',
    event_type        VARCHAR(30)            COMMENT '事件类型',
    event_time        DATETIME               COMMENT '事件时间',
    operator_id       BIGINT                 COMMENT '操作人ID',
    before_status     VARCHAR(30)            COMMENT '变更前状态',
    after_status      VARCHAR(30)            COMMENT '变更后状态',
    event_amount      DECIMAL(20,2)          COMMENT '事件金额',
    event_rate        DECIMAL(18,8)          COMMENT '事件汇率',
    related_trade_id  VARCHAR(50)            COMMENT '关联交易ID',
    remark            VARCHAR(500)           COMMENT '备注',
    audit_snapshot    TEXT                   COMMENT '审计快照',
    PRIMARY KEY (event_id),
    KEY idx_trade_id (trade_id),
    KEY idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易生命周期事件表';

-- 审批日志表
DROP TABLE IF EXISTS fx_approval_log;
CREATE TABLE fx_approval_log (
    log_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    trade_id      VARCHAR(50)  NOT NULL COMMENT '交易ID',
    approval_node VARCHAR(20)           COMMENT '审批节点：MAKE/CHECK/AUTHORIZE',
    approver_id   BIGINT                COMMENT '审批人ID',
    approver_name VARCHAR(50)           COMMENT '审批人姓名',
    approver_org  VARCHAR(20)           COMMENT '审批人机构',
    decision      VARCHAR(20)           COMMENT '决定：APPROVE/REJECT/RETURN',
    decision_time DATETIME              COMMENT '决定时间',
    comment       VARCHAR(500)          COMMENT '审批意见',
    before_status VARCHAR(30)           COMMENT '变更前状态',
    after_status  VARCHAR(30)           COMMENT '变更后状态',
    PRIMARY KEY (log_id),
    KEY idx_trade_id (trade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批日志表';

-- ============================================================
-- 七、任务表
-- ============================================================

-- 任务表
DROP TABLE IF EXISTS fx_task;
CREATE TABLE fx_task (
    task_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    task_type          VARCHAR(30)           COMMENT '任务类型',
    trade_id           VARCHAR(50)           COMMENT '交易ID',
    business_key       VARCHAR(50)           COMMENT '业务键',
    business_type      VARCHAR(30)           COMMENT '业务类型',
    priority           INT          DEFAULT 0 COMMENT '优先级',
    assignee_id        BIGINT                COMMENT '指派人ID',
    assignee_role      VARCHAR(50)           COMMENT '指派角色',
    assignee_org       VARCHAR(20)           COMMENT '指派机构',
    assignee_org_level INT                   COMMENT '指派机构层级',
    status             VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/CLAIMED/DONE/CANCELLED/TIMEOUT',
    create_time        DATETIME              COMMENT '创建时间',
    due_time           DATETIME              COMMENT '到期时间',
    claim_time         DATETIME              COMMENT '认领时间',
    complete_time      DATETIME              COMMENT '完成时间',
    claim_lock         VARCHAR(100)          COMMENT '认领锁',
    payload            TEXT                  COMMENT '任务载荷（JSON）',
    PRIMARY KEY (task_id),
    KEY idx_status (status),
    KEY idx_assignee_id (assignee_id),
    KEY idx_trade_id (trade_id),
    KEY idx_business_key (business_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- ============================================================
-- 八、参数表
-- ============================================================

-- 客户授信额度表
DROP TABLE IF EXISTS fx_credit_limit;
CREATE TABLE fx_credit_limit (
    limit_id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '额度ID',
    customer_id         VARCHAR(50)   NOT NULL COMMENT '客户ID',
    currency            VARCHAR(10)   NOT NULL COMMENT '币种',
    credit_limit_amount DECIMAL(20,2)          COMMENT '授信额度',
    used_amount         DECIMAL(20,2) DEFAULT 0 COMMENT '已用额度',
    available_amount    DECIMAL(20,2)          COMMENT '可用额度',
    status              VARCHAR(2)    DEFAULT '1' COMMENT '状态：1启用 0停用',
    updated_at          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (limit_id),
    KEY idx_customer_currency (customer_id, currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户授信额度表';

-- 保证金参数表
DROP TABLE IF EXISTS fx_margin_param;
CREATE TABLE fx_margin_param (
    param_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '参数ID',
    param_code  VARCHAR(50)  NOT NULL COMMENT '参数编码',
    param_name  VARCHAR(100)          COMMENT '参数名称',
    param_value VARCHAR(200)          COMMENT '参数值',
    currency    VARCHAR(10)           COMMENT '币种',
    trade_type  VARCHAR(20)           COMMENT '交易类型',
    description VARCHAR(200)          COMMENT '描述',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (param_id),
    UNIQUE KEY uk_param_code (param_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保证金参数表';

-- 期权参数表
DROP TABLE IF EXISTS fx_option_param;
CREATE TABLE fx_option_param (
    param_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '参数ID',
    param_code  VARCHAR(50)  NOT NULL COMMENT '参数编码',
    param_name  VARCHAR(100)          COMMENT '参数名称',
    param_value VARCHAR(200)          COMMENT '参数值',
    description VARCHAR(200)          COMMENT '描述',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (param_id),
    UNIQUE KEY uk_param_code (param_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期权参数表';

-- 系统参数表
DROP TABLE IF EXISTS fx_sys_param;
CREATE TABLE fx_sys_param (
    param_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '参数ID',
    param_code  VARCHAR(50)  NOT NULL COMMENT '参数编码',
    param_name  VARCHAR(100)          COMMENT '参数名称',
    param_value VARCHAR(500)          COMMENT '参数值',
    param_type  VARCHAR(20)           COMMENT '参数类型',
    description VARCHAR(200)          COMMENT '描述',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (param_id),
    UNIQUE KEY uk_param_code (param_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数表';

-- ============================================================
-- 九、初始化数据
-- ============================================================

-- 1. 机构数据（三级机构）
INSERT INTO fx_org (org_code, org_name, org_level, parent_org_code, org_type, is_trading_org, approval_limit, fx_business_flag, status) VALUES
('100000', '总行', 1, NULL, 'HEAD_OFFICE', 'Y', 1000000000.00, 'Y', '1'),
('110000', '北京分行', 2, '100000', 'BRANCH', 'Y', 100000000.00, 'Y', '1'),
('110100', '北京中关村支行', 3, '110000', 'SUB_BRANCH', 'Y', 10000000.00, 'Y', '1');

-- 2. 角色数据
INSERT INTO fx_role (role_code, role_name, description) VALUES
('MAKER', '经办', '负责发起交易'),
('CHECKER', '复核', '负责复核交易'),
('AUTHORIZER', '授权', '负责授权交易');

-- 3. 用户数据（密码为明文，实际项目应加密存储）
INSERT INTO fx_user (username, password, real_name, org_code, status) VALUES
('admin', 'admin123', '系统管理员', '100000', '1'),
('maker1', '123456', '张经办', '110000', '1'),
('checker1', '123456', '李复核', '110000', '1');

-- 4. 用户角色关联（admin拥有全部角色，maker1为经办，checker1为复核）
INSERT INTO fx_user_role (user_id, role_id) VALUES
(1, 1), (1, 2), (1, 3),
(2, 1),
(3, 2);

-- 5. 客户数据
INSERT INTO fx_customer (customer_id, customer_name, customer_type, id_type, id_no, org_code, credit_level, status, contact_person, contact_phone) VALUES
('C20240001', '北京科技有限公司', 'CORP', 'USCC', '91110000MA01ABC123', '110000', 'A', '1', '王经理', '13800138001'),
('C20240002', '上海贸易股份有限公司', 'CORP', 'USCC', '91310000MA01DEF456', '110000', 'BBB', '1', '刘总监', '13900139002');

-- 6. 客户账户数据（每个客户USD和CNY账户）
INSERT INTO fx_customer_account (customer_id, account_no, currency, account_type, balance, frozen_amount, status) VALUES
('C20240001', '6222000100000001', 'USD', 'SPOT', 1000000.00, 0.00, '1'),
('C20240001', '6222000100000002', 'CNY', 'SPOT', 5000000.00, 0.00, '1'),
('C20240002', '6222000200000001', 'USD', 'SPOT', 500000.00, 0.00, '1'),
('C20240002', '6222000200000002', 'CNY', 'SPOT', 3000000.00, 0.00, '1');

-- 7. 客户余额数据
INSERT INTO fx_customer_balance (customer_id, currency, balance_amount, frozen_amount) VALUES
('C20240001', 'USD', 1000000.00, 0.00),
('C20240001', 'CNY', 5000000.00, 0.00),
('C20240002', 'USD', 500000.00, 0.00),
('C20240002', 'CNY', 3000000.00, 0.00);

-- 8. 即期报价数据（10个货币对）
INSERT INTO fx_quote (quote_type, currency_pair, base_currency, quote_currency, market_mid_rate, total_buy_rate, total_sell_rate, branch_customer_buy_rate, branch_customer_sell_rate, status, effective_time, published_by) VALUES
('SPOT', 'USD/CNY', 'USD', 'CNY', 7.12340000, 7.12000000, 7.12680000, 7.11500000, 7.13180000, 'ACTIVE', NOW(), 1),
('SPOT', 'EUR/CNY', 'EUR', 'CNY', 7.68500000, 7.68000000, 7.69000000, 7.67500000, 7.69500000, 'ACTIVE', NOW(), 1),
('SPOT', 'GBP/CNY', 'GBP', 'CNY', 9.01200000, 9.00600000, 9.01800000, 9.00100000, 9.02300000, 'ACTIVE', NOW(), 1),
('SPOT', 'JPY/CNY', 'JPY', 'CNY', 0.04650000, 0.04640000, 0.04660000, 0.04615000, 0.04685000, 'ACTIVE', NOW(), 1),
('SPOT', 'HKD/CNY', 'HKD', 'CNY', 0.91200000, 0.91100000, 0.91300000, 0.90980000, 0.91420000, 'ACTIVE', NOW(), 1),
('SPOT', 'AUD/CNY', 'AUD', 'CNY', 4.68500000, 4.68000000, 4.69000000, 4.67500000, 4.69500000, 'ACTIVE', NOW(), 1),
('SPOT', 'SGD/CNY', 'SGD', 'CNY', 5.28500000, 5.28000000, 5.29000000, 5.27500000, 5.29500000, 'ACTIVE', NOW(), 1),
('SPOT', 'CAD/CNY', 'CAD', 'CNY', 5.21500000, 5.21000000, 5.22000000, 5.20500000, 5.22500000, 'ACTIVE', NOW(), 1),
('SPOT', 'CHF/CNY', 'CHF', 'CNY', 8.01500000, 8.01000000, 8.02000000, 8.00500000, 8.02500000, 'ACTIVE', NOW(), 1),
('SPOT', 'NTD/CNY', 'NTD', 'CNY', 0.22050000, 0.22000000, 0.22100000, 0.21950000, 0.22150000, 'ACTIVE', NOW(), 1);

-- 9. 远期报价数据（USD/CNY 1M/3M/6M）
INSERT INTO fx_quote (quote_type, currency_pair, base_currency, quote_currency, term, maturity_date, market_mid_rate, total_buy_rate, total_sell_rate, branch_customer_buy_rate, branch_customer_sell_rate, forward_point, status, effective_time, published_by) VALUES
('FORWARD', 'USD/CNY', 'USD', 'CNY', '1M', DATE_ADD(CURDATE(), INTERVAL 1 MONTH), 7.13500000, 7.13100000, 7.13900000, 7.12600000, 7.14400000, 0.01160000, 'ACTIVE', NOW(), 1),
('FORWARD', 'USD/CNY', 'USD', 'CNY', '3M', DATE_ADD(CURDATE(), INTERVAL 3 MONTH), 7.15800000, 7.15400000, 7.16200000, 7.14900000, 7.16700000, 0.03460000, 'ACTIVE', NOW(), 1),
('FORWARD', 'USD/CNY', 'USD', 'CNY', '6M', DATE_ADD(CURDATE(), INTERVAL 6 MONTH), 7.19500000, 7.19100000, 7.19900000, 7.18600000, 7.20400000, 0.07160000, 'ACTIVE', NOW(), 1);

-- 10. 掉期报价数据（USD/CNY 1M/3M）
INSERT INTO fx_quote (quote_type, currency_pair, base_currency, quote_currency, term, maturity_date, market_mid_rate, total_buy_rate, total_sell_rate, branch_customer_buy_rate, branch_customer_sell_rate, swap_point, status, effective_time, published_by) VALUES
('SWAP', 'USD/CNY', 'USD', 'CNY', '1M', DATE_ADD(CURDATE(), INTERVAL 1 MONTH), 7.12340000, 7.12000000, 7.12680000, 7.11500000, 7.13180000, 0.01160000, 'ACTIVE', NOW(), 1),
('SWAP', 'USD/CNY', 'USD', 'CNY', '3M', DATE_ADD(CURDATE(), INTERVAL 3 MONTH), 7.12340000, 7.12000000, 7.12680000, 7.11500000, 7.13180000, 0.03460000, 'ACTIVE', NOW(), 1);

-- 11. 保证金参数
INSERT INTO fx_margin_param (param_code, param_name, param_value, currency, trade_type, description) VALUES
('MARGIN_RATE_FORWARD', '远期保证金比例', '0.05', 'CNY', 'FORWARD', '远期交易保证金比例5%'),
('MARGIN_RATE_OPTION', '期权保证金比例', '0.08', 'CNY', 'OPTION', '期权卖方保证金比例8%'),
('MARGIN_RATE_SWAP', '掉期保证金比例', '0.03', 'CNY', 'SWAP', '掉期交易保证金比例3%'),
('MARGIN_MIN_AMOUNT', '最低保证金金额', '10000', 'CNY', NULL, '最低保证金金额10000元'),
('MARGIN_CALL_THRESHOLD', '追保阈值', '0.5', 'CNY', NULL, '保证金占用率超过50%触发追保');

-- 12. 期权参数
INSERT INTO fx_option_param (param_code, param_name, param_value, description) VALUES
('OPTION_PREMIUM_RATE', '期权费率', '0.02', '期权费占名义金额比例2%'),
('OPTION_MIN_PREMIUM', '最低期权费', '1000', '最低期权费1000元'),
('OPTION_AUTO_EXERCISE', '自动行权标志', 'N', '是否自动行权：Y/N'),
('OPTION_EXERCISE_NOTICE_DAYS', '行权通知天数', '3', '到期前通知天数');

-- 13. 系统参数
INSERT INTO fx_sys_param (param_code, param_name, param_value, param_type, description) VALUES
('SYS_NAME', '系统名称', '银行外汇交易系统', 'STRING', '系统名称'),
('SYS_VERSION', '系统版本', '1.0.0', 'STRING', '系统版本号'),
('TRADE_BUSINESS_NO_PREFIX', '业务编号前缀', 'XF', 'STRING', '交易业务编号前缀'),
('CHECK_REQUIRED', '是否需要复核', 'Y', 'BOOLEAN', '交易是否需要复核'),
('AUTHORIZE_REQUIRED', '是否需要授权', 'Y', 'BOOLEAN', '交易是否需要授权'),
('BRANCH_APPROVAL_LIMIT', '分行审批额度', '10000000', 'NUMBER', '分行审批额度上限'),
('SUBBRANCH_APPROVAL_LIMIT', '支行审批额度', '1000000', 'NUMBER', '支行审批额度上限'),
('RCPMIS_REPORT_ENABLED', 'RCPMIS上报开关', 'Y', 'BOOLEAN', '是否启用RCPMIS上报'),
('MATURITY_REMIND_DAYS', '到期提醒天数', '7', 'NUMBER', '到期前提醒天数'),
('DEFAULT_PAGE_SIZE', '默认分页大小', '10', 'NUMBER', '默认分页大小');

-- ============================================================
-- 十、定时任务执行日志表
-- ============================================================

-- 定时任务执行日志表
DROP TABLE IF EXISTS fx_scheduled_job_log;
CREATE TABLE fx_scheduled_job_log (
    log_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    job_name      VARCHAR(50)  NOT NULL COMMENT '任务名称',
    trigger_type  VARCHAR(10)  NOT NULL COMMENT '触发类型：AUTO 自动 / MANUAL 手动',
    run_time      DATETIME     NOT NULL COMMENT '运行时间',
    total_count   INT          DEFAULT 0 COMMENT '处理总数',
    success_count INT          DEFAULT 0 COMMENT '成功数',
    fail_count    INT          DEFAULT 0 COMMENT '失败数',
    status        VARCHAR(20)  NOT NULL COMMENT '执行状态：SUCCESS 全部成功 / PARTIAL 部分失败 / FAILED 异常',
    error_message VARCHAR(500)           COMMENT '错误信息',
    duration_ms   BIGINT       DEFAULT 0 COMMENT '执行耗时（毫秒）',
    operator_id   BIGINT                 COMMENT '触发操作人ID',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (log_id),
    KEY idx_job_name (job_name),
    KEY idx_run_time (run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行日志表';

-- 定时任务执行明细表（每次执行后逐笔记录交易交割情况）
DROP TABLE IF EXISTS fx_scheduled_job_detail;
CREATE TABLE fx_scheduled_job_detail (
    detail_id      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    log_id         BIGINT        NOT NULL COMMENT '执行日志ID',
    trade_id       VARCHAR(64)   NOT NULL COMMENT '交易ID',
    business_no    VARCHAR(64)            COMMENT '业务编号',
    trade_type     VARCHAR(20)            COMMENT '交易类型',
    settle_account VARCHAR(50)            COMMENT '交易账户',
    settle_amount  DECIMAL(18,2)          COMMENT '交易金额',
    margin_account VARCHAR(50)            COMMENT '保证金账户',
    margin_amount  DECIMAL(18,2)          COMMENT '保证金金额',
    result         VARCHAR(20)            COMMENT '执行结果：SUCCESS 成功 / FAIL 失败',
    error_message  VARCHAR(500)           COMMENT '错误信息',
    created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (detail_id),
    KEY idx_log_id (log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行明细表';
