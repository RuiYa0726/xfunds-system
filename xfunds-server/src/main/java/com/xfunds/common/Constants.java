package com.xfunds.common;

/**
 * 系统通用常量
 */
public class Constants {

    /** 业务编号前缀：即期 */
    public static final String PREFIX_SPOT = "SP";
    /** 业务编号前缀：远期 */
    public static final String PREFIX_FORWARD = "FW";
    /** 业务编号前缀：掉期 */
    public static final String PREFIX_SWAP = "SW";
    /** 业务编号前缀：期权 */
    public static final String PREFIX_OPTION = "OP";

    /** 默认版本号 */
    public static final int DEFAULT_VERSION = 0;

    /** 启用状态 */
    public static final String STATUS_ACTIVE = "1";
    /** 停用状态 */
    public static final String STATUS_INACTIVE = "0";

    /** 是 */
    public static final String YES = "Y";
    /** 否 */
    public static final String NO = "N";

    /** 审批节点：经办 */
    public static final String NODE_MAKE = "MAKE";
    /** 审批节点：复核 */
    public static final String NODE_CHECK = "CHECK";
    /** 审批节点：授权 */
    public static final String NODE_AUTHORIZE = "AUTHORIZE";

    /** 审批决定：通过 */
    public static final String DECISION_APPROVE = "APPROVE";
    /** 审批决定：拒绝 */
    public static final String DECISION_REJECT = "REJECT";
    /** 审批决定：退回 */
    public static final String DECISION_RETURN = "RETURN";

    private Constants() {
    }
}
