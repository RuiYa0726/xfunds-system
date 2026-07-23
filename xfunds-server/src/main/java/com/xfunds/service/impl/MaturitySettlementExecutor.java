package com.xfunds.service.impl;

import com.xfunds.entity.FxCustomerAccount;
import com.xfunds.entity.FxForwardTrade;
import com.xfunds.entity.FxMarginAccount;
import com.xfunds.entity.FxMarginTxn;
import com.xfunds.entity.FxSpotTrade;
import com.xfunds.entity.FxSwapTrade;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.common.Constants;
import com.xfunds.dto.SettleResult;
import com.xfunds.enums.SettlementMethod;
import com.xfunds.enums.TradeStatus;
import com.xfunds.enums.TradeType;
import com.xfunds.mapper.FxCustomerAccountMapper;
import com.xfunds.mapper.FxForwardTradeMapper;
import com.xfunds.mapper.FxMarginAccountMapper;
import com.xfunds.mapper.FxMarginTxnMapper;
import com.xfunds.mapper.FxSpotTradeMapper;
import com.xfunds.mapper.FxSwapTradeMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.service.TradeLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 到期交割执行器
 * 每笔交易的交割在独立事务中执行，单笔失败回滚自身事务而不影响其他交易
 *
 * 交割规则（保证金交易：审批通过已扣保证金，交割成功退还，交割失败没收）：
 * 1. 成功：双向更新客户账户余额（借记客户付出币种账户，贷记客户收到币种账户），退还保证金，交易状态 -> SETTLED
 * 2. 失败（账户余额不足或贷记账户缺失）：保证金已在审批通过时扣除，此处没收不再退还，交易状态 -> SETTLE_FAILED
 */
@Service
public class MaturitySettlementExecutor {

    @Autowired
    private FxTradeMasterMapper fxTradeMasterMapper;

    @Autowired
    private FxCustomerAccountMapper fxCustomerAccountMapper;

    @Autowired
    private FxMarginAccountMapper fxMarginAccountMapper;

    @Autowired
    private FxMarginTxnMapper fxMarginTxnMapper;

    @Autowired
    private FxForwardTradeMapper fxForwardTradeMapper;

    @Autowired
    private FxSpotTradeMapper fxSpotTradeMapper;

    @Autowired
    private FxSwapTradeMapper fxSwapTradeMapper;

    @Autowired
    private TradeLifecycleService tradeLifecycleService;

