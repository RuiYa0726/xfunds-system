package com.xfunds.service;

import com.xfunds.dto.CustomerAccountRequest;
import com.xfunds.dto.CustomerSaveRequest;
import com.xfunds.dto.MarginAccountAdjustRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxCustomer;
import com.xfunds.entity.FxCustomerAccount;
import com.xfunds.entity.FxCustomerBalance;

import java.util.List;
import java.math.BigDecimal;

/**
 * 客户服务接口
 */
public interface FxCustomerService {

    /**
     * 根据客户ID查询客户
     */
    FxCustomer getByCustomerId(String customerId);

    /**
     * 查询所有客户
     */
    List<FxCustomer> listAll();

    /**
     * 按关键字搜索客户（客户ID或客户名称模糊匹配）
     */
    List<FxCustomer> search(String keyword);

    /**
     * 分页查询客户列表
     * 客户号模糊匹配，客户类型精确匹配
     */
    PageResponse<FxCustomer> listPage(String customerId, String customerName, String customerType, String orgCode, int pageNum, int pageSize);

    /**
     * 新增或更新客户
     */
    void saveCustomer(CustomerSaveRequest request);

    /**
     * 查询客户账户列表（可按币种过滤）
     */
    List<FxCustomerAccount> getAccounts(String customerId, String currency);

    /**
     * 查询客户所有币种余额
     */
    List<FxCustomerBalance> getBalances(String customerId);

    /**
     * 新增客户账户
     */
    void addAccount(String customerId, CustomerAccountRequest request);

    /**
     * 更新客户账户
     */
    void updateAccount(String customerId, Long accountId, CustomerAccountRequest request);

    /**
     * 人工调整保证金账户余额（记录保证金流水，与定时交割任务联动同一张表）
     */
    void adjustMarginAccountBalance(MarginAccountAdjustRequest request, Long operatorId);

    /**
     * 新增保证金账户
     */
    void addMarginAccount(String customerId, String currency, BigDecimal initialBalance);

    /**
     * 按当日即期汇率刷新所有客户账户的折人民币余额
     * 每天定时调用：CNY 账户 cny_balance=balance，其他币种 cny_balance=balance*当日X/CNY中间价
     *
     * @return 更新账户数
     */
    int refreshAllAccountsCnyBalance();
}
