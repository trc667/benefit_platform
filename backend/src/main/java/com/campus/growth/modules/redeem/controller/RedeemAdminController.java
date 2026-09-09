package com.campus.growth.modules.redeem.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.redeem.dto.RedeemBatchCreateDTO;
import com.campus.growth.modules.redeem.service.RedeemService;
import com.campus.growth.modules.redeem.vo.RedeemBatchVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 管理端兑换码接口。
 */
@RestController
@RequestMapping("/api/admin/redeem")
@RequiredArgsConstructor
public class RedeemAdminController {

    private final RedeemService redeemService;

    @GetMapping("/batch/page")
    public Result<PageResult<RedeemBatchVO>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status) {
        return Result.ok(redeemService.pageBatches(keyword, status, page, size));
    }

    @PostMapping("/batch/create")
    @OpLog(module = "兑换码", action = "创建批次")
    public Result<RedeemBatchVO> create(@Valid @RequestBody RedeemBatchCreateDTO dto) {
        return Result.ok(redeemService.createBatch(dto));
    }

    /** 生成/预览码：从游标位置开始批量生成 */
    @GetMapping("/batch/{batchNo}/codes")
    public Result<List<String>> codes(@PathVariable String batchNo,
                                      @RequestParam(defaultValue = "10") int count) {
        return Result.ok(redeemService.generateCodes(batchNo, count));
    }

    /** 核销统计（位图 vs 数据库，可用于对账） */
    @GetMapping("/batch/{batchNo}/stat")
    public Result<RedeemBatchVO> stat(@PathVariable String batchNo) {
        return Result.ok(redeemService.stat(batchNo));
    }

    @PostMapping("/batch/status")
    @OpLog(module = "兑换码", action = "修改批次状态")
    public Result<Void> status(@RequestBody Map<String, Object> body) {
        redeemService.updateStatus(String.valueOf(body.get("batchNo")),
                Integer.valueOf(String.valueOf(body.get("status"))));
        return Result.ok();
    }
}