    /**
     * 交割单笔交易（独立事务）
     *
     * @param trade      待交割交易
     * @param operatorId 操作人ID
     * @return 交割结果描述，便于上层日志记录
     */
    @Transactional(rollbackFor = Exception.class)
    public SettleResult settleOne(FxTradeMaster trade, Long operatorId) {
        // 重新加载以获取最新版本号，保证乐观锁更新
        FxTradeMaster current = fxTradeMasterMapper.selectByTradeId(trade.getTradeId());
        if (current == null) {
            throw new IllegalStateException("交易不存在：" + trade.getTradeId());
        }
        String beforeStatus = current.getStatus();

        // ===== 无需交割：直接标记为已交割，不做任何账户变更 =====
        if (SettlementMethod.NONE.name().equals(current.getSettlementMethod())) {
            current.setStatus(TradeStatus.SETTLED.name());
            current.setCheckerId(operatorId);
            current.setCheckTime(LocalDateTime.now());
            int updated = fxTradeMasterMapper.update(current);
            if (updated == 0) {
                throw new IllegalStateException("交易已被其他操作修改，交割失败：" + trade.getTradeId());
            }
            tradeLifecycleService.recordEvent(current.getTradeId(), "SCHEDULED_SETTLE", operatorId,
                    beforeStatus, TradeStatus.SETTLED.name(), null, current.getCustomerRate(),
                    null, "无需交割，自动完成");
            return buildResult(current, null, BigDecimal.ZERO, null, BigDecimal.ZERO, "SUCCESS", null);
        }

        // ===== 差额交割：仅扣减轧差账户的轧差金额 =====
        if (SettlementMethod.NET.name().equals(current.getSettlementMethod())) {
            return settleNet(current, beforeStatus, operatorId);
        }

        // ===== 全额交割：双向记账（原有逻辑） =====
        // 按交易方向确定借记/贷记账户与金额（全额交割双向记账）
        // BUY(客户买入基础币): 客户付报价币、收基础币 → 借记报价币账户(counterAmount), 贷记基础币账户(notionalAmount)
        // SELL(客户卖出基础币): 客户付基础币、收报价币 → 借记基础币账户(notionalAmount), 贷记报价币账户(counterAmount)
        boolean isBuy = "BUY".equals(current.getTradeDirection());
        BigDecimal baseAmount = current.getNotionalAmount();
        BigDecimal quoteAmount = current.getCounterAmount();
        BigDecimal debitAmount = isBuy ? quoteAmount : baseAmount;
        BigDecimal creditAmount = isBuy ? baseAmount : quoteAmount;
        String debitCurrency = isBuy ? current.getQuoteCurrency() : current.getBaseCurrency();
        String creditCurrency = isBuy ? current.getBaseCurrency() : current.getQuoteCurrency();

        // 提前解析保证金账户与保证金金额，用于明细记录
        String marginAccountId = getTradeMarginAccountId(current);
        BigDecimal marginAmount = getTradeMarginAmount(current);

        if (debitAmount == null || debitAmount.compareTo(BigDecimal.ZERO) <= 0
                || creditAmount == null || creditAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 无法确定结算金额，按失败处理
            markSettleFailed(current, beforeStatus, debitAmount, operatorId, "定时交割失败：无法确定结算金额");
            return buildResult(current, null, debitAmount, marginAccountId, marginAmount, "FAIL", "定时交割失败：无法确定结算金额");
        }

        // 查找客户借记/贷记账户（同币种，优先现汇 SPOT 账户）
        FxCustomerAccount debitAccount = pickSettleAccount(current.getCustomerId(), debitCurrency);
        FxCustomerAccount creditAccount = pickSettleAccount(current.getCustomerId(), creditCurrency);

        if (debitAccount != null && debitAccount.getBalance() != null
                && debitAccount.getBalance().compareTo(debitAmount) >= 0 && creditAccount != null) {
            // ===== 交割成功：双向记账 =====
            // 借记账户扣减（客户付出的币种）
            debitAccount.setBalance(debitAccount.getBalance().subtract(debitAmount));
            fxCustomerAccountMapper.update(debitAccount);
            // 贷记账户增加（客户收到的币种）
            creditAccount.setBalance(creditAccount.getBalance().add(creditAmount));
            fxCustomerAccountMapper.update(creditAccount);

            // 退还保证金（保证金已在审批通过时扣除，交割成功全额退还）
            refundMargin(current, operatorId);

            // 状态变更为已交割
            current.setStatus(TradeStatus.SETTLED.name());
            current.setCheckerId(operatorId);
            current.setCheckTime(LocalDateTime.now());
            int updated = fxTradeMasterMapper.update(current);
            if (updated == 0) {
                throw new IllegalStateException("交易已被其他操作修改，交割失败：" + trade.getTradeId());
            }

            tradeLifecycleService.recordEvent(current.getTradeId(), "SCHEDULED_SETTLE", operatorId,
                    beforeStatus, TradeStatus.SETTLED.name(), debitAmount, current.getCustomerRate(),
                    null, "定时任务到期交割成功");
            return buildResult(current, debitAccount.getAccountNo(), debitAmount, marginAccountId, marginAmount, "SUCCESS", null);
        } else {
            // ===== 交割失败：账户余额不足或贷记账户缺失 =====
            // 保证金已在审批通过时扣除，交割失败即没收（不再额外扣减，不退还）
            current.setStatus(TradeStatus.SETTLE_FAILED.name());
            current.setCheckerId(operatorId);
            current.setCheckTime(LocalDateTime.now());
            int updated = fxTradeMasterMapper.update(current);
            if (updated == 0) {
                throw new IllegalStateException("交易已被其他操作修改，交割失败：" + trade.getTradeId());
            }

            markSettleFailed(current, beforeStatus, debitAmount, operatorId, "定时任务到期交割失败：账户余额不足");
            String accountNo = debitAccount != null ? debitAccount.getAccountNo() : null;
            return buildResult(current, accountNo, debitAmount, marginAccountId, marginAmount, "FAIL", "定时任务到期交割失败：账户余额不足");
        }
    }

