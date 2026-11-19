<script setup lang="ts">
/*
 * Nhap du lieu tu phan mem cu (man Tai khoan, chi quan tri) - lam het tren web, khong can vao VPS.
 * Chon file du-lieu-cu.json -> may chu nap + doi chieu tung dong roi HUY (kiem tra thu) -> xem bao cao
 * -> khop het moi bam "Nhap du lieu" (nap + doi chieu lai, lech thi tu huy). He thong da co du lieu -> khoa, ghi ly do.
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, CircleCheckFilled, CircleCloseFilled, Lock, Delete } from '@element-plus/icons-vue'
import http from '../api/http'
import { dtShort } from '../format'

interface Line { label: string; before: string; after: string; ok: boolean }
interface Report { ok: boolean; summary: Line[]; anomalies: string[]; errors: string[]; errorCount: number }
interface Result { dryRun: boolean; committed: boolean; ok: boolean; source: string; report: Report }
interface Status { canImport: boolean; canReset: boolean; blockedReason: string | null; last: { at: string; by: string; source: string; report: string } | null }

const status = ref<Status | null>(null)
const busy = ref<'' | 'test' | 'import'>('')
const fileName = ref('')
const body = ref<Blob | null>(null)
const result = ref<Result | null>(null)
const fileInput = ref<HTMLInputElement>()
const showLast = ref(false)
const showOdd = ref(false)

async function load() { status.value = (await http.get('/admin/legacy-import')).data }
onMounted(load)

// luon hien cho quan tri: bi khoa (da co du lieu) thi ghi ro ly do, de khong phai doan vi sao khong thay
const visible = computed(() => !!status.value)

/** Nen gzip tren may truoc khi gui (6 MB -> ~0,6 MB): nhanh tren 4G. Trinh duyet cu khong nen duoc -> gui nguyen. */
async function prepare(file: File): Promise<Blob> {
  const head = new Uint8Array(await file.slice(0, 2).arrayBuffer())
  if (head[0] === 0x1f && head[1] === 0x8b) return file // da la .gz
  if (typeof CompressionStream === 'undefined') return file
  return new Response(file.stream().pipeThrough(new CompressionStream('gzip'))).blob()
}

async function send(dryRun: boolean) {
  busy.value = dryRun ? 'test' : 'import'
  try {
    const { data } = await http.post<Result>(`/admin/legacy-import?dryRun=${dryRun}`, body.value,
      { headers: { 'Content-Type': 'application/octet-stream' }, timeout: 300_000 })
    result.value = data
    return data
  } finally {
    busy.value = ''
  }
}

async function onPick(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  ;(e.target as HTMLInputElement).value = ''
  if (!f) return
  result.value = null
  fileName.value = f.name
  body.value = await prepare(f)
  await send(true).catch(() => {}) // loi: interceptor da bao
}

const RESET_PHRASE = 'XOA DU LIEU THU'
const resetting = ref(false)
/** Xoa du lieu thu (chua nhap du lieu cu lan nao) -> he thong trong, nhap duoc. Phai go dung chu xac nhan. */
async function resetTest() {
  const { value } = await ElMessageBox.prompt(
    `${status.value?.blockedReason?.split(', nên')[0]}. Xóa hết các dữ liệu này (khách, công nợ, sản phẩm, loại hàng, hóa đơn, kho, nhà cung cấp) để nhập dữ liệu từ phần mềm cũ? Tài khoản và mật khẩu giữ nguyên. KHÔNG khôi phục được trên web. Gõ ${RESET_PHRASE} để xác nhận.`,
    'Xóa dữ liệu thử', {
      confirmButtonText: 'Xóa', cancelButtonText: 'Không', type: 'warning', inputPlaceholder: RESET_PHRASE,
      confirmButtonClass: 'el-button--danger',
      inputValidator: (v) => (v ?? '').trim().toUpperCase() === RESET_PHRASE || `Gõ đúng ${RESET_PHRASE}`,
    })
  resetting.value = true
  try {
    await http.post('/admin/legacy-import/reset', { confirm: value })
    ElMessage.success('Đã xóa dữ liệu thử. Giờ nhập được dữ liệu từ phần mềm cũ.')
    result.value = null
    await load()
  } finally {
    resetting.value = false
  }
}

async function doImport() {
  await ElMessageBox.confirm(
    'Nhập toàn bộ dữ liệu từ phần mềm cũ vào hệ thống? Chỉ nhập được 1 lần. Nếu đối chiếu lệch, hệ thống tự hủy, không ghi gì.',
    'Nhập dữ liệu', { confirmButtonText: 'Nhập dữ liệu', cancelButtonText: 'Không', type: 'warning' })
  const r = await send(false).catch(() => null)
  if (r?.committed) {
    ElMessage.success('Đã nhập dữ liệu, đối chiếu khớp hết')
    await load()
  } else if (r) ElMessage.error('Đối chiếu lệch: đã hủy, không ghi gì')
}
</script>

