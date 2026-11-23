<script setup lang="ts">
/*
 * Gui Zalo: chi 1 viec - gui file PDF (hoa don / phieu thu / sao ke) qua Zalo, nguoi dung chon nguoi nhan trong Zalo.
 *  - May tinh (giong app cu): mo Zalo PC kem file -> Zalo hien danh sach nguoi nhan. Can tien ich "Gui Zalo"
 *    cai 1 lan (zaloHelper.ts) vi trinh duyet khong tu chay duoc Zalo.exe.
 *  - Dien thoai: bang chia se cua may kem file -> chon Zalo -> chon nguoi nhan.
 */
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { ChatDotRound, Download, RefreshRight } from '@element-plus/icons-vue'
import http, { errorMessage } from '../api/http'
import { ZALO_SCHEME, downloadInstaller } from '../zaloHelper'

const props = defineProps<{
  modelValue: boolean
  title: string       // vd "Hóa đơn HD00000960"
  pdfPath: string     // vd /orders/12/invoice.pdf
  fileName: string    // vd HD00000960.pdf
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()

type Step = 'loading' | 'phone' | 'opening' | 'opened' | 'install' | 'error'
const step = ref<Step>('loading')
const error = ref('')
const pdf = shallowRef<Blob | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
const INSTALLED_KEY = 'zaloHelperInstalled'
const installedBefore = () => { try { return localStorage.getItem(INSTALLED_KEY) === '1' } catch { return false } }


const mobile = () => {
  const ua = navigator.userAgent
  return (navigator as any).userAgentData?.mobile === true || /Android|iPhone|iPad|iPod/i.test(ua)
    || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) // iPad bao la Mac
}

function close() { emit('update:modelValue', false) }

async function loadPdf() {
  if (prefetched.value?.path === props.pdfPath) { pdf.value = prefetched.value.blob; return }
  const res = await http.get(props.pdfPath, { responseType: 'blob', silent: true } as any)
  pdf.value = res.data as Blob
}

// Dien thoai: tai SAN file ngay khi hien hoa don -> bam "Gui Zalo" mo bang chia se NGAY (Safari chi cho chia se
// trong luc nguoi dung vua bam; cho tai file roi moi chia se thi bi chan) -> khong can hop thoai trung gian.
const prefetched = shallowRef<{ path: string; blob: Blob } | null>(null)
watch(() => props.pdfPath, (p) => {
  prefetched.value = null
  if (!p || !mobile()) return
  http.get(p, { responseType: 'blob', silent: true } as any)
    .then((r) => { if (props.pdfPath === p) prefetched.value = { path: p, blob: r.data as Blob } })
    .catch(() => { /* bam Gui Zalo se tai lai va bao loi neu co */ })
}, { immediate: true })

/** Nut "Gui Zalo" o man goi: dien thoai co file san -> bang chia se luon; con lai -> mo hop thoai (may tinh / chua tai xong). */
async function send() {
  if (mobile() && prefetched.value?.path === props.pdfPath) {
    const file = new File([prefetched.value.blob], props.fileName, { type: 'application/pdf' })
    if (navigator.canShare?.({ files: [file] })) {
      try {
        await navigator.share({ files: [file], title: props.title })
        return
      } catch (e: any) {
        if (e?.name === 'AbortError') return // nguoi dung dong bang chia se
        // loi khac -> mo hop thoai (co nut Gui Zalo / Tai file)
      }
    }
  }
  emit('update:modelValue', true)
}
defineExpose({ send })

/** May tinh: gui file len may chu (ma 10 phut) -> mo nskd-zalo:// -> tien ich tai file, chay Zalo.exe "file". */
async function openZaloPc() {
  step.value = 'opening'
  clearTimeout(timer)
  const { data } = await http.post<{ token: string; name: string }>(`/share?name=${encodeURIComponent(props.fileName)}`, pdf.value,
    { headers: { 'Content-Type': 'application/octet-stream' } })
  const onBlur = () => { // trinh duyet mat focus = Zalo / tien ich da mo
    step.value = 'opened'
    try { localStorage.setItem(INSTALLED_KEY, '1') } catch { /* bo qua */ }
  }
  window.addEventListener('blur', onBlur, { once: true })
  window.location.href = `${ZALO_SCHEME}://send/${data.token}/${data.name}`
  // chua thay mo (chua cai tien ich, hoac con dang hoi "Mo Gui Zalo?") -> huong dan
  timer = setTimeout(() => {
    window.removeEventListener('blur', onBlur)
    if (step.value === 'opening') step.value = installedBefore() ? 'opened' : 'install'
  }, installedBefore() ? 2500 : 6000)
}

async function start() {
  step.value = 'loading'
  error.value = ''
  pdf.value = null
  try {
    await loadPdf()
    if (mobile()) step.value = 'phone'
    else await openZaloPc()
  } catch (e: any) {
    let msg = errorMessage(e)
    const d = e?.response?.data
    if (d instanceof Blob) { try { msg = JSON.parse(await d.text()).message || msg } catch { /* giu msg */ } }
    error.value = msg
    step.value = 'error'
  }
}

