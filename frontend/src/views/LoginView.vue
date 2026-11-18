<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { rememberedUsername, useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()
const formRef = ref<FormInstance>()
// Anh nen khung trai (quan tri dat o man Tai khoan). Chua dat (404) / loi -> giao dien mac dinh.
const bgOk = ref(false)
// ":src" (bien), khong "src=" co dinh: Vue se coi duong dan co dinh la file tinh cua du an va bao loi khi build
const bgUrl = "/api/public/login-image"
const saved = rememberedUsername()
// remember: phien 7 ngay + nho ten; trust: khong hoi ma 2 lop tren may nay 7 ngay
const form = reactive({ username: saved, password: '', otp: '', remember: !!saved, trust: !!saved })
const passwordInput = ref()
onMounted(() => { if (saved) passwordInput.value?.focus() }) // da nho ten -> go mat khau luon
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
    const r = await auth.login(form.username, form.password, needOtp.value ? form.otp : undefined,
      { remember: form.remember, trustDevice: needOtp.value && form.trust })
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
    <section class="side" :class="{ 'has-bg': bgOk }" aria-hidden="true">
      <img :src="bgUrl" alt="" class="bg" :hidden="!bgOk" @load="bgOk = true" @error="bgOk = false" />
      <div v-if="!bgOk" class="profiles">
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
          <el-input v-model="form.username" :disabled="needOtp" size="large" autocomplete="username" :autofocus="!saved" />
        </el-form-item>
        <el-form-item label="Mật khẩu" prop="password">
          <el-input ref="passwordInput" v-model="form.password" :disabled="needOtp" size="large" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item v-if="needOtp" label="Mã xác thực (6 số)">
          <el-input ref="otpInput" v-model="form.otp" size="large" inputmode="numeric" autocomplete="one-time-code" maxlength="11"
                    placeholder="Mở ứng dụng xác thực trên điện thoại" />
          <div class="muted small otp-hint">Tài khoản đã bật xác thực 2 lớp. Mất điện thoại thì nhập một mã dự phòng (dạng abcd-efgh).</div>
          <el-checkbox v-model="form.trust" class="opt">Không hỏi mã trên máy này 7 ngày</el-checkbox>
        </el-form-item>
        <el-checkbox v-if="!needOtp" v-model="form.remember" class="opt remember">Ghi nhớ đăng nhập trên máy này (7 ngày)</el-checkbox>
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
/* anh nen: phu kin khung, lop mo phia duoi de chu doc duoc tren moi anh */
.bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.side.has-bg::after { content: ''; position: absolute; inset: 0; background: linear-gradient(to top, rgba(15, 23, 42, 0.85) 0%, rgba(15, 23, 42, 0.35) 40%, rgba(15, 23, 42, 0) 70%); }
.side-text { position: relative; z-index: 1; }
.side.has-bg .side-text p { color: #E2E8F0; }
.side-text b { display: block; font-size: 28px; color: #fff; letter-spacing: -0.02em; }
.side-text p { margin: 8px 0 0; color: var(--steel-400); max-width: 34ch; }
.form-wrap { display: grid; place-items: center; padding: 32px 24px; }
.form { width: 100%; max-width: 360px; }
h1 { font-size: 26px; }
.form > p { margin: 4px 0 24px; }
.err { margin-bottom: 16px; }
.otp-hint { line-height: 1.4; margin-top: 4px; font-size: 12px; }
.opt { margin-top: 6px; }
.remember { margin: -4px 0 16px; display: flex; }
.submit { width: 100%; }
.change { display: inline-block; margin-top: 16px; color: var(--steel-800); font-size: 14px; }
@media (max-width: 800px) {
  .login { grid-template-columns: 1fr; }
  .side { padding: 24px; min-height: 140px; }
  .profiles { display: none; }
  .side-text b { font-size: 22px; }
}
</style>
