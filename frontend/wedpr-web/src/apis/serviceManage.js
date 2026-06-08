import http from '../utils/http'

// 服务列表
const getPublishList = (params) => http.post('/publish/list', params)
// 发布对外服务
const publishService = (params) => http.post('/publish/create', params)
// 更新服务
const updateService = (params) => http.post('/publish/update', params)
// 撤回服务
const revokeService = (params) => http.delete('/publish/revoke/' + params.serviceId, params)
// 获取服务使用记录
const getServerUseRecord = (params) => http.post('/publish/invoke/search', params)

export default { getPublishList, publishService, updateService, revokeService, getServerUseRecord }
