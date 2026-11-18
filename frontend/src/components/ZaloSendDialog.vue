<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Download, Picture, ChatDotRound } from '@element-plus/icons-vue'
import http, { errorMessage } from '../api/http'
import { zaloChatUrl } from '../format'

/*
 * Gui hoa don / bien lai qua Zalo. Zalo khong co API cong khai de web gui file toi 1 nguoi, nen:
 *  - Dien thoai / may tinh bang: bang chia se cua he dieu hanh (Web Share API) kem ANH -> chon Zalo -> chon nguoi nhan.
 *  - May tinh: chep ANH vao clipboard + mo thang khung chat Zalo cua khach (zalo.me/<so di dong>) -> Ctrl+V, Enter.
 *    Khach khong co so di dong: mo Zalo PC (giao thuc zalo://) -> tu chon nguoi nhan.
 *    Du phong: Zalo Web, tai anh ve, chep noi dung tin nhan.
 * Anh ve tu chinh mau Jasper (giong ban in). imagePath loi (vd chua co bien lai) -> gui tin nhan chu.
 */
const props = defineProps<{
  modelValue: boolean
  title: string            // vd "Hóa đơn HD00000960"
  imagePath?: string       // vd /orders/12/invoice.png (bo qua -> chi gui chu)
  fileName: string         // ten anh khi chia se / tai ve
  text: string             // noi dung tin nhan kem theo
  phone?: string | null    // SDT nguoi nhan (de tim trong Zalo)
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()

type Step = 'loading' | 'shared' | 'desktop' | 'error'
const step = ref<Step>('loading')
const blob = ref<Blob | null>(null)
const preview = ref('')
const copied = ref(false)
const imageError = ref('')
const appOpened = ref<boolean | null>(null)
// khung chat Zalo cua khach (co so di dong); chatOpened: false = trinh duyet chan mo tab -> hien nut bam
const chatUrl = computed(() => zaloChatUrl(props.phone))
const chatOpened = ref<boolean | null>(null)
function openChat() {
  if (!chatUrl.value) return
  // khong dung 'noopener' o day: can biet tab co mo duoc khong (bi chan -> null); mo xong thi cat lien ket ngay
  const w = window.open(chatUrl.value, '_blank')
  if (w) w.opener = null
  chatOpened.value = !!w
}

const mobile = () => {
  const ua = navigator.userAgent
  return (navigator as any).userAgentData?.mobile === true || /Android|iPhone|iPad|iPod/i.test(ua)
    || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) // iPad bao la Mac
}

const file = () => new File([blob.value!], props.fileName, { type: 'image/png' })

async function copyImage() {
  if (!blob.value) return false
  try {
    await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob.value })])
    copied.value = true
    return true
  } catch {
    copied.value = false
    return false
  }
}

async function copyText(t: string, what: string) {
  try {
    await navigator.clipboard.writeText(t)
    ElMessage.success(`Đã chép ${what}`)
  } catch {
    ElMessage.warning('Trình duyệt không cho chép. Bôi đen rồi nhấn Ctrl+C.')
  }
}

/** Mo Zalo PC qua giao thuc zalo:// (Zalo PC dang ky khi cai). Trinh duyet hoi "Mo Zalo?" lan dau. */
function openZaloApp() {
  appOpened.value = null
  const onBlur = () => (appOpened.value = true)
  window.addEventListener('blur', onBlur, { once: true })
  setTimeout(() => {
    window.removeEventListener('blur', onBlur)
    if (appOpened.value === null) appOpened.value = false
  }, 2500)
  window.location.href = 'zalo://'
}

function openZaloWeb() { window.open('https://chat.zalo.me/', '_blank', 'noopener') }

function download() {
  const a = document.createElement('a')
  a.href = preview.value
  a.download = props.fileName
  a.click()
}

async function start() {
  step.value = 'loading'
  copied.value = false
  imageError.value = ''
  appOpened.value = null
  chatOpened.value = null
  blob.value = null
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''

  if (props.imagePath) {
    try {
      const res = await http.get(props.imagePath, { responseType: 'blob', silent: true } as any)
      blob.value = res.data as Blob
      preview.value = URL.createObjectURL(blob.value)
    } catch (e: any) {
      // loi tra ve dang blob -> doc message
      let msg = errorMessage(e)
      const data = e?.response?.data
      if (data instanceof Blob) { try { msg = JSON.parse(await data.text()).message || msg } catch { /* giu msg */ } }
      imageError.value = msg
    }
  }

  // Dien thoai: bang chia se he dieu hanh -> Zalo -> chon nguoi
  if (mobile() && navigator.share) {
    const data: ShareData = { title: props.title, text: props.text }
    if (blob.value && navigator.canShare?.({ files: [file()] })) data.files = [file()]
    try {
      await navigator.share(data)
      step.value = 'shared'
      return
    } catch (e: any) {
      if (e?.name === 'AbortError') { close(); return } // nguoi dung dong bang chia se
      // loi khac -> roi xuong cach may tinh
    }
  }

  // May tinh: chep anh (hoac chu) TRUOC (can trang dang duoc chon), roi mo khung chat cua khach / Zalo PC
  if (blob.value) await copyImage()
  else await navigator.clipboard?.writeText(props.text).then(() => (copied.value = true)).catch(() => {})
  step.value = 'desktop'
  if (chatUrl.value) openChat()
  else openZaloApp()
}

