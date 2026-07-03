package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.ResultCode;
import com.xfunds.entity.FxMarginAccount;
import com.xfunds.entity.FxMarginTxn;
import com.xfunds.mapper.FxMarginAccountMapper;
import com.xfunds.mapper.FxMarginTxnMapper;
import com.xfunds.service.MarginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 保证金服务实现类
 * 统一处理保证金账户余额变动与流水记录，保证账户余额与流水的一致性
 */
@Service
public class MarginServiceImpl implements MarginService {

    @Autowired
    private FxMarginAccountMapper fxMarginAccountMapper;

    @Autowired
    private FxMarginTxnMapper fxMarginTxnMapper;

    /**
     * 保证金增补：账户余额 += 金额，记录 SUPPLEMENT 流水
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void supplement(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark) {
        FxMarginAccount account = getAccountOrThrow(marginAccountId);
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        fxMarginAccountMapper.update(account);
        recordTxn(marginAccountId, tradeId, "SUPPLEMENT", amount, newBalance, operatorId, remark);
    }

    /**
     * 保证金释放：冻结金额 -= 释放额（不可超过冻结金额），记录 RELEASE 流水
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark) {
        FxMarginAccount account = getAccountOrThrow(marginAccountId);
        BigDecimal frozen = account.getFrozenAmount() == null ? BigDecimal.ZERO : account.getFrozenAmount();
        if (frozen.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "冻结金额不足，无法释放");
        }
        account.setFrozenAmount(frozen.subtract(amount));
        fxMarginAccountMapper.update(account);
        recordTxn(marginAccountId, tradeId, "RELEASE", amount, account.getBalance(), operatorId, remark);
    }

    /**
     * 保证金扣减：账户余额 -= 金额（不可超过余额），记录 DEDUCT 流水
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark) {
        FxMarginAccount account = getAccountOrThrow(marginAccountId);
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "保证金余额不足，无法扣减");
        }
        BigDecimal newBalance = balance.subtract(amount);
        account.setBalance(newBalance);
        fxMarginAccountMapper.update(account);
        recordTxn(marginAccountId, tradeId, "DEDUCT", amount, newBalance, operatorId, remark);
    }

    /**
     * 保证金冻结：冻结金额 += 金额（不可超过可用余额），记录 FREEZE 流水
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(String marginAccountId, BigDecimal amount, String tradeId, Long operatorId, String remark) {
        FxMarginAccount account = getAccountOrThrow(marginAccountId);
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        BigDecimal frozen = account.getFrozenAmount() == null ? BigDecimal.ZERO : account.getFrozenAmount();
        // 可用余额 = 账户余额 - 冻结金额
        BigDecimal available = balance.subtract(frozen);
        if (available.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "可用保证金余额不足，无法冻结");
        }
        account.setFrozenAmount(frozen.add(amount));
        fxMarginAccountMapper.update(account);
        recordTxn(marginAccountId, tradeId, "FREEZE", amount, balance, operatorId, remark);
    }

    /**
     * 根据保证金账户ID查询账户，不存在则抛出业务异常
     */
    private FxMarginAccount getAccountOrThrow(String marginAccountId) {
        FxMarginAccount account = fxMarginAccountMapper.selectByMarginAccountId(marginAccountId);
        if (account == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "保证金账户不存在：" + marginAccountId);
        }
        return account;
    }

    /**
     * 记录保证金交易流水
     */
    private void recordTxn(String marginAccountId, String tradeId, String txnType,
                           BigDecimal amount, BigDecimal balanceAfter, Long operatorId, String remark) {
        FxMarginTxn txn = new FxMarginTxn();
        txn.setMarginAccountId(marginAccountId);
        txn.setTradeId(tradeId);
        txn.setTxnType(txnType);
        txn.setAmount(amount);
        txn.setBalanceAfter(balanceAfter);
        txn.setOperatorId(operatorId);
        txn.setRemark(remark);
        fxMarginTxnMapper.insert(txn);
    }
}
