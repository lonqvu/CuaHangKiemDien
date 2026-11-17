import { test } from 'node:test'
import assert from 'node:assert/strict'
import { highlight } from '../src/format.ts'

const marked = (name: string, q: string) => highlight(name, q).filter((s) => s.m).map((s) => s.t)

test('go dau phay to dam so dau cham trong ten', () => {
  assert.deepEqual(marked('Tôn lạnh 0.40mm', 'ton 0,4'), ['Tôn', '0.4'])
  assert.deepEqual(marked('Que hàn 2.5', 'que 2,5'), ['Que', '2.5'])
})
