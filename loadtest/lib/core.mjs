/**
 * 压测内核：零依赖（只用 Node 内置 http）+ keep-alive 连接池 + 百分位统计。
 *
 * 为什么不用 k6/JMeter：
 *   1. 这两个工具要么装不上、要么要额外下载几十 MB 二进制，clone 下来跑不动；
 *   2. 本项目要压的接口不多（签到 / 领券 / 结算 / 兑换），自研 100 行内核足够，
 *      而且能把"并发正确性断言"直接写进同一个脚本里（k6 里做 DB 断言很别扭）。
 *   Node 单进程在本机回环上能打出 1 万+ RPS，远超本项目的容量拐点，不是瓶颈。
 */
import http from 'node:http';

/** keep-alive 连接池：并发数决定 socket 上限，避免每次请求都三次握手 */
export function makeAgent(maxSockets) {
  return new http.Agent({
    keepAlive: true,
    maxSockets: Math.max(1, maxSockets),
    maxFreeSockets: Math.max(1, maxSockets)
  });
}

/**
 * 发一个请求，永远 resolve（失败也返回对象），方便统计错误率。
 * @returns {Promise<{status:number, ms:number, json:any, body:string, error?:string}>}
 */
export function httpRequest({ host = '127.0.0.1', port = 8090, method = 'GET', path, headers = {}, body, agent, timeoutMs = 15000 }) {
  return new Promise((resolve) => {
    const payload = body === undefined || body === null
      ? null
      : Buffer.from(typeof body === 'string' ? body : JSON.stringify(body));
    const finalHeaders = { ...headers };
    if (payload) {
      finalHeaders['Content-Type'] = 'application/json; charset=utf-8';
      finalHeaders['Content-Length'] = payload.length;
    }
    const started = process.hrtime.bigint();
    const done = (extra) => {
      const ms = Number(process.hrtime.bigint() - started) / 1e6;
      resolve({ ms, ...extra });
    };
    const req = http.request({ host, port, method, path, headers: finalHeaders, agent }, (res) => {
      let d = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { d += c; });
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(d); } catch { /* 非 JSON 也能统计 */ }
        done({ status: res.statusCode, body: d, json });
      });
    });
    req.setTimeout(timeoutMs, () => req.destroy(new Error('timeout')));
    req.on('error', (e) => done({ status: 0, body: '', json: null, error: e.message }));
    if (payload) req.write(payload);
    req.end();
  });
}

/** 百分位（输入必须已升序） */
export function percentile(sortedAsc, p) {
  if (!sortedAsc.length) return 0;
  const idx = Math.min(sortedAsc.length - 1, Math.max(0, Math.ceil((p / 100) * sortedAsc.length) - 1));
  return sortedAsc[idx];
}

/**
 * 汇总一批采样。
 * @param samples [{ms,status,json,error}]
 * @param elapsedMs 实际墙钟耗时（用于算吞吐）
 */
export function summarize(samples, elapsedMs) {
  const ok = samples.filter((s) => s.status >= 200 && s.status < 400 && (s.json?.code === 0 || s.json == null));
  const lat = samples.filter((s) => s.status > 0).map((s) => s.ms).sort((a, b) => a - b);
  const bizFail = samples.filter((s) => s.json && s.json.code !== 0);
  const httpFail = samples.filter((s) => s.status === 0 || s.status >= 400);
  return {
    total: samples.length,
    ok: ok.length,
    bizFail: bizFail.length,
    httpFail: httpFail.length,
    successRate: samples.length ? +(ok.length / samples.length * 100).toFixed(2) : 0,
    rps: elapsedMs > 0 ? +(samples.length / (elapsedMs / 1000)).toFixed(1) : 0,
    okRps: elapsedMs > 0 ? +(ok.length / (elapsedMs / 1000)).toFixed(1) : 0,
    avg: lat.length ? +(lat.reduce((a, b) => a + b, 0) / lat.length).toFixed(1) : 0,
    p50: +percentile(lat, 50).toFixed(1),
    p90: +percentile(lat, 90).toFixed(1),
    p95: +percentile(lat, 95).toFixed(1),
    p99: +percentile(lat, 99).toFixed(1),
    max: lat.length ? +lat[lat.length - 1].toFixed(1) : 0,
    /** 业务错误码分布，例如 {2001: 299} 表示 299 次"今天已经签到过了" */
    codeDist: samples.reduce((acc, s) => {
      const c = s.json?.code ?? (s.status === 0 ? 'network' : `http${s.status}`);
      acc[c] = (acc[c] || 0) + 1;
      return acc;
    }, {})
  };
}

/**
 * 持续压测：vus 个并发循环请求，直到 durationMs 到点。
 * 适合无副作用 / 幂等接口（结算预览、商品详情）。
 */
export async function runLoad({ vus, durationMs, task }) {
  const samples = [];
  const startedAt = Date.now();
  const deadline = startedAt + durationMs;
  const worker = async () => {
    while (Date.now() < deadline) {
      samples.push(await task());
    }
  };
  await Promise.all(Array.from({ length: vus }, worker));
  return { samples, elapsedMs: Date.now() - startedAt };
}

/**
 * 突发压测：vus 个请求"同时"发出，各打一次。
 * 适合一次性动作（签到、领券秒杀、兑换码核销）——这些接口天然不能重复打。
 */
export async function runBurst({ vus, task }) {
  const samples = new Array(vus);
  const startedAt = Date.now();
  await Promise.all(
    Array.from({ length: vus }, async (_, i) => {
      samples[i] = await task(i);
    })
  );
  return { samples, elapsedMs: Date.now() - startedAt };
}

/** 阶梯压测：按 stages 逐级加压，找出错误率/P99 突变的拐点 */
export async function runStages({ stages, perStageMs, taskFactory }) {
  const rounds = [];
  for (const vus of stages) {
    const { samples, elapsedMs } = await runLoad({ vus, durationMs: perStageMs, task: taskFactory(vus) });
    rounds.push({ vus, summary: summarize(samples, elapsedMs) });
    // 级间留一点时间让服务端连接与队列回落，避免上一级的影响叠加
    await new Promise((r) => setTimeout(r, 1500));
  }
  return rounds;
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
