package com.campus.growth.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户。
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    /** BCrypt 密文，任何接口都不返回该字段 */
    private String password;
    private String nickname;
    private String avatar;
    private String phone;
    private String studentNo;
    private String school;
    /** STUDENT / OPERATOR / ADMIN */
    private String role;
    private Integer growthLevel;
    /** 1 正常 0 禁用 */
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
