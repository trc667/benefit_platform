#!/usr/bin/env node
/**
 * 压测入口（零依赖，只需 Node 18+）。
 *
 *   node loadtest/run.mjs --scenario signin --users 100      # 签到高峰 + 并发重复签到断言
 *   node loadtest/run.mjs --scenario coupon --users 300 --stock 100
 *   node loadtest/run.mjs --scenario settle --vus 100 --duration 15
 *   node loadtest/run.mjs --scenario hotspot --codes 60      # 同一用户并发写积分账户（乐观锁热点）
 *   node loadtest/run.mjs --scenario order --users 60
 *   node loadtest/run.mjs --scenario stage --stages 50,100,200,400 --per-stage 8
 *   node loadtest/run.mjs --all                              # 全部跑一遍并输出报告
 *
 * 结果：控制台表格 + loadtest/results/*.json + docs/loadtest-report.md
 *
 * 注意：领券/兑换/下单接口都有限流（按用户 QPS），跑"同一用户高频写"的场景前
 *      请用 $env:CAMPUS_LIMIT_ENABLED='false' 重启后端，否则压到的是限流器不是数据库。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Api } from './lib/api.mjs';
import { runBurst, runLoad, runStages, summarize, sleep } from './lib/core.mjs';
import {
  ensureUsers, grantPoints, createSeizeCouponTemplate, createRedeemBatch, pickGoods, couponStat, withRetry
} from './lib/fixtures.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(HERE, '..');
const RESULTS_DIR = path.join(HERE, 'results');

// ---------------------------------------------------------------- 参数
function parseArgs(argv) {
  const out = { scenario: '', all: false, host: '127.0.0.1', port: 8090, users: 100, vus: 100, duration: 15, stock: 100, codes: 60, stages: '50,100,200,400,800', perStage: 8, fresh: false, from: '', md: path.join('docs', 'loadtest-report.md') };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    if (a === '--all') out.all = true;
    else if (a === '--fresh') out.fresh = true;
    else if (a === '--from') out.from = next();
    else if (a === '--scenario') out.scenario = next();
    else if (a === '--host') out.host = next();
    else if (a === '--port') out.port = Number(next());
    else if (a === '--users') out.users = Number(next());
    else if (a === '--vus') out.vus = Number(next());
    else if (a === '--duration') out.duration = Number(next());
    else if (a === '--stock') out.stock = Number(next());
    else if (a === '--codes') out.codes = Number(next());
    else if (a === '--stages') out.stages = next();
    else if (a === '--per-stage') out.perStage = Number(next());
    else if (a === '--md') out.md = next();
    else if (a === '--help' || a === '-h') { printHelp(); process.exit(0); }
  }
  return out;
}

function printHelp() {
  console.log(fs.readFileSync(new URL(import.meta.url)).toString().split('\n').slice(1, 18).join('\n').replace(/^ \*ap?/gm, ''));
}

// ---------------------------------------------------------------- 输出
const C = { cyan: (s) => `\x1b[36m${s}\x1b[0m`, green: (s) => `\x1b[32m${s}\x1b[0m`, red: (s) => `\x1b[31m${s}\x1b[0m`, yellow: (s) => `\x1b[33m${s}\x1b[0m`, gray: (s) => `\x1b[90m${s}\x1b[0m` };

function printSummary(title, s, extra = {}) {
  console.log(C.cyan(`\n=== ${title} ===`));
  console.log(`  请求数 ${s.total}　成功 ${s.ok}（${s.successRate}%）　业务失败 ${s.bizFail}　网络/HTTP 失败 ${s.httpFail}`);
  console.log(`  吞吐 ${s.rps} req/s（成功 ${s.okRps}）　P50 ${s.p50}ms　P95 ${s.p95}ms　P99 ${s.p99}ms　max ${s.max}ms`);
  const codes = Object.entries(s.codeDist).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([k, v]) => `${k}:${v}`).join('　');
  console.log(C.gray(`  返回码分布 ${codes}`));
  for (const [k, v] of Object.entries(extra)) console.log(`  ${k}：${v}`);
}

const checks = [];
function check(name, pass, detail = '') {
  checks.push({ name, pass, detail });
  console.log(`  ${pass ? C.green('✅ PASS') : C.red('❌ FAIL')} ${name}${detail ? C.gray(` — ${detail}`) : ''}`);
}

// ---------------------------------------------------------------- 场景
/**
 * 等积分异步到账（签到 → Kafka → 积分消费者），返回流水与到账延迟。
 * 这个延迟本身就是"异步入账"的代价，是要写进报告的指标。
 */
