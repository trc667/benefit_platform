import request from '@/utils/request'

/** 认证：/auth（登录、注册无需 token，其余由拦截器统一注入） */

/** 登录 → {token,userInfo} */
export const login = (data) => request.post('/auth/login', data)

/** 学生注册 → {token,userInfo} */
export const register = (data) => request.post('/auth/register', data)

/** 退出登录（后端清 Redis 白名单） */
export const logout = () => request.post('/auth/logout')

/** 当前登录用户 → UserInfoVO */
export const getMe = () => request.get('/auth/me')

/** 修改个人资料 → UserInfoVO（补齐学校/学号会联动"完善资料"任务） */
export const updateProfile = (data) => request.put('/auth/profile', data)
