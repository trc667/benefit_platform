package com.campus.growth.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 消息（含工具调用审计：调了哪个工具、传了什么参数、返回了什么、耗时多少）。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long userId;
    /** system / user / assistant / tool */
    private String role;
    private String content;
    private String toolName;
    private String toolArgs;
    private String toolResult;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer costMs;
    private LocalDateTime createTime;
}
