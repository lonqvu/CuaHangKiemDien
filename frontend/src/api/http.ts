import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 30_000 })

http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

/** Thong bao loi cho nguoi dung: noi ro chuyen gi va lam gi tiep, khong hien "Request failed with status code 500". */
export function errorMessage(err: any): string {
  const serverMsg = err?.response?.data?.message
  if (typeof serverMsg === 'string' && serverMsg) return serverMsg
  if (err?.code === 'ECONNABORTED') return 'Máy chủ phản hồi quá lâu. Kiểm tra mạng rồi thử lại.'
  if (!err?.response) return 'Không kết nối được máy chủ. Kiểm tra mạng hoặc máy chủ rồi thử lại.'
  switch (err.response.status) {
    case 403: return 'Tài khoản của bạn không có quyền làm việc này.'
    case 404: return 'Không tìm thấy dữ liệu. Có thể đã bị xóa; tải lại trang.'
    case 409: return 'Dữ liệu vừa thay đổi. Tải lại rồi thử lại.'
    default: return 'Máy chủ gặp lỗi. Thử lại; nếu vẫn lỗi, báo quản trị.'
  }
}

http.interceptors.response.use(
  (r) => r,
  (err) => {
    const isLogin = err.config?.url?.includes('/auth/') || err.config?.silent // dang nhap / doi mat khau / tai PDF tu hien loi
    if (isLogin || axios.isCancel(err)) return Promise.reject(err)
    // Chi dang xuat khi het han/sai token (401). Loi khac (500...) chi bao loi, khong day nguoi dung ra man dang nhap.
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      location.href = '/login'
    } else {
      ElMessage.error({ message: errorMessage(err), duration: 5000, showClose: true })
    }
    return Promise.reject(err)
  },
)

export default http