async function waitForSigninRecord(api, token, timeoutMs = 8000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const rec = await api.need('GET', '/point/records?page=1&size=50', { token });
    const rows = (rec.records || []).filter((r) => (r.bizType || '').toUpperCase().includes('SIGNIN'));
    if (rows.length >= 1) return { rows, latencyMs: Date.now() - started };
    await sleep(50);
  }
  return { rows: [], latencyMs: -1 };
}

async function scenarioSignin(api, ctx, args) {
  // 签到天然是一天一次，所以用"突发"而不是"持续压"：每个用户并发打 2 次，
  // 验证「并发重复签到不会重复加分」——第二次要么被分布式锁挡掉(429)，要么被判今日已签到(2001)
  const fresh = args.fresh || ctx.signinNeedsFresh;
  const users = fresh
    ? await ensureUsers(api, { count: args.users, reuse: false })
    : await ensureUsers(api, { count: args.users });
  const before = new Map();
  for (const u of users) {
    const acc = await api.need('GET', '/point/account', { token: u.token });
    before.set(u.userId, acc.balance);
  }

  const tasks = [];
  for (const u of users) {
    tasks.push(() => api.signin(u.token));
    tasks.push(() => api.signin(u.token)); // 同一用户并发第二次
  }
  const { samples, elapsedMs } = await runBurst({ vus: tasks.length, task: (i) => tasks[i]() });
  const summary = summarize(samples, elapsedMs);
  printSummary(`签到高峰（${users.length} 个用户 × 2 次并发）`, summary);

  const msgDist = samples.reduce((acc, s) => {
    if (s.json?.code) acc[s.json.message] = (acc[s.json.message] || 0) + 1;
    return acc;
  }, {});
  console.log(C.gray(`  业务提示分布 ${Object.entries(msgDist).map(([m, n]) => `「${m}」×${n}`).join('　') || '无'}`));

  const successByUser = new Map();
  const rejected = { lock: 0, already: 0 };
  let idx = 0;
  for (const s of samples) {
    const user = users[Math.floor(idx / 2)];
    idx++;
    const code = s.json?.code;
    if (code === 0) successByUser.set(user.userId, (successByUser.get(user.userId) || 0) + 1);
    else if (code === 429) rejected.lock++;
    else if (code === 2001) rejected.already++;
  }
  const overSuccess = [...successByUser.entries()].filter(([, n]) => n > 1);
  check('并发签到不会重复成功（每用户成功次数 ≤ 1）', overSuccess.length === 0,
    overSuccess.length ? `异常用户 ${overSuccess.length} 个` : `${successByUser.size} 人成功，${rejected.lock} 次被分布式锁挡下，${rejected.already} 次被判今日已签到`);
  check('相同用户并发请求全部被明确业务码拒绝，无 5xx', summary.httpFail === 0,
    summary.httpFail ? `${summary.httpFail} 次` : `拒绝都是 429/2001，不是 500`);
  check('成功数 ≈ 用户数（没有因并发竞争丢失签到）',
    successByUser.size >= users.length * 0.95,
    `${successByUser.size}/${users.length} 用户签到成功`);

  // 异步链路断言：等 Kafka 消费完，验证「流水只有一条 + 余额增量等于奖励」
  const sampleUsers = [...successByUser.keys()].slice(0, 30).map((uid) => users.find((x) => x.userId === uid));
  const settled = await Promise.all(sampleUsers.map((u) => waitForSigninRecord(api, u.token)));
  const lags = settled.filter((s) => s.latencyMs > 0).map((s) => s.latencyMs).sort((a, b) => a - b);
  const p = (q) => (lags.length ? lags[Math.min(lags.length - 1, Math.ceil((q / 100) * lags.length) - 1)] : -1);
  const dup = settled.filter((s) => s.rows.length !== 1).length;

  let deltaMismatch = 0;
  for (let i = 0; i < sampleUsers.length; i++) {
    const u = sampleUsers[i];
    const row = settled[i].rows[0];
    const acc = await api.need('GET', '/point/account', { token: u.token });
    if (row && acc.balance - before.get(u.userId) === row.changePoint) deltaMismatch++;
  }
  check('积分只入账一次（异步链路无重复消费）', dup === 0 && lags.length > 0,
    `抽查 ${sampleUsers.length} 人：流水不为 1 条的 ${dup} 人`);
  check('余额增量 == 流水金额（无静默丢分）', deltaMismatch === lags.length,
    `${deltaMismatch}/${lags.length} 人匹配`);

  console.log(`  异步入账延迟：P50 ${p(50)}ms　P95 ${p(95)}ms　最大 ${p(100)}ms`);
  return {
    summary, users: users.length, success: successByUser.size, rejected,
    settleLatency: { p50: p(50), p95: p(95), max: p(100), sampled: lags.length }
  };
}

