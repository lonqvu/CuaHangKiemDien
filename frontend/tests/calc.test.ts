/*
 * Test phan tinh toan va nhap so o frontend. Chay: npm test (node:test, khong can thu vien them).
 * Bo case tinh tien dung chung voi backend: backend/src/test/resources/line-cases.json
 * (sinh bang scripts/gen-line-cases.py, ket qua tinh doc lap bang Decimal cua Python).
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { formatInput, groupDigits, highlight, parseNumber, previewLine } from '../src/format.ts'

interface Case {
  name: string; quantity: string; sheets: number; factor: string; unitPrice: string
  expectFactor: string; expectTotalQuantity: string; expectLineTotal: string; reject: boolean
}
const cases: Case[] = JSON.parse(readFileSync(new URL('../../backend/src/test/resources/line-cases.json', import.meta.url), 'utf8'))

test(`previewLine khop backend tren ${cases.length} case`, () => {
  const fails: string[] = []
  for (const c of cases) {
    const r = previewLine(Number(c.quantity), c.sheets, Number(c.factor), Number(c.unitPrice))
    const ok = r.factor === Number(c.expectFactor)
      && r.totalQuantity === Number(c.expectTotalQuantity)
      && r.lineTotal === Number(c.expectLineTotal)
      && (r.totalQuantity === 0) === c.reject
    if (!ok) fails.push(`${c.name}: he so ${r.factor}/${c.expectFactor}, SL ${r.totalQuantity}/${c.expectTotalQuantity}, tien ${r.lineTotal}/${c.expectLineTotal}`)
  }
  assert.deepEqual(fails, [])
})

test('parseNumber: so luong / he so (dau phay hay cham deu la thap phan)', () => {
  const d = (s: string) => parseNumber(s, 'decimal')
  assert.equal(d('4,2'), 4.2)
  assert.equal(d('4.2'), 4.2)
  assert.equal(d('1,08'), 1.08)
  assert.equal(d(' 12 '), 12)
  assert.equal(d('4,'), 4) // dang go do
  assert.equal(d(',5'), 0.5)
  assert.equal(d('1.234,5'), 1234.5) // ca hai dau: dau sau cung la thap phan
  assert.equal(d('1,234.5'), 1234.5)
  assert.equal(d('2,375'), 2.375) // KHONG duoc hieu thanh 2375
  assert.equal(d(''), null)
  assert.equal(d(','), null)
})

test('parseNumber: tien / so tam (bo moi dau ngan cach)', () => {
  const i = (s: string) => parseNumber(s, 'integer')
  assert.equal(i('95000'), 95000)
  assert.equal(i('95.000'), 95000)
  assert.equal(i('95,000'), 95000)
  assert.equal(i('12.450.000'), 12450000)
  assert.equal(i('1 500 000'), 1500000)
  assert.equal(i(''), null)
})

test('formatInput: so le dung dau phay (khong ngan cach nghin); so tien nhom nghin bang dau cham', () => {
  assert.equal(formatInput(4.2, 'decimal'), '4,2')
  assert.equal(formatInput(1.08, 'decimal', 4), '1,08')
  assert.equal(formatInput(2.375, 'decimal'), '2,375')
  assert.equal(formatInput(1000, 'decimal'), '1000') // khong hien "1.000" (doc nham thanh 1)
  assert.equal(formatInput(95000, 'integer'), '95.000')
  assert.equal(formatInput(1000000, 'integer'), '1.000.000')
  assert.equal(formatInput(999, 'integer'), '999')
  assert.equal(formatInput(null, 'integer'), '0')
  assert.equal(groupDigits('0001500'), '1.500')
  // so tien: hien -> go lai van ra dung so
  for (const n of [0, 5, 95000, 1000000, 121981883]) assert.equal(parseNumber(formatInput(n, 'integer'), 'integer'), n)
  // vong di vong ve: go -> so -> hien -> go lai van ra dung so
  for (const s of ['4,2', '0,5', '12', '2,375', '1,08']) assert.equal(parseNumber(formatInput(parseNumber(s, 'decimal'), 'decimal', 4), 'decimal'), parseNumber(s, 'decimal'))
})

test('highlight: to dam phan khop, khong dau', () => {
  const marked = (name: string, q: string) => highlight(name, q).filter((x) => x.m).map((x) => x.t)
  assert.deepEqual(marked('Tôn lạnh 0,4 ly', 'ton 0,4'), ['Tôn', '0,4'])
  assert.deepEqual(marked('Hộp sắt 40x80', 'HOP'), ['Hộp'])
  assert.equal(highlight('Tôn lạnh', '').map((x) => x.t).join(''), 'Tôn lạnh')
})
