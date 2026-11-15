import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue') },
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      redirect: '/sale',
      children: [
        { path: 'sale', component: () => import('../views/SaleView.vue') },
        { path: 'products', component: () => import('../views/ProductsView.vue'), meta: { admin: true } },
        { path: 'customers', component: () => import('../views/CustomersView.vue'), meta: { admin: true } },
        { path: 'orders', component: () => import('../views/OrdersView.vue'), meta: { admin: true } },
        { path: 'reports', component: () => import('../views/ReportsView.vue'), meta: { admin: true } },
      ],
    },
  ],
})

// Nhan vien (STAFF) chi duoc vao man Ban hang
router.beforeEach((to) => {
  const auth = useAuth()
  if (to.path !== '/login' && !auth.loggedIn) return '/login'
  if (to.meta.admin && !auth.isAdmin) return '/sale'
})

export default router
