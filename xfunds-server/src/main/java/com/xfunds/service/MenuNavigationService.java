package com.xfunds.service;

import com.xfunds.dto.MenuNavigationResponse;

/**
 * 菜单导航服务接口
 */
public interface MenuNavigationService {

    /**
     * 根据用户输入查询菜单路径（支持多轮澄清）
     * @param query    用户输入的查询文本
     * @param sessionId 会话ID，用于维持多轮对话上下文
     * @return 匹配的菜单导航结果，或需要澄清的反问结果
     */
    MenuNavigationResponse searchMenuPath(String query, String sessionId);
}
