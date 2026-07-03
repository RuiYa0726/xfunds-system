package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.Constants;
import com.xfunds.common.ResultCode;
import com.xfunds.dto.PageResponse;
import com.xfunds.dto.UserSaveRequest;
import com.xfunds.dto.UserVO;
import com.xfunds.entity.FxUser;
import com.xfunds.entity.FxUserRole;
import com.xfunds.mapper.FxUserMapper;
import com.xfunds.mapper.FxUserRoleMapper;
import com.xfunds.service.FxUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户服务实现类
 */
@Service
public class FxUserServiceImpl implements FxUserService {

    @Autowired
    private FxUserMapper fxUserMapper;

    @Autowired
    private FxUserRoleMapper fxUserRoleMapper;

    /**
     * 根据用户名查询用户
     */
    @Override
    public FxUser getByUsername(String username) {
        return fxUserMapper.selectByUsername(username);
    }

    /**
     * 根据用户ID查询用户
     */
    @Override
    public FxUser getByUserId(Long userId) {
        return fxUserMapper.selectByUserId(userId);
    }

    /**
     * 查询所有用户
     */
    @Override
    public List<FxUser> listAll() {
        return fxUserMapper.selectAll();
    }

    /**
     * 分页查询用户列表，返回不含密码的用户视图对象
     */
    @Override
    public PageResponse<UserVO> listPage(String username, String orgCode, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        long total = fxUserMapper.countByCondition(username, orgCode);
        List<FxUser> users = fxUserMapper.selectByCondition(username, orgCode, offset, pageSize);
        List<UserVO> voList = new ArrayList<>();
        for (FxUser user : users) {
            voList.add(convertToVO(user));
        }
        return new PageResponse<>(total, pageNum, pageSize, voList);
    }

    /**
     * 获取用户详情（含角色编码和角色ID列表）
     */
    @Override
    public UserVO getUserDetail(Long userId) {
        FxUser user = fxUserMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return convertToVO(user);
    }

    /**
     * 新增或更新用户，同时维护用户角色关联（多表操作需事务）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveUser(UserSaveRequest request) {
        FxUser user = new FxUser();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setOrgCode(request.getOrgCode());
        user.setStatus(request.getStatus() != null ? request.getStatus() : Constants.STATUS_ACTIVE);

        if (request.getUserId() == null) {
            // 新增用户：校验用户名唯一
            FxUser existUser = fxUserMapper.selectByUsername(request.getUsername());
            if (existUser != null) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户名已存在");
            }
            // 新增时密码必填
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "新增用户时密码不能为空");
            }
            user.setPassword(request.getPassword());
            fxUserMapper.insert(user);
        } else {
            // 更新用户
            user.setUserId(request.getUserId());
            fxUserMapper.update(user);
        }

        // 维护用户角色关联：先删除旧关联，再插入新关联
        if (request.getRoleIds() != null) {
            fxUserRoleMapper.deleteByUserId(user.getUserId());
            for (Long roleId : request.getRoleIds()) {
                FxUserRole userRole = new FxUserRole();
                userRole.setUserId(user.getUserId());
                userRole.setRoleId(roleId);
                fxUserRoleMapper.insert(userRole);
            }
        }
        return user.getUserId();
    }

    /**
     * 重置用户密码
     */
    @Override
    public void resetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能为空");
        }
        fxUserMapper.updatePassword(userId, newPassword);
    }

    /**
     * 删除用户：先清理用户角色关联，再删除用户（多表操作需事务）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        FxUser user = fxUserMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 先清理用户角色关联
        fxUserRoleMapper.deleteByUserId(userId);
        // 再删除用户
        fxUserMapper.deleteByUserId(userId);
    }

    /**
     * 将用户实体转换为视图对象（不含密码，含角色信息）
     */
    private UserVO convertToVO(FxUser user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setOrgCode(user.getOrgCode());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        // 查询角色编码列表
        List<String> roleCodes = fxUserRoleMapper.selectRoleCodesByUserId(user.getUserId());
        vo.setRoles(roleCodes);
        // 查询角色ID列表
        List<FxUserRole> userRoles = fxUserRoleMapper.selectByUserId(user.getUserId());
        List<Long> roleIds = new ArrayList<>();
        for (FxUserRole ur : userRoles) {
            roleIds.add(ur.getRoleId());
        }
        vo.setRoleIds(roleIds);
        return vo;
    }
}