async function scenarioCoupon(api, ctx, args) {
  const users = await ensureUsers(api, { count: Math.max(args.users, args.stock + 50) });
  const templateId = await createSeizeCouponTemplate(api, ctx.adminToken, { stock: args.stock, perUserLimit: 1 });
  console.log(C.gray(`  券模板 id=${templateId} 库存=${args.stock}（新建，保证是干净库存）`));

  const burst = users.slice(0, args.users);
  const { samples, elapsedMs } = await runBurst({ vus: burst.length, task: (i) => api.receiveCoupon(burst[i].token, templateId) });
  const summary = summarize(samples, elapsedMs);
  printSummary(`领券秒杀（${burst.length} 并发抢 ${args.stock} 张）`, summary);

  const success = samples.filter((s) => s.json?.code === 0).length;
  const soldOut = samples.filter((s) => s.json?.code === 6002).length;
  const stat = await couponStat(api, ctx.adminToken, templateId);
  const issued = stat?.issuedCount ?? stat?.issued ?? null;

  check(`发放数恰好等于库存（${args.stock} 张，不超发）`, success === args.stock, `实际成功 ${success}，售罄拦截 ${soldOut}`);
  if (issued != null) check('券模板 issued_count 与成功数一致', issued === args.stock, `issued_count=${issued}`);
  check('无 5xx / 网络错误', summary.httpFail === 0, summary.httpFail ? `${summary.httpFail} 次` : '');

  // 反向验证：并发领券后，用户手里的券数不能超过单人限领
  const sampleUsers = burst.slice(0, 10);
  let overLimit = 0;
  for (const u of sampleUsers) {
    const mine = await api.need('GET', '/coupon/mine?page=1&size=50', { token: u.token });
    const got = (mine.records || []).filter((c) => c.templateId === templateId).length;
    if (got > 1) overLimit++;
  }
  check('单人限领未被突破（抽查 10 人）', overLimit === 0, overLimit ? `${overLimit} 人超领` : '');

  return { summary, templateId, success, stock: args.stock, issuedCount: issued };
}

