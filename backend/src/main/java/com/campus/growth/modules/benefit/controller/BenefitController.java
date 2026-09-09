package com.campus.growth.modules.benefit.controller;

import com.campus.growth.common.result.PageResult;
import com.campus.growth.common.result.Result;
import com.campus.growth.modules.benefit.service.BenefitService;
import com.campus.growth.modules.benefit.vo.CategoryVO;
import com.campus.growth.modules.benefit.vo.GoodsDetailVO;
import com.campus.growth.modules.benefit.vo.GoodsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端权益商城接口。
 */
@RestController
@RequestMapping("/api/benefit")
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitService benefitService;

    @GetMapping("/goods/page")
    public Result<PageResult<GoodsVO>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "12") long size,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String sort) {
        return Result.ok(benefitService.pageGoods(category, keyword, sort, page, size));
    }

    @GetMapping("/goods/{id}")
    public Result<GoodsDetailVO> detail(@PathVariable Long id) {
        return Result.ok(benefitService.detail(id));
    }

    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.ok(benefitService.categories());
    }
}
