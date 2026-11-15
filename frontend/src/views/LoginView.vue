<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <el-card class="card" header="Đăng nhập">
      <el-form @submit.prevent="submit" label-position="top">
        <el-form-item label="Tên đăng nhập"><el-input v-model="form.username" autofocus /></el-form-item>
        <el-form-item label="Mật khẩu"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">Đăng nhập</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.wrap { display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f3f5f8; }
.card { width: 360px; }
</style>
