package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保证金交易流水实体
 */
@Data
public class FxMarginTxn {

    /** 流水ID */
    private Long txnId;
    /** 保证金账户ID */
    private String marginAccountId;
    /** 交易ID */
    private String tradeId;
    /** 交易类型：SUPPLEMENT补充/RELEASE释放/DEDUCT扣减/FREEZE冻结 */
    private String txnType;
    /** 金额 */
    private BigDecimal amount;
    /** 交易后余额 */
    private BigDecimal balanceAfter;
    /** 操作人ID */
    private Long operatorId;
    /** 操作时间 */
    private LocalDateTime operateTime;
    /** 备注 */
    private String remark;
}
