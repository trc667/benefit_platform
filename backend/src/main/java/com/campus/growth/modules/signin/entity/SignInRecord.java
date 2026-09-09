package com.campus.growth.modules.signin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 签到流水。
 * <p>签到的"真源"是 Redis BitMap，本表用于对账、连续天数统计与补偿。
 * 唯一索引 {@code (user_id, sign_date)} 是防止 BitMap 异常时重复签到的兜底。</p>
 */
@Data
@TableName("sign_in_record")
public class SignInRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private LocalDate signDate;
    /** 连续签到天数 */
    private Integer continuousDays;
    /** 本次获得积分（含连续奖励） */
    private Integer pointAward;
    /** 来源 APP / WEB / ADMIN */
    private String source;
    private LocalDateTime createTime;
}
