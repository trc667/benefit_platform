package com.campus.growth.modules.task.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.task.dto.TaskSaveDTO;
import com.campus.growth.modules.task.entity.TaskDefinition;
import com.campus.growth.modules.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端任务配置接口。
 */
@RestController
@RequestMapping("/api/admin/task")
@RequiredArgsConstructor
public class TaskAdminController {

    private final TaskService taskService;

    @GetMapping("/page")
    public Result<PageResult<TaskDefinition>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer status) {
        return Result.ok(taskService.pageForAdmin(keyword, status, page, size));
    }

    @PostMapping("/save")
    @OpLog(module = "任务配置", action = "保存任务")
    public Result<Map<String, Object>> save(@Valid @RequestBody TaskSaveDTO dto) {
        return Result.ok(Map.of("id", taskService.save(dto)));
    }

    @PostMapping("/status")
    @OpLog(module = "任务配置", action = "启停任务")
    public Result<Void> status(@RequestBody TaskSaveDTO dto) {
        taskService.updateStatus(dto.getId(), dto.getStatus());
        return Result.ok();
    }
}
