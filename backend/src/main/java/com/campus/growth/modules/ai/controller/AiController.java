package com.campus.growth.modules.ai.controller;

import com.campus.growth.common.annotation.RateLimit;
import com.campus.growth.common.enums.RateLimitType;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.ai.dto.AiChatDTO;
import com.campus.growth.modules.ai.service.AiChatService;
import com.campus.growth.modules.ai.vo.AiChatVO;
import com.campus.growth.modules.ai.vo.AiMessageVO;
import com.campus.growth.modules.ai.vo.AiSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 助手接口。
 * <p>限流 3 QPS/用户：大模型调用有成本，必须挡住连点与脚本刷。</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    @RateLimit(key = "ai:chat", qps = 3, type = RateLimitType.DEFAULT,
            message = "AI 助手忙不过来啦，请稍等几秒")
    public Result<AiChatVO> chat(@Valid @RequestBody AiChatDTO dto) {
        return Result.ok(aiChatService.chat(dto));
    }

    @GetMapping("/sessions")
    public Result<List<AiSessionVO>> sessions() {
        return Result.ok(aiChatService.sessions());
    }

    @GetMapping("/messages")
    public Result<List<AiMessageVO>> messages(@RequestParam String sessionId) {
        return Result.ok(aiChatService.messages(sessionId));
    }

    @DeleteMapping("/session/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId) {
        aiChatService.deleteSession(sessionId);
        return Result.ok();
    }

    /** 订单清单文案：走轻量模型 HTTP 直连，超时降级为模板 */
    @GetMapping("/order-summary")
    public Result<Map<String, String>> orderSummary(@RequestParam String orderNo) {
        return Result.ok(Map.of("summary", aiChatService.orderSummary(orderNo)));
    }
}
