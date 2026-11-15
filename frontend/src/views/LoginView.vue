<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')
const rules: FormRules = {
  username: [{ required: true, message: 'Nhập tên đăng nhập', trigger: 'blur' }],
  password: [{ required: true, message: 'Nhập mật khẩu', trigger: 'blur' }],
}

async function submit() {
  error.value = ''
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    router.push('/')
  } catch (e: any) {
    // loi dang nhap hien ngay duoi form, khong chi bang toast
    error.value = e?.response?.data?.message || 'Không đăng nhập được, thử lại'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="side" aria-hidden="true">
      <div class="profiles">
        <span v-for="n in 9" :key="n" />
      </div>
      <div class="side-text">
        <b>Nhôm Sắt Kiểm Diên</b>
        <p>Bán hàng theo tấm, tính hệ số, ghi công nợ khách quen.</p>
      </div>
    </section>
    <main class="form-wrap">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="form" @submit.prevent="submit">
        <h1>Đăng nhập</h1>
        <p class="muted">Dùng tài khoản do quản trị cấp.</p>
        <el-form-item label="Tên đăng nhập" prop="username">
          <el-input v-model="form.username" size="large" autocomplete="username" autofocus />
        </el-form-item>
        <el-form-item label="Mật khẩu" prop="password">
          <el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="err" />
        <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">Đăng nhập</el-button>
      </el-form>
    </main>
  </div>
</template>

<style scoped>
.login { min-height: 100vh; display: grid; grid-template-columns: minmax(0, 5fr) minmax(360px, 4fr); background: var(--surface); }
.side { background: var(--steel-900); color: #E2E8F0; position: relative; overflow: hidden; display: flex; align-items: flex-end; padding: 48px; }
/* mat cat cac thanh nhom/sat: hop, chu U, chu L xep lop */
.profiles { position: absolute; inset: 48px 48px auto auto; display: grid; grid-template-columns: repeat(3, 72px); gap: 18px; opacity: 0.9; }
.profiles span { width: 72px; height: 72px; border: 10px solid #334155; }
.profiles span:nth-child(3n + 2) { border-top-color: transparent; }
.profiles span:nth-child(3n) { border-top-color: transparent; border-right-color: transparent; }
.profiles span:nth-child(5) { border-color: #94A3B8; border-top-color: transparent; }
.side-text b { display: block; font-size: 28px; color: #fff; letter-spacing: -0.02em; }
.side-text p { margin: 8px 0 0; color: var(--steel-400); max-width: 34ch; }
.form-wrap { display: grid; place-items: center; padding: 32px 24px; }
.form { width: 100%; max-width: 360px; }
h1 { font-size: 26px; }
.form > p { margin: 4px 0 24px; }
.err { margin-bottom: 16px; }
.submit { width: 100%; }
@media (max-width: 800px) {
  .login { grid-template-columns: 1fr; }
  .side { padding: 24px; min-height: 140px; }
  .profiles { display: none; }
  .side-text b { font-size: 22px; }
}
</style>
