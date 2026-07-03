package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.entity.FxRole;
import com.xfunds.mapper.FxRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理控制器（提供角色下拉列表）
 */
@RestController
@RequestMapping("/api/system/role")
public class FxRoleController {

    @Autowired
    private FxRoleMapper fxRoleMapper;

    /**
     * 查询所有角色列表
     */
    @GetMapping("/list")
    public Result<List<FxRole>> list() {
        return Result.ok(fxRoleMapper.selectAll());
    }
}
