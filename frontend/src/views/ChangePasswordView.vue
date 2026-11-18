<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import http from '../api/http'
import PageHeader from '../components/PageHeader.vue'
import TwoFactorPanel from '../components/TwoFactorPanel.vue'
import { useAuth } from '../stores/auth'

// Thay man ChangePassWord cu: doi duoc ca khi chua dang nhap (vao tu man Dang nhap)
const auth = useAuth()
const standalone = computed(() => !auth.loggedIn)
const formRef = ref<FormInstance>()
const form = reactive({ username: auth.user?.username ?? '', oldPassword: '', newPassword: '', confirm: '', otp: '' })
const saving = ref(false)
const error = ref('')
const rules: FormRules = {
  username: [{ required: true, message: 'Bạn chưa nhập tên đăng nhập', trigger: 'blur' }],
  oldPassword: [{ required: true, message: 'Bạn chưa nhập mật khẩu hiện tại', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: 'Mật khẩu mới ít nhất 6 ký tự', trigger: 'blur' }],
  confirm: [{
    validator: (_r, v, cb) => (v === form.newPassword ? cb() : cb(new Error('Nhập lại mật khẩu mới không chính xác'))),
    trigger: 'blur',
  }],
}

// Nghi lo mat khau / mat dien thoai: cat moi phien dang nhap o may khac (ke ca may "khong hoi ma 2 lop")
const loggingOut = ref(false)
async function logoutOthers() {
  await ElMessageBox.confirm('Đăng xuất tài khoản này trên mọi máy tính, điện thoại khác? Máy đang dùng vẫn giữ đăng nhập. Máy khác phải đăng nhập lại (và nhập mã 2 lớp nếu đã bật).',
    'Đăng xuất mọi thiết bị khác', { confirmButtonText: 'Đăng xuất máy khác', cancelButtonText: 'Không', type: 'warning' })
  loggingOut.value = true
  try {
    auth.replaceToken((await http.post('/auth/logout-others')).data.token)
    ElMessage.success('Đã đăng xuất mọi thiết bị khác')
  } finally {
    loggingOut.value = false
  }
}

async function save() {
  error.value = ''
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const { data } = await http.post('/auth/change-password', { username: form.username, oldPassword: form.oldPassword, newPassword: form.newPassword, otp: form.otp || undefined })
    auth.replaceToken(data?.token) // may nay giu dang nhap; cac may khac bi dang xuat
    ElMessage.success(standalone.value ? 'Đổi mật khẩu thành công' : 'Đổi mật khẩu thành công. Các máy khác đang đăng nhập tài khoản này đã bị đăng xuất.')
    form.oldPassword = form.newPassword = form.confirm = form.otp = ''
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Không đổi được mật khẩu'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div :class="{ wrap: standalone }">
    <PageHeader title="Đổi mật khẩu" :subtitle="standalone ? 'Nhập tên đăng nhập và mật khẩu hiện tại.' : 'Áp dụng cho tài khoản đang đăng nhập.'" />
    <div class="panel panel-pad" style="max-width: 440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item v-if="standalone" label="Tên đăng nhập" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="Mật khẩu hiện tại" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="Mật khẩu mới" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="Nhập lại mật khẩu mới" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item v-if="standalone" label="Mã xác thực 2 lớp (nếu tài khoản đã bật)">
          <el-input v-model="form.otp" inputmode="numeric" autocomplete="one-time-code" maxlength="11" placeholder="Để trống nếu chưa bật" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon style="margin-bottom: 16px" />
        <el-button type="primary" native-type="submit" :loading="saving">Đồng ý</el-button>
        <router-link v-if="standalone" to="/login" class="back">Quay lại đăng nhập</router-link>
      </el-form>
    </div>
    <TwoFactorPanel v-if="!standalone && auth.isAdmin" />
    <section v-if="!standalone" class="panel panel-pad sessions" aria-labelledby="ss-title">
      <h2 id="ss-title">Phiên đăng nhập</h2>
      <p class="muted">Mất điện thoại hoặc nghi người khác biết mật khẩu: đăng xuất tài khoản này trên mọi máy khác. Đổi mật khẩu cũng tự làm việc này.</p>
      <el-button :loading="loggingOut" @click="logoutOthers">Đăng xuất mọi thiết bị khác</el-button>
    </section>
  </div>
</template>

<style scoped>
.wrap { max-width: 480px; margin: 48px auto; padding: 0 16px; }
.back { margin-left: 16px; color: var(--steel-800); font-size: 14px; }
.sessions { max-width: 440px; margin-top: var(--space-lg); display: flex; flex-direction: column; gap: var(--space-md); align-items: flex-start; }
.sessions h2 { font-size: 17px; margin: 0; }
.sessions p { margin: 0; font-size: 14px; }
</style>
