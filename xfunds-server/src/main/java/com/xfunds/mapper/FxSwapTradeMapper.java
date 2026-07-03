package com.xfunds.mapper;

import com.xfunds.entity.FxSwapTrade;
import org.apache.ibatis.annotations.Mapper;

/**
 * 掉期交易子表 Mapper 接口
 */
@Mapper
public interface FxSwapTradeMapper {

    /**
     * 根据交易ID查询掉期交易
     */
    FxSwapTrade selectByTradeId(String tradeId);

    /**
     * 新增掉期交易
     */
    int insert(FxSwapTrade swapTrade);

    /**
     * 根据交易ID删除掉期交易
     */
    int deleteByTradeId(String tradeId);
}