<template>
  <section v-if="visible" class="panel panel-pad lg" aria-labelledby="lg-title">
    <div>
      <h2 id="lg-title">Nhập dữ liệu từ phần mềm cũ</h2>
      <p class="muted">Khách hàng, công nợ, sản phẩm, loại hàng và hóa đơn cũ. Làm trên web, không cần vào máy chủ.</p>
    </div>

    <!-- da nhap -->
    <div v-if="status?.last" class="done">
      <p><el-icon class="ok"><CircleCheckFilled /></el-icon>
        Đã nhập lúc <b>{{ dtShort(status.last.at) }}</b> bởi {{ status.last.by }}, đối chiếu khớp hết.
        <el-button link type="primary" @click="showLast = !showLast">{{ showLast ? 'Ẩn báo cáo' : 'Xem báo cáo' }}</el-button></p>
      <pre v-if="showLast" class="raw">{{ status.last.source }}

{{ status.last.report }}</pre>
    </div>

    <!-- chua nhap -->
    <template v-else-if="status?.canImport">
      <ol class="steps">
        <li>Chọn file <b class="mono">du-lieu-cu.json</b> (hoặc <span class="mono">.json.gz</span>) tạo từ <span class="mono">BackupFile.bak</span> của phần mềm cũ.</li>
        <li>Hệ thống <b>kiểm tra thử</b>: nạp rồi đối chiếu từng dòng, sau đó hủy (chưa ghi gì) và hiện báo cáo.</li>
        <li>Khớp hết thì bấm <b>Nhập dữ liệu</b>. Chỉ nhập được 1 lần, vào hệ thống chưa có khách, sản phẩm, hóa đơn.</li>
      </ol>
      <div class="actions">
        <input ref="fileInput" type="file" accept=".json,.gz,application/json,application/gzip" hidden aria-label="Chọn file dữ liệu cũ" @change="onPick" />
        <el-button :type="result?.ok ? 'default' : 'primary'" :icon="Upload" :loading="busy === 'test'" :disabled="!!busy" @click="fileInput?.click()">
          {{ busy === 'test' ? 'Đang kiểm tra...' : fileName ? 'Chọn file khác' : 'Chọn file' }}</el-button>
        <el-button v-if="result?.ok && !result.committed" type="primary" :loading="busy === 'import'" :disabled="!!busy" @click="doImport">
          Nhập dữ liệu</el-button>
        <span v-if="fileName" class="muted small mono">{{ fileName }}</span>
      </div>
      <p v-if="busy" class="muted small">Đang nạp và đối chiếu khoảng 25.000 dòng, mất vài giây đến nửa phút. Đừng đóng trang.</p>
    </template>

    <!-- da co du lieu (khong phai tu lan nhap nay) -> khoa -->
    <template v-else>
      <p class="blocked"><el-icon><Lock /></el-icon><span>{{ status?.blockedReason }}</span></p>
      <div v-if="status?.canReset" class="actions">
        <el-button type="danger" plain :icon="Delete" :loading="resetting" @click="resetTest">Xóa dữ liệu thử</el-button>
        <span class="muted small">Chỉ dùng khi đây là dữ liệu nhập thử, chưa phải dữ liệu thật.</span>
      </div>
    </template>

    <!-- bao cao lan kiem tra / nhap vua chay -->
    <div v-if="result && !status?.last" class="report" :class="result.ok ? 'is-ok' : 'is-bad'">
      <p class="verdict">
        <el-icon><component :is="result.ok ? CircleCheckFilled : CircleCloseFilled" /></el-icon>
        <span v-if="result.ok">Kiểm tra thử: <b>khớp hết</b> giữa 2 bên. Chưa ghi gì, bấm <b>Nhập dữ liệu</b> để nhập.</span>
        <span v-else>Đối chiếu <b>lệch {{ result.report.errorCount }} chỗ</b>. Đã hủy, không ghi gì. Gửi báo cáo này cho người cài đặt.</span>
      </p>
      <!-- may tinh: 4 cot; dien thoai: moi muc 1 khoi (ten + cu / moi xep doc) -->
      <div class="cmp" role="table" aria-label="Đối chiếu phần mềm cũ và hệ thống mới">
        <div class="cmp-row cmp-head" role="row">
          <span role="columnheader">Mục</span><span role="columnheader">Phần mềm cũ</span><span role="columnheader">Hệ thống mới</span><span role="columnheader" aria-label="Khớp"></span>
        </div>
        <div v-for="l in result.report.summary" :key="l.label" class="cmp-row" role="row">
          <span class="cmp-label" role="cell">{{ l.label }}</span>
          <span class="num" role="cell"><i class="cmp-tag">Cũ</i>{{ l.before || '—' }}</span>
          <span class="num" role="cell"><i class="cmp-tag">Mới</i>{{ l.after }}</span>
          <span class="cmp-ok" role="cell"><el-icon :class="l.ok ? 'ok' : 'bad'" :aria-label="l.ok ? 'Khớp' : 'Lệch'"><component :is="l.ok ? CircleCheckFilled : CircleCloseFilled" /></el-icon></span>
        </div>
      </div>
      <div v-if="result.report.errors.length" class="errs">
        <p><b>Chỗ lệch</b>{{ result.report.errorCount > result.report.errors.length ? ` (${result.report.errors.length} / ${result.report.errorCount} đầu tiên)` : '' }}:</p>
        <ul><li v-for="e in result.report.errors" :key="e" class="mono">{{ e }}</li></ul>
      </div>
      <div v-if="result.report.anomalies.length">
        <el-button link type="primary" @click="showOdd = !showOdd">
          {{ showOdd ? 'Ẩn' : 'Xem' }} {{ result.report.anomalies.length }} chỗ bất thường trong dữ liệu cũ</el-button>
        <span class="muted small"> (giữ nguyên như cũ, không ảnh hưởng công nợ)</span>
        <ul v-if="showOdd" class="odd"><li v-for="a in result.report.anomalies" :key="a">{{ a }}</li></ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.lg { margin-top: var(--space-lg); display: flex; flex-direction: column; gap: var(--space-md); }
