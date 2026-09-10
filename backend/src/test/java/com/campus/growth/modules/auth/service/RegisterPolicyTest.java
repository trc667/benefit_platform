package com.campus.growth.modules.auth.service;

import com.campus.growth.common.exception.BizException;
import com.campus.growth.modules.auth.dto.RegisterDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册准入策略单测：这是防"批量开小号薅权益"的第一道闸门，规则必须可预测。
 */
class RegisterPolicyTest {

    private RegisterDTO dto(String studentNo, String school, String inviteCode) {
        RegisterDTO d = new RegisterDTO();
        d.setUsername("tester01");
        d.setPassword("123456");
        d.setNickname("测试");
        d.setStudentNo(studentNo);
        d.setSchool(school);
        d.setInviteCode(inviteCode);
        return d;
    }

    @Test
    @DisplayName("OPEN 模式：只校验学号格式，合法学号放行")
    void openModeOnlyChecksPattern() {
        RegisterPolicy policy = new RegisterPolicy();
        policy.setMode("OPEN");
        assertDoesNotThrow(() -> policy.validate(dto("2023010101", "示范大学", null)));
        assertDoesNotThrow(() -> policy.validate(dto(null, null, null)));
    }

    @Test
    @DisplayName("学号格式非法时拒绝，且提示带上规则")
    void invalidStudentNoRejected() {
        RegisterPolicy policy = new RegisterPolicy();
        policy.setMode("OPEN");
        BizException e = assertThrows(BizException.class, () -> policy.validate(dto("abc", "示范大学", null)));
        assertTrue(e.getMessage().contains("学号格式不正确"), "实际提示：" + e.getMessage());
    }

    @Test
    @DisplayName("INVITE 模式：邀请码不对直接拒绝")
    void inviteModeRejectsWrongCode() {
        RegisterPolicy policy = new RegisterPolicy();
        policy.setMode("INVITE");
        policy.setInviteCodes("CAMPUS-2026, CAMPUS-VIP");
        assertThrows(BizException.class, () -> policy.validate(dto("2023010101", "示范大学", "WRONG")));
        assertThrows(BizException.class, () -> policy.validate(dto("2023010101", "示范大学", null)));
        assertDoesNotThrow(() -> policy.validate(dto("2023010101", "示范大学", "CAMPUS-2026")));
    }

    @Test
    @DisplayName("INVITE 模式但没配置邀请码时，拒绝所有注册（避免误开放）")
    void inviteModeWithoutCodesRejectsAll() {
        RegisterPolicy policy = new RegisterPolicy();
        policy.setMode("INVITE");
        policy.setInviteCodes("");
        assertThrows(BizException.class, () -> policy.validate(dto("2023010101", "示范大学", "ANY")));
    }

    @Test
    @DisplayName("SCHOOL 模式：学校不在白名单拒绝，在白名单且学号合法放行")
    void schoolModeChecksWhitelist() {
        RegisterPolicy policy = new RegisterPolicy();
        policy.setMode("SCHOOL");
        policy.setAllowedSchools("示范大学,示例学院");
        assertThrows(BizException.class, () -> policy.validate(dto("2023010101", "野鸡大学", null)));
        assertThrows(BizException.class, () -> policy.validate(dto("2023010101", null, null)));
        assertDoesNotThrow(() -> policy.validate(dto("2023010101", "示例学院", null)));
    }

    @Test
    @DisplayName("前端配置下发：needInviteCode / needSchool 与模式一致")
    void configFlagsMatchMode() {
        RegisterPolicy invite = new RegisterPolicy();
        invite.setMode("INVITE");
        invite.setInviteCodes("CODE-1");
        assertTrue(invite.needInviteCode());
        assertEquals(1, invite.inviteCodeList().size());

        RegisterPolicy school = new RegisterPolicy();
        school.setMode("SCHOOL");
        school.setAllowedSchools("示范大学");
        assertTrue(school.needSchool());
        // 白名单为空时不要求学校（退化成只校验学号格式）
        RegisterPolicy empty = new RegisterPolicy();
        empty.setMode("SCHOOL");
        org.junit.jupiter.api.Assertions.assertFalse(empty.needSchool());
    }
}
