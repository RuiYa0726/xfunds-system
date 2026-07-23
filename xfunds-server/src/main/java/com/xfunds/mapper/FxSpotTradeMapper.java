package com.xfunds.mapper;

import com.xfunds.entity.FxSpotTrade;
import org.apache.ibatis.annotations.Mapper;

/**
 * 即期交易子表 Mapper 接口
 */
@Mapper
public interface FxSpotTradeMapper {

    /**
     * 根据交易ID查询即期交易
     */
    FxSpotTrade selectByTradeId(String tradeId);

    /**
     * 新增即期交易
     */
    int insert(FxSpotTrade spotTrade);

    /**
     * 根据交易ID删除即期交易
     */
    int deleteByTradeId(String tradeId);
}
