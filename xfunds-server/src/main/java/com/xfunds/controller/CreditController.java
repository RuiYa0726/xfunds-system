package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.dto.CreditLimitSaveRequest;
import com.xfunds.dto.MarginCalcRequest;
import com.xfunds.entity.FxCreditLimit;
import com.xfunds.entity.FxMarginParam;
import com.xfunds.service.CreditService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 授信额度与保证金参数管理控制器
 */
@RestController
@RequestMapping("/api/system/credit")
public class CreditController {

    @Autowired
    private CreditService creditService;

    /**
     * 查询授信额度列表（可按客户ID过滤）
     */
    @GetMapping("/list")
    public Result<List<FxCreditLimit>> list(@RequestParam(required = false) String customerId) {
        return Result.ok(creditService.listCreditLimits(customerId));
    }

    /**
     * 新增或更新授信额度
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody CreditLimitSaveRequest request) {
        creditService.saveCreditLimit(request);
        return Result.ok();
    }

    /**
     * 查询所有保证金参数
     */
    @GetMapping("/margin-param")
    public Result<List<FxMarginParam>> marginParamList() {
        return Result.ok(creditService.listMarginParams());
    }

    /**
     * 更新保证金参数
     */
    @PostMapping("/margin-param")
    public Result<Void> saveMarginParam(@RequestBody FxMarginParam param) {
        creditService.saveMarginParam(param);
        return Result.ok();
    }

    /**
     * 计算所需保证金金额
     */
    @GetMapping("/calc-margin")
    public Result<Map<String, BigDecimal>> calcMargin(@Valid MarginCalcRequest request) {
        BigDecimal marginAmount = creditService.calcMargin(request);
        return Result.ok(Map.of("marginAmount", marginAmount));
    }
}
