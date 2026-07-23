package com.xfunds.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfunds.dto.MenuNavigationResponse;
import com.xfunds.service.MenuNavigationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜单导航服务实现
 * <p>
 * 支持多轮上下文感知澄清：当用户输入只明确了部分维度（交易类型 / 子交易类型），
 * 且候选菜单存在多个不同菜单路径时，系统会反问缺失的维度，并将多轮答案联合后定位菜单。
 */
@Service
public class MenuNavigationServiceImpl implements MenuNavigationService {

    private static final Logger logger = LoggerFactory.getLogger(MenuNavigationServiceImpl.class);

    /** 会话上下文空闲超时时间（毫秒），超过则视为新会话 */
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000L;

    /** 澄清维度：交易类型 */
    private static final String FIELD_TRADE_TYPE = "TRADE_TYPE";
    /** 澄清维度：子交易类型 */
    private static final String FIELD_SUB_TYPE = "SUB_TYPE";

    private final ObjectMapper objectMapper;

    private List<MenuKnowledgeItem> knowledgeBase = new ArrayList<>();

    /** 交易类型 -> 候选项索引（用于反问时给出可选项） */
    private final List<String> tradeTypeOrder = Arrays.asList("即期", "远期", "掉期", "期权");

    /** 会话上下文：sessionId -> ConversationContext */
    private final Map<String, ConversationContext> sessionContexts = new ConcurrentHashMap<>();

