import request from '@/utils/request'

/** AI 助手：/ai */

/** 对话 → {sessionId,reply,model,toolCalls:[{name,args,result,success,costMs}],costMs} */
export const chat = (data) => request.post('/ai/chat', data)

/** 会话列表 */
export const getSessions = () => request.get('/ai/sessions')

/** 消息列表 */
export const getMessages = (sessionId) => request.get('/ai/messages', { params: { sessionId } })

/** 删除会话 */
export const deleteSession = (sessionId) => request.delete(`/ai/session/${sessionId}`)
