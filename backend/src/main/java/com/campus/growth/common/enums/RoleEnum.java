package com.campus.growth.common.enums;

/**
 * 角色枚举。
 */
public enum RoleEnum {

    /** 学生 */
    STUDENT("学生"),
    /** 运营 */
    OPERATOR("运营"),
    /** 管理员 */
    ADMIN("管理员");

    private final String desc;

    RoleEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public static boolean isAdmin(String role) {
        return ADMIN.name().equals(role);
    }

    public static boolean isOperator(String role) {
        return OPERATOR.name().equals(role) || ADMIN.name().equals(role);
    }
}
