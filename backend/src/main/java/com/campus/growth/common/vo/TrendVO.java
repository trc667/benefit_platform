package com.campus.growth.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 趋势数据点（仪表盘图表用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendVO {

    /** 日期 yyyy-MM-dd */
    private String date;
    /** 数量 */
    private Long count;
    /** 附加数值（如订单积分），可为空 */
    private Long value;
}
