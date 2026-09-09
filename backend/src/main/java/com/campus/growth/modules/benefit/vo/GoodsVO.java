package com.campus.growth.modules.benefit.vo;

import lombok.Data;

/**
 * 商品列表项（学生端卡片 / 管理端表格共用）。
 */
@Data
public class GoodsVO {

    private Long id;
    private String goodsCode;
    private String title;
    private String subTitle;
    private String coverUrl;
    private String category;
    private String categoryName;
    private Integer pricePoint;
    private Integer originPrice;
    private Integer stock;
    private Integer soldCount;
    private String tags;
    private Integer sort;
    private Integer status;
}
