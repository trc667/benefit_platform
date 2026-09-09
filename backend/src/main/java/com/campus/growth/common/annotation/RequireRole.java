package com.campus.growth.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解。
 *
 * <h3>为什么必须有</h3>
 * <p>登录态 ≠ 权限。只校验 token 有效，任何学生账号都能调管理端接口改价格、发券、审售后。
 * 本项目采用"两层"策略：</p>
 * <ol>
 *   <li><b>路径兜底</b>：{@code /api/admin/**} 一律要求 OPERATOR 或 ADMIN，
 *       新增管理端接口忘记加注解也不会漏防护（fail-safe）；</li>
 *   <li><b>注解显式声明</b>：非 admin 路径但需要特定角色时用本注解，语义清晰。</li>
 * </ol>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许访问的角色，满足其中之一即可。
     * <p>取值见 {@code BizConst.ROLE_*}：STUDENT / OPERATOR / ADMIN。</p>
     */
    String[] value();
}
