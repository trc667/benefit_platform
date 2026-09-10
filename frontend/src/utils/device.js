/**
 * 设备标识：首次访问生成一个 UUID 存在 localStorage，之后每个请求都带上。
 *
 * 为什么需要它：校园权益平台的薅羊毛方式是"一个人开一堆小号"，
 * 只按 IP 做风控会误伤整个宿舍楼（同一个 NAT 出口），所以服务端的风控是**设备维度**的。
 * 设备号不参与鉴权、不含个人信息，只用于"这台设备今天注册了几个号 / 给几个账号签过到"。
 */
const KEY = 'cg_device_id';

function uuid() {
  // crypto.randomUUID 在 https / localhost 可用；降级到时间戳 + 随机串
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `d-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

let cached = '';

/** 获取（必要时创建）设备号，失败时返回空串——服务端会把空设备号归入 nodev:{ip} 桶 */
export function getDeviceId() {
  if (cached) return cached;
  try {
    let id = localStorage.getItem(KEY);
    if (!id) {
      id = uuid();
      localStorage.setItem(KEY, id);
    }
    cached = id;
    return id;
  } catch (e) {
    return '';
  }
}
