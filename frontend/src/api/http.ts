import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api' })

http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

http.interceptors.response.use(
  (r) => r,
  (err) => {
    const isLogin = err.config?.url?.includes('/auth/') // dang nhap / doi mat khau tu hien loi duoi form
    if (isLogin) return Promise.reject(err) // man dang nhap tu hien loi duoi form
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      location.href = '/login'
    } else {
      ElMessage.error(err.response?.data?.message || err.message)
    }
    return Promise.reject(err)
  },
)

export default http
