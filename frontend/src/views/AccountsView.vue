<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '../api/http'
import type { UserAccount } from '../api/types'
import { useAuth } from '../stores/auth'
import PageHeader from '../components/PageHeader.vue'

// Thay man Account cu. Khoa tai khoan thay vi xoa de giu ten nguoi lap tren hoa don.
const auth = useAuth()
const rows = ref<UserAccount[]>([])
const formRef = ref<FormInstance>()
const dialog = reactive({ open: false, id: null as number | null })
const form = reactive({ username: '', fullName: '', role: 'STAFF' as 'ADMIN' | 'STAFF', active: true, password: '' })
const rules: FormRules = {
  username: [{ required: true, message: 'Nhập tên đăng nhập', trigger: 'blur' }],
  fullName: [{ required: true, message: 'Nhập họ tên', trigger: 'blur' }],
  password: [{ validator: (_r, v, cb) => (dialog.id || (v && v.length >= 6) ? cb() : cb(new Error('Mật khẩu ít nhất 6 ký tự'))), trigger: 'blur' }],
}

async function load() { rows.value = (await http.get<UserAccount[]>('/users')).data }

function openForm(u?: UserAccount) {
  dialog.id = u?.id ?? null
  Object.assign(form, u
    ? { username: u.username, fullName: u.fullName, role: u.role, active: u.active, password: '' }
    : { username: '', fullName: '', role: 'STAFF', active: true, password: '' })
  dialog.open = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (dialog.id) await http.put(`/users/${dialog.id}`, { fullName: form.fullName, role: form.role, active: form.active })
  else await http.post('/users', { username: form.username, fullName: form.fullName, role: form.role, password: form.password })
  dialog.open = false
  ElMessage.success('Đã lưu tài khoản')
  load()
}

// Nguoi do mat dien thoai + mat ma du phong: tat 2 lop giup, ho dang nhap bang mat khau roi tu bat lai
async function resetTwoFactor(u: UserAccount) {
  await ElMessageBox.confirm(`Tắt xác thực 2 lớp của ${u.username}? Dùng khi người đó mất điện thoại và mất mã dự phòng. Họ đăng nhập bằng mật khẩu rồi tự bật lại.`,
    'Tắt xác thực 2 lớp', { confirmButtonText: 'Tắt 2 lớp', cancelButtonText: 'Không', type: 'warning' })
  await http.post(`/users/${u.id}/2fa/reset`)
  ElMessage.success(`Đã tắt xác thực 2 lớp của ${u.username}`)
  await load()
}

async function resetPassword(u: UserAccount) {
  const { value } = await ElMessageBox.prompt(`Mật khẩu mới cho ${u.username} (ít nhất 6 ký tự)`, 'Đặt lại mật khẩu', {
    inputType: 'password', inputPattern: /^.{6,}$/, inputErrorMessage: 'Ít nhất 6 ký tự',
    confirmButtonText: 'Đặt lại', cancelButtonText: 'Hủy',
  })
  await http.post(`/users/${u.id}/reset-password`, { password: value })
  ElMessage.success('Đã đặt lại mật khẩu')
}

onMounted(load)
</script>

<template>
  <PageHeader title="Tài khoản" subtitle="Quản trị dùng được mọi màn; nhân viên chỉ vào màn Bán hàng.">
    <el-button type="primary" :icon="Plus" @click="openForm()">Thêm tài khoản</el-button>
  </PageHeader>

  <div class="panel">
    <el-table :data="rows">
      <el-table-column label="Tên đăng nhập" width="170"><template #default="{ row }"><span class="mono">{{ row.username }}</span></template></el-table-column>
      <el-table-column prop="fullName" label="Họ tên" min-width="180" />
      <el-table-column label="Vai trò" width="130">
        <template #default="{ row }"><el-tag :type="row.role === 'ADMIN' ? 'primary' : 'info'" effect="plain">{{ row.role === 'ADMIN' ? 'Quản trị' : 'Nhân viên' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="Trạng thái" width="120">
        <template #default="{ row }"><span :class="row.active ? 'tone-money' : 'muted'">{{ row.active ? 'Đang dùng' : 'Đã khóa' }}</span></template>
      </el-table-column>
      <el-table-column label="Xác thực 2 lớp" width="140">
        <template #default="{ row }"><span :class="row.twoFactor ? 'tone-money' : 'muted'">{{ row.twoFactor ? 'Đang bật' : 'Chưa bật' }}</span></template>
      </el-table-column>
      <el-table-column min-width="260" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">Sửa</el-button>
          <el-button link type="primary" @click="resetPassword(row)">Đặt lại mật khẩu</el-button>
          <el-button v-if="row.twoFactor" link type="danger" @click="resetTwoFactor(row)">Tắt 2 lớp</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="dialog.open" :title="dialog.id ? 'Sửa tài khoản' : 'Thêm tài khoản'" width="440px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
      <el-form-item label="Tên đăng nhập" prop="username"><el-input v-model="form.username" :disabled="!!dialog.id" autocomplete="off" /></el-form-item>
      <el-form-item label="Họ tên" prop="fullName"><el-input v-model="form.fullName" /></el-form-item>
      <el-form-item label="Vai trò">
        <el-radio-group v-model="form.role">
          <el-radio value="STAFF">Nhân viên bán hàng</el-radio>
          <el-radio value="ADMIN">Quản trị</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="!dialog.id" label="Mật khẩu ban đầu" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
      <el-form-item v-else label="Trạng thái">
        <el-switch v-model="form.active" active-text="Đang dùng" inactive-text="Khóa" :disabled="form.username === auth.user?.username" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.open = false">Hủy</el-button>
      <el-button type="primary" @click="save">Lưu</el-button>
    </template>
  </el-dialog>
</template>
