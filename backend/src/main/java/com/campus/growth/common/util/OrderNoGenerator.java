package com.campus.growth.common.util;

import com.campus.growth.common.constant.BizConst;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单号生成器。
 * <p>格式：{@code 前缀 + yyMMddHHmmss + 4 位进程内自增 + 4 位随机}，共 22 位。</p>
 * <p>说明：不引入雪花算法。单机 QPS 上万时同一秒内的自增位也不会耗尽，
 * 且所有单号表都有唯一索引兜底，重复即报错重试，不依赖时钟回拨处理。</p>
 */
public final class OrderNoGenerator {

    private OrderNoGenerator() {
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    public static String orderNo() {
        return generate(BizConst.ORDER_NO_PREFIX);
    }

    public static String refundNo() {
        return generate(BizConst.REFUND_NO_PREFIX);
    }

    public static String couponCode() {
        // 券码用 Base32 随机段，长度固定 16，便于用户展示与复制
        return BizConst.COUPON_CODE_PREFIX + Base32Codec.random(13);
    }

    public static String templateCode() {
        return BizConst.TEMPLATE_CODE_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + Base32Codec.random(4);
    }

    /** 批次号：RCB + yyyyMMdd + 6 位随机 Base32（对应"时间戳 + UUID 随机段"的分段设计） */
    public static String redeemBatchNo() {
        return BizConst.BATCH_NO_PREFIX
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + Base32Codec.random(6);
    }

    public static String eventId() {
        return "EV" + System.currentTimeMillis() + Base32Codec.random(4);
    }

    public static String sessionId() {
        return "AS" + System.currentTimeMillis() + Base32Codec.random(4);
    }

    private static String generate(String prefix) {
        int seq = Math.floorMod(SEQUENCE.getAndIncrement(), 10000);
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return prefix + LocalDateTime.now().format(FMT) + String.format("%04d", seq) + random;
    }
}