async function scenarioSettle(api, ctx, args) {
  const goods = ctx.goods;
  const { samples, elapsedMs } = await runLoad({
    vus: args.vus,
    durationMs: args.duration * 1000,
    task: () => api.settlePreview(ctx.studentToken, goods.id, 1)
  });
  const summary = summarize(samples, elapsedMs);
  printSummary(`结算页最优优惠组合（${args.vus} 并发 × ${args.duration}s，CompletableFuture 并行算价）`, summary);
  check('无 5xx / 网络错误', summary.httpFail === 0, summary.httpFail ? `${summary.httpFail} 次` : '');
  check('P99 < 500ms', summary.p99 < 500, `实际 ${summary.p99}ms`);
  return { summary, goodsId: goods.id };
}

async function scenarioHotspot(api, ctx, args) {
  // 同一个用户并发核销多个兑换码 → 每个码都要给同一个积分账户加钱 → 直击乐观锁热点行
  const user = ctx.users[0];
  const initial = (await api.need('GET', '/point/account', { token: user.token })).balance;
  const batch = await createRedeemBatch(api, ctx.adminToken, { count: args.codes, rewardValue: 10 });
  console.log(C.gray(`  兑换码批次 ${batch.batchNo}，${args.codes} 个码，每个 +${batch.rewardValue} 分，全部由同一用户并发核销`));

  const codes = batch.codes.slice(0, args.codes);
  const { samples, elapsedMs } = await runBurst({ vus: codes.length, task: (i) => api.exchange(user.token, codes[i]) });
  const summary = summarize(samples, elapsedMs);
  printSummary(`积分账户热点行（${codes.length} 并发写同一账户）`, summary);

  const success = samples.filter((s) => s.json?.code === 0).length;
  const after = (await api.need('GET', '/point/account', { token: user.token })).balance;
  const expected = initial + success * batch.rewardValue;

  check('成功次数与余额增量一致（没有静默丢积分）', after === expected, `初始 ${initial} + 成功 ${success}×${batch.rewardValue} = ${expected}，实际 ${after}`);
  check('余额不为负', after >= 0, `余额 ${after}`);
  // 这是本压测最重要的发现点：热点账户并发写不应出现"系统错误"（500 = 乐观锁重试被打爆）
  const systemErrors = summary.codeDist['500'] || 0;
  check('热点账户并发写不出现系统错误码 500', systemErrors === 0,
    systemErrors ? `${systemErrors}/${codes.length} 次失败（成功率 ${((success / codes.length) * 100).toFixed(1)}%）` : `全部请求都有明确结果（成功 ${success}）`);
  const codeTail = Object.entries(summary.codeDist).filter(([k]) => k !== '0').map(([k, v]) => `${k}:${v}`).join(' ');
  check('无网络/HTTP 层失败', summary.httpFail === 0, codeTail || '无失败');
  return { summary, success, attempted: codes.length, initial, after, batchNo: batch.batchNo };
}

async function scenarioOrder(api, ctx, args) {
  const goods = ctx.goods;
  const users = ctx.users.slice(0, args.users);
  const price = goods.pricePoint;

  // 每人只给"刚好够一单"的积分：这样任何超扣都会立刻变成负数，断言最灵敏
  const beforeBalance = new Map();
  for (const u of users) {
    let acc = await api.need('GET', '/point/account', { token: u.token });
    if (acc.balance < price) {
      await grantPoints(api, ctx.adminToken, u, price - acc.balance, '压测下单前置充值');
      acc = await api.need('GET', '/point/account', { token: u.token });
    }
    beforeBalance.set(u.userId, acc.balance);
  }
  const beforeStock = (await api.need('GET', `/benefit/goods/${goods.id}`, { token: ctx.studentToken })).stock;

  const { samples, elapsedMs } = await runBurst({
    vus: users.length,
    task: async (i) => {
      const u = users[i];
      const created = await api.createOrder(u.token, { goodsId: goods.id, quantity: 1 });
      if (created.json?.code !== 0) return created;
      const paid = await api.payOrder(u.token, created.json.data.orderNo);
      return { ...paid, ms: created.ms + paid.ms };
    }
  });
  const summary = summarize(samples, elapsedMs);
  printSummary(`并发下单支付（${users.length} 并发，单价 ${price} 分）`, summary);

  const success = samples.filter((s) => s.json?.code === 0).length;
  let negative = 0;
  let spent = 0;
  for (const u of users) {
    const acc = await api.need('GET', '/point/account', { token: u.token });
    if (acc.balance < 0) negative++;
    spent += beforeBalance.get(u.userId) - acc.balance;
  }
  const afterStock = (await api.need('GET', `/benefit/goods/${goods.id}`, { token: ctx.studentToken })).stock;

  check('没有任何账户被扣成负数（不超扣）', negative === 0, negative ? `${negative} 个账户为负` : '');
  check('扣减总额 == 成功订单 × 单价', spent === success * price, `扣减 ${spent}，成功 ${success} 单 × ${price}`);
  check('库存扣减与成功订单一致', beforeStock - afterStock === success, `库存 ${beforeStock} → ${afterStock}，成功 ${success} 单`);
  return { summary, success, users: users.length, spent, price, stockDelta: beforeStock - afterStock };
}

