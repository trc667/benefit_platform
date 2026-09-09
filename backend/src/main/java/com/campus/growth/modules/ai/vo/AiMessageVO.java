package com.campus.growth.modules.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 消息列表项。
 */
@Data
public class AiMessageVO {

    private Long id;
    private String role;
    private String content;
    private String toolName;
    private String toolArgs;
    private String toolResult;
    private Integer costMs;
    private LocalDateTime createTime;
}
