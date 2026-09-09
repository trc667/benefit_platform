package com.campus.growth.common.constant;

/**
 * 业务常量。
 */
public final class BizConst {

    private BizConst() {
    }

    /** 通用状态 */
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    /** 逻辑删除 */
    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;

    /** 角色 */
    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_ADMIN = "ADMIN";

    /** 请求头 */
    public static final String HEADER_TOKEN = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 业务键前缀 */
    public static final String BIZ_SIGNIN = "SIGNIN";
    public static final String BIZ_TASK = "TASK";
    public static final String BIZ_ORDER_PAY = "ORDER_PAY";
    public static final String BIZ_ORDER_REFUND = "ORDER_REFUND";
    public static final String BIZ_REDEEM = "REDEEM";
    public static final String BIZ_ADMIN = "ADMIN";

    /** 兑换码：单批最大容量（2^31 - 1，配合 Base32 分段编码支持 20 亿级） */
    public static final long REDEEM_MAX_CAPACITY = 2147483647L;

    /** 订单号前缀 */
    public static final String ORDER_NO_PREFIX = "CG";
    public static final String REFUND_NO_PREFIX = "RF";
    public static final String COUPON_CODE_PREFIX = "UCP";
    public static final String TEMPLATE_CODE_PREFIX = "CT";
    public static final String BATCH_NO_PREFIX = "RCB";

    /** 分页保护：防止深翻页拖垮数据库 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 结算页最多参与计算的优惠券张数（超过则按面额剪枝） */
    public static final int MAX_COUPON_COMBINATION_SIZE = 3;
    public static final int MAX_COUPON_ENUMERATE = 10;

    /** 积分等级：每 1000 分一级 */
    public static final int POINT_PER_LEVEL = 1000;
}
