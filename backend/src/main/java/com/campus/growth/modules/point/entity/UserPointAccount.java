package com.campus.growth.modules.point.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分账户。
 * <p>余额是热点字段，用 {@code @Version} 乐观锁更新；流水单独落 {@code point_record}，
 * 两者分离的好处是账户行只有一行、更新语句极短，流水表只追加不更新。</p>
 */
@Data
@TableName("user_point_account")
public class UserPointAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 可用积分 */
    private Integer balance;
    /** 累计获得 */
    private Integer totalEarned;
    /** 累计消耗 */
    private Integer totalUsed;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
