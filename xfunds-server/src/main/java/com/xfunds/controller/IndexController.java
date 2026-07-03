package com.xfunds.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端页面控制器
 */
@Controller
public class IndexController {

    /**
     * 首页和所有前端路由都返回 index.html
     */
    @GetMapping(value = {"/", "/login", "/home", "/fx/**", "/option/**", "/system/**"})
    public String index() {
        return "forward:/index.html";
    }
}
