package com.xfunds.controller;

import com.xfunds.common.BusinessException;
import com.xfunds.common.Result;
import com.xfunds.common.ResultCode;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.OptionAbandonRequest;
import com.xfunds.dto.OptionCloseRequest;
import com.xfunds.dto.OptionExerciseRequest;
import com.xfunds.dto.OptionPremiumSettleRequest;
import com.xfunds.dto.OptionReminderVO;
import com.xfunds.dto.OptionTradeDetailVO;
import com.xfunds.dto.OptionTradeEntryRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.dto.TaskVO;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.enums.TradeType;
import com.xfunds.service.OptionTradeService;
import com.xfunds.service.TaskService;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 期权交易控制器
 */
@RestController
@RequestMapping("/api/option")
public class OptionTradeController {

    @Autowired
    private OptionTradeService optionTradeService;

    @Autowired
    private TaskService taskService;

    // ==================== 期权交易录入 ====================

    /**
     * 创建期权交易
     */
    @PostMapping("/create")
    public Result<String> create(@Valid @RequestBody OptionTradeEntryRequest req) {
        String tradeId = optionTradeService.createOption(req);
        return Result.ok(tradeId);
    }

    /**
     * 查询期权交易完整详情（主表 + 期权子表明细 + 生命周期 + 审批日志）
     */
    @GetMapping("/detail/{tradeId}")
    public Result<OptionTradeDetailVO> getDetail(@PathVariable String tradeId) {
        return Result.ok(optionTradeService.getOptionDetail(tradeId));
    }

    /**
     * 分页查询期权交易列表
     */
    @GetMapping("/list")
    public Result<PageResponse<FxTradeMaster>> list(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String optionStyle,
            @RequestParam(required = false) String optionType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listOptions(businessNo, customerId, optionStyle,
                optionType, status, pageNum, pageSize));
    }

    /**
     * 查询已平仓的期权交易列表
     */
    @GetMapping("/close-list")
    public Result<PageResponse<FxTradeMaster>> closeList(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listCloseTrades(businessNo, customerId, pageNum, pageSize));
    }

    /**
     * 查询期权费已交割的期权交易列表
     */
    @GetMapping("/premium-list")
    public Result<PageResponse<FxTradeMaster>> premiumList(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listPremiumTrades(businessNo, customerId, pageNum, pageSize));
    }

    /**
     * 查询已行权的期权交易列表
     */
    @GetMapping("/exercise-list")
    public Result<PageResponse<FxTradeMaster>> exerciseList(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listExerciseTrades(businessNo, customerId, pageNum, pageSize));
    }

    /**
     * 查询已放弃的期权交易列表
     */
    @GetMapping("/abandon-list")
    public Result<PageResponse<FxTradeMaster>> abandonList(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listAbandonTrades(businessNo, customerId, pageNum, pageSize));
    }

    // ==================== 期权工作台 ====================

    /**
     * 查询美式期权实值提醒列表
     */
    @GetMapping("/workbench/reminders")
    public Result<List<OptionReminderVO>> reminders() {
        return Result.ok(optionTradeService.getAmericanInMoneyReminders());
    }

    /**
     * 查看原始期权交易详情
     */
    @GetMapping("/workbench/original/{tradeId}")
    public Result<OptionTradeDetailVO> viewOriginal(@PathVariable String tradeId) {
        return Result.ok(optionTradeService.viewOriginalTrade(tradeId));
    }

    /**
     * 行权
     */
    @PostMapping("/workbench/execute")
    public Result<Void> execute(@Valid @RequestBody OptionExerciseRequest request) {
        optionTradeService.executeOption(request.getTradeId(), request);
        return Result.ok();
    }

    /**
     * 暂不处理（仅记录生命周期事件）
     */
    @PostMapping("/workbench/postpone")
    public Result<Void> postpone(@RequestBody Map<String, String> body) {
        String tradeId = body.get("tradeId");
        String remark = body.get("remark");
        if (tradeId == null || tradeId.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "交易ID不能为空");
        }
        optionTradeService.postponeReminder(tradeId, remark);
        return Result.ok();
    }

    /**
     * 查询当前用户的期权相关待办任务（过滤业务类型为期权）
     */
    @GetMapping("/workbench/tasks")
    public Result<List<TaskVO>> workbenchTasks() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }
        List<TaskVO> allTasks = taskService.getMyTaskVOs(userId);
        // 过滤出期权相关的任务
        List<TaskVO> optionTasks = allTasks.stream()
                .filter(t -> TradeType.OPTION.name().equals(t.getTradeType()))
                .collect(Collectors.toList());
        return Result.ok(optionTasks);
    }

    // ==================== 期权存续期管理 ====================

    /**
     * 分页查询未到期期权交易
     */
    @GetMapping("/unmatured")
    public Result<PageResponse<FxTradeMaster>> unmatured(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String optionStyle,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listUnmaturedOptions(businessNo, customerId,
                optionStyle, pageNum, pageSize));
    }

    /**
     * 平仓
     */
    @PostMapping("/close")
    public Result<Void> close(@Valid @RequestBody OptionCloseRequest request) {
        optionTradeService.closeOption(request.getTradeId(), request);
        return Result.ok();
    }

    /**
     * 分页查询已到期的欧式期权交易
     */
    @GetMapping("/european-matured")
    public Result<PageResponse<FxTradeMaster>> europeanMatured(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listEuropeanMaturedOptions(businessNo, customerId, pageNum, pageSize));
    }

    /**
     * 放弃
     */
    @PostMapping("/abandon")
    public Result<Void> abandon(@Valid @RequestBody OptionAbandonRequest request) {
        optionTradeService.abandonOption(request.getTradeId(), request);
        return Result.ok();
    }

    /**
     * 期权费交割
     */
    @PostMapping("/premium-settle")
    public Result<Void> premiumSettle(@Valid @RequestBody OptionPremiumSettleRequest request) {
        optionTradeService.premiumSettle(request.getTradeId(), request);
        return Result.ok();
    }

    /**
     * 分页查询美式期权监控期交易
     */
    @GetMapping("/american-monitoring")
    public Result<PageResponse<FxTradeMaster>> americanMonitoring(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listAmericanMonitoring(businessNo, customerId, pageNum, pageSize));
    }

    /**
     * 分页查询已到期的美式期权交易
     */
    @GetMapping("/american-matured")
    public Result<PageResponse<FxTradeMaster>> americanMatured(
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(optionTradeService.listAmericanMaturedOptions(businessNo, customerId, pageNum, pageSize));
    }

    /**
     * 行权（存续期管理入口，与工作台行权共用）
     */
    @PostMapping("/exercise")
    public Result<Void> exercise(@Valid @RequestBody OptionExerciseRequest request) {
        optionTradeService.executeOption(request.getTradeId(), request);
        return Result.ok();
    }
}
