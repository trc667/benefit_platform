package com.campus.growth.modules.point.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水视图。
 */
@Data
public class PointRecordVO {

    private Long id;
    private String bizType;
    /** 业务类型中文描述 */
    private String bizTypeDesc;
    private String bizNo;
    private Integer changePoint;
    private Integer balanceAfter;
    private String remark;
    private LocalDateTime createTime;
}
