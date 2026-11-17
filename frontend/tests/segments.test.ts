import { test } from 'node:test'
import assert from 'node:assert/strict'
import { groupItems, parseSegments } from '../src/format.ts'

const segs = (t: string) => parseSegments(t).segments.map((g) => [g.quantity, g.sheets])

test('doc chuoi nhieu doan', () => {
  assert.deepEqual(segs('4,7x2 4,5x2 3x1'), [[4.7, 2], [4.5, 2], [3, 1]])
  assert.deepEqual(segs('4.7 x 2; 4,5×2 + 3'), [[4.7, 2], [4.5, 2], [3, 0]]) // khong co x = so met tron
  assert.deepEqual(segs('4,7x2, 4,5x2'), [[4.7, 2], [4.5, 2]]) // ", " la dau ngan cach
  assert.deepEqual(segs('2,85X6 4,7 x 3'), [[2.85, 6], [4.7, 3]])
  assert.deepEqual(segs('   '), [])
})

test('doan sai -> bao loi, khong lay doan nao', () => {
  assert.deepEqual(segs('4,x5'), [[4, 5]])
  for (const t of ['4,7x', '4,7x0', '0x2', 'abc', '4,7x2,5', '4,7x2 x3']) {
    const r = parseSegments(t)
    assert.equal(r.segments.length, 0, t)
    assert.ok(r.error, t)
  }
  assert.match(parseSegments('4,7x2 4,5y2').error, /4,5y2/)
})

test('gom dong cung san pham + don gia, giu thu tu', () => {
  const it = (id: number, productId: number, unitPrice: number, sheets: number, totalQuantity: number, lineTotal: number) => ({ id, productId, unitPrice, sheets, totalQuantity, lineTotal })
  const g = groupItems([it(1, 7, 95000, 2, 10.152, 964440), it(2, 9, 280000, 0, 6, 1680000), it(3, 7, 95000, 2, 9.72, 923400), it(4, 7, 99000, 1, 3.24, 320760)])
  assert.deepEqual(g.map((x) => x.items.map((i) => i.id)), [[1, 3], [2], [4]]) // gia khac -> cum rieng
  assert.equal(g[0].sheets, 4)
  assert.equal(g[0].totalQuantity, 19.872)
  assert.equal(g[0].lineTotal, 1887840)
})
