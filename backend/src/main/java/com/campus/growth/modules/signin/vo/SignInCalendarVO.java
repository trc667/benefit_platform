package com.campus.growth.modules.signin.vo;

import lombok.Data;

import java.util.List;

/**
 * 月度签到日历。
 * <p>数据直接来自 Redis BitMap 的逐位读取，不需要查数据库。</p>
 */
@Data
public class SignInCalendarVO {

    /** 月份 yyyyMM */
    private String month;
    private Boolean todaySigned;
    private Integer continuousDays;
    /** 本月签到天数 */
    private Integer monthCount;
    /** 本年签到天数 */
    private Integer yearCount;
    /** 日历格子 */
    private List<Day> days;

    @Data
    public static class Day {
        private int day;
        private boolean signed;
        /** 是否是未来日期（前端置灰） */
        private boolean future;
        private boolean today;
    }
}
