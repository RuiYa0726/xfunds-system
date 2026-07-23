package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.dto.PageResponse;
import com.xfunds.dto.UserSaveRequest;
import com.xfunds.dto.UserVO;
import com.xfunds.service.FxUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户管理控制器
 */
@RestController
@RequestMapping("/api/system/user")
public class FxUserController {

    @Autowired
    private FxUserService fxUserService;

    /**
     * 分页查询用户列表（支持用户名和机构编码筛选）
     */
    @GetMapping("/list")
    public Result<PageResponse<UserVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String username,
                                             @RequestParam(required = false) String orgCode) {
        return Result.ok(fxUserService.listPage(username, orgCode, pageNum, pageSize));
    }

    /**
     * 根据用户ID查询用户详情（含角色）
     */
    @GetMapping("/{userId}")
    public Result<UserVO> getByUserId(@PathVariable Long userId) {
        return Result.ok(fxUserService.getUserDetail(userId));
    }

    /**
     * 新增或更新用户
     */
    @PostMapping("/save")
    public Result<Long> save(@RequestBody UserSaveRequest request) {
        return Result.ok(fxUserService.saveUser(request));
    }

    /**
     * 重置用户密码
     */
    @PostMapping("/{userId}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long userId, @RequestBody UserSaveRequest request) {
        fxUserService.resetPassword(userId, request.getPassword());
        return Result.ok();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable Long userId) {
        fxUserService.deleteUser(userId);
        return Result.ok();
    }
}
