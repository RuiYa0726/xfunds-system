package com.xfunds.service;

import com.xfunds.dto.PageResponse;
import com.xfunds.dto.ScheduledJobInfoVO;
import com.xfunds.dto.SettleResult;
import com.xfunds.entity.FxScheduledJobDetail;
import com.xfunds.entity.FxScheduledJobLog;

import java.util.List;

/**
 * 定时任务服务接口
 */
public interface ScheduledJobService {

    /**
     * 执行到期交割定时任务
     *
     * @param triggerType 触发类型：AUTO 自动 / MANUAL 手动
     * @param operatorId  操作人ID
     * @return 执行日志
     */
    FxScheduledJobLog runMaturitySettlement(String triggerType, Long operatorId);

    /**
     * 获取到期交割定时任务信息（名称、cron、上次执行、下次执行等）
     */
    ScheduledJobInfoVO getMaturitySettlementJobInfo();

    /**
     * 执行获取牌价定时任务
     *
     * @param triggerType 触发类型：AUTO 自动 / MANUAL 手动
     * @param operatorId  操作人ID
     * @return 执行日志
     */
    FxScheduledJobLog runQuoteRefresh(String triggerType, Long operatorId);

    /**
     * 获取牌价定时任务信息（名称、cron、上次执行、下次执行等）
     */
    ScheduledJobInfoVO getQuoteRefreshJobInfo();

    /**
     * 分页查询定时任务执行日志
     *
     * @param jobName  任务名称（为空时查询全部）
     * @param pageNum  页码
     * @param pageSize 每页大小
     */
    PageResponse<FxScheduledJobLog> listJobLogs(String jobName, int pageNum, int pageSize);

    /**
     * 根据执行日志ID查询本次执行的逐笔明细
     */
    List<FxScheduledJobDetail> listJobDetails(Long logId);

    /**
     * 重新执行单笔交割失败的交易
     * 用于账户余额不足导致交割失败后，人工补充余额再重试
     *
     * @param tradeId    交易ID
     * @param operatorId 操作人ID
     * @return 交割结果
     */
    SettleResult retrySettle(String tradeId, Long operatorId);
}