/** Dien thoai: bang chia se cua may (phai bam nut - trinh duyet chi cho chia se khi nguoi dung vua bam). */
async function sharePhone() {
  const file = new File([pdf.value!], props.fileName, { type: 'application/pdf' })
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: props.title })
      close()
    } catch (e: any) {
      if (e?.name !== 'AbortError') { error.value = 'Không mở được bảng chia sẻ. Bấm Tải file rồi gửi trong Zalo.'; step.value = 'error' }
    }
  } else {
    error.value = 'Trình duyệt này không chia sẻ được file. Bấm Tải file rồi gửi trong Zalo.'
    step.value = 'error'
  }
}

/** Mo Zalo PC (giao thuc zalo:// Zalo PC dang ky khi cai) - de gui tay khi tien ich loi. */
function openZaloApp() { window.location.href = 'zalo://' }

function downloadPdf() {
  if (!pdf.value) return
  const a = document.createElement('a')
  a.href = URL.createObjectURL(pdf.value)
  a.download = props.fileName
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 5000)
}

watch(() => props.modelValue, (v) => { if (v) start(); else clearTimeout(timer) })
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`Gửi Zalo: ${title}`" width="min(460px, 94vw)" @update:model-value="close">
    <div class="zl" aria-live="polite">
      <div v-if="step === 'loading' || step === 'opening'" v-loading="true" class="loading"
           :element-loading-text="step === 'loading' ? 'Đang tạo file...' : 'Đang mở Zalo...'" />

      <template v-else-if="step === 'phone'">
        <p>Bấm <b>Gửi Zalo</b>, chọn <b>Zalo</b>, rồi chọn người nhận.</p>
        <el-button type="success" size="large" :icon="ChatDotRound" class="big" @click="sharePhone">Gửi Zalo</el-button>
      </template>

      <template v-else-if="step === 'opened'">
        <p><b>Zalo đang mở.</b> Chọn người nhận trong Zalo rồi bấm Gửi.</p>
        <p class="muted small">Trình duyệt hỏi "Mở Gửi Zalo?" thì tích <b>Luôn cho phép</b> rồi bấm <b>Mở</b>.</p>
        <div class="row">
          <el-button :icon="RefreshRight" @click="openZaloPc">Mở lại Zalo</el-button>
          <el-button link type="primary" @click="step = 'install'">Không thấy Zalo mở?</el-button>
        </div>
        <div class="manual">
          <p class="muted small"><b>Gửi tay</b> (khi tiện ích lỗi): tải file hóa đơn, mở Zalo, chọn người nhận rồi <b>kéo file</b> vào khung chat.</p>
          <div class="row">
            <el-button size="small" :icon="Download" @click="downloadPdf">Tải file hóa đơn</el-button>
            <el-button size="small" :icon="ChatDotRound" @click="openZaloApp">Mở Zalo</el-button>
          </div>
        </div>
      </template>

      <template v-else-if="step === 'install'">
        <p><b>Máy tính này chưa cài "Gửi Zalo"</b> (cài 1 lần, để bấm là mở Zalo PC kèm hóa đơn như phần mềm cũ).</p>
        <ol>
          <li>Bấm <b>Tải bộ cài</b>, mở file <b class="mono">cai-gui-zalo.bat</b> vừa tải (Windows hỏi thì bấm <b>Chạy</b>).</li>
          <li>Quay lại đây bấm <b>Thử lại</b>. Trình duyệt hỏi "Mở Gửi Zalo?": tích <b>Luôn cho phép</b>, bấm <b>Mở</b>.</li>
        </ol>
        <p class="muted small">Máy cần cài sẵn Zalo PC.</p>
        <div class="row">
          <el-button type="primary" :icon="Download" @click="downloadInstaller">Tải bộ cài</el-button>
          <el-button :icon="RefreshRight" @click="openZaloPc">Thử lại</el-button>
        </div>
        <p class="muted small">Đã cài mà vẫn không mở được: gửi người cài đặt file nhật ký
          <span class="mono">%LOCALAPPDATA%\NhomSatKiemDien\gui-zalo.log</span> (Windows + R, dán đường dẫn, Enter).</p>
        <div class="manual">
          <p class="muted small"><b>Gửi tay</b>: tải file hóa đơn, mở Zalo, chọn người nhận rồi <b>kéo file</b> vào khung chat.</p>
          <div class="row">
            <el-button size="small" :icon="Download" @click="downloadPdf">Tải file hóa đơn</el-button>
            <el-button size="small" :icon="ChatDotRound" @click="openZaloApp">Mở Zalo</el-button>
          </div>
        </div>
      </template>

      <template v-else>
        <p class="err" role="alert">{{ error }}</p>
        <div class="row">
          <el-button v-if="pdf" :icon="Download" @click="downloadPdf">Tải file</el-button>
          <el-button :icon="RefreshRight" @click="start">Thử lại</el-button>
        </div>
      </template>
    </div>
    <template #footer><el-button @click="close">Đóng</el-button></template>
  </el-dialog>
</template>

<style scoped>
.zl { display: flex; flex-direction: column; gap: var(--space-md); }
.zl p, .zl ol { margin: 0; line-height: 1.55; }
.zl ol { padding-left: 20px; display: flex; flex-direction: column; gap: 4px; }
.loading { height: 120px; }
.big { width: 100%; }
.small { font-size: 13px; }
.row { display: flex; flex-wrap: wrap; gap: var(--space-sm); align-items: center; }
.row .el-button + .el-button { margin-left: 0; }
.err { color: var(--debt); }
.manual { border-top: 1px solid var(--steel-200); padding-top: var(--space-md); display: flex; flex-direction: column; gap: var(--space-sm); }
</style>
