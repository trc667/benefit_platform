import request from '@/utils/request'

/** 积分与排行榜：/point */

/** 积分账户 → {balance,totalEarned,totalUsed,growthLevel,nextLevelPoint} */
export const getPointAccount = () => request.get('/point/account')

/** 流水分页 → PageResult */
export const getPointRecords = (params) => request.get('/point/records', { params })

/** 排行榜 → [{rank,userId,nickname,avatar,point,isMe}]（type: TOTAL|MONTH） */
export const getPointRank = (params) => request.get('/point/rank', { params })

/** 我的排名 → {rank,point,total} */
export const getMyRank = (type = 'TOTAL') => request.get('/point/rank/me', { params: { type } })
