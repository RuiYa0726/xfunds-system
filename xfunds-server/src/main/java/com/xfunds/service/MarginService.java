package com.xfunds.service;

import java.math.BigDecimal;

/**
 * 保证金服务接口
 * 提供保证金账户的补充、释放、扣减、冻结操作，并记录交易流水
 */
public interface MarginService {

    /**
     * 保证金增补：增加账户余额
     *
     * @param marginAccountId 保证金账户ID
     * @param amount          增补金额
     * @param tradeId         关联交易ID
     * @param operatorId      操作人ID
     * @param remark          备注
     */
    void supplement(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark);

    /**
     * 保证金释放：释放冻结金额
     *
     * @param marginAccountId 保证金账户ID
     * @param amount          释放金额
     * @param tradeId         关联交易ID
     * @param operatorId      操作人ID
     * @param remark          备注
     */
    void release(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark);

    /**
     * 保证金扣减：从账户余额中扣减
     *
     * @param marginAccountId 保证金账户ID
     * @param amount          扣减金额
     * @param tradeId         关联交易ID
     * @param operatorId      操作人ID
     * @param remark          备注
     */
    void deduct(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark);

    /**
     * 保证金冻结：冻结指定金额
     *
     * @param marginAccountId 保证金账户ID
     * @param amount          冻结金额
     * @param tradeId         关联交易ID
     * @param operatorId      操作人ID
     * @param remark          备注
     */
    void freeze(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark);
}
