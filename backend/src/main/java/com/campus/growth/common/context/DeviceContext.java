package com.campus.growth.common.context;

/**
 * 设备标识上下文（由 {@code DeviceIdFilter} 从请求头 {@code X-Device-Id} 写入）。
 *
 * <h3>为什么需要设备维度</h3>
 * <p>校园权益平台的薅羊毛不是"一个人狂点"，而是"一个人开一堆小号"。
 * 只按 IP 做风控会把整个宿舍楼（同一个 NAT 出口）一起误伤，所以真正的判据是**设备**：
 * 前端首次访问生成一个 UUID 存在 localStorage，之后每个请求都带 {@code X-Device-Id}。</p>
 *
 * <p>请求头缺失时不放行、也不跳过，而是归到 {@code nodev:{ip}} 这个桶里——
 * 否则攻击者只要不发这个头就绕过了所有设备维度限制。</p>
 */
public final class DeviceContext {

    private static final ThreadLocal<String> DEVICE_ID = new ThreadLocal<>();

    private DeviceContext() {
    }

    public static void set(String deviceId) {
        DEVICE_ID.set(deviceId);
    }

    /** 原始设备号，可能为 null */
    public static String get() {
        return DEVICE_ID.get();
    }

    public static void clear() {
        DEVICE_ID.remove();
    }
}
