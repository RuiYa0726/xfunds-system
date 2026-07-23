package com.xfunds.service;

import com.xfunds.dto.CreditLimitSaveRequest;
import com.xfunds.dto.MarginCalcRequest;
import com.xfunds.entity.FxCreditLimit;
import com.xfunds.entity.FxMarginParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 授信额度与保证金参数服务接口
 */
public interface CreditService {

    /**
     * 查询授信额度列表（可按客户ID过滤）
     */
    List<FxCreditLimit> listCreditLimits(String customerId);

    /**
     * 新增或更新授信额度（自动计算可用额度）
     */
    void saveCreditLimit(CreditLimitSaveRequest request);

    /**
     * 查询所有保证金参数
     */
    List<FxMarginParam> listMarginParams();

    /**
     * 更新保证金参数
     */
    void saveMarginParam(FxMarginParam param);

    /**
     * 计算所需保证金金额
     */
    BigDecimal calcMargin(MarginCalcRequest request);
}
