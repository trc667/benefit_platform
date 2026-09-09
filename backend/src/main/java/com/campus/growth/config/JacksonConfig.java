package com.campus.growth.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 时间格式配置。
 *
 * <h3>为什么不能只靠 spring.jackson.date-format</h3>
 * <p>{@code spring.jackson.date-format} 只影响老的 {@code java.util.Date}，
 * 对 {@code java.time.LocalDateTime} 无效——JavaTimeModule 默认输出 ISO-8601
 * （形如 {@code 2026-09-09T09:57:56.2050228}）。前端契约要求
 * {@code yyyy-MM-dd HH:mm:ss}，所以必须显式注册序列化器。</p>
 */
@Configuration
public class JacksonConfig {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> {
            builder.serializerByType(java.time.LocalDateTime.class,
                    new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.deserializerByType(java.time.LocalDateTime.class,
                    new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
            builder.serializerByType(java.time.LocalDate.class,
                    new LocalDateSerializer(DATE_FORMATTER));
            builder.deserializerByType(java.time.LocalDate.class,
                    new LocalDateDeserializer(DATE_FORMATTER));
        };
    }
}
