package com.xfunds.scheduler;

import com.xfunds.service.ScheduledJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 获取牌价定时调度器
 * 每小时自动模拟生成新的外汇牌价并更新牌价展示
 * 牌价最多存储一周，超出自动删除
 * 银行盈利约束：总/分买价 > 分/客买价，总/分卖价 < 分/客卖价
 */
@Component
public class FxQuoteRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(FxQuoteRefreshScheduler.class);

    @Autowired
    private ScheduledJobService scheduledJobService;

    /**
     * cron 表达式：秒 分 时 日 月 周 -> 每小时整点执行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshQuotesAuto() {
        log.info("[定时调度] 获取牌价定时任务开始执行（每小时自动刷新）");
        try {
            scheduledJobService.runQuoteRefresh("AUTO", 1L);
        } catch (Exception e) {
            log.error("[定时调度] 获取牌价定时任务执行异常：{}", e.getMessage(), e);
        }
    }
}
