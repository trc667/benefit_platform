package com.campus.growth.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 兑换码算法测试：容量、可反解、防篡改。
 */
class RedeemCodeUtilTest {

    private static final String BATCH_NO = "RCB20260901ABCDEF";

    @Test
    @DisplayName("码长度固定为 12，且可反解出序号")
    void generateAndParse() {
        long[] seqs = {0L, 1L, 999L, 1_000_000L, RedeemCodeUtil.MAX_CAPACITY};
        for (long seq : seqs) {
            String code = RedeemCodeUtil.generate(BATCH_NO, seq);
            assertEquals(RedeemCodeUtil.CODE_LENGTH, code.length());
            RedeemCodeUtil.RedeemCode parsed = RedeemCodeUtil.parse(code);
            assertNotNull(parsed, "code=" + code);
            assertEquals(seq, parsed.getSeqNo());
            assertEquals(RedeemCodeUtil.batchSignature(BATCH_NO), parsed.getBatchSignature());
        }
    }

    @Test
    @DisplayName("容量满足 20 亿级要求")
    void capacity() {
        assertTrue(RedeemCodeUtil.MAX_CAPACITY > 2_000_000_000L,
                "实际容量=" + RedeemCodeUtil.MAX_CAPACITY);
    }

    @Test
    @DisplayName("带连字符与大小写不影响解析")
    void formatTolerant() {
        String code = RedeemCodeUtil.generate(BATCH_NO, 12345L);
        String formatted = RedeemCodeUtil.format(code);
        assertTrue(formatted.contains("-"));
        assertEquals(12345L, RedeemCodeUtil.parse(formatted).getSeqNo());
        assertEquals(12345L, RedeemCodeUtil.parse(formatted.toLowerCase()).getSeqNo());
    }

    @Test
    @DisplayName("任意一位被篡改都能被校验位检出")
    void tamperDetected() {
        String code = RedeemCodeUtil.generate(BATCH_NO, 88888L);
        int detected = 0;
        for (int i = 0; i < code.length(); i++) {
            char origin = code.charAt(i);
            char replaced = origin == '0' ? '1' : '0';
            String bad = code.substring(0, i) + replaced + code.substring(i + 1);
            if (RedeemCodeUtil.parse(bad) == null) {
                detected++;
            }
        }
        // 校验位对字符序列做可逆加权，单字符篡改必须 100% 检出
        assertEquals(code.length(), detected, "应当检出全部单字符篡改");
    }

    @Test
    @DisplayName("非法长度/空值返回 null 而不抛异常")
    void invalidInput() {
        assertNull(RedeemCodeUtil.parse(null));
        assertNull(RedeemCodeUtil.parse("ABC"));
        assertNull(RedeemCodeUtil.parse("!!!!!!!!!!!!"));
    }

    @Test
    @DisplayName("序号混淆：相邻序号的码不可预测，但依然可反解")
    void sequentialCodesAreObfuscated() {
        String c0 = RedeemCodeUtil.generate(BATCH_NO, 100);
        String c1 = RedeemCodeUtil.generate(BATCH_NO, 101);
        int diff = 0;
        for (int i = 0; i < c0.length(); i++) {
            if (c0.charAt(i) != c1.charAt(i)) {
                diff++;
            }
        }
        assertTrue(diff >= 3, "相邻序号的码差异过小，容易被枚举：c0=" + c0 + " c1=" + c1);
        assertEquals(100L, RedeemCodeUtil.parse(c0).getSeqNo());
        assertEquals(101L, RedeemCodeUtil.parse(c1).getSeqNo());
    }

    @Test
    @DisplayName("不同批次签名不同（同序号不会串码）")
    void batchIsolation() {
        String codeA = RedeemCodeUtil.generate("RCB20260901AAAAAA", 1L);
        String codeB = RedeemCodeUtil.generate("RCB20260901BBBBBB", 1L);
        assertTrue(!codeA.equals(codeB));
        assertEquals(RedeemCodeUtil.batchSignature("RCB20260901AAAAAA"),
                RedeemCodeUtil.parse(codeA).getBatchSignature());
    }
}
