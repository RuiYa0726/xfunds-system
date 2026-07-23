package com.xfunds.scheduler;

import com.xfunds.service.ScheduledJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 到期交割定时调度器
 * 每天 18:00 自动检索当天到期交易并执行交割
 */
@Component
public class MaturitySettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaturitySettlementScheduler.class);

    @Autowired
    private ScheduledJobService scheduledJobService;

    /**
     * cron 表达式：秒 分 时 日 月 周 -> 每天 18:00:00 执行
     */
    @Scheduled(cron = "0 0 18 * * ?")
    public void runMaturitySettlementAuto() {
        log.info("[定时调度] 到期交割定时任务开始执行（自动触发）");
        try {
            scheduledJobService.runMaturitySettlement("AUTO", null);
        } catch (Exception e) {
            log.error("[定时调度] 到期交割定时任务执行异常：{}", e.getMessage(), e);
        }
    }
}
