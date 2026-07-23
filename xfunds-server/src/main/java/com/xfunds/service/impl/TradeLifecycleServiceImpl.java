package com.xfunds.service.impl;

import com.xfunds.entity.FxTradeLifecycle;
import com.xfunds.mapper.FxTradeLifecycleMapper;
import com.xfunds.service.TradeLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易生命周期服务实现类
 */
@Service
public class TradeLifecycleServiceImpl implements TradeLifecycleService {

    @Autowired
    private FxTradeLifecycleMapper fxTradeLifecycleMapper;

    /**
     * 记录生命周期事件
     */
    @Override
    public void recordEvent(String tradeId, String eventType, Long operatorId, String beforeStatus,
                            String afterStatus, BigDecimal eventAmount, BigDecimal eventRate,
                            String relatedTradeId, String remark) {
        FxTradeLifecycle lifecycle = new FxTradeLifecycle();
        lifecycle.setTradeId(tradeId);
        lifecycle.setEventType(eventType);
        lifecycle.setOperatorId(operatorId);
        lifecycle.setBeforeStatus(beforeStatus);
        lifecycle.setAfterStatus(afterStatus);
        lifecycle.setEventAmount(eventAmount);
        lifecycle.setEventRate(eventRate);
        lifecycle.setRelatedTradeId(relatedTradeId);
        lifecycle.setRemark(remark);
        fxTradeLifecycleMapper.insert(lifecycle);
    }

    /**
     * 根据交易ID查询生命周期事件列表
     */
    @Override
    public List<FxTradeLifecycle> listByTradeId(String tradeId) {
        return fxTradeLifecycleMapper.selectByTradeId(tradeId);
    }
}
