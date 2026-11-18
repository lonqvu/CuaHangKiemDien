<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '', otp: '' })
// buoc 2: tai khoan bat xac thuc 2 lop -> hoi ma 6 so tu app (hoac ma du phong)
const needOtp = ref(false)
const otpInput = ref()
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
    const r = await auth.login(form.username, form.password, needOtp.value ? form.otp : undefined)
    if (r.otpRequired) {
      needOtp.value = true
      await nextTick()
      otpInput.value?.focus()
      return
    }
    router.push('/')
  } catch (e: any) {
    // loi dang nhap hien ngay duoi form, khong chi bang toast
    error.value = e?.response?.data?.message || 'Không đăng nhập được, thử lại'
    if (needOtp.value) form.otp = ''
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
          <el-input v-model="form.username" :disabled="needOtp" size="large" autocomplete="username" autofocus />
        </el-form-item>
        <el-form-item label="Mật khẩu" prop="password">
          <el-input v-model="form.password" :disabled="needOtp" size="large" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item v-if="needOtp" label="Mã xác thực (6 số)">
          <el-input ref="otpInput" v-model="form.otp" size="large" inputmode="numeric" autocomplete="one-time-code" maxlength="11"
                    placeholder="Mở ứng dụng xác thực trên điện thoại" />
          <div class="muted small otp-hint">Tài khoản đã bật xác thực 2 lớp. Mất điện thoại thì nhập một mã dự phòng (dạng abcd-efgh).</div>
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="err" />
        <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">Đăng nhập</el-button>
        <router-link to="/change-password" class="change">Đổi mật khẩu</router-link>
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
.otp-hint { line-height: 1.4; margin-top: 4px; font-size: 12px; }
.submit { width: 100%; }
.change { display: inline-block; margin-top: 16px; color: var(--steel-800); font-size: 14px; }
@media (max-width: 800px) {
  .login { grid-template-columns: 1fr; }
  .side { padding: 24px; min-height: 140px; }
  .profiles { display: none; }
  .side-text b { font-size: 22px; }
}
</style>
