import request from '@/utils/request'

/** 任务：/task */

/** 任务列表 → [{taskCode,taskName,taskType,targetValue,progress,status,pointAward,icon,description,periodKey}] */
export const getTaskList = () => request.get('/task/list')

/** 上报进度 → {progress,targetValue,status} */
export const reportTaskProgress = (data) => request.post('/task/progress/report', data)

/** 领取奖励 → {pointAward,balance} */
export const claimTaskReward = (taskCode) => request.post('/task/reward/claim', { taskCode })
