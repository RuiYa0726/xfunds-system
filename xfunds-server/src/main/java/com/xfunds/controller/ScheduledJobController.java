package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.PageResponse;
import com.xfunds.dto.ScheduledJobInfoVO;
import com.xfunds.dto.SettleResult;
import com.xfunds.entity.FxScheduledJobDetail;
import com.xfunds.entity.FxScheduledJobLog;
import com.xfunds.service.ScheduledJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务控制器
 */
@RestController
@RequestMapping("/api/scheduled-job")
public class ScheduledJobController {

    @Autowired
    private ScheduledJobService scheduledJobService;

    /**
     * 获取到期交割定时任务信息
     */
    @GetMapping("/maturity-settlement/info")
    public Result<ScheduledJobInfoVO> getMaturitySettlementInfo() {
        return Result.ok(scheduledJobService.getMaturitySettlementJobInfo());
    }

    /**
     * 手动触发到期交割任务（一键交割当天到期交易）
     */
    @PostMapping("/maturity-settlement/run")
    public Result<FxScheduledJobLog> runMaturitySettlement() {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.ok(scheduledJobService.runMaturitySettlement("MANUAL", operatorId));
    }

    /**
     * 获取牌价定时任务信息
     */
    @GetMapping("/quote-refresh/info")
    public Result<ScheduledJobInfoVO> getQuoteRefreshInfo() {
        return Result.ok(scheduledJobService.getQuoteRefreshJobInfo());
    }

    /**
     * 手动触发获取牌价任务
     */
    @PostMapping("/quote-refresh/run")
    public Result<FxScheduledJobLog> runQuoteRefresh() {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.ok(scheduledJobService.runQuoteRefresh("MANUAL", operatorId));
    }

    /**
     * 重新执行单笔交割失败的交易
     * 用于账户余额不足导致交割失败后，人工补充余额再重试
     */
    @PostMapping("/retry-settle/{tradeId}")
    public Result<SettleResult> retrySettle(@PathVariable String tradeId) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.ok(scheduledJobService.retrySettle(tradeId, operatorId));
    }

    /**
     * 分页查询定时任务执行日志
     */
    @GetMapping("/logs")
    public Result<PageResponse<FxScheduledJobLog>> listLogs(
            @RequestParam(required = false) String jobName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(scheduledJobService.listJobLogs(jobName, pageNum, pageSize));
    }

    /**
     * 根据执行日志ID查询本次执行的逐笔明细
     */
    @GetMapping("/logs/{logId}/details")
    public Result<List<FxScheduledJobDetail>> listLogDetails(@PathVariable Long logId) {
        return Result.ok(scheduledJobService.listJobDetails(logId));
    }
}
