package com.campus.growth.modules.redeem.service;

import com.campus.growth.common.result.PageResult;
import com.campus.growth.modules.redeem.dto.RedeemBatchCreateDTO;
import com.campus.growth.modules.redeem.vo.RedeemBatchVO;
import com.campus.growth.modules.redeem.vo.RedeemResultVO;

import java.util.List;

/**
 * 兑换码服务。
 */
public interface RedeemService {

    /** 学生兑换 */
    RedeemResultVO exchange(String code);

    // ---------------- 管理端 ----------------

    PageResult<RedeemBatchVO> pageBatches(String keyword, Integer status, long page, long size);

    RedeemBatchVO createBatch(RedeemBatchCreateDTO dto);

    /** 从当前游标开始生成 N 个码（不落库，仅返回给运营分发） */
    List<String> generateCodes(String batchNo, int count);

    RedeemBatchVO stat(String batchNo);

    void updateStatus(String batchNo, Integer status);
}
