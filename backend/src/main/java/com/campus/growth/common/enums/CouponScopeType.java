package com.campus.growth.common.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 优惠券适用范围。
 */
public enum CouponScopeType {

    /** 全场通用 */
    ALL,
    /** 指定商品 */
    GOODS,
    /** 指定分类 */
    CATEGORY;

    /**
     * 判断券是否适用于某商品。
     *
     * @param scopeValue 逗号分隔的商品 ID 或分类编码
     */
    public static boolean matches(String scopeType, String scopeValue, Long goodsId, String category) {
        if (scopeType == null || ALL.name().equalsIgnoreCase(scopeType)) {
            return true;
        }
        if (scopeValue == null || scopeValue.isBlank()) {
            return true;
        }
        Set<String> values = Arrays.stream(scopeValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (GOODS.name().equalsIgnoreCase(scopeType)) {
            return goodsId != null && values.contains(String.valueOf(goodsId));
        }
        if (CATEGORY.name().equalsIgnoreCase(scopeType)) {
            return category != null && values.contains(category);
        }
        return true;
    }
}
