package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.dto.ApprovalRequest;
import com.xfunds.dto.EarlyDefaultRequest;
import com.xfunds.dto.EarlyDeliveryRequest;
import com.xfunds.dto.ForwardTradeEntryRequest;
import com.xfunds.dto.FullDefaultRequest;
import com.xfunds.dto.MarginSupplementRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.dto.RolloverMarketRequest;
import com.xfunds.dto.RolloverRequest;
import com.xfunds.dto.SpotTradeEntryRequest;
import com.xfunds.dto.SwapTradeEntryRequest;
import com.xfunds.dto.TradeDetailVO;
import com.xfunds.dto.TradeResponse;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.service.FxTradeService;
import com.xfunds.service.TradeLifecycleOpsService;
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
 * 交易控制器
 */
@RestController
@RequestMapping("/api/trade")
public class FxTradeController {

    @Autowired
    private FxTradeService fxTradeService;

    @Autowired
    private TradeLifecycleOpsService tradeLifecycleOpsService;

    /**
     * 查询所有交易
     */
    @GetMapping("/list")
    public Result<List<FxTradeMaster>> list() {
        return Result.ok(fxTradeService.listAll());
    }

    /**
     * 根据交易ID查询交易
     */
    @GetMapping("/{tradeId}")
    public Result<FxTradeMaster> getByTradeId(@PathVariable String tradeId) {
        return Result.ok(fxTradeService.getByTradeId(tradeId));
    }

    /**
     * 创建即期交易
     */
    @PostMapping("/spot")
    public Result<String> createSpot(@Valid @RequestBody SpotTradeEntryRequest req) {
        String tradeId = fxTradeService.createSpotTrade(req);
        return Result.ok(tradeId);
    }

    /**
     * 创建远期交易
     */
    @PostMapping("/forward")
    public Result<String> createForward(@Valid @RequestBody ForwardTradeEntryRequest req) {
        String tradeId = fxTradeService.createForwardTrade(req);
        return Result.ok(tradeId);
    }

    /**
     * 创建掉期交易
     */
    @PostMapping("/swap")
    public Result<String> createSwap(@Valid @RequestBody SwapTradeEntryRequest req) {
        String tradeId = fxTradeService.createSwapTrade(req);
        return Result.ok(tradeId);
    }

    /**
     * 更新即期交易并重新提交复核
     */
    @PostMapping("/spot/{tradeId}/resubmit")
    public Result<String> updateAndResubmitSpot(@PathVariable String tradeId, @Valid @RequestBody SpotTradeEntryRequest req) {
        String resultTradeId = fxTradeService.updateAndResubmitSpotTrade(tradeId, req);
        return Result.ok(resultTradeId);
    }

    /**
     * 更新远期交易并重新提交复核
     */
    @PostMapping("/forward/{tradeId}/resubmit")
    public Result<String> updateAndResubmitForward(@PathVariable String tradeId, @Valid @RequestBody ForwardTradeEntryRequest req) {
        String resultTradeId = fxTradeService.updateAndResubmitForwardTrade(tradeId, req);
        return Result.ok(resultTradeId);
    }

    /**
     * 更新掉期交易并重新提交复核
     */
    @PostMapping("/swap/{tradeId}/resubmit")
    public Result<String> updateAndResubmitSwap(@PathVariable String tradeId, @Valid @RequestBody SwapTradeEntryRequest req) {
        String resultTradeId = fxTradeService.updateAndResubmitSwapTrade(tradeId, req);
        return Result.ok(resultTradeId);
    }

    /**
     * 查询交易完整详情（主表 + 子表明细 + 生命周期 + 审批日志）
     */
    @GetMapping("/detail/{tradeId}")
    public Result<TradeDetailVO> getDetail(@PathVariable String tradeId) {
        return Result.ok(fxTradeService.getTradeDetail(tradeId));
    }

    /**
     * 分页查询未到期交易（状态为生效且类型为远期/掉期）
     * swapNear tab 过滤起息日 > 今天，swapFar tab 过滤到期日 > 今天
     */
    @GetMapping("/unmatured")
    public Result<PageResponse<FxTradeMaster>> listUnmatured(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String tradeType,
            @RequestParam(required = false) String currencyPair,
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String tab,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(fxTradeService.listUnmatured(businessNo, tradeType, currencyPair,
                branchCode, customerId, tab, pageNum, pageSize));
    }

    /**
     * 分页查询客户交易（支持多条件过滤）
     */
    @GetMapping("/customer")
    public Result<PageResponse<TradeResponse>> listCustomerTrades(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String tradeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String specialTradeType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(fxTradeService.listCustomerTrades(businessNo, customerId, tradeType,
                status, specialTradeType, pageNum, pageSize));
    }

    // ==================== 审批流程 ====================

    /**
     * 复核通过
     */
    @PostMapping("/approve")
    public Result<String> approve(@Valid @RequestBody ApprovalRequest request) {
        String tradeId = tradeLifecycleOpsService.approveTrade(
                request.getTaskId(), request.getTradeId(), request.getComment());
        return Result.ok(tradeId);
    }

    /**
     * 复核拒绝
     */
    @PostMapping("/reject")
    public Result<Void> reject(@Valid @RequestBody ApprovalRequest request) {
        tradeLifecycleOpsService.rejectTrade(
                request.getTaskId(), request.getTradeId(), request.getComment());
        return Result.ok();
    }

    /**
     * 退回经办
     */
    @PostMapping("/return")
    public Result<Void> returnTrade(@Valid @RequestBody ApprovalRequest request) {
        tradeLifecycleOpsService.returnTrade(
                request.getTaskId(), request.getTradeId(), request.getComment());
        return Result.ok();
    }

    // ==================== 未到期交易操作 ====================

    /**
     * 提前交割
     */
    @PostMapping("/early-delivery")
    public Result<String> earlyDelivery(@Valid @RequestBody EarlyDeliveryRequest request) {
        String newTradeId = tradeLifecycleOpsService.earlyDelivery(request.getTradeId(), request);
        return Result.ok(newTradeId);
    }

    /**
     * 提前违约
     */
    @PostMapping("/early-default")
    public Result<String> earlyDefault(@Valid @RequestBody EarlyDefaultRequest request) {
        String newTradeId = tradeLifecycleOpsService.earlyDefault(request.getTradeId(), request);
        return Result.ok(newTradeId);
    }

    /**
     * 原价展期
     */
    @PostMapping("/rollover-original")
    public Result<String> rolloverOriginal(@Valid @RequestBody RolloverRequest request) {
        String newTradeId = tradeLifecycleOpsService.rolloverOriginal(request.getTradeId(), request);
        return Result.ok(newTradeId);
    }

    /**
     * 市价展期
     */
    @PostMapping("/rollover-market")
    public Result<String> rolloverMarket(@Valid @RequestBody RolloverMarketRequest request) {
        String newTradeId = tradeLifecycleOpsService.rolloverMarket(request.getTradeId(), request);
        return Result.ok(newTradeId);
    }

    /**
     * 保证金增补
     */
    @PostMapping("/margin-supplement")
    public Result<Void> marginSupplement(@Valid @RequestBody MarginSupplementRequest request) {
        tradeLifecycleOpsService.marginSupplement(request.getTradeId(), request);
        return Result.ok();
    }

    /**
     * 全部违约
     */
    @PostMapping("/full-default")
    public Result<Void> fullDefault(@Valid @RequestBody FullDefaultRequest request) {
        tradeLifecycleOpsService.fullDefault(request.getTradeId(), request);
        return Result.ok();
    }

}
