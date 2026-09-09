package com.campus.growth.modules.coupon.rpc;

import com.campus.growth.modules.coupon.entity.CouponTemplate;
import com.campus.growth.modules.coupon.entity.UserCoupon;
import com.campus.growth.modules.coupon.mapper.CouponTemplateMapper;
import com.campus.growth.modules.coupon.rpc.dto.CouponTemplateDTO;
import com.campus.growth.modules.coupon.rpc.dto.UserCouponDTO;
import com.campus.growth.modules.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 优惠券 RPC 实现。
 *
 * <h3>关键约束（读代码时请先看这里）</h3>
 * <ul>
 *   <li>本类<b>只做参数适配 + 委派本地 Service</b>，不写第二套业务逻辑。
 *       拆服务时把本类搬到独立进程即可，业务代码零改动；</li>
 *   <li>{@code @DubboService} 只有在 {@code dubbo.enabled=true} 时才会被扫描。
 *       单体部署（默认）下本类不会被实例化，也不会监听 20880 端口；</li>
 *   <li>单体内部禁止注入本接口做远程调用——本地一律用 {@link CouponService}。</li>
 * </ul>
 */
@Slf4j
@DubboService(interfaceClass = CouponRpcService.class, timeout = 3000, retries = 0)
@RequiredArgsConstructor
public class CouponRpcServiceImpl implements CouponRpcService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CouponService couponService;
    private final CouponTemplateMapper templateMapper;

    @Override
    public UserCouponDTO receive(Long userId, Long templateId, String source, String bizNo) {
        // bizNo 幂等由 user_coupon 唯一索引 + 单人限领共同保证
        log.info("RPC 发券 userId={} templateId={} source={} bizNo={}", userId, templateId, source, bizNo);
        com.campus.growth.modules.coupon.vo.UserCouponVO vo =
                couponService.receiveBySource(userId, templateId, source == null ? "REDEEM" : source);
        UserCouponDTO dto = new UserCouponDTO();
        dto.setId(vo.getId());
        dto.setUserId(userId);
        dto.setTemplateId(vo.getTemplateId());
        dto.setCouponCode(vo.getCouponCode());
        dto.setCouponTitle(vo.getCouponTitle());
        dto.setCouponType(vo.getCouponType());
        dto.setFaceValue(vo.getFaceValue());
        dto.setDiscountRate(vo.getDiscountRate());
        dto.setThresholdPoint(vo.getThresholdPoint());
        dto.setMaxDiscount(vo.getMaxDiscount());
        dto.setScopeType(vo.getScopeType());
        dto.setScopeValue(vo.getScopeValue());
        dto.setStatus(vo.getStatus());
        dto.setExpireTime(vo.getExpireTime() == null ? null : vo.getExpireTime().format(TIME_FMT));
        return dto;
    }

    @Override
    public List<UserCouponDTO> listUserCoupons(Long userId, String status) {
        return couponService.listByStatus(userId, status).stream().map(this::toDto).toList();
    }

    @Override
    public UserCouponDTO validate(Long userId, Long couponId, int orderAmount, Long goodsId, String category) {
        return toDto(couponService.getUsable(userId, couponId, orderAmount, goodsId, category));
    }

    @Override
    public boolean lock(Long userId, Long couponId, String orderNo) {
        return couponService.lockCoupon(userId, couponId, orderNo);
    }

    @Override
    public void unlock(Long couponId, String orderNo) {
        couponService.unlockCoupon(couponId, orderNo);
    }

    @Override
    public CouponTemplateDTO getTemplate(Long templateId) {
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            return null;
        }
        CouponTemplateDTO dto = new CouponTemplateDTO();
        dto.setId(template.getId());
        dto.setTemplateCode(template.getTemplateCode());
        dto.setTitle(template.getTitle());
        dto.setCouponType(template.getCouponType());
        dto.setFaceValue(template.getFaceValue());
        dto.setDiscountRate(template.getDiscountRate());
        dto.setThresholdPoint(template.getThresholdPoint());
        dto.setMaxDiscount(template.getMaxDiscount());
        dto.setScopeType(template.getScopeType());
        dto.setScopeValue(template.getScopeValue());
        dto.setTotalCount(template.getTotalCount());
        dto.setIssuedCount(template.getIssuedCount());
        dto.setPerUserLimit(template.getPerUserLimit());
        dto.setStatus(template.getStatus());
        return dto;
    }

    private UserCouponDTO toDto(UserCoupon coupon) {
        UserCouponDTO dto = new UserCouponDTO();
        dto.setId(coupon.getId());
        dto.setUserId(coupon.getUserId());
        dto.setTemplateId(coupon.getTemplateId());
        dto.setCouponCode(coupon.getCouponCode());
        dto.setCouponTitle(coupon.getCouponTitle());
        dto.setCouponType(coupon.getCouponType());
        dto.setFaceValue(coupon.getFaceValue());
        dto.setDiscountRate(coupon.getDiscountRate());
        dto.setThresholdPoint(coupon.getThresholdPoint());
        dto.setMaxDiscount(coupon.getMaxDiscount());
        dto.setScopeType(coupon.getScopeType());
        dto.setScopeValue(coupon.getScopeValue());
        dto.setStatus(coupon.getStatus());
        dto.setExpireTime(coupon.getExpireTime() == null ? null : coupon.getExpireTime().format(TIME_FMT));
        dto.setOrderNo(coupon.getOrderNo());
        return dto;
    }
}
