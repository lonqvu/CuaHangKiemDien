import { test } from 'node:test'
import assert from 'node:assert/strict'
import { highlight } from '../src/format.ts'

const marked = (name: string, q: string) => highlight(name, q).filter((s) => s.m).map((s) => s.t)

test('go dau phay to dam so dau cham trong ten', () => {
  assert.deepEqual(marked('Tôn lạnh 0.40mm', 'ton 0,4'), ['Tôn', '0.4'])
  assert.deepEqual(marked('Que hàn 2.5', 'que 2,5'), ['Que', '2.5'])
})

test('zaloChatUrl: mo thang khung chat Zalo theo so di dong', async () => {
  const { zaloChatUrl } = await import('../src/format.ts')
  assert.equal(zaloChatUrl('0912 345 678'), 'https://zalo.me/0912345678')
  assert.equal(zaloChatUrl('+84 912.345.678'), 'https://zalo.me/0912345678')
  assert.equal(zaloChatUrl('84912345678'), 'https://zalo.me/0912345678')
  assert.equal(zaloChatUrl('0383987987'), 'https://zalo.me/0383987987')
  assert.equal(zaloChatUrl('0283777999'), null) // so ban -> khong co Zalo, mo Zalo tu chon
  assert.equal(zaloChatUrl('091234567'), null)  // thieu so
  assert.equal(zaloChatUrl(null), null)          // khach le
  assert.equal(zaloChatUrl(''), null)
})
