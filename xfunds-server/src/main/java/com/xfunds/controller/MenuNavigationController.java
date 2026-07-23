package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.dto.MenuNavigationResponse;
import com.xfunds.service.MenuNavigationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单导航控制器
 */
@RestController
@RequestMapping("/api/menu-nav")
public class MenuNavigationController {

    private final MenuNavigationService menuNavigationService;

    public MenuNavigationController(MenuNavigationService menuNavigationService) {
        this.menuNavigationService = menuNavigationService;
    }

    /**
     * 根据用户输入查询菜单路径（支持多轮澄清）
     */
    @GetMapping("/search")
    public Result<MenuNavigationResponse> search(@RequestParam String query,
                                                 @RequestParam(required = false) String sessionId) {
        MenuNavigationResponse response = menuNavigationService.searchMenuPath(query, sessionId);
        return Result.ok(response);
    }
}