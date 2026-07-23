package com.xfunds.mapper;

import com.xfunds.entity.FxOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 机构 Mapper 接口
 */
@Mapper
public interface FxOrgMapper {

    /**
     * 根据机构编码查询机构
     */
    FxOrg selectByOrgCode(String orgCode);

    /**
     * 查询所有机构
     */
    List<FxOrg> selectAll();

    /**
     * 查询指定机构及其所有上级机构（按层级从低到高排序）
     */
    List<FxOrg> selectOrgAndParents(String orgCode);

    /**
     * 查询指定机构及其所有下级机构（含自身，按层级从高到低排序）
     */
    List<FxOrg> selectOrgAndChildren(@Param("orgCode") String orgCode);

    /**
     * 新增机构
     */
    int insert(FxOrg fxOrg);

    /**
     * 更新机构
     */
    int update(FxOrg fxOrg);
}
