package com.campus.growth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 签到业务配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.signin")
public class SigninProperties {

    /** 基础签到积分 */
    private int basePoint = 10;

    /** 连续签到额外奖励：连续 N 天 → 额外积分 */
    private Map<Integer, Integer> continuousBonus = new LinkedHashMap<>();

    /**
     * 计算连续签到奖励：取"不超过当前连续天数"的最大档位。
     * <p>例如配置 3/7/15/30，连续 10 天时命中 7 天档位。</p>
     */
    public int bonusOf(int continuousDays) {
        int bonus = 0;
        for (Map.Entry<Integer, Integer> entry : continuousBonus.entrySet()) {
            if (continuousDays >= entry.getKey() && entry.getValue() > bonus) {
                bonus = entry.getValue();
            }
        }
        return bonus;
    }
}
