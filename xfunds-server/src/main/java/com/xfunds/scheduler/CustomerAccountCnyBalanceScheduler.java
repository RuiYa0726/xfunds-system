package com.xfunds.scheduler;

import com.xfunds.service.ScheduledJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 客户账户折人民币余额定时刷新调度器
 * 每天 01:00 依据当日即期汇率重新计算所有客户账户的折人民币余额
 */
@Component
public class CustomerAccountCnyBalanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountCnyBalanceScheduler.class);

    @Autowired
    private ScheduledJobService scheduledJobService;

    /**
     * cron 表达式：秒 分 时 日 月 周 -> 每天 01:00:00 执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void refreshCnyBalanceAuto() {
        log.info("[定时调度] 客户账户折人民币余额刷新任务开始执行（每天 01:00 自动刷新）");
        try {
            scheduledJobService.runCnyBalanceRefresh("AUTO", 1L);
        } catch (Exception e) {
            log.error("[定时调度] 客户账户折人民币余额刷新任务执行异常：{}", e.getMessage(), e);
        }
    }
}
