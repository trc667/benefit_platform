package com.campus.growth.modules.redeem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 兑换码核销记录。
 * <p>只记录"被核销过"的码，不预生成全量码。
 * 唯一索引 {@code code} 是幂等的最后一道防线（位图被清空时也不会重复兑换）。</p>
 */
@Data
@TableName("redeem_code")
public class RedeemCode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private String code;
    /** 序号，对应 Redis BitMap 的 offset */
    private Long seqNo;
    private Long userId;
    /** 0 未用 1 已用 */
    private Integer status;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
}
