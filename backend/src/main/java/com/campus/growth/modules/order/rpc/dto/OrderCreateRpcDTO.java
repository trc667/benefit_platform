package com.campus.growth.modules.order.rpc.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * RPC 传输对象：下单请求。
 */
@Data
public class OrderCreateRpcDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 调用方幂等键 */
    private String requestId;
    private Long userId;
    private Long goodsId;
    private Integer quantity;
    private List<Long> couponIds;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
}
