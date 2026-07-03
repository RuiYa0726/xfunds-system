package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.entity.FxOptionParam;
import com.xfunds.mapper.FxOptionParamMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 期权参数管理控制器
 */
@RestController
@RequestMapping("/api/option/param")
public class OptionParamController {

    @Autowired
    private FxOptionParamMapper fxOptionParamMapper;

    /**
     * 查询所有期权参数
     */
    @GetMapping("/list")
    public Result<List<FxOptionParam>> list() {
        return Result.ok(fxOptionParamMapper.selectAll());
    }

    /**
     * 更新期权参数
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody FxOptionParam param) {
        fxOptionParamMapper.update(param);
        return Result.ok();
    }

    /**
     * 根据参数编码查询期权参数
     */
    @GetMapping("/{paramCode}")
    public Result<FxOptionParam> getByCode(@PathVariable String paramCode) {
        return Result.ok(fxOptionParamMapper.selectByParamCode(paramCode));
    }
}
