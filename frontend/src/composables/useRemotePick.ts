import { ref, type Ref } from 'vue'
import { debounce } from '../format'

/**
 * O tim kieu quay ban hang (dung chung man Ban hang, Nhap hang): tim o server, Enter chon ket qua DAU TIEN.
 * - Bo qua ket qua cu ve muon hon ket qua moi (moi lan go tang seq).
 * - Go nhanh roi Enter truoc khi ket qua ve: nho lai, ket qua cua DUNG cau vua go ve thi chon luon ket qua dau
 *   (khong bao gio chon tu danh sach cu theo cau tim truoc).
 * Dung voi el-select: filterable remote default-first-option :debounce="0" :remote-method="search" :loading="loading",
 * va @keydown.enter.capture="onEnter" o phan tu bao ngoai.
 * keep: muc dang chon, giu trong danh sach (de el-select con hien ten) neu ket qua moi khong co - nhung Enter khong chon no.
 */
export function useRemotePick<T extends { id: number }>(
  fetcher: (q: string) => Promise<T[]>,
  onEnterPick: (first: T) => void,
  opts: { keep?: () => T | null | undefined; ms?: number } = {},
) {
  const options = ref([]) as Ref<T[]>
  const loading = ref(false)
  const query = ref('')
  let seq = 0
  let enterPending = false
  const run = debounce(async (q: string, s: number) => {
    try {
      const data = await fetcher(q)
      if (s !== seq) return
      const k = opts.keep?.()
      options.value = k && !data.some((x) => x.id === k.id) ? [...data, k] : data
      if (enterPending) {
        enterPending = false
        if (data.length) onEnterPick(data[0])
      }
    } finally {
      if (s === seq) loading.value = false
    }
  }, opts.ms ?? 200)
  function search(q: string) {
    query.value = q
    loading.value = true
    run(q, ++seq)
  }
  function onEnter() {
    if (loading.value) enterPending = true
  }
  return { options, loading, query, search, onEnter }
}
