package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.entity.FxSysParam;
import com.xfunds.service.SysParamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 系统参数管理控制器
 */
@RestController
@RequestMapping("/api/system/param")
public class SysParamController {

    @Autowired
    private SysParamService sysParamService;

    /**
     * 查询所有系统参数
     */
    @GetMapping("/list")
    public Result<List<FxSysParam>> list() {
        return Result.ok(sysParamService.listAll());
    }

    /**
     * 更新系统参数
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody FxSysParam param) {
        sysParamService.save(param);
        return Result.ok();
    }

    /**
     * 获取当前系统业务日期
     */
    @GetMapping("/date")
    public Result<Map<String, Object>> getDate() {
        LocalDate date = sysParamService.getBusinessDate();
        return Result.ok(Map.of("businessDate", date.toString()));
    }

    /**
     * 更新系统业务日期
     */
    @PostMapping("/date")
    public Result<Void> updateDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        sysParamService.updateBusinessDate(date);
        return Result.ok();
    }
}
