import request from '@/utils/request'

/** 权益商品：/benefit */

/** 商品卡片分页 → PageResult（sort: default|hot|priceAsc|priceDesc） */
export const getGoodsPage = (params) => request.get('/benefit/goods/page', { params })

/** 商品详情 */
export const getGoodsDetail = (id) => request.get(`/benefit/goods/${id}`)

/** 分类 → [{code,name,count}] */
export const getGoodsCategories = () => request.get('/benefit/categories')
