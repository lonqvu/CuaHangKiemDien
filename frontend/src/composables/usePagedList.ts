import { reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import http from '../api/http'
import type { Page } from '../api/types'

type Value = string | number | boolean
export type ListQuery = { page: number; size: number; sort: string } & Record<string, Value>

export const PAGE_SIZES = [20, 50, 100]

/**
 * Danh sach co phan trang phia server.
 * - sync = true: trang, so dong/trang, sap xep va bo loc nam tren URL -> bam Quay lai, tai lai trang, gui link deu giu dung danh sach.
 * - sync = false: dung cho danh sach phu trong drawer (so no, hoa don cua khach).
 * Doi bo loc/sap xep thi ve trang 1. page tren URL tinh tu 1, gui len server tinh tu 0.
 */
export function usePagedList<T, S = Record<string, unknown>>(
  url: string | (() => string),
  defaults: Record<string, Value>,
  opts: { sync?: boolean; params?: (q: ListQuery) => Record<string, unknown> } = {},
) {
  const sync = opts.sync ?? true
  const base: ListQuery = { page: 1, size: PAGE_SIZES[0], sort: '', ...defaults }
  const route = sync ? useRoute() : null
  const router = sync ? useRouter() : null
  const path = route?.path.replace(/\/\d+$/, '') // /orders/12 -> /orders: drawer chi tiet khong lam doi danh sach

  const query = reactive<ListQuery>({ ...base })
  const items = shallowRef<T[]>([])
  const total = ref(0)
  const summary = shallowRef<S | null>(null)
  const loading = ref(false)
  const failed = ref(false)
  let seq = 0

  function parse(raw: Record<string, unknown>) {
    for (const [k, d] of Object.entries(base)) {
      const v = raw[k]
      const s = Array.isArray(v) ? v[0] : v
      if (s === undefined || s === null || s === '') { (query as any)[k] = d; continue }
      if (typeof d === 'number') (query as any)[k] = Number.isFinite(Number(s)) ? Number(s) : d
      else if (typeof d === 'boolean') (query as any)[k] = s === '1' || s === 'true'
      else (query as any)[k] = String(s)
    }
    if (!PAGE_SIZES.includes(query.size)) query.size = PAGE_SIZES[0]
    if (query.page < 1) query.page = 1
  }

  /** Chi ghi len URL nhung gia tri khac mac dinh, cho link gon. */
  function serialize(): LocationQueryRaw {
    const q: LocationQueryRaw = {}
    for (const [k, d] of Object.entries(base)) {
      const v = (query as any)[k]
      if (v === d || v === '' || v === null || v === undefined) continue
      q[k] = typeof v === 'boolean' ? (v ? '1' : '0') : String(v)
    }
    return q
  }

  async function load() {
    const my = ++seq
    loading.value = true
    failed.value = false
    try {
      const raw = opts.params ? opts.params(query) : { ...query }
      const params: Record<string, unknown> = {}
      for (const [k, v] of Object.entries(raw)) if (v !== '' && v !== null && v !== undefined) params[k] = v
      params.page = query.page - 1
      const { data } = await http.get<Page<T, S>>(typeof url === 'function' ? url() : url, { params })
      if (my !== seq) return // da co yeu cau moi hon
      // Trang vuot qua so trang (vd vua xoa dong cuoi cua trang cuoi) -> lui ve trang cuoi
      if (!data.items.length && data.total > 0 && query.page > 1) {
        return set({ page: Math.ceil(data.total / query.size) })
      }
      items.value = data.items
      total.value = data.total
      summary.value = data.summary
    } catch {
      if (my === seq) failed.value = true
    } finally {
      if (my === seq) loading.value = false
    }
  }

  /** Doi bo loc/trang. Khong doi trang ma doi thu khac -> ve trang 1. */
  function set(patch: Partial<ListQuery>) {
    const resetPage = Object.keys(patch).some((k) => k !== 'page')
    Object.assign(query, patch)
    if (resetPage && !('page' in patch)) query.page = 1
    if (sync) router!.replace({ path, query: serialize() })
    else load()
  }

  /** Cot sap xep cua el-table (sortable="custom") <-> tham so sort "truong,asc|desc". */
  function onSort({ prop, order }: { prop: string | null; order: 'ascending' | 'descending' | null }) {
    set({ sort: prop && order ? `${prop},${order === 'ascending' ? 'asc' : 'desc'}` : '' })
  }
  const defaultSort = () => {
    const [prop, dir] = query.sort.split(',')
    return prop ? { prop, order: dir === 'asc' ? 'ascending' : 'descending' } as const : undefined
  }

  if (sync) {
    parse(route!.query)
    // Moi lan URL doi (bam trang, loc, Quay lai/Tien toi) -> doc lai va tai
    // Khoa la CHUOI (duong dan bo so id + bo loc): mo / dong chi tiet (/orders <-> /orders/12) khong doi khoa -> khong tai
    // lai danh sach (truoc day so sanh mang moi moi lan -> moi lan mo / dong chi tiet danh sach nhay 1 cai).
    watch(() => route!.path.replace(/\/\d+$/, '') + '?' + JSON.stringify(route!.query), (key, old) => {
      if (!key.startsWith(path + '?') || !old?.startsWith(path + '?')) return // da roi khoi man nay
      parse(route!.query)
      load()
    })
  }

  return { query, items, total, summary, loading, failed, load, set, onSort, defaultSort, reset: () => { Object.assign(query, base); load() } }
}
