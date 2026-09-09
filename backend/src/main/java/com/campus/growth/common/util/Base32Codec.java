package com.campus.growth.common.util;

/**
 * Base32 编解码器（Crockford 变体，去除易混淆字符）。
 * <p>字母表 32 个字符：{@code 0-9 A-Z}（去掉 I、L、O、U），每字符承载 5 bit。
 * 兑换码、券码都由它按"分段"方式拼装：每段独立编码后拼接，解码时可逐段还原。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>定长编码：不足位数在高位补 0，保证解码时位数固定，便于按位段切分；</li>
 *   <li>宽容解码：I/l→1、O/o→0，用户手抄兑换码时不至于因为字形输错而失败；</li>
 *   <li>纯静态无状态，可安全并发调用。</li>
 * </ul>
 */
public final class Base32Codec {

    private Base32Codec() {
    }

    /** 32 个字符，索引即 5 bit 的值 */
    public static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 反查表：字符 → 5bit 值，-1 表示非法字符 */
    private static final int[] LOOKUP = new int[128];

    static {
        java.util.Arrays.fill(LOOKUP, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            LOOKUP[ALPHABET[i]] = i;
            LOOKUP[Character.toLowerCase(ALPHABET[i])] = i;
        }
        // 易混淆字符的宽容映射
        LOOKUP['I'] = 1;
        LOOKUP['i'] = 1;
        LOOKUP['L'] = 1;
        LOOKUP['l'] = 1;
        LOOKUP['O'] = 0;
        LOOKUP['o'] = 0;
    }

    /** 承载 {@code bits} 位需要多少个 Base32 字符 */
    public static int charCountForBits(int bits) {
        if (bits <= 0) {
            throw new IllegalArgumentException("bits must be positive");
        }
        return (bits + 4) / 5;
    }

    /**
     * 定长编码：把 value 的低 {@code charCount * 5} 位编码成 {@code charCount} 个字符。
     *
     * @param value     待编码值（必须 >= 0）
     * @param charCount 输出字符数
     */
    public static String encode(long value, int charCount) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (charCount <= 0 || charCount > 12) {
            throw new IllegalArgumentException("charCount must be in (0, 12]");
        }
        int totalBits = charCount * 5;
        if (totalBits < 63 && value >= (1L << totalBits)) {
            throw new IllegalArgumentException("value " + value + " exceeds " + totalBits + " bits capacity");
        }
        char[] out = new char[charCount];
        for (int i = charCount - 1; i >= 0; i--) {
            out[i] = ALPHABET[(int) (value & 0x1F)];
            value >>>= 5;
        }
        return new String(out);
    }

    /**
     * 解码定长 Base32 字符串（不区分大小写，宽容处理 I/L/O）。
     *
     * @return 解码后的长整型值
     * @throws IllegalArgumentException 出现非法字符或长度过大
     */
    public static long decode(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text is empty");
        }
        if (text.length() > 12) {
            throw new IllegalArgumentException("text too long");
        }
        long value = 0L;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int v = c < 128 ? LOOKUP[c] : -1;
            if (v < 0) {
                throw new IllegalArgumentException("illegal base32 char: " + c);
            }
            value = (value << 5) | v;
        }
        return value;
    }

    /** 解码并校验期望长度 */
    public static long decode(String text, int expectedLength) {
        if (text == null || text.length() != expectedLength) {
            throw new IllegalArgumentException("illegal base32 length");
        }
        return decode(text);
    }

    /**
     * 取单个字符的 5 bit 值。
     * <p>注意不要用 {@code char} 的 ASCII 码做校验计算：'0'(48) 与 'P'(80) 相差 32，
     * 在 mod 32 下同余，会形成校验盲区。这里统一取字母表下标。</p>
     */
    public static int valueOf(char c) {
        int v = c < 128 ? LOOKUP[c] : -1;
        if (v < 0) {
            throw new IllegalArgumentException("illegal base32 char: " + c);
        }
        return v;
    }

    /** 生成 {@code charCount} 个随机 Base32 字符（用于批次号随机段） */
    public static String random(int charCount) {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        char[] out = new char[charCount];
        for (int i = 0; i < charCount; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
