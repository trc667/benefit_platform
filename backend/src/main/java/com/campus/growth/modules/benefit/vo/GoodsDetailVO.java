package com.campus.growth.modules.benefit.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情（在列表字段基础上追加详情字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GoodsDetailVO extends GoodsVO {

    private String detail;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 标签数组（把逗号串拆开，前端直接渲染 el-tag） */
    private List<String> tagList;
    /** 同分类推荐 */
    private List<GoodsVO> recommends;
}
