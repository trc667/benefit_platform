package com.campus.growth.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.system.entity.SysOperationLog;
import com.campus.growth.modules.system.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务。
 * <p>写入走异步线程池，避免日志落库拖慢主业务；日志写失败只告警，不影响业务结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogMapper operationLogMapper;

    @Async("logExecutor")
    public void saveAsync(SysOperationLog entity) {
        try {
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("操作日志落库失败 module={} action={}", entity.getModule(), entity.getAction(), e);
        }
    }

    public PageResult<SysOperationLog> page(long page, long size, String module, String username) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(module != null && !module.isBlank(), SysOperationLog::getModule, module)
                .like(username != null && !username.isBlank(), SysOperationLog::getUsername, username)
                .orderByDesc(SysOperationLog::getId);
        Page<SysOperationLog> result = operationLogMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }
}
