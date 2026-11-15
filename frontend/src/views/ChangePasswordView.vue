<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import http from '../api/http'
import PageHeader from '../components/PageHeader.vue'

const formRef = ref<FormInstance>()
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const saving = ref(false)
const rules: FormRules = {
  oldPassword: [{ required: true, message: 'Nhập mật khẩu hiện tại', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: 'Mật khẩu mới ít nhất 6 ký tự', trigger: 'blur' }],
  confirm: [{
    validator: (_r, v, cb) => (v === form.newPassword ? cb() : cb(new Error('Hai lần nhập không khớp'))),
    trigger: 'blur',
  }],
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await http.post('/auth/change-password', { oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('Đã đổi mật khẩu')
    formRef.value?.resetFields()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PageHeader title="Đổi mật khẩu" subtitle="Áp dụng cho tài khoản đang đăng nhập." />
  <div class="panel panel-pad" style="max-width: 440px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Mật khẩu hiện tại" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="Mật khẩu mới" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="Nhập lại mật khẩu mới" prop="confirm">
        <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="saving">Lưu mật khẩu</el-button>
    </el-form>
  </div>
</template>
