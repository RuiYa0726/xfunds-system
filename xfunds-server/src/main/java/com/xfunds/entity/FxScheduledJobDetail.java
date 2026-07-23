package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定时任务执行明细实体
 * 每次到期交割定时任务执行后，逐笔记录交易交割情况
 */
@Data
public class FxScheduledJobDetail {

    /** 明细ID */
    private Long detailId;
    /** 执行日志ID */
    private Long logId;
    /** 交易ID */
    private String tradeId;
    /** 业务编号 */
    private String businessNo;
    /** 交易类型 */
    private String tradeType;
    /** 交易账户 */
    private String settleAccount;
    /** 交易金额 */
    private BigDecimal settleAmount;
    /** 保证金账户 */
    private String marginAccount;
    /** 保证金金额 */
    private BigDecimal marginAmount;
    /** 执行结果：SUCCESS 成功 / FAIL 失败 */
    private String result;
    /** 错误信息 */
    private String errorMessage;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
