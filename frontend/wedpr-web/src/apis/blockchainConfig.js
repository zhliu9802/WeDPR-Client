import http from '../utils/http'

const getBlockchainConfig = () => http.get('/blockchain/getConfig')
const saveBlockchainConfig = (params) => http.post('/blockchain/saveConfig', params)

export default { getBlockchainConfig, saveBlockchainConfig }
