package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.CustomerAccountRequest;
import com.xfunds.dto.CustomerSaveRequest;
import com.xfunds.dto.MarginAccountAdjustRequest;
import com.xfunds.dto.MarginAccountCreateRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxCustomer;
import com.xfunds.entity.FxCustomerAccount;
import com.xfunds.entity.FxCustomerBalance;
import com.xfunds.entity.FxMarginAccount;
import com.xfunds.mapper.FxMarginAccountMapper;
import com.xfunds.service.FxCustomerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户管理控制器（交易录入与系统管理共用）
 */
@RestController
@RequestMapping("/api/customer")
public class FxCustomerController {

    @Autowired
    private FxCustomerService fxCustomerService;

    @Autowired
    private FxMarginAccountMapper fxMarginAccountMapper;

    /**
     * 搜索客户（按客户ID或客户名称模糊匹配，用于交易录入选择）
     */
    @GetMapping("/search")
    public Result<List<FxCustomer>> search(@RequestParam(required = false) String keyword) {
        return Result.ok(fxCustomerService.search(keyword));
    }

    /**
     * 根据客户ID查询客户详情
     */
    @GetMapping("/{customerId}")
    public Result<FxCustomer> getByCustomerId(@PathVariable String customerId) {
        return Result.ok(fxCustomerService.getByCustomerId(customerId));
    }

    /**
     * 查询客户账户列表（可按币种过滤）
     */
    @GetMapping("/{customerId}/accounts")
    public Result<List<FxCustomerAccount>> getAccounts(@PathVariable String customerId,
                                                       @RequestParam(required = false) String currency) {
        return Result.ok(fxCustomerService.getAccounts(customerId, currency));
    }

    /**
     * 查询客户所有币种余额
     */
    @GetMapping("/{customerId}/balance")
    public Result<List<FxCustomerBalance>> getBalance(@PathVariable String customerId) {
        return Result.ok(fxCustomerService.getBalances(customerId));
    }

    /**
     * 查询客户保证金账户列表（用于交易录入选择保证金账户）
     */
    @GetMapping("/{customerId}/margin-accounts")
    public Result<List<FxMarginAccount>> getMarginAccounts(@PathVariable String customerId) {
        return Result.ok(fxMarginAccountMapper.selectByCustomerId(customerId));
    }

    /**
     * 分页查询客户列表（用于系统管理页面）
     */
    @GetMapping("/list")
    public Result<PageResponse<FxCustomer>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String customerId,
                                                 @RequestParam(required = false) String customerName,
                                                 @RequestParam(required = false) String customerType,
                                                 @RequestParam(required = false) String orgCode) {
        return Result.ok(fxCustomerService.listPage(customerId, customerName, customerType, orgCode, pageNum, pageSize));
    }

    /**
     * 新增或更新客户
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody CustomerSaveRequest request) {
        fxCustomerService.saveCustomer(request);
        return Result.ok();
    }

    /**
     * 新增客户账户
     */
    @PostMapping("/{customerId}/account")
    public Result<Void> addAccount(@PathVariable String customerId, @RequestBody CustomerAccountRequest request) {
        fxCustomerService.addAccount(customerId, request);
        return Result.ok();
    }

    /**
     * 更新客户账户
     */
    @PostMapping("/{customerId}/account/{accountId}")
    public Result<Void> updateAccount(@PathVariable String customerId, @PathVariable Long accountId,
                                      @RequestBody CustomerAccountRequest request) {
        fxCustomerService.updateAccount(customerId, accountId, request);
        return Result.ok();
    }

    /**
     * 人工调整保证金账户余额（与定时交割任务操作同一张表，记录保证金流水）
     */
    @PostMapping("/margin-account/adjust")
    public Result<Void> adjustMarginAccount(@Valid @RequestBody MarginAccountAdjustRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        fxCustomerService.adjustMarginAccountBalance(request, operatorId);
        return Result.ok();
    }

    /**
     * 新增保证金账户（手工为客户创建保证金账户）
     */
    @PostMapping("/{customerId}/margin-account")
    public Result<Void> addMarginAccount(@PathVariable String customerId,
                                          @Valid @RequestBody MarginAccountCreateRequest request) {
        fxCustomerService.addMarginAccount(customerId, request.getCurrency(), request.getInitialBalance());
        return Result.ok();
    }
}