function close() { emit('update:modelValue', false) }

watch(() => props.modelValue, (v) => { if (v) start() })
onBeforeUnmount(() => { if (preview.value) URL.revokeObjectURL(preview.value) })

const phoneDigits = computed(() => (props.phone ?? '').replace(/\D/g, ''))
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`Gửi Zalo: ${title}`" width="min(560px, 94vw)" @update:model-value="close">
    <div v-if="step === 'loading'" v-loading="true" class="loading" element-loading-text="Đang tạo ảnh..." />

    <div v-else-if="step === 'shared'" class="done">
      <p><b>Đã mở bảng chia sẻ.</b> Chọn Zalo, chọn người nhận rồi gửi.</p>
    </div>

    <div v-else class="desk">
      <ol class="steps">
        <template v-if="chatUrl">
          <li v-if="chatOpened !== false">
            <b>Đã mở khung chat Zalo với <span class="num">{{ phone }}</span>.</b>
            Nếu trình duyệt hỏi "Mở Zalo?" hoặc hiện trang Zalo, bấm <b>Mở</b> / <b>Nhắn tin</b>.
          </li>
          <li v-else>
            Trình duyệt chặn tự mở. Bấm <el-button type="primary" size="small" :icon="ChatDotRound" @click="openChat">Mở chat với khách</el-button>
          </li>
        </template>
        <template v-else>
        <li v-if="appOpened !== false">
          <b>Zalo đang mở.</b> Nếu trình duyệt hỏi "Mở Zalo?", bấm <b>Mở</b>.
        </li>
        <li v-else>
          <b>Chưa mở được ứng dụng Zalo</b> (máy chưa cài Zalo PC hoặc bạn đã bấm Hủy). Dùng <b>Zalo Web</b> bên dưới.
        </li>
        <li>
          Chọn người nhận<template v-if="phoneDigits">: tìm số <b class="num">{{ phone }}</b>
            <el-button link type="primary" :icon="CopyDocument" @click="copyText(phoneDigits, 'số điện thoại')">Chép số</el-button></template>.
        </li>
        </template>
        <li v-if="blob">
          <template v-if="copied">Ảnh {{ title.charAt(0).toLowerCase() + title.slice(1) }} <b>đã được chép</b>. Bấm vào ô soạn tin, nhấn <kbd>Ctrl</kbd> + <kbd>V</kbd> rồi <kbd>Enter</kbd>.</template>
          <template v-else>Trình duyệt không cho chép ảnh. Bấm <b>Tải ảnh</b> rồi kéo file vào khung chat Zalo.</template>
        </li>
        <li v-else>
          <template v-if="copied">Nội dung tin nhắn <b>đã được chép</b>. Nhấn <kbd>Ctrl</kbd> + <kbd>V</kbd> rồi <kbd>Enter</kbd>.</template>
          <template v-else>Bấm <b>Chép nội dung</b> rồi dán vào Zalo.</template>
        </li>
      </ol>

      <p v-if="imageError" class="warn">{{ imageError }}. Sẽ gửi nội dung bằng chữ.</p>

      <img v-if="preview" :src="preview" :alt="title" class="preview" />
      <pre class="msg">{{ text }}</pre>

      <div class="actions">
        <el-button v-if="chatUrl" type="primary" :icon="ChatDotRound" @click="openChat">Mở chat với khách</el-button>
        <el-button :type="chatUrl ? 'default' : 'primary'" :icon="chatUrl ? undefined : ChatDotRound" @click="openZaloApp">{{ chatUrl ? 'Mở Zalo (tự chọn người)' : 'Mở lại Zalo' }}</el-button>
        <el-button @click="openZaloWeb">Mở Zalo Web</el-button>
        <el-button v-if="blob" :icon="Picture" @click="copyImage().then((ok) => ok ? ElMessage.success('Đã chép ảnh') : ElMessage.warning('Không chép được ảnh, hãy tải ảnh'))">Chép lại ảnh</el-button>
        <el-button v-if="blob" :icon="Download" @click="download">Tải ảnh</el-button>
        <el-button :icon="CopyDocument" @click="copyText(text, 'nội dung tin nhắn')">Chép nội dung</el-button>
      </div>
    </div>

    <template #footer><el-button @click="close">Đóng</el-button></template>
  </el-dialog>
</template>

<style scoped>
.loading { height: 160px; }
.done p { margin: 0; }
.desk { display: flex; flex-direction: column; gap: var(--space-lg); }
.steps { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: var(--space-sm); line-height: 1.55; }
.warn { margin: 0; color: var(--debt); font-size: 14px; }
.preview { width: 100%; max-height: 300px; object-fit: contain; object-position: top; border: 1px solid var(--steel-200); border-radius: var(--radius-md); background: #fff; }
.msg { margin: 0; white-space: pre-wrap; font-family: var(--font-sans); font-size: 14px; background: var(--steel-50); border-radius: var(--radius-md); padding: var(--space-md) var(--space-lg); }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); }
.actions .el-button + .el-button { margin-left: 0; }
kbd { font-family: var(--font-mono); font-size: 12px; padding: 0 5px; border: 1px solid var(--steel-200); border-radius: 4px; background: var(--steel-50); }
</style>
