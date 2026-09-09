package com.campus.growth.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base32 编解码测试。
 */
class Base32CodecTest {

    @Test
    @DisplayName("定长编码后能原样解回")
    void encodeDecodeRoundTrip() {
        long[] values = {0L, 1L, 31L, 32L, 1023L, 65535L, 1L << 34, (1L << 35) - 1};
        for (long value : values) {
            String encoded = Base32Codec.encode(value, 7);
            assertEquals(7, encoded.length());
            assertEquals(value, Base32Codec.decode(encoded), "value=" + value);
        }
    }

    @Test
    @DisplayName("宽容解码：I/L 视为 1，O 视为 0")
    void tolerantDecode() {
        String normal = Base32Codec.encode(1023, 4);
        // 把可能出现的 1/0 换成易混淆字符，解码结果应一致
        String confused = normal.replace('1', 'I').replace('0', 'O');
        assertEquals(Base32Codec.decode(normal), Base32Codec.decode(confused));
    }

    @Test
    @DisplayName("非法字符抛异常")
    void illegalChar() {
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("U1"));
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode(""));
    }

    @Test
    @DisplayName("超出位宽抛异常")
    void overflow() {
        assertThrows(IllegalArgumentException.class, () -> Base32Codec.encode(1L << 20, 4));
    }

    @Test
    @DisplayName("随机段只包含字母表内字符")
    void randomChars() {
        String random = Base32Codec.random(64);
        assertEquals(64, random.length());
        for (char c : random.toCharArray()) {
            assertTrue(new String(Base32Codec.ALPHABET).indexOf(c) >= 0, "非法字符: " + c);
        }
    }
}
