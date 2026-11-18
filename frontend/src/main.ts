import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import vi from 'element-plus/es/locale/lang/vi'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import App from './App.vue'
import router from './router'
import { isDialogCancel, isShownApiError } from './api/http'

const app = createApp(App)
// Nut bam goi API loi (409 trung ten, 400...): interceptor da hien thong bao -> khong de thanh loi chua xu ly.
// Bam "Huy" o hop xac nhan cung khong phai loi. Loi khac van log binh thuong.
app.config.errorHandler = (err, _vm, info) => {
  if (isShownApiError(err) || isDialogCancel(err)) return
  console.error(err, info)
}
window.addEventListener('unhandledrejection', (e) => {
  if (isShownApiError(e.reason) || isDialogCancel(e.reason)) e.preventDefault()
})
app.use(createPinia()).use(router).use(ElementPlus, { locale: vi }).mount('#app')
