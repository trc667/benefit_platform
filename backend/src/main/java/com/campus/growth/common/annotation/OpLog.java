package com.campus.growth.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。切面异步落 {@code sys_operation_log}，用于管理端审计。
 * <p>只加在"写操作"上，查询接口不加，避免日志表膨胀。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 模块名，如"权益商品" */
    String module();

    /** 动作名，如"上架商品" */
    String action();

    /** 是否记录请求入参（大对象接口可关闭） */
    boolean saveParams() default true;
}
