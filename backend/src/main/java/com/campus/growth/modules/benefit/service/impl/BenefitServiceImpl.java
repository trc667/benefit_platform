package com.campus.growth.modules.benefit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.growth.common.constant.BizConst;
import com.campus.growth.common.constant.RedisKeyConst;
import com.campus.growth.common.exception.BizException;
import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.PageResult;
import com.campus.growth.infra.cache.CacheProperties;
import com.campus.growth.infra.cache.TwoLevelCache;
import com.campus.growth.modules.benefit.dto.GoodsSaveDTO;
import com.campus.growth.modules.benefit.entity.BenefitGoods;
import com.campus.growth.modules.benefit.mapper.BenefitGoodsMapper;
import com.campus.growth.modules.benefit.service.BenefitService;
import com.campus.growth.modules.benefit.vo.CategoryVO;
import com.campus.growth.modules.benefit.vo.GoodsDetailVO;
import com.campus.growth.modules.benefit.vo.GoodsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权益商品服务实现。
 *
 * <h3>缓存策略（亮点 #8 的落地场景）</h3>
 * <ul>
 *   <li>商品列表与详情走 {@link TwoLevelCache}：Caffeine L1 挡掉绝大部分读请求，
 *       Redis L2 负责多实例共享，Redis 压力下降一个量级；</li>
 *   <li>写路径（保存/上下架）先更新 DB，再 {@code evictWithDoubleDelete} 删缓存；</li>
 *   <li><b>下单校验一律读 DB</b>（{@link #getFromDb}）：缓存里的库存是"展示值"，
 *       不能作为扣减依据，否则会把超卖引入系统。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitServiceImpl implements BenefitService {

    /** 分类字典：项目规模小，用常量维护即可，不必再建一张字典表 */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>() {{
        put("STUDY", "学习办公");
        put("FOOD", "餐饮美食");
        put("LIFE", "生活日用");
        put("SPORT", "运动健康");
        put("OTHER", "其他");
    }};

    private final BenefitGoodsMapper goodsMapper;
    private final TwoLevelCache twoLevelCache;
    private final CacheProperties cacheProperties;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /** 商品列表缓存版本号（写操作后自增） */
    private static final String GOODS_LIST_VERSION_KEY = RedisKeyConst.PREFIX + "cache:goods:listver";

    @Override
    public PageResult<GoodsVO> pageGoods(String category, String keyword, String sort, long page, long size) {
        // 列表缓存键带"列表版本号"：管理端任何写操作都会 INCR 版本号，
        // 旧键立刻不可达并靠 TTL 自然回收，避免"改价后 5 分钟内学生看到旧价"
        String cacheKey = RedisKeyConst.PREFIX + "cache:goods:list:v" + currentListVersion() + ":"
                + category + ":" + keyword + ":" + sort + ":" + page + ":" + size;
        Duration ttl = Duration.ofMinutes(5);
        return twoLevelCache.get(cacheKey, GoodsPageCache.class, ttl,
                () -> GoodsPageCache.of(queryPage(category, keyword, sort, page, size, true))).toPage();
    }

    @Override
    public GoodsDetailVO detail(Long goodsId) {
        String cacheKey = RedisKeyConst.CACHE_GOODS.formatted(goodsId);
        Duration ttl = Duration.ofSeconds(cacheProperties.getGoodsTtlSeconds());
        return twoLevelCache.get(cacheKey, GoodsDetailVO.class, ttl, () -> {
            BenefitGoods goods = goodsMapper.selectById(goodsId);
            if (goods == null) {
                return null;
            }
            GoodsDetailVO vo = new GoodsDetailVO();
            copy(goods, vo);
            vo.setDetail(goods.getDetail());
            vo.setStartTime(goods.getStartTime());
            vo.setEndTime(goods.getEndTime());
            vo.setTagList(StringUtils.hasText(goods.getTags())
                    ? Arrays.stream(goods.getTags().split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList()
                    : List.of());
            // 同分类推荐：查 4 个同类上架商品
            vo.setRecommends(goodsMapper.selectList(new LambdaQueryWrapper<BenefitGoods>()
                            .eq(BenefitGoods::getCategory, goods.getCategory())
                            .eq(BenefitGoods::getStatus, BizConst.STATUS_ENABLED)
                            .ne(BenefitGoods::getId, goodsId)
                            .orderByDesc(BenefitGoods::getSort)
                            .last("limit 4"))
                    .stream().map(this::toVo).toList());
            return vo;
        });
    }

    @Override
    public List<CategoryVO> categories() {
        List<BenefitGoods> goods = goodsMapper.selectList(new LambdaQueryWrapper<BenefitGoods>()
                .eq(BenefitGoods::getStatus, BizConst.STATUS_ENABLED)
                .select(BenefitGoods::getCategory));
        Map<String, Long> counts = goods.stream()
                .collect(Collectors.groupingBy(BenefitGoods::getCategory, Collectors.counting()));
        List<CategoryVO> list = new ArrayList<>();
        CATEGORY_NAMES.forEach((code, name) -> {
            CategoryVO vo = new CategoryVO();
            vo.setCode(code);
            vo.setName(name);
            vo.setCount(counts.getOrDefault(code, 0L));
            list.add(vo);
        });
        return list;
    }

    @Override
    public BenefitGoods getFromDb(Long goodsId) {
        return goodsId == null ? null : goodsMapper.selectById(goodsId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long goodsId, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        // 条件更新：stock >= quantity 才扣，靠数据库行锁保证不超卖
        int updated = goodsMapper.update(null, new LambdaUpdateWrapper<BenefitGoods>()
                .eq(BenefitGoods::getId, goodsId)
                .ge(BenefitGoods::getStock, quantity)
                .setSql("stock = stock - " + quantity)
                .setSql("sold_count = sold_count + " + quantity));
        if (updated > 0) {
            evictGoodsCache(goodsId);
        }
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(Long goodsId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<BenefitGoods>()
                .eq(BenefitGoods::getId, goodsId)
                .setSql("stock = stock + " + quantity)
                .setSql("sold_count = GREATEST(sold_count - " + quantity + ", 0)"));
        evictGoodsCache(goodsId);
    }

    @Override
    public PageResult<GoodsVO> pageForAdmin(String category, String keyword, Integer status, long page, long size) {
        LambdaQueryWrapper<BenefitGoods> wrapper = new LambdaQueryWrapper<BenefitGoods>()
                .eq(StringUtils.hasText(category), BenefitGoods::getCategory, category)
                .eq(status != null, BenefitGoods::getStatus, status)
                .like(StringUtils.hasText(keyword), BenefitGoods::getTitle, keyword)
                .orderByAsc(BenefitGoods::getSort)
                .orderByDesc(BenefitGoods::getId);
        Page<BenefitGoods> result = goodsMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVo).toList(),
                result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(GoodsSaveDTO dto) {
        BenefitGoods entity = new BenefitGoods();
        entity.setId(dto.getId());
        entity.setGoodsCode(dto.getGoodsCode());
        entity.setTitle(dto.getTitle());
        entity.setSubTitle(dto.getSubTitle());
        entity.setCoverUrl(dto.getCoverUrl());
        entity.setCategory(dto.getCategory());
        entity.setPricePoint(dto.getPricePoint());
        entity.setOriginPrice(dto.getOriginPrice() == null ? 0 : dto.getOriginPrice());
        entity.setStock(dto.getStock());
        entity.setTags(dto.getTags());
        entity.setDetail(dto.getDetail());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? BizConst.STATUS_ENABLED : dto.getStatus());

        if (dto.getId() == null) {
            entity.setSoldCount(0);
            entity.setVersion(0);
            goodsMapper.insert(entity);
        } else {
            goodsMapper.updateById(entity);
            evictGoodsCache(dto.getId());
        }
        // 列表缓存整体失效（键里带条件，无法精确定位，直接按前缀清）
        evictListCache();
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long goodsId, Integer status) {
        BenefitGoods update = new BenefitGoods();
        update.setId(goodsId);
        update.setStatus(status);
        goodsMapper.updateById(update);
        evictGoodsCache(goodsId);
        evictListCache();
    }

    @Override
    public long countAll() {
        Long count = goodsMapper.selectCount(new LambdaQueryWrapper<BenefitGoods>()
                .eq(BenefitGoods::getStatus, BizConst.STATUS_ENABLED));
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private PageResult<GoodsVO> queryPage(String category, String keyword, String sort,
                                          long page, long size, boolean onlyEnabled) {
        LambdaQueryWrapper<BenefitGoods> wrapper = new LambdaQueryWrapper<BenefitGoods>()
                .eq(onlyEnabled, BenefitGoods::getStatus, BizConst.STATUS_ENABLED)
                .eq(StringUtils.hasText(category), BenefitGoods::getCategory, category)
                .and(StringUtils.hasText(keyword), w -> w.like(BenefitGoods::getTitle, keyword)
                        .or().like(BenefitGoods::getSubTitle, keyword))
                .orderByAsc(BenefitGoods::getSort)
                .orderByDesc(BenefitGoods::getId);
        if ("hot".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(BenefitGoods::getSoldCount);
        } else if ("priceAsc".equalsIgnoreCase(sort)) {
            wrapper.orderByAsc(BenefitGoods::getPricePoint);
        } else if ("priceDesc".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(BenefitGoods::getPricePoint);
        }
        Page<BenefitGoods> result = goodsMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVo).toList(),
                result.getTotal(), page, size);
    }

    private GoodsVO toVo(BenefitGoods goods) {
        GoodsVO vo = new GoodsVO();
        copy(goods, vo);
        return vo;
    }

    private void copy(BenefitGoods goods, GoodsVO vo) {
        vo.setId(goods.getId());
        vo.setGoodsCode(goods.getGoodsCode());
        vo.setTitle(goods.getTitle());
        vo.setSubTitle(goods.getSubTitle());
        vo.setCoverUrl(goods.getCoverUrl());
        vo.setCategory(goods.getCategory());
        vo.setCategoryName(CATEGORY_NAMES.getOrDefault(goods.getCategory(), goods.getCategory()));
        vo.setPricePoint(goods.getPricePoint());
        vo.setOriginPrice(goods.getOriginPrice());
        vo.setStock(goods.getStock());
        vo.setSoldCount(goods.getSoldCount());
        vo.setTags(goods.getTags());
        vo.setSort(goods.getSort());
        vo.setStatus(goods.getStatus());
    }

    private void evictGoodsCache(Long goodsId) {
        // 延迟双删：先删一次，再延迟删一次，兜住"删缓存后又被并发读回填旧值"的窗口
        twoLevelCache.evictWithDoubleDelete(RedisKeyConst.CACHE_GOODS.formatted(goodsId), 500);
    }

    /** 列表缓存版本号：写操作后自增，让带旧版本号的缓存键立即失效 */
    private long currentListVersion() {
        try {
            String value = stringRedisTemplate.opsForValue().get(GOODS_LIST_VERSION_KEY);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            // Redis 异常时退化为 0，缓存仍可用（最多读到 5 分钟旧数据）
            return 0L;
        }
    }

    /** 列表缓存整体失效：不扫 KEYS，只 bump 版本号（O(1) 且不会阻塞 Redis） */
    private void evictListCache() {
        try {
            stringRedisTemplate.opsForValue().increment(GOODS_LIST_VERSION_KEY);
        } catch (Exception e) {
            log.warn("商品列表缓存版本号自增失败：{}", e.getMessage());
        }
    }

    /**
     * 分页结果缓存包装。
     * <p>直接缓存 {@code PageResult} 也可以，但泛型反序列化容易踩坑，
     * 这里用固定结构承载，读写都明确。</p>
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GoodsPageCache {
        private List<GoodsVO> records;
        private long total;
        private long page;
        private long size;

        public static GoodsPageCache of(PageResult<GoodsVO> pageResult) {
            return new GoodsPageCache(pageResult.getRecords(), pageResult.getTotal(),
                    pageResult.getPage(), pageResult.getSize());
        }

        public PageResult<GoodsVO> toPage() {
            return PageResult.of(records, total, page, size);
        }
    }
}
