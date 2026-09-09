package com.campus.growth.modules.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券模板。
 * <p>发行量 {@code issued_count} 用乐观锁更新；真正的"防超发"由
 * Redis 预扣库存 + 分布式锁 + 本表的条件更新三层共同保证。</p>
 */
@Data
@TableName("coupon_template")
public class CouponTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateCode;
    private String title;
    /** CASH 满减 / DISCOUNT 折扣 / DIRECT 无门槛 */
    private String couponType;
    /** 面额（积分） */
    private Integer faceValue;
    /** 折扣率 * 100，85 表示 8.5 折 */
    private Integer discountRate;
    /** 使用门槛 */
    private Integer thresholdPoint;
    /** 最高抵扣，0 表示不限 */
    private Integer maxDiscount;
    /** ALL / GOODS / CATEGORY */
    private String scopeType;
    /** 适用范围值，逗号分隔 */
    private String scopeValue;
    /** 发行总量 */
    private Integer totalCount;
    /** 已发放 */
    private Integer issuedCount;
    /** 单人限领 */
    private Integer perUserLimit;
    /** FIXED 固定区间 / RELATIVE 领取后 N 天 */
    private String validType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer validDays;
    private Integer status;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
