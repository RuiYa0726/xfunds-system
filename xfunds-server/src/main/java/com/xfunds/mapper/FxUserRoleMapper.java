package com.xfunds.mapper;

import com.xfunds.entity.FxUserRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户角色关联 Mapper 接口
 */
@Mapper
public interface FxUserRoleMapper {

    /**
     * 根据用户ID查询角色关联
     */
    List<FxUserRole> selectByUserId(Long userId);

    /**
     * 新增用户角色关联
     */
    int insert(FxUserRole fxUserRole);

    /**
     * 根据用户ID删除角色关联
     */
    int deleteByUserId(Long userId);

    /**
     * 根据用户ID查询角色编码列表（关联角色表）
     */
    List<String> selectRoleCodesByUserId(Long userId);
}
