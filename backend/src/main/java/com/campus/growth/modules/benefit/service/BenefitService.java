package com.campus.growth.modules.benefit.service;

import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.benefit.dto.GoodsSaveDTO;
import com.campus.growth.modules.benefit.entity.BenefitGoods;
import com.campus.growth.modules.benefit.vo.CategoryVO;
import com.campus.growth.modules.benefit.vo.GoodsDetailVO;
import com.campus.growth.modules.benefit.vo.GoodsVO;

import java.util.List;

/**
 * 权益商品服务。
 */
public interface BenefitService {

    /** 学生端商品列表（走二级缓存） */
    PageResult<GoodsVO> pageGoods(String category, String keyword, String sort, long page, long size);

    /** 商品详情（走二级缓存，含同分类推荐） */
    GoodsDetailVO detail(Long goodsId);

    /** 分类及数量 */
    List<CategoryVO> categories();

    /**
     * 下单用：直接从数据库读取（不走缓存），保证价格与库存是准的。
     */
    BenefitGoods getFromDb(Long goodsId);

    /** 扣库存（乐观锁 + 条件更新，防超卖） */
    boolean deductStock(Long goodsId, int quantity);

    /** 回滚库存（取消订单 / 售后退货） */
    void restoreStock(Long goodsId, int quantity);

    /** 管理端分页 */
    PageResult<GoodsVO> pageForAdmin(String category, String keyword, Integer status, long page, long size);

    /** 新增或编辑 */
    Long save(GoodsSaveDTO dto);

    /** 上下架 */
    void updateStatus(Long goodsId, Integer status);

    /** 商品总数（仪表盘用） */
    long countAll();
}
