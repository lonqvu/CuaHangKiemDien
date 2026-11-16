import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'

declare module 'vue-router' {
  interface RouteMeta { admin?: boolean; title?: string; public?: boolean }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { title: 'Đăng nhập' } },
    { path: '/change-password', component: () => import('../views/ChangePasswordView.vue'), meta: { title: 'Đổi mật khẩu', public: true } },
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      redirect: () => (useAuth().isAdmin ? '/dashboard' : '/sale'),
      children: [
        // Man cu -> man moi: Home/ThongKe(o dau) -> Tong quan
        { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { admin: true, title: 'Tổng quan' } },
        // Sale + ProductDialog + ModifyProduct + CustomerDialog + PayDialog
        { path: 'sale', component: () => import('../views/SaleView.vue'), meta: { title: 'Bán hàng' } },
        // OrderHistory + OrderDetailDialog. /orders/123 mo thang chi tiet hoa don (link chia se duoc)
        { path: 'orders/:id(\\d+)?', component: () => import('../views/OrdersView.vue'), meta: { admin: true, title: 'Hóa đơn' } },
        // Customers + ThanhToanNo
        { path: 'customers/:id(\\d+)?', component: () => import('../views/CustomersView.vue'), meta: { admin: true, title: 'Khách hàng & công nợ' } },
        // Product
        { path: 'products', component: () => import('../views/ProductsView.vue'), meta: { admin: true, title: 'Sản phẩm' } },
        // Data (danh muc san pham)
        { path: 'categories', component: () => import('../views/CategoriesView.vue'), meta: { admin: true, title: 'Loại hàng' } },
        // ThongKe
        { path: 'reports', component: () => import('../views/ReportsView.vue'), meta: { admin: true, title: 'Thống kê' } },
        // Account
        { path: 'accounts', component: () => import('../views/AccountsView.vue'), meta: { admin: true, title: 'Tài khoản' } },
        // ChangePassWord
        { path: 'password', component: () => import('../views/ChangePasswordView.vue'), meta: { title: 'Đổi mật khẩu' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

// Nhan vien (STAFF) chi vao duoc Ban hang + Doi mat khau (giong HomeUser cu)
router.beforeEach((to) => {
  const auth = useAuth()
  if (to.path !== '/login' && !to.meta.public && !auth.loggedIn) return '/login'
  if (to.meta.admin && !auth.isAdmin) return '/sale'
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? 'Bán hàng'} · Nhôm Sắt Kiểm Diên`
})

export default router
