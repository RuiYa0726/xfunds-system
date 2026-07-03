package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 到期交割单笔执行结果 DTO
 * 用于在定时任务执行过程中收集每笔交易的执行明细信息
 */
@Data
public class SettleResult {

    /** 交易ID */
    private String tradeId;
    /** 业务编号 */
    private String businessNo;
    /** 交易类型 */
    private String tradeType;
    /** 交易账户（客户结算账号） */
    private String settleAccount;
    /** 交易金额（结算金额） */
    private BigDecimal settleAmount;
    /** 保证金账户ID */
    private String marginAccount;
    /** 保证金金额 */
    private BigDecimal marginAmount;
    /** 执行结果：SUCCESS 成功 / FAIL 失败 */
    private String result;
    /** 错误信息 */
    private String errorMessage;
}
