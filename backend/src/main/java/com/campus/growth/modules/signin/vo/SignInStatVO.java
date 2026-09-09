package com.campus.growth.modules.signin.vo;

import lombok.Data;

/**
 * 签到概览。
 */
@Data
public class SignInStatVO {

    private Integer continuousDays;
    private Integer monthCount;
    private Integer yearCount;
    private String lastSignDate;
    private Boolean todaySigned;
}
