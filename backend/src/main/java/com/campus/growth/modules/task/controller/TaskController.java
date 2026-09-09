package com.campus.growth.modules.task.controller;

import com.campus.growth.common.result.Result;
import com.campus.growth.modules.task.service.TaskService;
import com.campus.growth.modules.task.vo.UserTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学生端任务接口。
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/list")
    public Result<List<UserTaskVO>> list() {
        return Result.ok(taskService.listMine());
    }

    /** 上报进度：例如浏览商品、分享权益由前端调用 */
    @PostMapping("/progress/report")
    public Result<UserTaskVO> report(@RequestParam String taskCode,
                                     @RequestParam(defaultValue = "1") int delta) {
        return Result.ok(taskService.reportProgress(taskCode, delta));
    }

    /** 领取任务奖励 */
    @PostMapping("/reward/claim")
    public Result<Map<String, Object>> claim(@RequestParam String taskCode) {
        int point = taskService.claimReward(taskCode);
        return Result.ok(Map.of("pointAward", point));
    }
}
