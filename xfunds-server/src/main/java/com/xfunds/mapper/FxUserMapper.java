package com.xfunds.mapper;

import com.xfunds.entity.FxUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper 接口
 */
@Mapper
public interface FxUserMapper {

    /**
     * 根据用户名查询用户
     */
    FxUser selectByUsername(String username);

    /**
     * 根据用户ID查询用户
     */
    FxUser selectByUserId(Long userId);

    /**
     * 查询所有用户
     */
    List<FxUser> selectAll();

    /**
     * 新增用户
     */
    int insert(FxUser fxUser);

    /**
     * 更新用户（不含密码）
     */
    int update(FxUser fxUser);

    /**
     * 更新用户密码
     */
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    /**
     * 根据用户ID删除用户
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 按条件查询用户列表（分页）
     */
    List<FxUser> selectByCondition(@Param("username") String username, @Param("orgCode") String orgCode,
                                   @Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 按条件查询用户总数
     */
    long countByCondition(@Param("username") String username, @Param("orgCode") String orgCode);
}
