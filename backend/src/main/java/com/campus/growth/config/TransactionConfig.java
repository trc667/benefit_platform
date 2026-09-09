package com.campus.growth.config;

import com.campus.growth.common.aspect.AspectOrder;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 事务配置。
 *
 * <h3>这里为什么要显式写 order</h3>
 * <p>Spring 事务切面默认顺序是 {@code Ordered.LOWEST_PRECEDENCE}（即最内层）。
 * 当锁切面使用 {@link AspectOrder#LOCK} = 10 时，锁确实在事务外层，看起来没问题；
 * 但一旦有人新增了一个没有指定 order 的切面（默认也是 LOWEST_PRECEDENCE），
 * 顺序就变得不可预测。把事务顺序显式固定为 {@link AspectOrder#TRANSACTION} = 30，
 * 并让所有业务切面都必须落在它之外，才能保证"锁永远包住事务"这一不变量。</p>
 *
 * <p>最终执行链：限流(0) → 锁(10) → 幂等(20) → 事务(30) → 操作日志(40)。</p>
 */
@Configuration
@EnableTransactionManagement(order = AspectOrder.TRANSACTION)
public class TransactionConfig {
}
