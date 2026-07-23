package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.dto.OrgSaveRequest;
import com.xfunds.dto.OrgTreeVO;
import com.xfunds.entity.FxOrg;
import com.xfunds.service.FxOrgService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统机构管理控制器
 */
@RestController
@RequestMapping("/api/system/org")
public class FxOrgController {

    @Autowired
    private FxOrgService fxOrgService;

    /**
     * 查询机构树形结构
     */
    @GetMapping("/tree")
    public Result<List<OrgTreeVO>> tree() {
        return Result.ok(fxOrgService.listTree());
    }

    /**
     * 查询所有机构（扁平列表）
     */
    @GetMapping("/list")
    public Result<List<FxOrg>> list() {
        return Result.ok(fxOrgService.listAll());
    }

    /**
     * 查询当前登录用户所属机构及其所有下级机构（含自身，扁平列表）
     * 用于下拉选择"本机构及以下层级机构"
     */
    @GetMapping("/my-with-children")
    public Result<List<FxOrg>> myWithChildren() {
        return Result.ok(fxOrgService.listMyOrgAndChildren());
    }

    /**
     * 新增或更新机构
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody OrgSaveRequest request) {
        fxOrgService.saveOrg(request);
        return Result.ok();
    }
}