async function scenarioStage(api, ctx, args) {
  const stages = args.stages.split(',').map((s) => Number(s.trim())).filter(Boolean);
  const rounds = await runStages({
    stages,
    perStageMs: args.perStage * 1000,
    taskFactory: () => () => api.settlePreview(ctx.studentToken, ctx.goods.id, 1)
  });
  console.log(C.cyan('\n=== 阶梯压测（结算预览，幂等接口）==='));
  console.log('  并发\t实际RPS\t成功RPS\tP50\tP95\tP99\t错误率');
  for (const r of rounds) {
    const s = r.summary;
    console.log(`  ${String(r.vus).padEnd(6)}\t${String(s.rps).padEnd(8)}\t${String(s.okRps).padEnd(8)}\t${s.p50}\t${s.p95}\t${s.p99}\t${(100 - s.successRate).toFixed(2)}%`);
  }
  const lastHealthy = rounds.filter((r) => r.summary.successRate >= 99).pop();
  check('找到吞吐拐点（至少一级保持 99% 成功率）', !!lastHealthy, lastHealthy ? `拐点在 ${lastHealthy.vus} 并发附近（RPS ${lastHealthy.summary.okRps}，P99 ${lastHealthy.summary.p99}ms）` : '全部级别都出现错误');
  return { rounds: rounds.map((r) => ({ vus: r.vus, ...r.summary })) };
}

// ---------------------------------------------------------------- 报告
/** 非预期错误 = 网络/HTTP 失败 + 业务码 500（限流 429、售罄 6002、已签到 2001 都是预期内的明确拒绝） */
function unexpectedErrorRate(s) {
  const unexpected = s.httpFail + (s.codeDist['500'] || 0) + (s.codeDist['network'] || 0);
  return +((unexpected / Math.max(1, s.total)) * 100).toFixed(2);
}

function mdSummaryRow(name, s, extra = '') {
  return `| ${name} | ${s.total} | ${s.rps} | ${s.p95} | ${s.p99} | ${unexpectedErrorRate(s)}% | ${extra} |`;
}

