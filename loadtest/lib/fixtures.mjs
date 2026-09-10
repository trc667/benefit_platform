/**
 * 压测夹具：准备用户、积分、券模板、兑换码批次、商品。
 * 全部走真实接口，可重复执行；用户与 token 会缓存到 loadtest/.users.json。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { sleep } from './core.mjs';
import { makeUsernames } from './api.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const USERS_FILE = path.join(HERE, '..', '.users.json');

/** 登录失败/注册被限流时的退避：429 就等一会再试 */
export async function withRetry(fn, { attempts = 8, baseDelayMs = 400 } = {}) {
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      lastErr = e;
      if (!/429|500|网络|timeout/i.test(e.message)) throw e;
      await sleep(baseDelayMs * (i + 1));
    }
  }
  throw lastErr;
}

/**
 * 保证有 count 个可用压测学生账号（注册 + 登录），返回 [{username, token, userId}]。
 * 注册接口有 2 QPS / IP 的限流，第一次准备 300 个账号大约需要 2-3 分钟，之后走缓存。
 */
export async function ensureUsers(api, { count, password = '123456', reuse = true } = {}) {
  let cache = { users: [] };
  if (reuse && fs.existsSync(USERS_FILE)) {
    try { cache = JSON.parse(fs.readFileSync(USERS_FILE, 'utf8')); } catch { /* 忽略损坏缓存 */ }
  }
  const users = (cache.users || []).slice(0, count);

  // 缓存里的 token 有效期 12 小时，过期就重新登录（这里用登录成功与否判断，不做时间计算）
  const ready = [];
  for (const u of users) {
    const probe = await api.get('/auth/me', { token: u.token });
    if (probe.json?.code === 0) ready.push(u);
    else {
      const re = await withRetry(async () => {
        const r = await api.get('/auth/me', { token: u.token });
        if (r.json?.code === 0) return { ...u };
        return api.login(u.username, password);
      });
      ready.push(re);
    }
  }

  // 不够就注册新的（受 2 QPS 限流，失败退避重试）
  const missing = count - ready.length;
  if (missing > 0) {
    const names = makeUsernames(missing, Date.now().toString(36));
    for (const username of names) {
      const created = await withRetry(() => api.register(username));
      ready.push(created);
    }
  }

  fs.writeFileSync(USERS_FILE, JSON.stringify({ updatedAt: new Date().toISOString(), users: ready }, null, 2));
  return ready;
}

/** 给用户补积分（运营接口，幂等键唯一，避免重复入账） */
export async function grantPoints(api, adminToken, user, points, reason = '压测前置充值') {
  const bizNo = `lt-${user.userId}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  return api.need('POST', '/admin/point/adjust', {
    token: adminToken,
    body: { userId: user.userId, changePoint: points, bizNo, reason }
  });
}

/** 建一个用于"秒杀"的券模板：库存 stock、单人限领 perUserLimit */
export async function createSeizeCouponTemplate(api, adminToken, { stock, perUserLimit = 1, title } = {}) {
  const data = await api.need('POST', '/admin/coupon/template/save', {
    token: adminToken,
    body: {
      title: title || `压测秒杀券-${stock}张-${new Date().toISOString().slice(11, 19)}`,
      couponType: 'CASH',
      faceValue: 5,
      thresholdPoint: 0,
      totalCount: stock,
      perUserLimit,
      validType: 'RELATIVE',
      validDays: 7,
      status: 1
    }
  });
  return data.id;
}

/** 建一个兑换码批次（每个码核销后给 rewardValue 积分），返回批次号与码列表 */
export async function createRedeemBatch(api, adminToken, { count, rewardValue = 10 } = {}) {
  const batch = await api.need('POST', '/admin/redeem/batch/create', {
    token: adminToken,
    body: {
      title: `压测批次-${new Date().toISOString().slice(11, 19)}`,
      bizType: 'POINT',
      rewardValue,
      totalCount: count,
      remark: '压测用'
    }
  });
  const codes = await api.need('GET', `/admin/redeem/batch/${batch.batchNo}/codes?count=${count}`, { token: adminToken });
  return { batchNo: batch.batchNo, codes, rewardValue };
}

/** 找一件在售、库存充足、价格便宜的商品用于下单压测 */
export async function pickGoods(api, token, { maxPrice = 200 } = {}) {
  const page = await api.need('GET', '/benefit/goods/page?page=1&size=50&sort=priceAsc', { token });
  const list = (page.records || page.list || []).filter((g) => g.status === 1 && g.stock > 0);
  const cheap = list.find((g) => g.pricePoint <= maxPrice) || list[0];
  if (!cheap) throw new Error('没有可用于压测的在售商品');
  return cheap;
}

/** 券模板统计（管理端）：用于断言"发放数 == 库存" */
export async function couponStat(api, adminToken, templateId) {
  const list = await api.need('GET', '/admin/coupon/stat', { token: adminToken });
  return (list || []).find((s) => s.templateId === templateId || s.id === templateId) || null;
}

/** 签到流水条数（从积分流水里数 SIGNIN 记录，用来断言"重复签到没有重复加分"） */
export async function countSigninRecords(api, token) {
  const data = await api.need('GET', '/point/records?page=1&size=50&bizType=SIGNIN', { token });
  return (data.records || []).length;
}
