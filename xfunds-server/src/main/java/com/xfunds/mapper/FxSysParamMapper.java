package com.xfunds.mapper;

import com.xfunds.entity.FxSysParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统参数 Mapper 接口
 */
@Mapper
public interface FxSysParamMapper {

    /**
     * 查询所有系统参数
     */
    List<FxSysParam> selectAll();

    /**
     * 根据参数编码查询系统参数
     */
    FxSysParam selectByParamCode(String paramCode);

    /**
     * 新增系统参数
     */
    int insert(FxSysParam param);

    /**
     * 更新系统参数
     */
    int update(FxSysParam param);
}
