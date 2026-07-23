package com.xfunds.controller;

import com.xfunds.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 健康检查接口
     */
    @GetMapping
    public Result<String> health() {
        return Result.ok("running");
    }
}
