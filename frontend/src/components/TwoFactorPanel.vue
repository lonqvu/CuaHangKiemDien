<script setup lang="ts">
/*
 * Xac thuc 2 lop (ma 6 so tu app Google Authenticator / Microsoft Authenticator) cho tai khoan dang dang nhap.
 * Bat: nhap mat khau -> quet QR -> nhap 1 ma de xac nhan -> luu 8 ma du phong (chi hien 1 lan).
 * Tat: mat khau + ma (hoac ma du phong). Mat dien thoai + mat ma du phong: quan tri khac tat giup o man Tai khoan.
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import http from '../api/http'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const status = ref<{ enabled: boolean; recoveryLeft: number } | null>(null)
const step = ref<'idle' | 'password' | 'scan' | 'codes' | 'disable'>('idle')
const f = reactive({ password: '', code: '' })
const setup = ref<{ secret: string; otpauthUri: string; qr: string } | null>(null)
const codes = ref<string[]>([])
const busy = ref(false)
const error = ref('')

async function load() {
  status.value = (await http.get('/auth/2fa')).data
  auth.setTwoFactor(status.value!.enabled)
}
onMounted(load)

function reset(to: typeof step.value) {
  Object.assign(f, { password: '', code: '' })
  error.value = ''
  step.value = to
}

const msg = (e: any, d: string) => e?.response?.data?.message || d
const call = async (fn: () => Promise<void>, fallback: string) => {
  error.value = ''
  busy.value = true
  try { await fn() } catch (e) { error.value = msg(e, fallback) } finally { busy.value = false }
}

const startSetup = () => call(async () => {
  const { data } = await http.post('/auth/2fa/setup', { password: f.password }, { silent: true } as any)
  setup.value = { ...data, qr: await QRCode.toDataURL(data.otpauthUri, { width: 220, margin: 1 }) }
  f.code = ''
  step.value = 'scan'
}, 'Không tạo được mã QR')

const confirmEnable = () => call(async () => {
  const { data } = await http.post('/auth/2fa/enable', { code: f.code }, { silent: true } as any)
  codes.value = data.recoveryCodes
  setup.value = null
  step.value = 'codes'
  await load()
}, 'Không bật được')

const disable = () => call(async () => {
  await http.post('/auth/2fa/disable', { password: f.password, code: f.code }, { silent: true } as any)
  ElMessage.success('Đã tắt xác thực 2 lớp')
  reset('idle')
  await load()
}, 'Không tắt được')

async function copyCodes() {
  await navigator.clipboard?.writeText(codes.value.join('\n')).catch(() => {})
  ElMessage.success('Đã chép mã dự phòng')
}
function downloadCodes() {
  const text = `Mã dự phòng đăng nhập - ${auth.user?.username} - Nhôm Sắt Kiểm Diên\nMỗi mã dùng 1 lần khi không có điện thoại.\n\n${codes.value.join('\n')}\n`
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
  a.download = `ma-du-phong-${auth.user?.username}.txt`
  a.click()
  URL.revokeObjectURL(a.href)
}
</script>

<template>
  <section class="panel panel-pad tf" aria-labelledby="tf-title">
    <div class="tf-head">
      <h2 id="tf-title">Xác thực 2 lớp</h2>
      <el-tag v-if="status" :type="status.enabled ? 'success' : 'warning'" effect="plain">{{ status.enabled ? 'Đang bật' : 'Chưa bật' }}</el-tag>
    </div>
    <p class="muted">
      Đăng nhập cần thêm mã 6 số từ ứng dụng trên điện thoại (Google Authenticator hoặc Microsoft Authenticator).
      Lộ mật khẩu thì người khác vẫn không vào được.
    </p>

    <!-- dang tat -->
    <template v-if="status && !status.enabled && step === 'idle'">
      <el-button type="primary" @click="reset('password')">Bật xác thực 2 lớp</el-button>
    </template>
    <form v-if="step === 'password'" class="tf-form" @submit.prevent="startSetup">
      <label for="tf-pass">Nhập mật khẩu hiện tại để tiếp tục</label>
      <el-input id="tf-pass" v-model="f.password" type="password" show-password autocomplete="current-password" autofocus />
      <div class="row"><el-button @click="reset('idle')">Hủy</el-button><el-button type="primary" native-type="submit" :loading="busy" :disabled="!f.password">Tiếp tục</el-button></div>
    </form>
    <form v-if="step === 'scan' && setup" class="tf-form" @submit.prevent="confirmEnable">
      <ol class="steps">
        <li>Cài <b>Google Authenticator</b> (hoặc Microsoft Authenticator) trên điện thoại.</li>
        <li>Trong ứng dụng chọn <b>+</b> → <b>Quét mã QR</b>, quét mã bên dưới.</li>
        <li>Nhập mã 6 số ứng dụng đang hiện.</li>
      </ol>
      <img :src="setup.qr" alt="Mã QR cài xác thực 2 lớp" class="qr" width="220" height="220" />
      <details class="manual"><summary>Không quét được? Nhập khóa thủ công</summary><code class="secret">{{ setup.secret.match(/.{1,4}/g)?.join(' ') }}</code></details>
      <label for="tf-code">Mã 6 số</label>
      <el-input id="tf-code" v-model="f.code" inputmode="numeric" autocomplete="one-time-code" maxlength="7" placeholder="123456" class="code" />
      <div class="row"><el-button @click="reset('idle')">Hủy</el-button><el-button type="primary" native-type="submit" :loading="busy" :disabled="f.code.replace(/\D/g, '').length !== 6">Xác nhận, bật</el-button></div>
    </form>

    <!-- vua bat: ma du phong (chi hien 1 lan) -->
    <div v-if="step === 'codes'" class="tf-form">
      <el-alert type="success" :closable="false" show-icon title="Đã bật xác thực 2 lớp" />
      <p><b>Lưu 8 mã dự phòng này</b> (in ra hoặc chép vào chỗ an toàn). Mất điện thoại thì dùng 1 mã thay cho mã 6 số; mỗi mã dùng được 1 lần.
        Mã chỉ hiện <b>một lần</b> này.</p>
      <ul class="codes"><li v-for="c in codes" :key="c"><code>{{ c }}</code></li></ul>
      <div class="row"><el-button @click="copyCodes">Chép</el-button><el-button @click="downloadCodes">Tải về (.txt)</el-button><el-button type="primary" @click="reset('idle'); codes = []">Đã lưu xong</el-button></div>
    </div>

    <!-- dang bat -->
    <template v-if="status?.enabled && step === 'idle'">
      <p class="muted small">Còn {{ status.recoveryLeft }} mã dự phòng chưa dùng.</p>
      <el-button type="danger" plain @click="reset('disable')">Tắt xác thực 2 lớp</el-button>
    </template>
    <form v-if="step === 'disable'" class="tf-form" @submit.prevent="disable">
      <label for="tf-dpass">Mật khẩu hiện tại</label>
      <el-input id="tf-dpass" v-model="f.password" type="password" show-password autocomplete="current-password" />
      <label for="tf-dcode">Mã 6 số (hoặc mã dự phòng)</label>
      <el-input id="tf-dcode" v-model="f.code" autocomplete="one-time-code" maxlength="11" />
      <div class="row"><el-button @click="reset('idle')">Hủy</el-button><el-button type="danger" native-type="submit" :loading="busy" :disabled="!f.password || !f.code">Tắt</el-button></div>
    </form>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="err" />
  </section>
</template>

<style scoped>
.tf { max-width: 440px; display: flex; flex-direction: column; gap: var(--space-md); margin-top: var(--space-lg); }
.tf-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); }
.tf > .el-button { align-self: flex-start; } /* nut rong vua chu, giong nut o khung Phien dang nhap */
.tf-head h2 { font-size: 17px; margin: 0; }
.tf > p { margin: 0; font-size: 14px; }
.tf-form { display: flex; flex-direction: column; gap: var(--space-sm); }
.tf-form label { font-size: 13px; font-weight: 500; color: #334155; margin-top: var(--space-xs); }
.row { display: flex; gap: var(--space-sm); justify-content: flex-end; flex-wrap: wrap; margin-top: var(--space-sm); }
.row .el-button + .el-button { margin-left: 0; }
.steps { margin: 0; padding-left: 20px; font-size: 14px; line-height: 1.6; }
.qr { align-self: center; border: 1px solid var(--steel-200); border-radius: var(--radius-md); }
.manual summary { cursor: pointer; font-size: 13px; color: var(--steel-800); }
.secret { display: block; margin-top: 6px; font-size: 15px; letter-spacing: 0.04em; word-break: break-all; }
.code :deep(input) { font-family: var(--font-mono); font-size: 18px; letter-spacing: 0.2em; }
.codes { list-style: none; padding: 0; margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.codes code { display: block; padding: 6px 10px; background: var(--steel-50); border-radius: var(--radius-sm, 4px); font-size: 15px; text-align: center; }
.small { font-size: 12px; }
.err { margin-top: var(--space-sm); }
</style>