    /**
     * 差额交割：仅从轧差账户扣减轧差金额
     * 用于提前违约生成的即期交易，定时任务在当天执行差额交割
     */
    private SettleResult settleNet(FxTradeMaster current, String beforeStatus, Long operatorId) {
        BigDecimal nettingAmount = current.getNettingAmount();
        String nettingAccountNo = current.getNettingAccount();

        if (nettingAmount == null || nettingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 轧差金额为0，直接标记为已交割
            current.setStatus(TradeStatus.SETTLED.name());
            current.setCheckerId(operatorId);
            current.setCheckTime(LocalDateTime.now());
            fxTradeMasterMapper.update(current);
            tradeLifecycleService.recordEvent(current.getTradeId(), "SCHEDULED_SETTLE", operatorId,
                    beforeStatus, TradeStatus.SETTLED.name(), nettingAmount, current.getCustomerRate(),
                    null, "差额交割完成（轧差金额为0）");
            return buildResult(current, nettingAccountNo, BigDecimal.ZERO, null, BigDecimal.ZERO, "SUCCESS", null);
        }

        // 查找轧差账户
        FxCustomerAccount nettingAcc = null;
        if (nettingAccountNo != null && current.getNettingCurrency() != null) {
            List<FxCustomerAccount> accounts = fxCustomerAccountMapper.selectByCustomerIdAndCurrency(
                    current.getCustomerId(), current.getNettingCurrency());
            if (accounts != null) {
                for (FxCustomerAccount acc : accounts) {
                    if (nettingAccountNo.equals(acc.getAccountNo())) {
                        nettingAcc = acc;
                        break;
                    }
                }
            }
        }

        if (nettingAcc != null && nettingAcc.getBalance() != null
                && nettingAcc.getBalance().compareTo(nettingAmount) >= 0) {
            // ===== 差额交割成功：扣减轧差账户余额并释放冻结金额 =====
            nettingAcc.setBalance(nettingAcc.getBalance().subtract(nettingAmount));
            // 释放冻结的轧差金额（提交提前违约时已冻结）
            BigDecimal frozen = nettingAcc.getFrozenAmount() != null ? nettingAcc.getFrozenAmount() : BigDecimal.ZERO;
            BigDecimal newFrozen = frozen.subtract(nettingAmount);
            if (newFrozen.compareTo(BigDecimal.ZERO) < 0) {
                newFrozen = BigDecimal.ZERO;
            }
            nettingAcc.setFrozenAmount(newFrozen);
            fxCustomerAccountMapper.update(nettingAcc);

            current.setStatus(TradeStatus.SETTLED.name());
            current.setCheckerId(operatorId);
            current.setCheckTime(LocalDateTime.now());
            int updated = fxTradeMasterMapper.update(current);
            if (updated == 0) {
                throw new IllegalStateException("交易已被其他操作修改，交割失败：" + current.getTradeId());
            }

            tradeLifecycleService.recordEvent(current.getTradeId(), "SCHEDULED_SETTLE", operatorId,
                    beforeStatus, TradeStatus.SETTLED.name(), nettingAmount, current.getCustomerRate(),
                    null, "差额交割成功，扣减轧差账户金额");
            return buildResult(current, nettingAccountNo, nettingAmount, null, BigDecimal.ZERO, "SUCCESS", null);
        } else {
            // ===== 差额交割失败：轧差账户余额不足 =====
            current.setStatus(TradeStatus.SETTLE_FAILED.name());
            current.setCheckerId(operatorId);
            current.setCheckTime(LocalDateTime.now());
            int updated = fxTradeMasterMapper.update(current);
            if (updated == 0) {
                throw new IllegalStateException("交易已被其他操作修改，交割失败：" + current.getTradeId());
            }

            markSettleFailed(current, beforeStatus, nettingAmount, operatorId, "差额交割失败：轧差账户余额不足");
            return buildResult(current, nettingAccountNo, nettingAmount, null, BigDecimal.ZERO, "FAIL", "差额交割失败：轧差账户余额不足");
        }
    }

    /**
     * 组装交割结果 DTO
     */
    private SettleResult buildResult(FxTradeMaster trade, String settleAccountNo, BigDecimal settleAmount,
                                     String marginAccountId, BigDecimal marginAmount, String result, String errorMessage) {
        SettleResult r = new SettleResult();
        r.setTradeId(trade.getTradeId());
        r.setBusinessNo(trade.getBusinessNo());
        r.setTradeType(trade.getTradeType());
        r.setSettleAccount(settleAccountNo);
        r.setSettleAmount(settleAmount);
        r.setMarginAccount(marginAccountId);
        r.setMarginAmount(marginAmount);
        r.setResult(result);
        r.setErrorMessage(errorMessage);
        return r;
    }

