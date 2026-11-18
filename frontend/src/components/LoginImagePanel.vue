<script setup lang="ts">
/*
 * Anh nen khung trai man dang nhap (man Tai khoan, chi quan tri).
 * Anh chon duoc thu nho tren may (canh dai toi da 1920px, JPEG) truoc khi tai len: anh dien thoai 4-8 MB
 * con vai tram KB -> man dang nhap tai nhanh tren 4G, khong vuot gioi han 3 MB cua may chu.
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, RefreshLeft } from '@element-plus/icons-vue'
import http from '../api/http'
import { dtShort } from '../format'

const info = ref<{ hasImage: boolean; updatedAt: string | null; updatedBy: string | null } | null>(null)
const busy = ref(false)
const fileInput = ref<HTMLInputElement>()
const version = ref(Date.now()) // doi sau khi tai len -> xem truoc lay anh moi

async function load() { info.value = (await http.get('/settings/login-image')).data }
onMounted(load)

const MAX_SIDE = 1920

/** Thu nho + chuyen JPEG. Anh khong doc duoc (vd HEIC tren may tinh) -> loi ro rang. */
async function shrink(file: File): Promise<Blob> {
  let bmp: ImageBitmap
  try {
    bmp = await createImageBitmap(file)
  } catch {
    throw new Error('Không đọc được ảnh này. Chọn ảnh JPG hoặc PNG (ảnh HEIC của iPhone: chụp màn hình hoặc đổi sang JPG).')
  }
  const k = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height))
  const c = document.createElement('canvas')
  c.width = Math.round(bmp.width * k)
  c.height = Math.round(bmp.height * k)
  const ctx = c.getContext('2d')!
  ctx.fillStyle = '#16243A' // anh PNG trong suot -> nen toi giong mac dinh
  ctx.fillRect(0, 0, c.width, c.height)
  ctx.drawImage(bmp, 0, 0, c.width, c.height)
  bmp.close()
  return new Promise((ok, fail) => c.toBlob((b) => (b ? ok(b) : fail(new Error('Không xử lý được ảnh'))), 'image/jpeg', 0.85))
}

async function onPick(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  ;(e.target as HTMLInputElement).value = '' // chon lai cung file van chay
  if (!file) return
  busy.value = true
  try {
    const blob = await shrink(file)
    await http.put('/settings/login-image', blob, { headers: { 'Content-Type': 'image/jpeg' } })
    version.value = Date.now()
    await load()
    ElMessage.success('Đã đổi ảnh màn đăng nhập')
  } catch (err: any) {
    if (!err?.isAxiosError) ElMessage.error(err?.message || 'Không đổi được ảnh') // loi API: interceptor da bao
  } finally {
    busy.value = false
  }
}

async function useDefault() {
  await ElMessageBox.confirm('Bỏ ảnh, dùng lại nền mặc định cho màn đăng nhập?', 'Dùng nền mặc định',
    { confirmButtonText: 'Dùng mặc định', cancelButtonText: 'Không', type: 'warning' })
  await http.delete('/settings/login-image')
  await load()
  ElMessage.success('Đã về nền mặc định')
}
</script>

<template>
  <section class="panel panel-pad li" aria-labelledby="li-title">
    <div class="li-head">
      <div>
        <h2 id="li-title">Ảnh màn đăng nhập</h2>
        <p class="muted">Ảnh hiện ở khung bên trái màn đăng nhập (ảnh cửa hàng, kho hàng, sản phẩm...). Nên chọn ảnh ngang.</p>
      </div>
      <div class="li-actions">
        <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" hidden aria-label="Chọn ảnh màn đăng nhập" @change="onPick" />
        <el-button type="primary" :icon="Picture" :loading="busy" @click="fileInput?.click()">{{ info?.hasImage ? 'Đổi ảnh' : 'Chọn ảnh' }}</el-button>
        <el-button v-if="info?.hasImage" :icon="RefreshLeft" @click="useDefault">Dùng mặc định</el-button>
      </div>
    </div>
    <!-- xem truoc: cung ti le khung trai man dang nhap -->
    <div class="preview" :class="{ empty: !info?.hasImage }">
      <img v-if="info?.hasImage" :src="`/api/public/login-image?v=${version}`" alt="Ảnh màn đăng nhập hiện tại" />
      <div class="preview-text"><b>Nhôm Sắt Kiểm Diên</b><span>Bán hàng theo tấm, tính hệ số, ghi công nợ khách quen.</span></div>
    </div>
    <p v-if="info?.hasImage && info.updatedAt" class="muted small">Đổi lúc {{ dtShort(info.updatedAt) }} bởi {{ info.updatedBy }}.</p>
    <p v-else class="muted small">Đang dùng nền mặc định.</p>
  </section>
</template>

<style scoped>
.li { margin-top: var(--space-lg); display: flex; flex-direction: column; gap: var(--space-md); }
.li-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-lg); flex-wrap: wrap; }
.li-head h2 { font-size: 17px; margin: 0 0 4px; }
.li-head p { margin: 0; font-size: 14px; }
.li-actions { display: flex; gap: var(--space-sm); }
.li-actions .el-button + .el-button { margin-left: 0; }
.preview { position: relative; width: min(100%, 420px); aspect-ratio: 5 / 4; border-radius: var(--radius-md); overflow: hidden; background: var(--steel-900, #16243A); }
.preview img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.preview::after { content: ''; position: absolute; inset: 0; background: linear-gradient(to top, rgba(15, 23, 42, 0.85) 0%, rgba(15, 23, 42, 0.35) 40%, rgba(15, 23, 42, 0) 70%); }
.preview-text { position: absolute; left: 16px; right: 16px; bottom: 14px; z-index: 1; color: #fff; display: flex; flex-direction: column; gap: 2px; }
.preview-text b { font-size: 18px; }
.preview-text span { font-size: 12px; color: #E2E8F0; }
.small { font-size: 12px; margin: 0; }
</style>
