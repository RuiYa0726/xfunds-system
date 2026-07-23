package com.xfunds.service.impl;

import com.xfunds.common.Constants;
import com.xfunds.dto.CreditLimitSaveRequest;
import com.xfunds.dto.MarginCalcRequest;
import com.xfunds.entity.FxCreditLimit;
import com.xfunds.entity.FxMarginParam;
import com.xfunds.mapper.FxCreditLimitMapper;
import com.xfunds.mapper.FxMarginParamMapper;
import com.xfunds.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 授信额度与保证金参数服务实现类
 */
@Service
public class CreditServiceImpl implements CreditService {

    /** 保证金比例参数编码前缀 */
    private static final String MARGIN_RATE_PREFIX = "MARGIN_RATE_";

    /** 默认保证金比例（未配置参数时使用） */
    private static final BigDecimal DEFAULT_MARGIN_RATE = new BigDecimal("0.10");

    @Autowired
    private FxCreditLimitMapper fxCreditLimitMapper;

    @Autowired
    private FxMarginParamMapper fxMarginParamMapper;

    /**
     * 查询授信额度列表（可按客户ID过滤）
     */
    @Override
    public List<FxCreditLimit> listCreditLimits(String customerId) {
        if (customerId != null && !customerId.isEmpty()) {
            return fxCreditLimitMapper.selectByCustomerId(customerId);
        }
        return fxCreditLimitMapper.selectAll();
    }

    /**
     * 新增或更新授信额度（自动计算可用额度 = 授信额度 - 已用额度）
     */
    @Override
    public void saveCreditLimit(CreditLimitSaveRequest request) {
        // 计算可用额度
        BigDecimal creditLimitAmount = request.getCreditLimitAmount() != null ? request.getCreditLimitAmount() : BigDecimal.ZERO;
        BigDecimal usedAmount = request.getUsedAmount() != null ? request.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal availableAmount = creditLimitAmount.subtract(usedAmount);

        FxCreditLimit creditLimit = new FxCreditLimit();
        creditLimit.setLimitId(request.getLimitId());
        creditLimit.setCustomerId(request.getCustomerId());
        creditLimit.setCurrency(request.getCurrency());
        creditLimit.setCreditLimitAmount(creditLimitAmount);
        creditLimit.setUsedAmount(usedAmount);
        creditLimit.setAvailableAmount(availableAmount);
        creditLimit.setStatus(request.getStatus() != null ? request.getStatus() : Constants.STATUS_ACTIVE);

        // 判断新增或更新
        if (request.getLimitId() == null) {
            fxCreditLimitMapper.insert(creditLimit);
        } else {
            fxCreditLimitMapper.update(creditLimit);
        }
    }

    /**
     * 查询所有保证金参数
     */
    @Override
    public List<FxMarginParam> listMarginParams() {
        return fxMarginParamMapper.selectAll();
    }

    /**
     * 更新保证金参数
     */
    @Override
    public void saveMarginParam(FxMarginParam param) {
        fxMarginParamMapper.update(param);
    }

    /**
     * 计算所需保证金金额：保证金 = 金额 × 保证金比例
     * 保证金比例从 fx_margin_param 表读取，参数编码为 MARGIN_RATE_{tradeType}，未配置时使用默认比例10%
     */
    @Override
    public BigDecimal calcMargin(MarginCalcRequest request) {
        // 构造参数编码：MARGIN_RATE_{tradeType}
        String paramCode = MARGIN_RATE_PREFIX + request.getTradeType();
        FxMarginParam param = fxMarginParamMapper.selectByParamCode(paramCode);
        BigDecimal marginRate;
        if (param != null && param.getParamValue() != null) {
            marginRate = new BigDecimal(param.getParamValue());
        } else {
            // 未配置参数时使用默认比例
            marginRate = DEFAULT_MARGIN_RATE;
        }
        // 保证金 = 金额 × 保证金比例，保留两位小数
        return request.getAmount().multiply(marginRate).setScale(2, RoundingMode.HALF_UP);
    }
}
