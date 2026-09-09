package com.campus.growth.modules.redeem.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 兑换码批次视图。
 */
@Data
public class RedeemBatchVO {

    private Long id;
    private String batchNo;
    private String title;
    private String bizType;
    private Long refId;
    private Integer rewardValue;
    private Integer totalCount;
    private Integer usedCount;
    /** 核销率（%） */
    private Double useRate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    /** 已生成到第几个码（Redis cursor），管理端预览用 */
    private Long generatedCount;
}
