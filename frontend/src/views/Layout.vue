<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ShoppingCart, Document, User, Goods, Collection, DataLine, UserFilled, Odometer, Key, SwitchButton, Menu as MenuIcon,
} from '@element-plus/icons-vue'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const route = useRoute()
const router = useRouter()
const drawer = ref(false)

const groups = computed(() => {
  const g = [
    { title: 'Bán hàng', items: [
      ...(auth.isAdmin ? [{ to: '/dashboard', label: 'Tổng quan', icon: Odometer }] : []),
      { to: '/sale', label: 'Bán hàng', icon: ShoppingCart },
      ...(auth.isAdmin ? [{ to: '/orders', label: 'Hóa đơn', icon: Document }] : []),
    ] },
  ]
  if (auth.isAdmin) {
    g.push(
      { title: 'Danh mục', items: [
        { to: '/customers', label: 'Khách hàng & công nợ', icon: User },
        { to: '/products', label: 'Sản phẩm', icon: Goods },
        { to: '/categories', label: 'Loại hàng', icon: Collection },
      ] },
      { title: 'Quản trị', items: [
        { to: '/reports', label: 'Thống kê', icon: DataLine },
        { to: '/accounts', label: 'Tài khoản', icon: UserFilled },
      ] },
    )
  }
  return g
})

watch(() => route.path, () => (drawer.value = false))

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar" :class="{ open: drawer }" aria-label="Điều hướng chính">
      <div class="brand">
        <span class="mark" aria-hidden="true">NS</span>
        <div><b>Nhôm Sắt Kiểm Diên</b><small>Quản lý bán hàng</small></div>
      </div>
      <nav>
        <section v-for="g in groups" :key="g.title">
          <h2>{{ g.title }}</h2>
          <router-link v-for="i in g.items" :key="i.to" :to="i.to" class="nav-item">
            <el-icon :size="18" aria-hidden="true"><component :is="i.icon" /></el-icon>
            <span>{{ i.label }}</span>
          </router-link>
        </section>
      </nav>
      <div class="me">
        <div class="who">
          <b>{{ auth.user?.fullName }}</b>
          <small>{{ auth.isAdmin ? 'Quản trị' : 'Nhân viên bán hàng' }}</small>
        </div>
        <router-link to="/password" class="nav-item small"><el-icon aria-hidden="true"><Key /></el-icon><span>Đổi mật khẩu</span></router-link>
        <button class="nav-item small" type="button" @click="logout"><el-icon aria-hidden="true"><SwitchButton /></el-icon><span>Đăng xuất</span></button>
      </div>
    </aside>
    <div v-if="drawer" class="scrim" @click="drawer = false" />

    <div class="main">
      <div class="topbar">
        <el-button text :icon="MenuIcon" aria-label="Mở menu" @click="drawer = true" />
        <b>{{ route.meta.title }}</b>
      </div>
      <main class="content"><router-view /></main>
    </div>
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100vh; }
.sidebar {
  width: 232px; flex-shrink: 0; background: var(--steel-900); color: #E2E8F0;
  display: flex; flex-direction: column; position: sticky; top: 0; height: 100vh; overflow-y: auto;
}
.brand { display: flex; gap: var(--space-lg); align-items: center; padding: var(--space-xl) var(--space-xl) var(--space-lg); }
.brand b { display: block; font-size: 15px; color: #fff; line-height: 1.3; }
.brand small { color: var(--steel-400); font-size: 12px; }
/* dau logo: thanh thep cat vat */
.mark {
  width: 36px; height: 36px; flex-shrink: 0; display: grid; place-items: center; border-radius: 6px;
  font-family: var(--font-mono); font-weight: 600; font-size: 14px; color: var(--steel-900);
  background: linear-gradient(135deg, #E2E8EE 0 50%, #94A3B8 50% 100%);
}
nav { flex: 1; padding: 0 var(--space-md); }
nav h2 { font-size: 12px; font-weight: 500; color: var(--steel-400); padding: var(--space-lg) var(--space-md) var(--space-sm); }
.nav-item {
  display: flex; align-items: center; gap: var(--space-lg); width: 100%; min-height: 40px;
  padding: 0 var(--space-md); border-radius: var(--radius-md); color: #CBD5E1; text-decoration: none;
  font: inherit; font-size: 14px; background: none; border: 0; cursor: pointer; text-align: left;
  transition: background-color 0.15s, color 0.15s;
}
.nav-item:hover { background: rgba(255, 255, 255, 0.06); color: #fff; }
.nav-item.router-link-active { background: #fff; color: var(--steel-900); font-weight: 600; }
.nav-item.small { min-height: 36px; font-size: 13px; }
.me { border-top: 1px solid rgba(255, 255, 255, 0.1); padding: var(--space-lg) var(--space-md) var(--space-xl); }
.who { padding: 0 var(--space-md) var(--space-md); }
.who b { display: block; color: #fff; font-size: 14px; }
.who small { color: var(--steel-400); font-size: 12px; }
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar { display: none; }
.content { padding: var(--space-2xl); max-width: 1440px; width: 100%; }
.scrim { display: none; }

@media (max-width: 960px) {
  .sidebar { position: fixed; z-index: 30; left: 0; transform: translateX(-100%); transition: transform 0.2s ease-out; }
  .sidebar.open { transform: none; box-shadow: var(--shadow-lg); }
  .scrim { display: block; position: fixed; inset: 0; z-index: 20; background: rgba(15, 23, 42, 0.4); }
  .topbar {
    display: flex; align-items: center; gap: var(--space-md); position: sticky; top: 0; z-index: 10;
    background: var(--surface); border-bottom: 1px solid var(--steel-200); padding: var(--space-sm) var(--space-md);
  }
  .content { padding: var(--space-lg); }
}
</style>
