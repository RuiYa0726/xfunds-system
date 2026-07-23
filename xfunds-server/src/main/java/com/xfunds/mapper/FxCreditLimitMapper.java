package com.xfunds.mapper;

import com.xfunds.entity.FxCreditLimit;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 客户授信额度 Mapper 接口
 */
@Mapper
public interface FxCreditLimitMapper {

    /**
     * 根据客户ID和币种查询授信额度
     */
    FxCreditLimit selectByCustomerIdAndCurrency(String customerId, String currency);

    /**
     * 查询所有授信额度
     */
    List<FxCreditLimit> selectAll();

    /**
     * 根据客户ID查询授信额度列表
     */
    List<FxCreditLimit> selectByCustomerId(String customerId);

    /**
     * 新增授信额度
     */
    int insert(FxCreditLimit creditLimit);

    /**
     * 更新授信额度
     */
    int update(FxCreditLimit creditLimit);
}