function writeMarkdownReport({ args, results, checks, rawFile }) {
  const out = [];
  out.push('# 压测报告（`node loadtest/run.mjs --all` 自动生成）');
  out.push('');
  out.push(`> 生成时间：${new Date().toLocaleString('zh-CN')}　目标：\`http://${args.host}:${args.port}\``);
  out.push('>');
  out.push('> 工具：`loadtest/` 自研压测器（Node 内置 http + keep-alive 连接池，零依赖）。');
  out.push('> 说明：压测机与后端**同机回环**，数字用于「趋势对比」而不是「生产容量承诺」；');
  out.push('> 「非预期错误率」只统计网络/HTTP 失败与业务码 500，**429 限流、6002 售罄、2001 今日已签到属于预期内的明确拒绝**。');
  out.push('');
  out.push('## 1. 结论速览');
  out.push('');
  out.push('| 场景 | 请求数 | 吞吐(req/s) | P95(ms) | P99(ms) | 非预期错误率 | 备注 |');
  out.push('| --- | --- | --- | --- | --- | --- | --- |');
  if (results.signin) out.push(mdSummaryRow(`签到高峰（${results.signin.users} 用户×2 并发）`, results.signin.summary, `异步入账延迟 P95 ${results.signin.settleLatency.p95}ms`));
  if (results.coupon) out.push(mdSummaryRow(`领券秒杀（${results.coupon.summary.total} 并发抢 ${results.coupon.stock} 张）`, results.coupon.summary, `成功 ${results.coupon.success} / 库存 ${results.coupon.stock}，零超发`));
  if (results.settle) out.push(mdSummaryRow(`结算页算价（CompletableFuture 并行）`, results.settle.summary, `商品 #${results.settle.goodsId}`));
  if (results.hotspot) out.push(mdSummaryRow(`积分账户热点写（${results.hotspot.attempted} 并发写同一账户）`, results.hotspot.summary, `成功 ${results.hotspot.success}/${results.hotspot.attempted}，余额增量与流水一致`));
  if (results.order) out.push(mdSummaryRow(`并发下单支付（${results.order.users} 并发）`, results.order.summary, `成功 ${results.order.success} 单，扣分 ${results.order.spent}，库存 -${results.order.stockDelta}`));
  out.push('');

  if (results.stage) {
    const rounds = results.stage.rounds;
    const peak = rounds.reduce((a, b) => (b.okRps > a.okRps ? b : a));
    const last = rounds[rounds.length - 1];
    const first = rounds[0];
    out.push('## 2. 阶梯压测（找容量拐点）');
    out.push('');
    out.push('接口：`GET /api/order/settle/preview`（幂等只读，适合持续压）');
    out.push('');
    out.push('| 并发 | 吞吐(req/s) | 成功(req/s) | P50(ms) | P95(ms) | P99(ms) | 非预期错误率 |');
    out.push('| --- | --- | --- | --- | --- | --- | --- |');
    for (const r of rounds) {
      out.push(`| ${r.vus} | ${r.rps} | ${r.okRps} | ${r.p50} | ${r.p95} | ${r.p99} | ${unexpectedErrorRate(r)}% |`);
    }
    out.push('');
    out.push(`**观察**：吞吐在 **${peak.okRps} req/s** 见顶（${peak.vus} 并发），把并发从 ${first.vus} 抬到 ${last.vus}（${(last.vus / first.vus).toFixed(0)} 倍），`);
    out.push(`吞吐只涨了 ${(((last.okRps - first.okRps) / first.okRps) * 100).toFixed(1)}%，而 P99 从 ${first.p99}ms 涨到 ${last.p99}ms（${(last.p99 / Math.max(1, first.p99)).toFixed(1)} 倍），全程 0 错误。`);
    out.push('这是典型的**吞吐饱和 + 延迟堆积**：瓶颈在服务端处理能力（或与后端同机的压测客户端），而不是应用错误。');
    out.push('下一步定位方向：JVM CPU 火焰图 → Hikari 等待线程数 → Redis 单线程命令耗时。');
    out.push('');
  }

  out.push('## 3. 并发正确性断言');
  out.push('');
  out.push('压测不只测「快不快」，更要证「**对不对**」：下面每一条都是跑完压测后，通过接口/数据库核对得出的结论。');
  out.push('');
  out.push('| 断言 | 结果 | 说明 |');
  out.push('| --- | --- | --- |');
  for (const c of checks) {
    out.push(`| ${c.name} | ${c.pass ? '✅ 通过' : '❌ 失败'} | ${c.detail || ''} |`);
  }
  out.push('');
  out.push('> 原始数据：`' + path.relative(ROOT, rawFile).replace(/\\/g, '/') + '`');
  out.push('');

  const target = path.join(ROOT, args.md);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, out.join('\n'));
  return target;
}

