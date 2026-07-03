package com.xfunds.mapper;

import com.xfunds.entity.FxForwardTrade;
import org.apache.ibatis.annotations.Mapper;

/**
 * 远期交易子表 Mapper 接口
 */
@Mapper
public interface FxForwardTradeMapper {

    /**
     * 根据交易ID查询远期交易
     */
    FxForwardTrade selectByTradeId(String tradeId);

    /**
     * 新增远期交易
     */
    int insert(FxForwardTrade forwardTrade);

    /**
     * 更新远期交易
     */
    int update(FxForwardTrade forwardTrade);

    /**
     * 根据交易ID删除远期交易
     */
    int deleteByTradeId(String tradeId);
}
