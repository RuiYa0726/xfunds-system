package com.xfunds.mapper;

import com.xfunds.entity.FxTradeLifecycle;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 交易生命周期 Mapper 接口
 */
@Mapper
public interface FxTradeLifecycleMapper {

    /**
     * 根据交易ID查询生命周期事件列表
     */
    List<FxTradeLifecycle> selectByTradeId(String tradeId);

    /**
     * 新增生命周期事件
     */
    int insert(FxTradeLifecycle lifecycle);
}