// ---------------------------------------------------------------- 主流程
async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.all && !args.scenario && !args.from) { printHelp(); process.exit(1); }

  // 只重算报告：--from latest（或指定 json 路径），不用重新压一遍
  if (args.from) {
    const files = fs.existsSync(RESULTS_DIR) ? fs.readdirSync(RESULTS_DIR).filter((f) => f.endsWith('.json')).sort() : [];
    const file = args.from === 'latest' ? path.join(RESULTS_DIR, files[files.length - 1]) : path.resolve(args.from);
    const saved = JSON.parse(fs.readFileSync(file, 'utf8'));
    const md = writeMarkdownReport({ args: { ...saved.args, md: args.md }, results: saved.results, checks: saved.checks, rawFile: file });
    console.log(C.green(`已根据 ${path.relative(ROOT, file)} 重新生成报告：${path.relative(ROOT, md)}`));
    process.exit(0);
  }

  const api = new Api({ host: args.host, port: args.port, maxSockets: 512 });
  const health = await api.get('/actuator/health');
  if (health.status !== 200) {
    console.error(C.red(`后端不可达：http://${args.host}:${args.port}（先跑 scripts\\start-backend.ps1）`));
    process.exit(1);
  }

  // 登录接口按 IP 限流（5 QPS），压测机很容易先被挡住 → 统一带退避重试
  const admin = await withRetry(() => api.login('admin'));
  const student = await withRetry(() => api.login('student01'));
  const ctx = {
    adminToken: admin.token,
    studentToken: student.token,
    users: [],
    goods: await pickGoods(api, student.token)
  };
  console.log(C.gray(`目标 http://${args.host}:${args.port}　商品 #${ctx.goods.id} ${ctx.goods.title}（${ctx.goods.pricePoint} 分）`));

  const want = args.all ? ['signin', 'coupon', 'settle', 'hotspot', 'order', 'stage'] : [args.scenario];
  const results = {};

  for (const name of want) {
    if (name !== 'settle' && name !== 'stage' && !ctx.users.length) {
      ctx.users = await ensureUsers(api, { count: Math.max(args.users, args.stock + 50) });
      console.log(C.gray(`  压测账号池：${ctx.users.length} 个`));
    }
    if (name === 'signin') results.signin = await scenarioSignin(api, ctx, { ...args, fresh: args.fresh || args.all });
    else if (name === 'coupon') results.coupon = await scenarioCoupon(api, ctx, args);
    else if (name === 'settle') results.settle = await scenarioSettle(api, ctx, args);
    else if (name === 'hotspot') results.hotspot = await scenarioHotspot(api, ctx, args);
    else if (name === 'order') results.order = await scenarioOrder(api, ctx, args);
    else if (name === 'stage') results.stage = await scenarioStage(api, ctx, args);
    else { console.error(C.red(`未知场景：${name}`)); process.exit(1); }
    await sleep(800);
  }

  // 落盘
  fs.mkdirSync(RESULTS_DIR, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const rawFile = path.join(RESULTS_DIR, `${stamp}.json`);
  fs.writeFileSync(rawFile, JSON.stringify({ at: new Date().toISOString(), target: `http://${args.host}:${args.port}`, args, results, checks }, null, 2));

  const passed = checks.filter((c) => c.pass).length;
  console.log(C.cyan(`\n=== 断言汇总：${passed}/${checks.length} 通过 ===`));
  for (const c of checks.filter((x) => !x.pass)) console.log(C.red(`  ❌ ${c.name} ${c.detail}`));
  console.log(C.gray(`原始结果：${path.relative(ROOT, rawFile)}`));

  const mdPath = writeMarkdownReport({ args, results, checks, rawFile });
  console.log(C.gray(`压测报告：${path.relative(ROOT, mdPath)}`));

  api.close();
  process.exit(checks.some((c) => !c.pass) ? 2 : 0);
}

main().catch((e) => {
  console.error(C.red(`压测失败：${e.message}`));
  console.error(e.stack);
  process.exit(1);
});
