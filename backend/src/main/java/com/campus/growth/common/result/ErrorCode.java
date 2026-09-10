package com.campus.growth.common.result;

import lombok.Getter;

/**
 * 业务错误码。
 * <p>分段约定：1xxx 用户、2xxx 签到、3xxx 积分、4xxx 任务、5xxx 商品、
 * 6xxx 优惠券、7xxx 兑换码、8xxx 订单售后、9xxx AI。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),

    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问该资源"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),

    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "账号或密码错误"),
    USER_DISABLED(1003, "账号已被禁用，请联系管理员"),
    USERNAME_EXISTS(1004, "账号已存在"),
    TOKEN_INVALID(1005, "登录状态已失效，请重新登录"),
    USER_LOCKED(1006, "密码错误次数过多，账号已临时锁定"),
    REGISTER_DENIED(1007, "注册受限：邀请码无效或学校不在白名单内"),
    RISK_REJECTED(1008, "操作过于频繁，已被风控拦截，请稍后再试"),
    STUDENT_NO_EXISTS(1009, "该学号已注册过账号"),

    SIGNIN_ALREADY(2001, "今天已经签到过了"),
    SIGNIN_BUSY(2002, "签到请求正在处理中，请稍后再试"),

    POINT_ACCOUNT_NOT_FOUND(3001, "积分账户不存在"),
    POINT_NOT_ENOUGH(3002, "积分余额不足"),

    TASK_NOT_FOUND(4001, "任务不存在或已停用"),
    TASK_NOT_FINISHED(4002, "任务尚未完成，不能领取奖励"),
    TASK_REWARD_CLAIMED(4003, "该任务奖励已领取"),

    GOODS_NOT_FOUND(5001, "权益商品不存在"),
    GOODS_OFF_SHELF(5002, "权益商品已下架"),
    GOODS_STOCK_NOT_ENOUGH(5003, "权益商品库存不足"),

    COUPON_TEMPLATE_NOT_FOUND(6001, "优惠券不存在或已停用"),
    COUPON_SOLD_OUT(6002, "优惠券已被领完"),
    COUPON_LIMIT_EXCEED(6003, "已达到该券的单人领取上限"),
    COUPON_NOT_AVAILABLE(6004, "优惠券不满足使用条件"),
    COUPON_ALREADY_USED(6005, "优惠券已被使用"),
    COUPON_RECEIVE_BUSY(6006, "领券人数较多，请稍后再试"),

    REDEEM_CODE_INVALID(7001, "兑换码无效，请检查后重试"),
    REDEEM_CODE_USED(7002, "该兑换码已被使用"),
    REDEEM_BATCH_INVALID(7003, "兑换码批次不存在或不在有效期内"),
    REDEEM_BUSY(7004, "兑换请求处理中，请稍后再试"),

    ORDER_NOT_FOUND(8001, "订单不存在"),
    ORDER_STATUS_ILLEGAL(8002, "当前订单状态不允许该操作"),
    ORDER_AMOUNT_MISMATCH(8003, "订单金额已变化，请刷新结算页后重试"),
    ORDER_CREATE_BUSY(8004, "下单请求处理中，请稍后再试"),
    ORDER_ITEM_NOT_FOUND(8005, "订单明细不存在"),
    REFUND_NOT_FOUND(8006, "退换单不存在"),
    REFUND_STATUS_ILLEGAL(8007, "当前退换单状态不允许该操作"),
    REFUND_ALREADY_APPLIED(8008, "该订单已申请过售后"),

    AI_NOT_AVAILABLE(9001, "AI 助手暂不可用"),
    AI_TOOL_ERROR(9002, "AI 工具调用失败"),
    AI_SESSION_NOT_FOUND(9003, "会话不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
