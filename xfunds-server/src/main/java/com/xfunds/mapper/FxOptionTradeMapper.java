package com.xfunds.mapper;

import com.xfunds.entity.FxOptionTrade;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 期权交易子表 Mapper 接口
 */
@Mapper
public interface FxOptionTradeMapper {

    /**
     * 根据交易ID查询期权交易
     */
    FxOptionTrade selectByTradeId(String tradeId);

    /**
     * 新增期权交易
     */
    int insert(FxOptionTrade optionTrade);

    /**
     * 按条件分页查询期权交易列表
     *
     * @param params 查询条件：tradeIdList、optionStyle、optionType、buyerSeller、
     *               exerciseFlag、abandonFlag、premiumPaidFlag、closeDateNotNull、offset、pageSize
     */
    List<FxOptionTrade> selectByCondition(Map<String, Object> params);

    /**
     * 按条件查询期权交易总数
     */
    long countByCondition(Map<String, Object> params);

    /**
     * 更新期权交易
     */
    int update(FxOptionTrade optionTrade);

    /**
     * 查询美式期权且处于实值状态的期权交易（用于工作台提醒）
     */
    List<FxOptionTrade> selectAmericanInMoney();

    /**
     * 查询所有处于实值状态的期权交易（不限美式/欧式，用于期权价内提醒）
     * CALL: 参考汇率 > 执行价；PUT: 参考汇率 < 执行价
     */
    List<FxOptionTrade> selectAllInMoney();
}
