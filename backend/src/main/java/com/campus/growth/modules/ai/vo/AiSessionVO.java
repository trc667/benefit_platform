package com.campus.growth.modules.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话列表项。
 */
@Data
public class AiSessionVO {

    private String sessionId;
    private String title;
    private String model;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
