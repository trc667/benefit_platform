package com.campus.growth.modules.benefit.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品新增/编辑请求（管理端）。
 */
@Data
public class GoodsSaveDTO {

    /** 为空表示新增 */
    private Long id;

    @NotBlank(message = "请填写商品编码")
    @Size(max = 32, message = "商品编码不能超过 32 位")
    private String goodsCode;

    @NotBlank(message = "请填写商品标题")
    @Size(max = 128, message = "商品标题不能超过 128 个字")
    private String title;

    @Size(max = 255, message = "副标题不能超过 255 个字")
    private String subTitle;

    private String coverUrl;

    @NotBlank(message = "请选择商品分类")
    private String category;

    @NotNull(message = "请填写所需积分")
    @Min(value = 0, message = "所需积分不能为负")
    private Integer pricePoint;

    @Min(value = 0, message = "原价不能为负")
    private Integer originPrice;

    @NotNull(message = "请填写库存")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    private String tags;
    private String detail;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer sort = 0;
    private Integer status = 1;
}
