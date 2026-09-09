package com.campus.growth.modules.order.rpc.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * RPC 传输对象：订单。
 */
@Data
public class OrderRpcDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private Integer goodsTotalPoint;
    private Integer discountPoint;
    private Integer payPoint;
    private String status;
    private String createTime;
    private String payTime;
    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long goodsId;
        private String goodsTitle;
        private Integer unitPoint;
        private Integer quantity;
        private Integer itemAmount;
    }
}
