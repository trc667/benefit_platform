package com.campus.growth.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 周期键工具：任务进度按周期分桶存储，周期键同时用于 Redis Hash 与 MySQL 唯一索引。
 * <ul>
 *   <li>每日任务：{@code 20260909}</li>
 *   <li>每周任务：{@code 2026W37}（ISO 周，周一为一周开始）</li>
 *   <li>一次性任务：{@code ONCE}</li>
 * </ul>
 */
public final class PeriodKeyUtil {

    private PeriodKeyUtil() {
    }

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final WeekFields ISO_WEEK = WeekFields.of(DayOfWeek.MONDAY, 4);

    public static String dailyKey(LocalDate date) {
        return date.format(DATE_FMT);
    }

    public static String weeklyKey(LocalDate date) {
        int week = date.get(ISO_WEEK.weekOfWeekBasedYear());
        int weekYear = date.get(ISO_WEEK.weekBasedYear());
        return String.format(Locale.ROOT, "%dW%02d", weekYear, week);
    }

    public static String monthKey(LocalDate date) {
        return date.format(MONTH_FMT);
    }

    /** 按任务类型解析周期键 */
    public static String resolve(String taskType, LocalDate date) {
        if ("WEEKLY".equalsIgnoreCase(taskType)) {
            return weeklyKey(date);
        }
        if ("ONCE".equalsIgnoreCase(taskType)) {
            return "ONCE";
        }
        return dailyKey(date);
    }

    /** 周期键对应的 Redis TTL（秒）：周期结束后再多保留 1 天，避免跨天瞬间丢数据 */
    public static long ttlSeconds(String taskType) {
        return switch (taskType == null ? "DAILY" : taskType.toUpperCase(Locale.ROOT)) {
            case "WEEKLY" -> 8 * 24 * 3600L;
            case "ONCE" -> 3650 * 24 * 3600L;
            default -> 2 * 24 * 3600L;
        };
    }
}
