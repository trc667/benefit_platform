/**
 * 被测系统的 API 客户端 + 压测前置夹具（用户、券模板、兑换码批次）。
 *
 * 所有夹具都走真实接口创建（不用 SQL 直插），这样压测跑的就是完整业务链路：
 * 券库存走 Redis 预扣 + DB 乐观锁，兑换码走 Base32 生成 + BitMap 核销。
 */
import { httpRequest, makeAgent } from './core.mjs';

export class Api {
  constructor({ host = '127.0.0.1', port = 8090, maxSockets = 64 } = {}) {
    this.host = host;
    this.port = port;
    this.base = '/api';
    this.agent = makeAgent(maxSockets);
  }

  /** 底层请求：返回 {status, ms, json}，失败不抛异常 */
  call(method, path, { body, token, headers = {}, timeoutMs = 20000 } = {}) {
    return httpRequest({
      host: this.host,
      port: this.port,
      method,
      path: path.startsWith('/api') ? path : `${this.base}${path}`,
      body,
      agent: this.agent,
      timeoutMs,
      headers: token ? { Authorization: `Bearer ${token}`, ...headers } : headers
    });
  }

  get = (p, o) => this.call('GET', p, o);
  post = (p, o) => this.call('POST', p, o);

  /** 断言用：拿 code==0 的 data，失败直接抛错（夹具阶段希望快速失败） */
  async need(method, path, opts) {
    const r = await this.call(method, path, opts);
    if (r.status < 200 || r.status >= 400 || !r.json || r.json.code !== 0) {
      throw new Error(`${method} ${path} 失败: status=${r.status} code=${r.json?.code} msg=${r.json?.message || r.error}`);
    }
    return r.json.data;
  }

  async login(username, password = '123456') {
    const data = await this.need('POST', '/auth/login', { body: { username, password } });
    return { username, token: data.token, userId: data.userInfo.id, role: data.userInfo.role };
  }

  async register(username) {
    const data = await this.need('POST', '/auth/register', {
      body: { username, password: '123456', nickname: username, studentNo: username, school: '压测大学' }
    });
    return { username, token: data.token, userId: data.userInfo.id };
  }

  // ---------------- 业务接口 ----------------
  signin = (token) => this.post('/signin/do?source=APP', { token });
  account = (token) => this.get('/point/account', { token });
  pointRecords = (token, size = 50) => this.get(`/point/records?page=1&size=${size}`, { token });
  receiveCoupon = (token, templateId) => this.post('/coupon/receive', { token, body: { templateId } });
  myCoupons = (token, status = '') => this.get(`/coupon/mine?page=1&size=50${status ? `&status=${status}` : ''}`, { token });
  settlePreview = (token, goodsId, quantity = 1) => this.get(`/order/settle/preview?goodsId=${goodsId}&quantity=${quantity}`, { token });
  createOrder = (token, dto) => this.post('/order/create', { token, body: dto });
  payOrder = (token, orderNo) => this.post('/order/pay', { token, body: { orderNo } });
  exchange = (token, code) => this.post(`/redeem/exchange?code=${encodeURIComponent(code)}`, { token });
  goodsPage = (token, size = 12) => this.get(`/benefit/goods/page?page=1&size=${size}`, { token });

  // ---------------- 管理端 ----------------
  adminStatCoupons = (token) => this.get('/admin/coupon/stat', { token });
  adminSaveCouponTemplate = (token, dto) => this.post('/admin/coupon/template/save', { token, body: dto });
  adminCreateRedeemBatch = (token, dto) => this.post('/admin/redeem/batch/create', { token, body: dto });
  adminRedeemCodes = (token, batchNo, count) => this.get(`/admin/redeem/batch/${batchNo}/codes?count=${count}`, { token });
  adminOutboxPage = (token, size = 5) => this.get(`/admin/mq/outbox/page?page=1&size=${size}`, { token });

  /** 停掉连接池（压测结束后释放 socket） */
  close() {
    this.agent.destroy();
  }
}

/** 生成一批唯一用户名：lt_<batch>_<i> */
export function makeUsernames(count, batch = Date.now().toString(36)) {
  return Array.from({ length: count }, (_, i) => `lt${batch}${String(i).padStart(4, '0')}`);
}
