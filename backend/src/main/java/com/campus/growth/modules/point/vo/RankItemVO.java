package com.campus.growth.modules.point.vo;

import lombok.Data;

/**
 * 排行榜条目。
 */
@Data
public class RankItemVO {

    /** 名次，从 1 开始 */
    private Integer rank;
    private Long userId;
    private String nickname;
    private String avatar;
    /** 积分（总榜为累计积分，月榜为本月获得积分） */
    private Long point;
    /** 是否是当前登录用户 */
    private Boolean isMe;
}
