package com.xfunds.mapper;

import com.xfunds.entity.FxOptionParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 期权参数 Mapper 接口
 */
@Mapper
public interface FxOptionParamMapper {

    /**
     * 查询所有期权参数
     */
    List<FxOptionParam> selectAll();

    /**
     * 根据参数编码查询期权参数
     */
    FxOptionParam selectByParamCode(String paramCode);

    /**
     * 更新期权参数
     */
    int update(FxOptionParam optionParam);
}
