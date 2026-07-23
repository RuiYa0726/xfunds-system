package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxQuote;
import com.xfunds.service.FxQuoteService;
import com.xfunds.service.ScheduledJobService;
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
 * 报价控制器
 */
@RestController
@RequestMapping("/api/quote")
public class FxQuoteController {

    @Autowired
    private FxQuoteService fxQuoteService;

    @Autowired
    private ScheduledJobService scheduledJobService;

    /**
     * 查询所有即期牌价（quoteType=SPOT, status=1）
     */
    @GetMapping("/spot")
    public Result<List<FxQuote>> spot() {
        return Result.ok(fxQuoteService.listSpot());
    }

    /**
     * 查询所有远期牌价（quoteType=FORWARD, status=1）
     */
    @GetMapping("/forward")
    public Result<List<FxQuote>> forward() {
        return Result.ok(fxQuoteService.listForward());
    }

    /**
     * 查询所有掉期牌价（quoteType=SWAP, status=1）
     */
    @GetMapping("/swap")
    public Result<List<FxQuote>> swap() {
        return Result.ok(fxQuoteService.listSwap());
    }

    /**
     * 分页查询报价列表，支持按类型、货币对、状态过滤
     */
    @GetMapping("/list")
    public Result<PageResponse<FxQuote>> list(@RequestParam(required = false) String quoteType,
                                               @RequestParam(required = false) String currencyPair,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(fxQuoteService.listByCondition(quoteType, currencyPair, status, pageNum, pageSize));
    }

    /**
     * 根据报价类型和货币对查询报价
     */
    @GetMapping("/search")
    public Result<List<FxQuote>> search(@RequestParam String quoteType,
                                        @RequestParam String currencyPair) {
        return Result.ok(fxQuoteService.listByTypeAndPair(quoteType, currencyPair));
    }

    /**
     * 根据报价ID查询报价详情
     */
    @GetMapping("/{quoteId}")
    public Result<FxQuote> getByQuoteId(@PathVariable Long quoteId) {
        return Result.ok(fxQuoteService.getByQuoteId(quoteId));
    }

    /**
     * 新增或更新报价（quoteId 为空则新增，否则更新）
     */
    @PostMapping("/save")
    public Result<FxQuote> save(@RequestBody FxQuote fxQuote) {
        return Result.ok(fxQuoteService.saveQuote(fxQuote));
    }

    /**
     * 手动触发刷新牌价（定时任务每小时自动执行，此接口用于手动测试）
     * 走定时任务日志记录流程
     */
    @PostMapping("/refresh")
    public Result<String> refresh() {
        Long operatorId = SecurityUtils.getCurrentUserId();
        scheduledJobService.runQuoteRefresh("MANUAL", operatorId);
        return Result.ok("牌价刷新完成");
    }
}