    /**
     * 在客户同币种账户中挑选结算账户，优先现汇（SPOT）账户
     */
    private FxCustomerAccount pickSettleAccount(String customerId, String currency) {
        List<FxCustomerAccount> accounts = fxCustomerAccountMapper.selectByCustomerIdAndCurrency(customerId, currency);
        if (accounts == null || accounts.isEmpty()) {
            return null;
        }
        for (FxCustomerAccount acc : accounts) {
            if ("SPOT".equals(acc.getAccountType()) && Constants.STATUS_ACTIVE.equals(acc.getStatus())) {
                return acc;
            }
        }
        // 回退：取第一个启用账户
        for (FxCustomerAccount acc : accounts) {
            if (Constants.STATUS_ACTIVE.equals(acc.getStatus())) {
                return acc;
            }
        }
        return accounts.get(0);
    }

    /**
     * 退还保证金：保证金账户余额 += 该笔交易保证金金额，并记录 SUPPLEMENT 流水
     */
    private void refundMargin(FxTradeMaster trade, Long operatorId) {
        BigDecimal marginAmount = getTradeMarginAmount(trade);
        if (marginAmount == null || marginAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        FxMarginAccount account = resolveMarginAccount(trade);
        if (account == null) {
            return;
        }
        BigDecimal newBalance = account.getBalance().add(marginAmount);
        account.setBalance(newBalance);
        fxMarginAccountMapper.update(account);
        recordMarginTxn(account.getMarginAccountId(), trade.getTradeId(), "SUPPLEMENT",
                marginAmount, newBalance, operatorId, "定时交割退还保证金");
    }

    /**
     * 解析交易对应的保证金账户：优先使用子表记录的 margin_account_id，否则按客户+基础币种查找
     */
    private FxMarginAccount resolveMarginAccount(FxTradeMaster trade) {
        String marginAccountId = getTradeMarginAccountId(trade);
        if (marginAccountId != null && !marginAccountId.isEmpty()) {
            return fxMarginAccountMapper.selectByMarginAccountId(marginAccountId);
        }
        if (trade.getBaseCurrency() != null) {
            return fxMarginAccountMapper.selectByCustomerIdAndCurrency(trade.getCustomerId(), trade.getBaseCurrency());
        }
        return null;
    }

    /**
     * 从交易子表获取保证金账户ID（即期/远期/掉期有保证金字段；期权无）
     */
    private String getTradeMarginAccountId(FxTradeMaster trade) {
        if (TradeType.FORWARD.name().equals(trade.getTradeType())) {
            FxForwardTrade forward = fxForwardTradeMapper.selectByTradeId(trade.getTradeId());
            return forward == null ? null : forward.getMarginAccountId();
        } else if (TradeType.SPOT.name().equals(trade.getTradeType())) {
            FxSpotTrade spot = fxSpotTradeMapper.selectByTradeId(trade.getTradeId());
            return spot == null ? null : spot.getMarginAccountId();
        } else if (TradeType.SWAP.name().equals(trade.getTradeType())) {
            FxSwapTrade swap = fxSwapTradeMapper.selectByTradeId(trade.getTradeId());
            return swap == null ? null : swap.getMarginAccountId();
        }
        return null;
    }

    /**
     * 从交易子表获取保证金金额（即期/远期/掉期有保证金字段；期权无保证金返回 0）
     */
    private BigDecimal getTradeMarginAmount(FxTradeMaster trade) {
        if (TradeType.FORWARD.name().equals(trade.getTradeType())) {
            FxForwardTrade forward = fxForwardTradeMapper.selectByTradeId(trade.getTradeId());
            return forward == null || forward.getMarginAmount() == null ? BigDecimal.ZERO : forward.getMarginAmount();
        } else if (TradeType.SPOT.name().equals(trade.getTradeType())) {
            FxSpotTrade spot = fxSpotTradeMapper.selectByTradeId(trade.getTradeId());
            return spot == null || spot.getMarginAmount() == null ? BigDecimal.ZERO : spot.getMarginAmount();
        } else if (TradeType.SWAP.name().equals(trade.getTradeType())) {
            FxSwapTrade swap = fxSwapTradeMapper.selectByTradeId(trade.getTradeId());
            return swap == null || swap.getMarginAmount() == null ? BigDecimal.ZERO : swap.getMarginAmount();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 记录保证金流水
     */
    private void recordMarginTxn(String marginAccountId, String tradeId, String txnType,
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

    /**
     * 标记交割失败状态并记录生命周期事件（用于无法确定结算金额等异常分支）
     */
    private void markSettleFailed(FxTradeMaster current, String beforeStatus, BigDecimal settleAmount,
                                  Long operatorId, String remark) {
        tradeLifecycleService.recordEvent(current.getTradeId(), "SCHEDULED_SETTLE_FAIL", operatorId,
                beforeStatus, TradeStatus.SETTLE_FAILED.name(), settleAmount, current.getCustomerRate(),
                null, remark);
    }
}
