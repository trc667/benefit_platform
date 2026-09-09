package com.campus.growth.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * JSON 工具。Kafka 事件体、缓存值、AI 工具入参都走这里，避免各处 new ObjectMapper。
 */
@Slf4j
public final class JsonUtil {

    private JsonUtil() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // 事件体里带时间戳字符串，不做类型推断，避免反序列化报错
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败 type={}", obj.getClass().getName(), e);
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /** 反序列化失败返回 null，调用方自行决定降级策略（消息体损坏不应打挂消费者） */
    public static <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("JSON 反序列化失败 target={} json={}", clazz.getSimpleName(), abbreviate(json), e);
            return null;
        }
    }

    public static <T> T parse(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            log.warn("JSON 反序列化失败 json={}", abbreviate(json), e);
            return null;
        }
    }

    public static <T> List<T> parseList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.warn("JSON 反序列化列表失败 target={} json={}", clazz.getSimpleName(), abbreviate(json), e);
            return Collections.emptyList();
        }
    }

    /** 日志里截断超长 JSON，避免刷屏 */
    public static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...(truncated)";
    }
}
