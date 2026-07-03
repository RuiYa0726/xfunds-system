package com.xfunds.mapper;

import com.xfunds.entity.FxMarginTxn;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 保证金交易流水 Mapper 接口
 */
@Mapper
public interface FxMarginTxnMapper {

    /**
     * 根据保证金账户ID查询流水列表
     */
    List<FxMarginTxn> selectByMarginAccountId(String marginAccountId);

    /**
     * 新增保证金流水
     */
    int insert(FxMarginTxn marginTxn);
}
