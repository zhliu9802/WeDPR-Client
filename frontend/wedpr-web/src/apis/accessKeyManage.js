import http from '../utils/http'

// 生成key
const genAccessKey = (params) => http.post('/credential/applyFor', params)
// 新增key
const queryAccessKeyList = (params) => http.post('/credential/query', params)
// 删除key
const deleteAccessKey = (params) => http.post('/credential/delete', params)
// 更新key
const updateAccessKey = (params) => http.post('/credential/update', params)

export default {
  genAccessKey,
  queryAccessKeyList,
  deleteAccessKey,
  updateAccessKey
}
