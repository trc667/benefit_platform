package com.campus.growth.common.util;

import lombok.Getter;

/**
 * 兑换码生成 / 解析算法。
 *
 * <h3>码结构（12 个 Base32 字符 = 60 bit）</h3>
 * <pre>
 * ┌───────────────┬──────────────────────┬────────────┐
 * │ 批次签名 20bit │ 序号 35bit            │ 校验 5bit   │
 * │ 4 字符         │ 7 字符                │ 1 字符      │
 * └───────────────┴──────────────────────┴────────────┘
 * </pre>
 *
 * <ul>
 *   <li><b>批次签名</b>：由 batchNo 做 FNV-1a 稳定哈希取低 20 位，用于把码与批次绑定，防止跨批次串码；
 *       因为是纯函数计算，重启后依然能解析，无需额外存储映射；</li>
 *   <li><b>序号</b>：35 bit 容量 ≈ 343 亿，远超需求中的 20 亿级；序号直接作为 Redis BitMap 的 offset，
 *       核销状态查询/写入都是 O(1)；</li>
 *   <li><b>校验位</b>：(签名 * 31 + 序号) &amp; 0x1F，用于拦截手抄错码，避免无谓的 Redis 查询。</li>
 * </ul>
 *
 * <p>为什么不做全量预生成：20 亿个码若落库，仅存储就是不可接受的成本；
 * 本方案"按需生成 + 可反解"，数据库只记录真正被核销过的码。</p>
 */
public final class RedeemCodeUtil {

    private RedeemCodeUtil() {
    }

    /** 批次签名位数 */
    public static final int BATCH_SIG_BITS = 20;
    /** 序号位数（35 bit ≈ 343 亿） */
    public static final int SEQ_BITS = 35;
    /** 码总长度 */
    public static final int CODE_LENGTH = 12;

    private static final int SIG_CHARS = Base32Codec.charCountForBits(BATCH_SIG_BITS); // 4
    private static final int SEQ_CHARS = Base32Codec.charCountForBits(SEQ_BITS);       // 7
    private static final int CHECK_CHARS = 1;
    /** 单批最大容量（2^35 - 1，受 SEQ_BITS 限制） */
    public static final long MAX_CAPACITY = (1L << SEQ_BITS) - 1;

    /** 混淆用乘数：Knuth 黄金比例常数，奇数 ⇒ 在 2^35 下可逆 */
    private static final long ODD_MULTIPLIER = 0x9E3779B1L;

    /** 混淆乘数的模逆元（mod 2^35），解码时用来还原序号 */
    private static final long ODD_MULTIPLIER_INVERSE =
            java.math.BigInteger.valueOf(ODD_MULTIPLIER)
                    .modInverse(java.math.BigInteger.ONE.shiftLeft(SEQ_BITS))
                    .longValue();

    /** 批次签名：FNV-1a 32bit 后取低 20 位 */
    public static int batchSignature(String batchNo) {
        final int prime = 0x01000193;
        int hash = 0x811C9DC5;
        for (int i = 0; i < batchNo.length(); i++) {
            hash ^= batchNo.charAt(i);
            hash *= prime;
        }
        return hash & ((1 << BATCH_SIG_BITS) - 1);
    }

    /**
     * 生成兑换码。
     *
     * @param batchNo 批次号
     * @param seqNo   序号（0 起，必须 &lt; {@link #MAX_CAPACITY}）
     */
    public static String generate(String batchNo, long seqNo) {
        if (batchNo == null || batchNo.isEmpty()) {
            throw new IllegalArgumentException("batchNo is empty");
        }
        if (seqNo < 0 || seqNo > MAX_CAPACITY) {
            throw new IllegalArgumentException("seqNo out of range: " + seqNo);
        }
        int sig = batchSignature(batchNo);
        long masked = obfuscate(seqNo, sig);
        String payload = Base32Codec.encode(sig, SIG_CHARS) + Base32Codec.encode(masked, SEQ_CHARS);
        return payload + Base32Codec.encode(checksum(payload), CHECK_CHARS);
    }

