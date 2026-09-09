import request from '@/utils/request'

/** 签到：/signin */

/** 执行签到 → {signDate,continuousDays,pointAward,monthCount,extraAward} */
export const doSignIn = () => request.post('/signin/do')

/** 月度日历 → {month,todaySigned,continuousDays,monthCount,totalCount,days:[{day,signed,future}]} */
export const getSignInCalendar = (month) => request.get('/signin/calendar', { params: { month } })

/** 签到概览 → {continuousDays,monthCount,yearCount,lastSignDate} */
export const getSignInStat = () => request.get('/signin/stat')
