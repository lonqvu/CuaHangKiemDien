<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import http from '../api/http'
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'

// Thay man ChangePassWord cu: doi duoc ca khi chua dang nhap (vao tu man Dang nhap)
const auth = useAuth()
const standalone = computed(() => !auth.loggedIn)
const formRef = ref<FormInstance>()
const form = reactive({ username: auth.user?.username ?? '', oldPassword: '', newPassword: '', confirm: '' })
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

async function save() {
  error.value = ''
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await http.post('/auth/change-password', { username: form.username, oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('Đổi mật khẩu thành công')
    form.oldPassword = form.newPassword = form.confirm = ''
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
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon style="margin-bottom: 16px" />
        <el-button type="primary" native-type="submit" :loading="saving">Đồng ý</el-button>
        <router-link v-if="standalone" to="/login" class="back">Quay lại đăng nhập</router-link>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.wrap { max-width: 480px; margin: 48px auto; padding: 0 16px; }
.back { margin-left: 16px; color: var(--steel-800); font-size: 14px; }
</style>
