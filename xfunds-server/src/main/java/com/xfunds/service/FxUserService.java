package com.xfunds.service;

import com.xfunds.dto.PageResponse;
import com.xfunds.dto.UserSaveRequest;
import com.xfunds.dto.UserVO;
import com.xfunds.entity.FxUser;

import java.util.List;

/**
 * 用户服务接口
 */
public interface FxUserService {

    /**
     * 根据用户名查询用户
     */
    FxUser getByUsername(String username);

    /**
     * 根据用户ID查询用户
     */
    FxUser getByUserId(Long userId);

    /**
     * 查询所有用户
     */
    List<FxUser> listAll();

    /**
     * 分页查询用户列表
     */
    PageResponse<UserVO> listPage(String username, String orgCode, int pageNum, int pageSize);

    /**
     * 获取用户详情（含角色）
     */
    UserVO getUserDetail(Long userId);

    /**
     * 新增或更新用户（含角色关联）
     */
    Long saveUser(UserSaveRequest request);

    /**
     * 重置用户密码
     */
    void resetPassword(Long userId, String newPassword);

    /**
     * 删除用户（同时清理用户角色关联）
     */
    void deleteUser(Long userId);
}
