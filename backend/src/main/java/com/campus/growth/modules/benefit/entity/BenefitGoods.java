package com.campus.growth.modules.benefit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权益商品。
 * <p>库存用 {@code @Version} 乐观锁扣减，配合"Redis 预扣 + DB 兜底"两层防超卖。</p>
 */
@Data
@TableName("benefit_goods")
public class BenefitGoods implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String goodsCode;
    private String title;
    private String subTitle;
    private String coverUrl;
    /** STUDY / FOOD / LIFE / SPORT / OTHER */
    private String category;
    /** 所需积分 */
    private Integer pricePoint;
    /** 原价（分） */
    private Integer originPrice;
    private Integer stock;
    private Integer soldCount;
    /** 标签，逗号分隔 */
    private String tags;
    private String detail;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer sort;
    /** 1 上架 0 下架 */
    private Integer status;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
