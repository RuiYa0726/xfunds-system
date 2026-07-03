package com.xfunds.mapper;

import com.xfunds.entity.FxCustomerBalance;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 客户余额 Mapper 接口
 */
@Mapper
public interface FxCustomerBalanceMapper {

    /**
     * 根据客户ID和币种查询余额
     */
    FxCustomerBalance selectByCustomerIdAndCurrency(String customerId, String currency);

    /**
     * 根据客户ID查询所有币种余额
     */
    List<FxCustomerBalance> selectByCustomerId(String customerId);

    /**
     * 新增客户余额
     */
    int insert(FxCustomerBalance balance);

    /**
     * 更新客户余额
     */
    int update(FxCustomerBalance balance);
}