    /**
     * 解析兑换码。
     *
     * @return 解析结果；码非法时返回 {@code null}（不抛异常，调用方统一按"兑换码无效"处理）
     */
    public static RedeemCode parse(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().replace("-", "").replace(" ", "").toUpperCase();
        if (normalized.length() != CODE_LENGTH) {
            return null;
        }
        try {
            String payload = normalized.substring(0, CODE_LENGTH - CHECK_CHARS);
            long check = Base32Codec.decode(normalized.substring(CODE_LENGTH - CHECK_CHARS), CHECK_CHARS);
            if (check != checksum(payload)) {
                return null;
            }
            long sig = Base32Codec.decode(payload.substring(0, SIG_CHARS), SIG_CHARS);
            long masked = Base32Codec.decode(payload.substring(SIG_CHARS), SEQ_CHARS);
            return new RedeemCode((int) sig, deObfuscate(masked, (int) sig));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 序号混淆（可逆置换）。
     *
     * <h3>为什么必须做</h3>
     * <p>如果序号直接编码，用户拿到一个码就能推出同批次的相邻码（把末几位 +1 即可），
     * 这在积分兑换场景里等于"送分"。这里用一个与批次签名相关的仿射变换做置换：</p>
     * <pre>
     * masked = (seq * ODD + offset) mod 2^35
     * offset = batchSig * 0x9E3779B1 mod 2^35
     * </pre>
     * <p>ODD 是奇数，在 2^35 下一定存在乘法逆元，因此变换是<b>双射</b>：
     * 容量不损失，解码时用逆元即可还原。同一批次内相邻序号的码看起来完全不相关。</p>
     */
    private static long obfuscate(long seqNo, int batchSignature) {
        long offset = (batchSignature * 0x9E3779B1L) & MAX_CAPACITY;
        return (seqNo * ODD_MULTIPLIER + offset) & MAX_CAPACITY;
    }

    private static long deObfuscate(long masked, int batchSignature) {
        long offset = (batchSignature * 0x9E3779B1L) & MAX_CAPACITY;
        long inverse = ODD_MULTIPLIER_INVERSE;
        return ((masked - offset) * inverse) & MAX_CAPACITY;
    }

    /**
     * 5 bit 校验位：对前 11 个字符的 5 bit 值做 31 进制多项式滚动。
     *
     * <p>两个必须注意的点（都是踩过的坑）：</p>
     * <ol>
     *   <li>不能只算 {@code (sig*31+seq) & 0x1F}——那样只覆盖"每段最后一位"
     *       （因为 32^k ≡ 0 mod 32，高位字符对取模结果无影响）；</li>
     *   <li>不能直接用字符的 ASCII 码累加——'0'(48) 与 'P'(80) 相差 32，
     *       在 mod 32 下同余，会形成盲区。必须用 {@link Base32Codec#valueOf(char)} 取 5 bit 值。</li>
     * </ol>
     * <p>改成 5 bit 值 + 31 进制滚动后，任意单字符改动的权重是 31^k ≡ (-1)^k (mod 32)，
     * 可逆，因此**任意一位单字符篡改都必然被检出**。</p>
     */
    private static int checksum(CharSequence chars) {
        int sum = 0;
        for (int i = 0; i < chars.length(); i++) {
            sum = sum * 31 + Base32Codec.valueOf(chars.charAt(i));
        }
        return sum & 0x1F;
    }

    /** 格式化展示：每 4 位加一个连字符，方便用户抄写 */
    public static String format(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return code;
        }
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8);
    }

    /** 解析结果 */
    @Getter
    public static final class RedeemCode {
        /** 批次签名 */
        private final int batchSignature;
        /** 序号，也是 BitMap offset */
        private final long seqNo;

        public RedeemCode(int batchSignature, long seqNo) {
            this.batchSignature = batchSignature;
            this.seqNo = seqNo;
        }
    }
}
