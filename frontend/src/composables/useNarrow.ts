import { onBeforeUnmount, ref } from 'vue'

/** true khi man hinh hep (dien thoai): an bot cot phu de cot quan trong (tien, no) hien ngay khong phai cuon ngang. */
export function useNarrow(query = '(max-width: 640px)') {
  const mq = window.matchMedia(query)
  const narrow = ref(mq.matches)
  const on = (e: MediaQueryListEvent) => (narrow.value = e.matches)
  mq.addEventListener('change', on)
  onBeforeUnmount(() => mq.removeEventListener('change', on))
  return narrow
}
