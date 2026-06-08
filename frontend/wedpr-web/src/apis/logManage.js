import http from '../utils/http'

// 查询操作日志明细（站点端 API）
const getLogList = (params) => http.get('/logList', params)

export default { getLogList }
