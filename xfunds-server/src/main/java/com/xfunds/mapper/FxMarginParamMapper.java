package com.xfunds.mapper;

import com.xfunds.entity.FxMarginParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 保证金参数 Mapper 接口
 */
@Mapper
public interface FxMarginParamMapper {

    /**
     * 查询所有保证金参数
     */
    List<FxMarginParam> selectAll();

    /**
     * 根据参数编码查询保证金参数
     */
    FxMarginParam selectByParamCode(String paramCode);

    /**
     * 更新保证金参数
     */
    int update(FxMarginParam param);
}
