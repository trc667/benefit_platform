package com.campus.growth.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 对话请求。
 */
@Data
public class AiChatDTO {

    /** 为空表示新建会话 */
    private String sessionId;

    @NotBlank(message = "请输入内容")
    @Size(max = 500, message = "单次输入不能超过 500 个字")
    private String message;
}
