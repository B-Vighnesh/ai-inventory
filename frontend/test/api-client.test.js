import test from 'node:test'
import assert from 'node:assert/strict'
import { createApiClient, ApiError, consumeEventStream } from '../src/api-client.js'

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
})

test('loads pending pricing and reorder suggestions using backend DTO productId', async () => {
  const urls = []
  const api = createApiClient({ fetchImpl: async (url) => {
    urls.push(url)
    return json([{ id: url.includes('pricing') ? 'price-1' : 'reorder-1', productId: 'PRD-001' }])
  } })
  const result = await api.listSuggestions()
  assert.deepEqual(urls.sort(), ['/api/pricing-suggestions?status=PENDING', '/api/reorder-suggestions?status=PENDING'])
  assert.equal(result.pricing[0].type, 'pricing')
  assert.equal(result.reorder[0].type, 'reorder')
  assert.equal(result.pricing[0].productId, 'PRD-001')
})

test('writes orders, stock, and resolutions to the documented routes', async () => {
  const calls = []
  const api = createApiClient({ baseUrl: 'http://localhost:8080/', fetchImpl: async (url, options) => {
    calls.push([url, options.method, JSON.parse(options.body)])
    assert.equal(options.headers['Content-Type'], 'application/json')
    return json({ id: 'saved' })
  } })
  await api.order('PRD/001', 5)
  await api.setStock('PRD-001', 0)
  await api.resolve('pricing', 'price-1', 'ACCEPT')
  await api.resolve('reorder', 'reorder-1', 'REJECT')
  assert.deepEqual(calls, [
    ['http://localhost:8080/products/PRD%2F001/orders', 'POST', { quantity: 5 }],
    ['http://localhost:8080/products/PRD-001/stock', 'PATCH', { stockLevel: 0 }],
    ['http://localhost:8080/pricing-suggestions/price-1', 'PATCH', { action: 'ACCEPT' }],
    ['http://localhost:8080/reorder-suggestions/reorder-1', 'PATCH', { action: 'REJECT' }],
  ])
})

test('preserves backend conflict and validation messages', async () => {
  const conflict = createApiClient({ fetchImpl: async () => json({ message: 'Quantity exceeds available stock' }, 409) })
  await assert.rejects(conflict.order('PRD-001', 99), (error) => error.status === 409 && error.message === 'Quantity exceeds available stock')
  const validation = createApiClient({ fetchImpl: async () => json({ message: 'Validation failed', details: ['stockLevel: must be greater than or equal to 0'] }, 400) })
  await assert.rejects(validation.setStock('PRD-001', -1), (error) => error.status === 400 && error.message.includes('stockLevel:'))
})

test('a network failure never returns fake inventory and a subsequent request can recover', async () => {
  let available = false
  const api = createApiClient({ fetchImpl: async () => {
    if (!available) throw new TypeError('fetch failed')
    return json([{ id: 'REAL-PRODUCT' }])
  } })
  await assert.rejects(api.listProducts(), /Cannot reach the backend/)
  assert.equal(api.mode, 'live')
  available = true
  assert.deepEqual(await api.listProducts(), [{ id: 'REAL-PRODUCT' }])
})

test('timeouts explain that a mutation might already have been saved', async () => {
  const api = createApiClient({ timeout: 5, fetchImpl: (_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
  }) })
  await assert.rejects(api.order('PRD-001'), /Refresh before retrying a change/)
})

test('rejects HTML fallback pages and handles non-JSON proxy failures', async () => {
  const html = createApiClient({ fetchImpl: async () => new Response('<html>Vite index</html>', { headers: { 'Content-Type': 'text/html' } }) })
  await assert.rejects(html.listProducts(), /unexpected response/)
  const proxy = createApiClient({ fetchImpl: async () => new Response('Connection refused', { status: 502 }) })
  await assert.rejects(proxy.listProducts(), (error) => error instanceof ApiError && error.status === 502)
})

test('does not dispatch unsupported suggestion actions', () => {
  const api = createApiClient({ fetchImpl: () => assert.fail('must not fetch') })
  assert.throws(() => api.resolve('unknown', 'id', 'ACCEPT'), /Invalid suggestion action/)
  assert.throws(() => api.suggest('id', 'unknown'), /Invalid suggestion type/)
})


test('streams recommendation tokens and completion across chunk boundaries', async () => {
  const encoder = new TextEncoder()
  const parts = ['event: token\ndata: {"text":"hello"}\n', '\nevent: result\ndata: {"status":"COMPLETED"}\n\n']
  const stream = new ReadableStream({ start(controller) { parts.forEach(p => controller.enqueue(encoder.encode(p))); controller.close() } })
  const events = []
  await consumeEventStream(stream, (event, data) => events.push([event, data]))
  assert.deepEqual(events, [['token', { text: 'hello' }], ['result', { status: 'COMPLETED' }]])
})

test('manual review and runtime settings use backend endpoints', async () => {
  const calls = []
  const api = createApiClient({ fetchImpl: async (url, options) => { calls.push([url, options.method]); return json({ status: 'QUEUED' }) } })
  await api.review('PRD-003')
  await api.updateSettings({ strategy: 'RULE_BASED', demandSpikeMultiplier: 3 })
  await api.history('PRD-003')
  assert.deepEqual(calls, [['/api/products/PRD-003/review', 'POST'], ['/api/settings/commerce', 'PATCH'], ['/api/products/PRD-003/history', 'GET']])
})