h2 { font-size: 17px; margin: 0 0 4px; }
.lg > div > p { margin: 0; font-size: 14px; }
.steps { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 4px; font-size: 14px; line-height: 1.55; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm); }
.actions .el-button + .el-button { margin-left: 0; }
.small { font-size: 12px; margin: 0; }
.done p { margin: 0; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 14px; }
.raw { margin: var(--space-sm) 0 0; white-space: pre-wrap; font-family: var(--font-mono); font-size: 12px; background: var(--steel-50); border-radius: var(--radius-md); padding: var(--space-md); max-height: 360px; overflow: auto; }
.report { display: flex; flex-direction: column; gap: var(--space-md); border-radius: var(--radius-md); padding: var(--space-md) var(--space-lg); background: var(--steel-50); }
.verdict { margin: 0; display: flex; gap: 8px; align-items: flex-start; font-size: 14px; line-height: 1.55; }
.verdict .el-icon { margin-top: 3px; flex: none; }
.is-ok .verdict .el-icon, .ok { color: var(--money, #15803D); }
.is-bad .verdict .el-icon, .bad { color: var(--debt, #B91C1C); }
.cmp { background: var(--surface, #fff); border-radius: var(--radius-md); font-size: 14px; }
.cmp-row { display: grid; grid-template-columns: 150px 1fr 1fr 24px; gap: var(--space-md); padding: 8px 12px; border-bottom: 1px solid var(--steel-200); align-items: start; }
.cmp-row:last-child { border-bottom: 0; }
.cmp-head { font-weight: 600; color: var(--ink-muted); font-size: 13px; }
.cmp-label { font-weight: 600; }
.num { font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.cmp-tag { display: none; font-style: normal; color: var(--ink-muted); font-size: 12px; margin-right: 6px; }
.cmp-ok { display: flex; justify-content: center; padding-top: 2px; }
@media (max-width: 640px) {
  .cmp-head { display: none; }
  .cmp-row { grid-template-columns: 1fr 24px; gap: 2px var(--space-sm); }
  .cmp-label { grid-column: 1; grid-row: 1; }
  .cmp-ok { grid-column: 2; grid-row: 1; }
  .cmp-row .num { grid-column: 1 / -1; }
  .cmp-tag { display: inline-block; min-width: 30px; }
}
.errs p { margin: 0 0 4px; font-size: 14px; }
.errs ul, .odd { margin: 0; padding-left: 20px; font-size: 13px; line-height: 1.55; max-height: 280px; overflow: auto; }
.blocked { margin: 0; display: flex; gap: 8px; align-items: flex-start; font-size: 14px; line-height: 1.55; color: var(--ink-muted); background: var(--steel-50); border-radius: var(--radius-md); padding: var(--space-md); }
.blocked .el-icon { margin-top: 3px; flex: none; }
.errs li { color: var(--debt, #B91C1C); }
</style>
