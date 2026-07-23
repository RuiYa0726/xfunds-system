package com.xfunds.mapper;

import com.xfunds.entity.FxCustomerAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 客户账户 Mapper 接口
 */
@Mapper
public interface FxCustomerAccountMapper {

    /**
     * 根据客户ID查询账户列表
     */
    List<FxCustomerAccount> selectByCustomerId(String customerId);

    /**
     * 根据账户ID查询账户
     */
    FxCustomerAccount selectByAccountId(@Param("accountId") Long accountId);

    /**
     * 根据账户号查询账户
     */
    FxCustomerAccount selectByAccountNo(@Param("accountNo") String accountNo);

    /**
     * 根据客户ID和币种查询账户（币种为空时查询全部）
     */
    List<FxCustomerAccount> selectByCustomerIdAndCurrency(@Param("customerId") String customerId, @Param("currency") String currency);

    /**
     * 查询所有客户账户（用于定时批量更新折人民币余额）
     */
    List<FxCustomerAccount> selectAll();

    /**
     * 汇总客户名下启用账户的折人民币余额（AUM）
     * 仅统计 status='1' 的账户，避免将全部账户加载到内存
     *
     * @param customerId 客户ID
     * @return 折人民币余额合计；无启用账户时返回 0
     */
    BigDecimal sumCnyBalanceByCustomerId(@Param("customerId") String customerId);

    /**
     * 新增客户账户
     */
    int insert(FxCustomerAccount account);

    /**
     * 更新客户账户
     */
    int update(FxCustomerAccount account);
}
