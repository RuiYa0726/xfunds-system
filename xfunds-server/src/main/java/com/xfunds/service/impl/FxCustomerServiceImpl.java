package com.xfunds.service.impl;

import com.xfunds.common.Constants;
import com.xfunds.dto.CustomerAccountRequest;
import com.xfunds.dto.CustomerSaveRequest;
import com.xfunds.dto.MarginAccountAdjustRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxCustomer;
import com.xfunds.entity.FxCustomerAccount;
import com.xfunds.entity.FxCustomerBalance;
import com.xfunds.entity.FxMarginAccount;
import com.xfunds.entity.FxMarginTxn;
import com.xfunds.mapper.FxCustomerAccountMapper;
import com.xfunds.mapper.FxCustomerBalanceMapper;
import com.xfunds.mapper.FxCustomerMapper;
import com.xfunds.mapper.FxMarginAccountMapper;
import com.xfunds.mapper.FxMarginTxnMapper;
import com.xfunds.service.FxCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户服务实现类
 */
@Service
public class FxCustomerServiceImpl implements FxCustomerService {

    @Autowired
    private FxCustomerMapper fxCustomerMapper;

    @Autowired
    private FxCustomerAccountMapper fxCustomerAccountMapper;

    @Autowired
    private FxCustomerBalanceMapper fxCustomerBalanceMapper;

    @Autowired
    private FxMarginAccountMapper fxMarginAccountMapper;

    @Autowired
    private FxMarginTxnMapper fxMarginTxnMapper;

    /**
     * 根据客户ID查询客户
     */
    @Override
    public FxCustomer getByCustomerId(String customerId) {
        return fxCustomerMapper.selectByCustomerId(customerId);
    }

    /**
     * 查询所有客户
     */
    @Override
    public List<FxCustomer> listAll() {
        return fxCustomerMapper.selectAll();
    }

    /**
     * 按关键字搜索客户（客户ID或客户名称模糊匹配）
     */
    @Override
    public List<FxCustomer> search(String keyword) {
        return fxCustomerMapper.selectByKeyword(keyword);
    }

    /**
     * 分页查询客户列表
     * 客户号模糊匹配，客户类型精确匹配
     */
    @Override
    public PageResponse<FxCustomer> listPage(String customerId, String customerName, String customerType, String orgCode, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        long total = fxCustomerMapper.countByCondition(customerId, customerName, customerType, orgCode);
        List<FxCustomer> list = fxCustomerMapper.selectByCondition(customerId, customerName, customerType, orgCode, offset, pageSize);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 新增或更新客户
     */
    @Override
    public void saveCustomer(CustomerSaveRequest request) {
        FxCustomer customer = new FxCustomer();
        customer.setCustomerId(request.getCustomerId());
        customer.setCustomerName(request.getCustomerName());
        customer.setCustomerType(request.getCustomerType());
        customer.setIdType(request.getIdType());
        customer.setIdNo(request.getIdNo());
        customer.setOrgCode(request.getOrgCode());
        customer.setCreditLevel(request.getCreditLevel());
        customer.setStatus(request.getStatus() != null ? request.getStatus() : Constants.STATUS_ACTIVE);
        customer.setContactPerson(request.getContactPerson());
        customer.setContactPhone(request.getContactPhone());

        // 判断新增或更新
        FxCustomer existCustomer = fxCustomerMapper.selectByCustomerId(request.getCustomerId());
        if (existCustomer == null) {
            fxCustomerMapper.insert(customer);
        } else {
            fxCustomerMapper.update(customer);
        }
    }

    /**
     * 查询客户账户列表（可按币种过滤）
     */
    @Override
    public List<FxCustomerAccount> getAccounts(String customerId, String currency) {
        return fxCustomerAccountMapper.selectByCustomerIdAndCurrency(customerId, currency);
    }

    /**
     * 查询客户所有币种余额
     */
    @Override
    public List<FxCustomerBalance> getBalances(String customerId) {
        return fxCustomerBalanceMapper.selectByCustomerId(customerId);
    }

    /**
     * 新增客户账户
     */
    @Override
    public void addAccount(String customerId, CustomerAccountRequest request) {
        FxCustomerAccount account = new FxCustomerAccount();
        account.setCustomerId(customerId);
        account.setAccountNo(request.getAccountNo());
        account.setCurrency(request.getCurrency());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getBalance() != null ? request.getBalance() : BigDecimal.ZERO);
        account.setFrozenAmount(request.getFrozenAmount() != null ? request.getFrozenAmount() : BigDecimal.ZERO);
        account.setStatus(request.getStatus() != null ? request.getStatus() : Constants.STATUS_ACTIVE);
        fxCustomerAccountMapper.insert(account);
    }

    /**
     * 更新客户账户
     */
    @Override
    public void updateAccount(String customerId, Long accountId, CustomerAccountRequest request) {
        FxCustomerAccount account = new FxCustomerAccount();
        account.setAccountId(accountId);
        account.setCustomerId(customerId);
        account.setAccountNo(request.getAccountNo());
        account.setCurrency(request.getCurrency());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getBalance());
        account.setFrozenAmount(request.getFrozenAmount());
        account.setStatus(request.getStatus());
        fxCustomerAccountMapper.update(account);
    }

    /**
     * 人工调整保证金账户余额
     * 先查询完整账户对象（保证金 mapper 的 update 为整实体更新），修改 balance 后回写，
     * 同时记录一条 ADJUST 类型的保证金流水，与定时交割任务操作同一张 fx_margin_account 表实现联动
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustMarginAccountBalance(MarginAccountAdjustRequest request, Long operatorId) {
        // 查询完整的保证金账户对象（整实体更新需保留其他字段原值）
        FxMarginAccount account = fxMarginAccountMapper.selectByMarginAccountId(request.getMarginAccountId());
        if (account == null) {
            throw new IllegalArgumentException("保证金账户不存在：" + request.getMarginAccountId());
        }
        BigDecimal oldBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = request.getNewBalance();
        // 计算变动金额（正数为增加，负数为减少）
        BigDecimal delta = newBalance.subtract(oldBalance);

        // 更新保证金账户余额
        account.setBalance(newBalance);
        fxMarginAccountMapper.update(account);

        // 记录保证金流水（tradeId 为空表示人工调整，非交易触发）
        FxMarginTxn txn = new FxMarginTxn();
        txn.setMarginAccountId(account.getMarginAccountId());
        txn.setTradeId(null);
        txn.setTxnType("ADJUST");
        txn.setAmount(delta);
        txn.setBalanceAfter(newBalance);
        txn.setOperatorId(operatorId);
        txn.setOperateTime(LocalDateTime.now());
        txn.setRemark(request.getRemark() != null && !request.getRemark().isEmpty()
                ? request.getRemark() : "人工调整保证金余额");
        fxMarginTxnMapper.insert(txn);
    }
}
