package com.campus.growth.modules.benefit.controller;

import com.campus.growth.common.annotation.OpLog;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.benefit.dto.GoodsSaveDTO;
import com.campus.growth.modules.benefit.service.BenefitService;
import com.campus.growth.modules.benefit.vo.GoodsVO;
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
 * 管理端权益商品接口。
 */
@RestController
@RequestMapping("/api/admin/benefit/goods")
@RequiredArgsConstructor
public class BenefitAdminController {

    private final BenefitService benefitService;

    @GetMapping("/page")
    public Result<PageResult<GoodsVO>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(benefitService.pageForAdmin(category, keyword, status, page, size));
    }

    @PostMapping("/save")
    @OpLog(module = "权益商品", action = "保存商品")
    public Result<Map<String, Object>> save(@Valid @RequestBody GoodsSaveDTO dto) {
        Long id = benefitService.save(dto);
        return Result.ok(Map.of("id", id));
    }

    @PostMapping("/status")
    @OpLog(module = "权益商品", action = "修改上下架状态")
    public Result<Void> updateStatus(@RequestBody GoodsSaveDTO dto) {
        benefitService.updateStatus(dto.getId(), dto.getStatus());
        return Result.ok();
    }
}
