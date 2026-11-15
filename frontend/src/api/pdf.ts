import { ElMessage } from 'element-plus'
import http from './http'

/**
 * Mo file PDF do backend in bang mau JasperReports cu (hoa don, bien lai cong no).
 * Mo tab truoc khi tai de trinh duyet khong chan popup; nguoi dung in / luu tu trinh xem PDF.
 */
export async function openPdf(path: string) {
  const win = window.open('', '_blank')
  if (win) win.document.title = 'Đang tạo bản in...'
  try {
    const res = await http.get(path, { responseType: 'blob', silent: true } as any)
    const url = URL.createObjectURL(res.data as Blob)
    if (win) win.location.href = url
    else window.location.href = url
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e: any) {
    win?.close()
    let msg = 'Không tạo được bản in'
    const data = e?.response?.data
    if (data instanceof Blob) {
      try { msg = JSON.parse(await data.text()).message || msg } catch { /* giu thong bao mac dinh */ }
    }
    ElMessage.error(msg)
  }
}
