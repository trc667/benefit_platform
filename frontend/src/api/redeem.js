import request from '@/utils/request'

/** 兑换码：/redeem */

/** 兑换 → {batchNo,bizType,rewardValue,balance,message} */
export const exchangeCode = (code) => request.post('/redeem/exchange', { code })
