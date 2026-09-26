import axios from 'axios'

/**
 * 统一走 /api 前缀：
 *  - 容器里由 nginx 把 /api/ 反代到 backend:8080
 *  - 本地 vite dev 由 vite.config.js 的 proxy 转到 127.0.0.1:8363
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

/** 后端统一返回 {ok, message, data}；失败时把中文业务提示原样抽出来给页面用 */
function messageOf(error) {
  const body = error && error.response ? error.response.data : null
  if (body && typeof body === 'object' && body.message) {
    return body.message
  }
  if (error && error.message) {
    return error.message
  }
  return '网络异常，请稍后重试'
}

async function call(promise) {
  try {
    const res = await promise
    const body = res.data
    if (body && body.ok === false) {
      throw new Error(body.message || '操作失败')
    }
    return body ? body.data : null
  } catch (error) {
    throw new Error(messageOf(error))
  }
}

export const api = {
  // 模块一：球台
  tables: () => call(http.get('/tables')),
  floorPlan: () => call(http.get('/tables/floor-plan')),
  createTable: (body) => call(http.post('/tables', body)),
  updateTable: (id, body) => call(http.put(`/tables/${id}`, body)),
  tableMaintenance: (id) => call(http.post(`/tables/${id}/maintenance`)),
  tableRestore: (id) => call(http.post(`/tables/${id}/restore`)),

  // 模块二：开局与计费
  sessions: () => call(http.get('/sessions')),
  openSessions: () => call(http.get('/sessions/open')),
  openSession: (body) => call(http.post('/sessions', body)),
  closeSession: (id) => call(http.post(`/sessions/${id}/close`)),
  settleSession: (id, payMode) => call(http.post(`/sessions/${id}/settle`, { payMode })),
  transferSession: (id, body) => call(http.post(`/sessions/${id}/transfer`, body)),
  sessionsOfTable: (tableId) => call(http.get(`/sessions/by-table/${tableId}`)),

  // 模块三：器材与租借
  gears: () => call(http.get('/gears')),
  createGear: (body) => call(http.post('/gears', body)),
  updateGear: (id, body) => call(http.put(`/gears/${id}`, body)),
  repairGear: (id) => call(http.post(`/gears/${id}/repair`)),
  rentals: () => call(http.get('/rentals')),
  rent: (body) => call(http.post('/rentals', body)),
  returnGear: (id, body) => call(http.post(`/rentals/${id}/return`, body)),

  // 模块四：会员与充值
  members: () => call(http.get('/members')),
  createMember: (body) => call(http.post('/members', body)),
  updateMember: (id, body) => call(http.put(`/members/${id}`, body)),
  recharge: (id, body) => call(http.post(`/members/${id}/recharge`, body)),
  freezeMember: (id) => call(http.post(`/members/${id}/freeze`)),
  unfreezeMember: (id) => call(http.post(`/members/${id}/unfreeze`)),
  memberRecharges: (id) => call(http.get(`/members/${id}/recharges`)),
  allRecharges: () => call(http.get('/recharges'))
}

export default api
