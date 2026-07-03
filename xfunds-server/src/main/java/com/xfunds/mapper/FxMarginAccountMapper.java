package com.xfunds.mapper;

import com.xfunds.entity.FxMarginAccount;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 保证金账户 Mapper 接口
 */
@Mapper
public interface FxMarginAccountMapper {

    /**
     * 根据保证金账户ID查询账户
     */
    FxMarginAccount selectByMarginAccountId(String marginAccountId);

    /**
     * 根据客户ID和币种查询保证金账户
     */
    FxMarginAccount selectByCustomerIdAndCurrency(String customerId, String currency);

    /**
     * 根据客户ID查询其全部保证金账户列表（用于交易录入选择保证金账户）
     */
    List<FxMarginAccount> selectByCustomerId(String customerId);

    /**
     * 新增保证金账户
     */
    int insert(FxMarginAccount marginAccount);

    /**
     * 更新保证金账户
     */
    int update(FxMarginAccount marginAccount);
}
