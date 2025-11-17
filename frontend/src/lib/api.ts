import axios, { AxiosHeaders } from 'axios'

// VITE_API_BASE_URL이 설정되지 않았을 때만 기본값 사용 (빈 문자열은 상대 경로로 사용)
const envUrl = import.meta.env.VITE_API_BASE_URL
const API_BASE_URL = envUrl !== undefined ? envUrl : 'http://localhost:8080'

const api = axios.create({ baseURL: API_BASE_URL || undefined })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    if (!config.headers) {
      config.headers = new AxiosHeaders()
    }
    if (config.headers instanceof AxiosHeaders) {
      config.headers.set('Authorization', `Bearer ${token}`)
    } else {
      const existing =
        typeof config.headers === 'object' ? (config.headers as Record<string, unknown>) : {}
      config.headers = AxiosHeaders.from({
        ...existing,
        Authorization: `Bearer ${token}`
      })
    }
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
        { baseURL: API_BASE_URL || undefined, headers: { Authorization: `Bearer ${refreshToken}` } }
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