    public MenuNavigationServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("menu_knowledge.json");
            InputStream inputStream = resource.getInputStream();
            knowledgeBase = objectMapper.readValue(inputStream, new TypeReference<List<MenuKnowledgeItem>>() {});
            buildRouteMapping();
            logger.info("菜单知识库加载完成，共加载 {} 条记录", knowledgeBase.size());
        } catch (IOException e) {
            logger.error("加载菜单知识库失败", e);
        }
    }

    private void buildRouteMapping() {
        Map<String, String> menuToRoute = new HashMap<>();
        menuToRoute.put("外汇交易管理-外汇工作台-即期牌价", "/fx/workbench");
        menuToRoute.put("外汇交易管理-外汇工作台-远期牌价", "/fx/workbench");
        menuToRoute.put("外汇交易管理-外汇工作台-掉期牌价", "/fx/workbench");
        menuToRoute.put("外汇交易管理-未到期交易管理-远期未到期", "/fx/unmatured");
        menuToRoute.put("外汇交易管理-未到期交易管理-掉期未到期交易管理（远端）", "/fx/unmatured");
        menuToRoute.put("外汇交易管理-客户交易查询", "/fx/customer-query");
        menuToRoute.put("外汇交易管理-待办任务", "/fx/todo");
        menuToRoute.put("期权交易管理-期权工作台", "/option/workbench");
        menuToRoute.put("期权交易管理-期权交易录入", "/option/entry");
        menuToRoute.put("期权交易管理-期权交易复核", "/option/review");
        menuToRoute.put("期权交易管理-存续期管理", "/option/lifecycle");
        menuToRoute.put("期权交易管理-期权交易查询", "/option/query");
        menuToRoute.put("公共管理-系统参数管理", "/system/param");
        menuToRoute.put("公共管理-客户管理", "/system/customer");
        menuToRoute.put("公共管理-登录用户管理", "/system/user");
        menuToRoute.put("公共管理-定时任务", "/system/task");

        for (MenuKnowledgeItem item : knowledgeBase) {
            if (item.getEnabled()) {
                String key = String.join("-", item.getMenuPath());
                String routePath = menuToRoute.get(key);
                if (routePath != null) {
                    item.setRoutePath(routePath);
                } else {
                    String partialKey = key.substring(0, key.lastIndexOf("-"));
                    routePath = menuToRoute.get(partialKey);
                    if (routePath != null) {
                        item.setRoutePath(routePath);
                    }
                }
            }
        }
    }

    // ===================== 维度识别 =====================

    /**
     * 识别用户输入中包含的交易类型。
     * 返回匹配到的交易类型，若无法识别返回 null。
     */
    private String detectTradeType(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        // 注意“远期”包含“期”，需先匹配“远期”再匹配“期权”，最后“即期”/“掉期”
        if (query.contains("远期")) {
            return "远期";
        }
        if (query.contains("期权")) {
            return "期权";
        }
        if (query.contains("即期")) {
            return "即期";
        }
        if (query.contains("掉期")) {
            return "掉期";
        }
        return null;
    }

    /**
     * 识别用户输入中包含的子交易类型。
     * 由于“展期”可对应原价展期/市价展期，返回的是候选集合。
     */
    private Set<String> detectSubTypes(String query) {
        Set<String> result = new LinkedHashSet<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        // 先匹配更具体的词，再匹配通用词，避免误判
        if (query.contains("提前交割") || query.contains("交割") || query.contains("平仓")) {
            result.add("提前交割");
        }
        if (query.contains("提前违约") || query.contains("违约") || query.contains("终止")
                || query.contains("取消") || query.contains("撤销")) {
            result.add("提前违约");
        }
        if (query.contains("原价展期")) {
            result.add("原价展期");
        }
        if (query.contains("市价展期")) {
            result.add("市价展期");
        }
        if (query.contains("展期") || query.contains("续期")) {
            result.add("原价展期");
            result.add("市价展期");
        }
        if (query.contains("结售汇") || query.contains("结汇") || query.contains("购汇")
                || query.contains("牌价") || query.contains("买卖外汇") || query.contains("买卖")) {
            result.add("结售汇");
        }
        if (query.contains("期权交易录入") || query.contains("录入") || query.contains("开仓")
                || query.contains("发起期权") || query.contains("做一笔期权")) {
            result.add("期权交易录入");
        }
        if (query.contains("行权") || query.contains("行使")) {
            result.add("期权行权");
        }
        if (query.contains("放弃") || query.contains("作废") || query.contains("不行权")) {
            result.add("期权放弃");
        }
        if (query.contains("保证金")) {
            result.add("保证金账户维护");
        }
        return result;
    }

    // ===================== 主流程 =====================

    @Override
    public MenuNavigationResponse searchMenuPath(String query, String sessionId) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        String normalizedQuery = query.trim();
        String sid = (sessionId == null || sessionId.trim().isEmpty()) ? UUID.randomUUID().toString() : sessionId.trim();

        ConversationContext context = getOrCreateContext(sid);
        context.lastAccessTime = System.currentTimeMillis();

        // 1. 若当前处于待澄清状态，把本次输入当作对反问的回答，补全缺失维度
        if (context.pendingField != null) {
            String pending = context.pendingField;
            context.pendingField = null;
            boolean filled = false;
            if (FIELD_TRADE_TYPE.equals(pending)) {
                String tt = detectTradeType(normalizedQuery);
                if (tt != null) {
                    context.detectedTradeType = tt;
                    filled = true;
                }
            } else if (FIELD_SUB_TYPE.equals(pending)) {
                Set<String> st = detectSubTypes(normalizedQuery);
                if (!st.isEmpty()) {
                    // 合并到已识别的子类型集合（用户回答可能对应多个，如“展期”->原价/市价展期）
                    context.detectedSubTypes.addAll(st);
                    filled = true;
                }
            }
            if (!filled) {
                // 用户回答未能识别出对应维度：放弃上一轮上下文，把本次输入当作全新查询，避免死循环
                context.detectedTradeType = detectTradeType(normalizedQuery);
                context.detectedSubTypes = new LinkedHashSet<>(detectSubTypes(normalizedQuery));
            }
        } else {
            // 2. 全新输入：识别两个维度
            context.detectedTradeType = detectTradeType(normalizedQuery);
            context.detectedSubTypes = new LinkedHashSet<>(detectSubTypes(normalizedQuery));
            context.lastQuery = normalizedQuery;
        }

        // 3. 先尝试高置信度关键词匹配（输入与某个关键词完全相等时直接命中，跳过反问）
        MenuKnowledgeItem highConfidence = findHighConfidenceMatch(normalizedQuery);
        if (highConfidence != null) {
            sessionContexts.remove(sid);
            return convertToResponse(highConfidence);
        }

        // 3.1 两个维度都未识别且无任何关键词关联：视为无法识别
        if (context.detectedTradeType == null && context.detectedSubTypes.isEmpty()
                && !hasAnyKeywordRelation(normalizedQuery)) {
            sessionContexts.remove(sid);
            return null;
        }

        // 4. 按已识别维度过滤候选
        List<MenuKnowledgeItem> candidates = filterByDimensions(context.detectedTradeType, context.detectedSubTypes);

        if (candidates.isEmpty()) {
            // 5a. 没有候选：清空上下文，返回未识别
            sessionContexts.remove(sid);
            return null;
        }

        if (candidates.size() == 1) {
            // 5b. 唯一命中：返回菜单
            sessionContexts.remove(sid);
            return convertToResponse(candidates.get(0));
        }

        // 6. 多个候选：判断是否仍存在不同菜单路径
        long distinctMenuCount = candidates.stream()
                .map(it -> String.join("-", it.getMenuPath()))
                .distinct()
                .count();

        if (distinctMenuCount == 1) {
            // 6a. 候选虽多但都指向同一菜单（如远期原价展期/市价展期均到“远期未到期”），直接返回
            sessionContexts.remove(sid);
            return convertToResponse(candidates.get(0));
        }

        // 6b. 候选指向不同菜单：需要反问缺失维度
        List<String> distinctTradeTypes = candidates.stream()
                .map(MenuKnowledgeItem::getTradeType)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted(Comparator.comparingInt(t -> tradeTypeOrder.indexOf(t)))
                .toList();

        List<String> distinctSubTypes = candidates.stream()
                .map(MenuKnowledgeItem::getSubType)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (context.detectedTradeType == null && distinctTradeTypes.size() > 1) {
            // 交易类型缺失且候选覆盖多种交易类型 -> 反问交易类型
            context.pendingField = FIELD_TRADE_TYPE;
            return buildClarificationResponse(
                    "您提到的业务在多种交易类型下都存在，请问是" + joinOptions(distinctTradeTypes) + "中的哪一种？",
                    distinctTradeTypes,
                    FIELD_TRADE_TYPE,
                    context);
        }

        if (context.detectedSubTypes.isEmpty() && distinctSubTypes.size() > 1) {
            // 子交易类型缺失且候选覆盖多种子类型 -> 反问子类型
            String prefix = context.detectedTradeType != null ? context.detectedTradeType : "";
            context.pendingField = FIELD_SUB_TYPE;
            return buildClarificationResponse(
                    "请问您具体需要做" + prefix + "什么交易？可选：" + joinOptions(distinctSubTypes),
                    distinctSubTypes,
                    FIELD_SUB_TYPE,
                    context);
        }

        // 7. 兜底：两个维度都已识别但仍有多个候选（理论上不该出现），按关键词得分取最优
        MenuKnowledgeItem best = pickBestByKeywordScore(candidates, normalizedQuery);
        sessionContexts.remove(sid);
        return best != null ? convertToResponse(best) : null;
    }

    // ===================== 辅助方法 =====================

    private ConversationContext getOrCreateContext(String sessionId) {
        // 顺便清理过期上下文
        long now = System.currentTimeMillis();
        sessionContexts.entrySet().removeIf(e -> now - e.getValue().lastAccessTime > SESSION_TIMEOUT_MS);
        return sessionContexts.computeIfAbsent(sessionId, k -> new ConversationContext());
    }

    /**
     * 高置信度匹配：仅当输入与某条记录的某个关键词“完全相等”时命中。
     * 这样“我要做美元即期结售汇”等完整业务短语可一次性返回，无需反问；
     * 而“远期”“违约”这类短词不会触发（它们只是关键词的子串），从而进入维度澄清流程。
     */
    private MenuKnowledgeItem findHighConfidenceMatch(String query) {
        String normalizedQuery = query.toLowerCase();
        for (MenuKnowledgeItem item : knowledgeBase) {
            if (!item.getEnabled()) {
                continue;
            }
            for (String keyword : item.getKeywords()) {
                if (keyword.toLowerCase().equals(normalizedQuery)) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * 判断输入是否与任意关键词存在子串关联（任一方向包含）。
     * 用于在两个维度都未识别时，区分“相关但模糊”与“完全无关”的输入。
     */
    private boolean hasAnyKeywordRelation(String query) {
        String normalizedQuery = query.toLowerCase();
        for (MenuKnowledgeItem item : knowledgeBase) {
            if (!item.getEnabled()) {
                continue;
            }
            for (String keyword : item.getKeywords()) {
                String lowerKeyword = keyword.toLowerCase();
                if (lowerKeyword.contains(normalizedQuery) || normalizedQuery.contains(lowerKeyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<MenuKnowledgeItem> filterByDimensions(String tradeType, Set<String> subTypes) {
        List<MenuKnowledgeItem> result = new ArrayList<>();
        for (MenuKnowledgeItem item : knowledgeBase) {
            if (!item.getEnabled()) {
                continue;
            }
            if (tradeType != null && !tradeType.equals(item.getTradeType())) {
                continue;
            }
            // 子类型集合非空时，候选的子类型必须在集合内（支持“展期”->原价/市价展期这类多映射）
            if (subTypes != null && !subTypes.isEmpty() && !subTypes.contains(item.getSubType())) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private MenuKnowledgeItem pickBestByKeywordScore(List<MenuKnowledgeItem> items, String query) {
        String normalizedQuery = query.toLowerCase();
        int maxScore = -1;
        MenuKnowledgeItem best = null;
        for (MenuKnowledgeItem item : items) {
            int score = calculateScore(item, normalizedQuery);
            if (score > maxScore) {
                maxScore = score;
                best = item;
            }
        }
        return best;
    }

    private String joinOptions(List<String> options) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(options.get(i));
        }
        return sb.toString();
    }

    private MenuNavigationResponse buildClarificationResponse(String question, List<String> options,
                                                              String field, ConversationContext context) {
        MenuNavigationResponse response = new MenuNavigationResponse();
        response.setNeedClarification(true);
        response.setClarificationQuestion(question);
        response.setClarificationOptions(options);
        response.setClarificationField(field);
        response.setMatchedTradeType(context.detectedTradeType);
        response.setMatchedSubType(context.detectedSubTypes.isEmpty() ? null
                : String.join("、", context.detectedSubTypes));
        return response;
    }

    private int calculateScore(MenuKnowledgeItem item, String query) {
        int score = 0;

        for (String keyword : item.getKeywords()) {
            String lowerKeyword = keyword.toLowerCase();
            if (lowerKeyword.equals(query)) {
                score += 10;
            } else if (lowerKeyword.contains(query)) {
                score += 5;
            } else if (query.contains(lowerKeyword)) {
                score += 3;
            }
        }

        if (item.getTradeType() != null && !item.getTradeType().isEmpty()) {
            if (query.contains(item.getTradeType().toLowerCase())) {
                score += 2;
            }
        }

        if (item.getSubType() != null && !item.getSubType().isEmpty()) {
            if (query.contains(item.getSubType().toLowerCase())) {
                score += 2;
            }
        }

        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            if (query.contains(item.getDescription().toLowerCase())) {
                score += 2;
            }
        }

        return score;
    }

    private MenuNavigationResponse convertToResponse(MenuKnowledgeItem item) {
        MenuNavigationResponse response = new MenuNavigationResponse();
        response.setId(item.getId());
        response.setTradeType(item.getTradeType());
        response.setSubType(item.getSubType());
        response.setDescription(item.getDescription());
        response.setMenuPath(item.getMenuPath());
        response.setRoutePath(item.getRoutePath());
        response.setNeedClarification(false);
        return response;
    }

    // ===================== 内部数据结构 =====================

    /**
     * 会话上下文，记录多轮澄清过程中已识别的维度。
     */
    private static class ConversationContext {
        String detectedTradeType;
        /** 本次输入识别出的子类型集合（如“展期”->原价展期+市价展期） */
        Set<String> detectedSubTypes = new LinkedHashSet<>();
        /** 当前待用户回答的维度：TRADE_TYPE / SUB_TYPE / null */
        String pendingField;
        String lastQuery;
        long lastAccessTime = System.currentTimeMillis();
    }

    public static class MenuKnowledgeItem {
        private String id;
        private List<String> menuPath;
        private String tradeType;
        private String subType;
        private String description;
        private List<String> keywords;
        private boolean enabled;
        private String routePath;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getMenuPath() { return menuPath; }
        public void setMenuPath(List<String> menuPath) { this.menuPath = menuPath; }
        public String getTradeType() { return tradeType; }
        public void setTradeType(String tradeType) { this.tradeType = tradeType; }
        public String getSubType() { return subType; }
        public void setSubType(String subType) { this.subType = subType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public boolean getEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getRoutePath() { return routePath; }
        public void setRoutePath(String routePath) { this.routePath = routePath; }
    }
}
