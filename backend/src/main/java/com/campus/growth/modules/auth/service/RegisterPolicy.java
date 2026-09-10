package com.campus.growth.modules.auth.service;

import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.modules.auth.dto.RegisterDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 注册准入策略：解决"谁都能注册"导致的薅羊毛。
 *
 * <h3>三种模式</h3>
 * <table border="1">
 *   <tr><th>模式</th><th>校验</th><th>适用</th></tr>
 *   <tr><td>{@code OPEN}</td><td>只校验学号格式</td><td>本地演示 / 开源体验</td></tr>
 *   <tr><td>{@code INVITE}</td><td>必须带有效邀请码 + 学号格式</td><td>内测、社团定向开放</td></tr>
 *   <tr><td>{@code SCHOOL}</td><td>学校在白名单内 + 学号格式</td><td>正式上线（学校统一发放）</td></tr>
 * </table>
 *
 * <p>真实校园项目里，INVITE / SCHOOL 之上通常还会接"学号 + 姓名"与教务系统比对，
 * 那属于对接外部系统，本仓库不假装实现——留了 {@code RegisterPolicy} 这个扩展点。</p>
 *
 * <p>注意：不论哪种模式，<b>学号格式校验始终生效</b>，并且在数据库层面有唯一索引兜底
 * （一个人只能有一个学号，等价于"一个人一个账号"）。</p>
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "campus.auth.register")
public class RegisterPolicy {

    /** OPEN / INVITE / SCHOOL */
    private String mode = "OPEN";

    /** 邀请码，逗号分隔 */
    private String inviteCodes = "";

    /** 学校白名单，逗号分隔；为空表示不限制具体学校 */
    private String allowedSchools = "";

    /** 学号格式，默认 6-20 位数字 */
    private String studentNoPattern = "^[0-9]{6,20}$";

    public List<String> inviteCodeList() {
        return split(inviteCodes);
    }

    public List<String> schoolList() {
        return split(allowedSchools);
    }

    public boolean needInviteCode() {
        return "INVITE".equalsIgnoreCase(mode);
    }

    /** 前端据此决定要不要渲染"邀请码"输入框与学校下拉 */
    public boolean needSchool() {
        return "SCHOOL".equalsIgnoreCase(mode) && !schoolList().isEmpty();
    }

    /** 校验注册准入；不通过直接抛业务异常 */
    public void validate(RegisterDTO dto) {
        String studentNo = dto.getStudentNo() == null ? "" : dto.getStudentNo().trim();
        if (StringUtils.hasText(studentNo) && !studentNo.matches(studentNoPattern)) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "学号格式不正确（应为 " + studentNoPattern + "）");
        }

        if ("INVITE".equalsIgnoreCase(mode)) {
            List<String> codes = inviteCodeList();
            String input = dto.getInviteCode() == null ? "" : dto.getInviteCode().trim();
            if (codes.isEmpty()) {
                log.warn("[注册策略] mode=INVITE 但没有配置邀请码，已拒绝所有注册");
                throw BizException.of(ErrorCode.REGISTER_DENIED, "当前未开放注册");
            }
            if (!codes.contains(input)) {
                throw BizException.of(ErrorCode.REGISTER_DENIED, "邀请码无效");
            }
            return;
        }

        if ("SCHOOL".equalsIgnoreCase(mode)) {
            List<String> schools = schoolList();
            if (!schools.isEmpty()) {
                String school = dto.getSchool() == null ? "" : dto.getSchool().trim();
                boolean hit = schools.stream().anyMatch((s) -> s.equals(school));
                if (!hit) {
                    throw BizException.of(ErrorCode.REGISTER_DENIED, "仅限以下学校注册：" + String.join("、", schools));
                }
            }
            if (!StringUtils.hasText(studentNo)) {
                throw BizException.of(ErrorCode.PARAM_ERROR, "请填写学号");
            }
            return;
        }

        // OPEN：只做格式校验（上面已经做过）
    }

    private List<String> split(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }
}
