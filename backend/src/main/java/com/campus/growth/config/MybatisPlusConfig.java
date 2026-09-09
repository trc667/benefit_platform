package com.campus.growth.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.campus.growth.common.constant.BizConst;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：单页上限 100，防止前端传 size=100000 把数据库打挂
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit((long) BizConst.MAX_PAGE_SIZE);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 乐观锁：@Version 字段自动处理（积分账户、商品库存、券模板）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 阻断全表更新/删除：个人项目最容易手滑的地方，加上它心里踏实
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
