package com.xfunds.service;

import com.xfunds.entity.FxTradeLifecycle;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易生命周期服务接口
 */
public interface TradeLifecycleService {

    /**
     * 记录生命周期事件
     *
     * @param tradeId         交易ID
     * @param eventType       事件类型
     * @param operatorId      操作人ID
     * @param beforeStatus    变更前状态
     * @param afterStatus     变更后状态
     * @param eventAmount     事件金额
     * @param eventRate       事件汇率
     * @param relatedTradeId  关联交易ID
     * @param remark          备注
     */
    void recordEvent(String tradeId, String eventType, Long operatorId, String beforeStatus,
                     String afterStatus, BigDecimal eventAmount, BigDecimal eventRate,
                     String relatedTradeId, String remark);

    /**
     * 根据交易ID查询生命周期事件列表
     */
    List<FxTradeLifecycle> listByTradeId(String tradeId);
}
