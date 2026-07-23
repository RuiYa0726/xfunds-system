package com.xfunds.mapper;

import com.xfunds.entity.ReportFieldAlias;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字段别名表 Mapper
 */
@Mapper
public interface ReportFieldAliasMapper {

    /** 查询所有启用的别名（用于自然语言解析构建词典） */
    List<ReportFieldAlias> selectAll();

    /** 按字段编码查别名 */
    List<ReportFieldAlias> selectByFieldCode(@Param("fieldCode") String fieldCode);
}
