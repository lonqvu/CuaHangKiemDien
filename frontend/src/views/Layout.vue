<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()
function logout() { auth.logout(); router.push('/login') }
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-header class="header">
      <el-menu mode="horizontal" router :default-active="$route.path" class="menu">
        <el-menu-item index="/sale">Bán hàng</el-menu-item>
        <template v-if="auth.isAdmin">
          <el-menu-item index="/orders">Hóa đơn</el-menu-item>
          <el-menu-item index="/customers">Khách hàng</el-menu-item>
          <el-menu-item index="/products">Sản phẩm</el-menu-item>
          <el-menu-item index="/reports">Thống kê</el-menu-item>
        </template>
      </el-menu>
      <div class="user">
        {{ auth.user?.fullName }}
        <el-button link type="primary" @click="logout">Đăng xuất</el-button>
      </div>
    </el-header>
    <el-main><router-view /></el-main>
  </el-container>
</template>

<style scoped>
.header { display: flex; align-items: center; justify-content: space-between; padding: 0; border-bottom: 1px solid #e4e7ed; }
.menu { flex: 1; border-bottom: none; }
.user { padding: 0 16px; white-space: nowrap; }
</style>
