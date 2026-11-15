export const money = (v: number | null | undefined) =>
  new Intl.NumberFormat('vi-VN').format(Number(v ?? 0)) + ' đ'
export const dt = (s: string | null) => (s ? new Date(s).toLocaleString('vi-VN') : '')
