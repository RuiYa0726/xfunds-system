package com.xfunds.mapper;

import com.xfunds.entity.FxRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 角色 Mapper 接口
 */
@Mapper
public interface FxRoleMapper {

    /**
     * 查询所有角色
     */
    List<FxRole> selectAll();

    /**
     * 根据角色编码查询角色
     */
    FxRole selectByRoleCode(String roleCode);
}
