package com.campus.growth.modules.redeem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 兑换码批次。
 * <p>批次号参与兑换码的"批次签名"计算，因此码与批次强绑定，跨批次伪造会校验失败。</p>
 */
@Data
@TableName("redeem_code_batch")
public class RedeemCodeBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private String title;
    /** POINT / COUPON / GOODS */
    private String bizType;
    /** 关联券模板 / 商品 ID */
    private Long refId;
    /** 奖励值（积分数量） */
    private Integer rewardValue;
    private Integer totalCount;
    private Integer usedCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
