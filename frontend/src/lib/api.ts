import axios from 'axios'

const api = axios.create({ baseURL: 'http://localhost:8080' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers = { ...(config.headers || {}), Authorization: `Bearer ${token}` }
  }
  return config
})

let refreshing = false
api.interceptors.response.use(
  (r) => r,
  async (err) => {
    if (err.response?.status !== 401 || refreshing) throw err
    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) throw err
    try {
      refreshing = true
      const { data } = await axios.post(
        '/api/auth/refresh',
        null,
        { baseURL: 'http://localhost:8080', headers: { Authorization: `Bearer ${refreshToken}` } }
      )
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      err.config.headers.Authorization = `Bearer ${data.accessToken}`
      return api.request(err.config)
    } finally {
      refreshing = false
    }
  }
)

export default api


