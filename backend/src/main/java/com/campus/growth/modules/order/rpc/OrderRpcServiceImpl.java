package com.campus.growth.modules.order.rpc;

import com.campus.growth.common.context.LoginUser;
import com.campus.growth.common.context.UserContext;
import com.campus.growth.modules.order.dto.OrderCreateDTO;
import com.campus.growth.modules.order.rpc.dto.OrderCreateRpcDTO;
import com.campus.growth.modules.order.rpc.dto.OrderRpcDTO;
import com.campus.growth.modules.order.service.OrderService;
import com.campus.growth.modules.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 订单 RPC 实现。
 *
 * <h3>与本地 Service 的关系</h3>
 * <p>本类不包含业务逻辑，只做两件事：</p>
 * <ol>
 *   <li>把 RPC DTO 转成 Service DTO；</li>
 *   <li>补齐用户上下文（RPC 入口没有 HTTP 请求，{@link UserContext} 需要显式设置）。
 *       真实的分布式部署里这一步由 Dubbo Filter 统一完成，这里为保持轻量直接写在实现里。</li>
 * </ol>
 *
 * <p>只有 {@code dubbo.enabled=true} 时才会被扫描注册；单体部署下本类不会被实例化。</p>
 */
@Slf4j
@DubboService(interfaceClass = OrderRpcService.class, timeout = 5000, retries = 0)
@RequiredArgsConstructor
public class OrderRpcServiceImpl implements OrderRpcService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderService orderService;

    @Override
    public OrderRpcDTO createOrder(OrderCreateRpcDTO dto) {
        log.info("RPC 创建订单 requestId={} userId={} goodsId={}", dto.getRequestId(), dto.getUserId(), dto.getGoodsId());
        return withUser(dto.getUserId(), () -> {
            OrderCreateDTO createDTO = new OrderCreateDTO();
            createDTO.setGoodsId(dto.getGoodsId());
            createDTO.setQuantity(dto.getQuantity());
            createDTO.setCouponIds(dto.getCouponIds());
            createDTO.setReceiverName(dto.getReceiverName());
            createDTO.setReceiverPhone(dto.getReceiverPhone());
            createDTO.setReceiverAddress(dto.getReceiverAddress());
            createDTO.setRemark(dto.getRemark());
            return toDto(orderService.create(createDTO));
        });
    }

    @Override
    public OrderRpcDTO getByOrderNo(String orderNo) {
        OrderVO vo = orderService.detail(orderNo);
        return toDto(vo);
    }

    @Override
    public boolean cancelOrder(String orderNo, String reason) {
        orderService.cancel(orderNo);
        log.info("RPC 取消订单 orderNo={} reason={}", orderNo, reason);
        return true;
    }

    @Override
    public OrderRpcDTO payOrder(String orderNo) {
        return toDto(orderService.pay(orderNo));
    }

    /** 在 RPC 入口临时补齐用户上下文，调用结束后必须清理（Tomcat/Dubbo 线程复用） */
    private <T> T withUser(Long userId, java.util.function.Supplier<T> supplier) {
        LoginUser previous = UserContext.get();
        try {
            LoginUser user = new LoginUser();
            user.setUserId(userId);
            user.setUsername("rpc-caller");
            user.setRole("STUDENT");
            UserContext.set(user);
            return supplier.get();
        } finally {
            if (previous == null) {
                UserContext.clear();
            } else {
                UserContext.set(previous);
            }
        }
    }

    private OrderRpcDTO toDto(OrderVO vo) {
        OrderRpcDTO dto = new OrderRpcDTO();
        dto.setId(vo.getId());
        dto.setOrderNo(vo.getOrderNo());
        dto.setUserId(vo.getUserId());
        dto.setGoodsTotalPoint(vo.getGoodsTotalPoint());
        dto.setDiscountPoint(vo.getDiscountPoint());
        dto.setPayPoint(vo.getPayPoint());
        dto.setStatus(vo.getStatus());
        dto.setCreateTime(vo.getCreateTime() == null ? null : vo.getCreateTime().format(TIME_FMT));
        dto.setPayTime(vo.getPayTime() == null ? null : vo.getPayTime().format(TIME_FMT));
        List<OrderRpcDTO.Item> items = vo.getItems() == null ? List.of() : vo.getItems().stream().map(i -> {
            OrderRpcDTO.Item item = new OrderRpcDTO.Item();
            item.setGoodsId(i.getGoodsId());
            item.setGoodsTitle(i.getGoodsTitle());
            item.setUnitPoint(i.getUnitPoint());
            item.setQuantity(i.getQuantity());
            item.setItemAmount(i.getItemAmount());
            return item;
        }).toList();
        dto.setItems(items);
        return dto;
    }
}
