package com.campus.growth.common.util;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL 表达式工具，用于解析注解里的 {@code key} 表达式。
 * <p>依赖编译期 {@code -parameters} 参数保留（pom 中已配置），
 * 因此表达式里可以直接写 {@code #userId}、{@code #templateId} 这类参数名。</p>
 */
public final class SpelUtil {

    private SpelUtil() {
    }

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    /** 解析表达式；解析失败返回 null，调用方需有降级逻辑 */
    public static String evaluate(String expression, Method method, Object[] args) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Expression exp = EXPRESSION_CACHE.computeIfAbsent(expression, PARSER::parseExpression);
            EvaluationContext context = new StandardEvaluationContext();
            String[] names = NAME_DISCOVERER.getParameterNames(method);
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    ((StandardEvaluationContext) context).setVariable(names[i], args[i]);
                }
            }
            return exp.getValue(context, String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
