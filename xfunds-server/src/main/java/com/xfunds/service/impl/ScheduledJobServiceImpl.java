package com.xfunds.service.impl;

import com.xfunds.dto.PageResponse;
import com.xfunds.dto.ScheduledJobInfoVO;
import com.xfunds.dto.SettleResult;
import com.xfunds.entity.FxScheduledJobDetail;
import com.xfunds.entity.FxScheduledJobLog;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.enums.TradeStatus;
import com.xfunds.mapper.FxScheduledJobDetailMapper;
import com.xfunds.mapper.FxScheduledJobLogMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.service.FxQuoteService;
import com.xfunds.service.OptionTradeService;
import com.xfunds.service.ScheduledJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务服务实现类
 * 编排到期交割任务的整体执行流程，并记录执行日志
 */
@Service
public class ScheduledJobServiceImpl implements ScheduledJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobServiceImpl.class);

    /** 到期交割任务名称 */
    public static final String JOB_MATURITY_SETTLEMENT = "MATURITY_SETTLEMENT";
    /** cron 表达式：每天 18:00 执行 */
    public static final String CRON_MATURITY_SETTLEMENT = "0 0 18 * * ?";
    /** 获取牌价任务名称 */
    public static final String JOB_QUOTE_REFRESH = "QUOTE_REFRESH";
    /** cron 表达式：每小时整点执行 */
    public static final String CRON_QUOTE_REFRESH = "0 0 * * * ?";
    /** 系统操作人ID（自动触发时使用，对应 admin 用户） */
    private static final Long SYSTEM_OPERATOR_ID = 1L;

    @Autowired
    private FxTradeMasterMapper fxTradeMasterMapper;

    @Autowired
    private FxScheduledJobLogMapper fxScheduledJobLogMapper;

    @Autowired
    private FxScheduledJobDetailMapper fxScheduledJobDetailMapper;

    @Autowired
    private MaturitySettlementExecutor maturitySettlementExecutor;

    @Autowired
    private FxQuoteService fxQuoteService;

    @Autowired
    private OptionTradeService optionTradeService;

    /** 防止到期交割任务并发执行（自动与手动触发互斥） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 防止牌价刷新任务并发执行 */
    private final AtomicBoolean quoteRunning = new AtomicBoolean(false);

    @Override
    public FxScheduledJobLog runMaturitySettlement(String triggerType, Long operatorId) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("到期交割任务正在执行中，请稍后再试");
        }
        Long opId = operatorId != null ? operatorId : SYSTEM_OPERATOR_ID;
        long start = System.currentTimeMillis();
        LocalDateTime runTime = LocalDateTime.now();

        FxScheduledJobLog jobLog = new FxScheduledJobLog();
        jobLog.setJobName(JOB_MATURITY_SETTLEMENT);
        jobLog.setTriggerType(triggerType);
        jobLog.setRunTime(runTime);
        jobLog.setOperatorId(opId);

        String firstError = null;
        List<FxScheduledJobDetail> detailList = new ArrayList<>();
        try {
            // 检索到期日 <= 当天 且 状态为 生效/到期 的交易
            LocalDate today = LocalDate.now();
            List<String> statusList = List.of(TradeStatus.ACTIVE.name(), TradeStatus.MATURED.name());
            List<FxTradeMaster> trades = fxTradeMasterMapper.selectToSettle(today, statusList);

            int total = trades == null ? 0 : trades.size();
            int success = 0;
            int fail = 0;

            log.info("[定时交割] 触发类型={}，待交割交易数={}", triggerType, total);

            if (total > 0) {
                for (FxTradeMaster trade : trades) {
                    try {
                        SettleResult result = maturitySettlementExecutor.settleOne(trade, opId);
                        if ("SUCCESS".equals(result.getResult())) {
                            success++;
                        } else {
                            fail++;
                        }
                        detailList.add(toDetail(result));
                    } catch (Exception e) {
                        fail++;
                        log.error("[定时交割] 交易 {} 交割异常：{}", trade.getTradeId(), e.getMessage(), e);
                        if (firstError == null) {
                            firstError = trade.getTradeId() + "：" + e.getMessage();
                        }
                        // 异常情况也记录一条失败明细
                        detailList.add(toDetailOnError(trade, e.getMessage()));
                    }
                }
            }

            jobLog.setTotalCount(total);
            jobLog.setSuccessCount(success);
            jobLog.setFailCount(fail);
            jobLog.setStatus(determineStatus(total, success, fail));
            if (firstError != null) {
                jobLog.setErrorMessage(truncate(firstError, 500));
            }
        } catch (Exception e) {
            log.error("[定时交割] 任务执行异常：{}", e.getMessage(), e);
            jobLog.setTotalCount(0);
            jobLog.setSuccessCount(0);
            jobLog.setFailCount(0);
            jobLog.setStatus("FAILED");
            jobLog.setErrorMessage(truncate(e.getMessage(), 500));
        } finally {
            jobLog.setDurationMs(System.currentTimeMillis() - start);
            try {
                fxScheduledJobLogMapper.insert(jobLog);
                // 写入本次执行的逐笔明细
                if (!detailList.isEmpty()) {
                    for (FxScheduledJobDetail d : detailList) {
                        d.setLogId(jobLog.getLogId());
                    }
                    fxScheduledJobDetailMapper.batchInsert(detailList);
                }
            } catch (Exception e) {
                log.error("[定时交割] 写入执行日志/明细失败：{}", e.getMessage(), e);
            }
            running.set(false);
        }
        return jobLog;
    }

    /**
     * 将单笔交割结果转换为执行明细实体
     */
    private FxScheduledJobDetail toDetail(SettleResult r) {
        FxScheduledJobDetail d = new FxScheduledJobDetail();
        d.setTradeId(r.getTradeId());
        d.setBusinessNo(r.getBusinessNo());
        d.setTradeType(r.getTradeType());
        d.setSettleAccount(r.getSettleAccount());
        d.setSettleAmount(r.getSettleAmount());
        d.setMarginAccount(r.getMarginAccount());
        d.setMarginAmount(r.getMarginAmount());
        d.setResult(r.getResult());
        d.setErrorMessage(r.getErrorMessage());
        return d;
    }

    /**
     * 交割过程抛出异常时，根据交易主表信息构造一条失败明细
     */
    private FxScheduledJobDetail toDetailOnError(FxTradeMaster trade, String errorMsg) {
        FxScheduledJobDetail d = new FxScheduledJobDetail();
        d.setTradeId(trade.getTradeId());
        d.setBusinessNo(trade.getBusinessNo());
        d.setTradeType(trade.getTradeType());
        d.setResult("FAIL");
        d.setErrorMessage(truncate(errorMsg, 500));
        return d;
    }

    @Override
    public ScheduledJobInfoVO getMaturitySettlementJobInfo() {
        ScheduledJobInfoVO vo = new ScheduledJobInfoVO();
        vo.setJobName(JOB_MATURITY_SETTLEMENT);
        vo.setDescription("检索当天到期的交易并进行交割：扣减客户结算账户余额并退还保证金，余额不足则扣除全部保证金并标记交割失败");
        vo.setCronExpression(CRON_MATURITY_SETTLEMENT);
        vo.setScheduleDesc("每天 18:00 自动执行");
        vo.setEnabled(true);
        vo.setNextRunTime(calcNextRunTime(LocalDateTime.now()));

        FxScheduledJobLog latest = fxScheduledJobLogMapper.selectLatestByJobName(JOB_MATURITY_SETTLEMENT);
        if (latest != null) {
            vo.setLatestRunTime(latest.getRunTime());
            vo.setLatestRunStatus(latest.getStatus());
            vo.setLatestRunSummary(String.format("处理 %d 笔，成功 %d 笔，失败 %d 笔，耗时 %d 毫秒",
                    latest.getTotalCount(), latest.getSuccessCount(), latest.getFailCount(), latest.getDurationMs()));
        }
        return vo;
    }

    @Override
    public FxScheduledJobLog runQuoteRefresh(String triggerType, Long operatorId) {
        if (!quoteRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("获取牌价任务正在执行中，请稍后再试");
        }
        Long opId = operatorId != null ? operatorId : SYSTEM_OPERATOR_ID;
        long start = System.currentTimeMillis();
        LocalDateTime runTime = LocalDateTime.now();

        FxScheduledJobLog jobLog = new FxScheduledJobLog();
        jobLog.setJobName(JOB_QUOTE_REFRESH);
        jobLog.setTriggerType(triggerType);
        jobLog.setRunTime(runTime);
        jobLog.setOperatorId(opId);

        try {
            log.info("[牌价刷新] 触发类型={}，开始刷新牌价", triggerType);
            fxQuoteService.refreshQuotes();
            // 牌价刷新后同步更新期权参考汇率，用于期权价内提醒监听
            try {
                optionTradeService.updateReferenceRates();
            } catch (Exception ex) {
                log.warn("[牌价刷新] 更新期权参考汇率失败：{}", ex.getMessage());
            }
            jobLog.setTotalCount(1);
            jobLog.setSuccessCount(1);
            jobLog.setFailCount(0);
            jobLog.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("[牌价刷新] 任务执行异常：{}", e.getMessage(), e);
            jobLog.setTotalCount(1);
            jobLog.setSuccessCount(0);
            jobLog.setFailCount(1);
            jobLog.setStatus("FAILED");
            jobLog.setErrorMessage(truncate(e.getMessage(), 500));
        } finally {
            jobLog.setDurationMs(System.currentTimeMillis() - start);
            try {
                fxScheduledJobLogMapper.insert(jobLog);
            } catch (Exception e) {
                log.error("[牌价刷新] 写入执行日志失败：{}", e.getMessage(), e);
            }
            quoteRunning.set(false);
        }
        return jobLog;
    }

    @Override
    public ScheduledJobInfoVO getQuoteRefreshJobInfo() {
        ScheduledJobInfoVO vo = new ScheduledJobInfoVO();
        vo.setJobName(JOB_QUOTE_REFRESH);
        vo.setDescription("每小时自动模拟生成新的外汇牌价并更新牌价展示，牌价最多存储一周，超出自动删除。确保银行盈利：总/分买价 > 分/客买价，总/分卖价 < 分/客卖价");
        vo.setCronExpression(CRON_QUOTE_REFRESH);
        vo.setScheduleDesc("每小时整点自动执行");
        vo.setEnabled(true);
        vo.setNextRunTime(calcNextHourRunTime(LocalDateTime.now()));

        FxScheduledJobLog latest = fxScheduledJobLogMapper.selectLatestByJobName(JOB_QUOTE_REFRESH);
        if (latest != null) {
            vo.setLatestRunTime(latest.getRunTime());
            vo.setLatestRunStatus(latest.getStatus());
            vo.setLatestRunSummary(String.format("耗时 %d 毫秒，状态：%s",
                    latest.getDurationMs(),
                    "SUCCESS".equals(latest.getStatus()) ? "成功" : "失败"));
        }
        return vo;
    }

    @Override
    public PageResponse<FxScheduledJobLog> listJobLogs(String jobName, int pageNum, int pageSize) {
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1 || pageSize > 200) {
            pageSize = 10;
        }
        int offset = (pageNum - 1) * pageSize;
        List<FxScheduledJobLog> list = fxScheduledJobLogMapper.selectByCondition(jobName, offset, pageSize);
        long total = fxScheduledJobLogMapper.countByCondition(jobName);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    @Override
    public List<FxScheduledJobDetail> listJobDetails(Long logId) {
        if (logId == null) {
            return new ArrayList<>();
        }
        List<FxScheduledJobDetail> list = fxScheduledJobDetailMapper.selectByLogId(logId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public SettleResult retrySettle(String tradeId, Long operatorId) {
        FxTradeMaster trade = fxTradeMasterMapper.selectByTradeId(tradeId);
        if (trade == null) {
            throw new IllegalStateException("交易不存在：" + tradeId);
        }
        if (!TradeStatus.SETTLE_FAILED.name().equals(trade.getStatus())) {
            throw new IllegalStateException("仅交割失败的交易可重新执行，当前状态：" + trade.getStatus());
        }
        log.info("[交割重试] 交易 {} 由操作人 {} 手动重新执行交割", tradeId, operatorId);
        return maturitySettlementExecutor.settleOne(trade, operatorId);
    }

    /**
     * 根据处理结果判定任务整体状态
     */
    private String determineStatus(int total, int success, int fail) {
        if (total == 0) {
            return "SUCCESS";
        }
        if (fail == 0) {
            return "SUCCESS";
        }
        if (success == 0) {
            return "FAILED";
        }
        return "PARTIAL";
    }

    /**
     * 计算下次执行时间：当天 18:00（已过则顺延至次日 18:00）
     */
    private LocalDateTime calcNextRunTime(LocalDateTime now) {
        LocalDateTime next = now.toLocalDate().atTime(18, 0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * 计算牌价刷新任务下次执行时间：下一个整点
     */
    private LocalDateTime calcNextHourRunTime(LocalDateTime now) {
        return now.toLocalDate().atTime(now.getHour(), 0).plusHours(1);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
