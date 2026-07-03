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
