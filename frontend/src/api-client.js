export class ApiError extends Error {
  constructor(message, status = 0, details = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

export function createApiClient({ baseUrl = '/api', fetchImpl = globalThis.fetch, timeout = 10000 } = {}) {
  const base = baseUrl.replace(/\/+$/, '')

  async function http(path, { method = 'GET', body } = {}) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeout)
    try {
      const response = await fetchImpl(base + path, {
        method,
        headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      })
      if (!response.ok) {
        let error = {}
        try { error = await response.json() } catch { /* Proxy errors may not contain JSON. */ }
        const details = Array.isArray(error.details) ? error.details : []
        const message = error.message || error.error || `Request failed (${response.status})`
        throw new ApiError(details.length ? `${message}: ${details.join('; ')}` : message, response.status, details)
      }
      if (response.status === 204) return null
      if (!response.headers.get('content-type')?.includes('application/json')) {
        throw new ApiError('The API returned an unexpected response. Check the backend URL.', response.status)
      }
      return await response.json()
    } catch (error) {
      if (error instanceof ApiError) throw error
      if (error.name === 'AbortError') throw new ApiError('The request timed out. Refresh before retrying a change; it may have been saved.')
      throw new ApiError('Cannot reach the backend. Check that it is running and retry.')
    } finally {
      clearTimeout(timer)
    }
  }

  const idPath = (id) => encodeURIComponent(id)
  return {
    mode: 'live',
    getSettings: () => http('/settings/commerce'),
    updateSettings: (settings) => http('/settings/commerce', { method: 'PATCH', body: settings }),
    listJobs: () => http('/recommendation-jobs'),
    review: (id) => http(`/products/${idPath(id)}/review`, { method: 'POST' }),
    suggest: (id, type) => {
      if (!['pricing', 'reorder'].includes(type)) throw new ApiError('Invalid suggestion type')
      return http(`/products/${idPath(id)}/suggest-${type}`, { method: 'POST' })
    },
    history: (id) => http(`/products/${idPath(id)}/history`),
    createProduct: (product) => http('/products', { method: 'POST', body: product }),
    async streamPrice(id, onEvent) {
      const controller = new AbortController()
      const timer = setTimeout(() => controller.abort(), 60000)
      try {
        const response = await fetchImpl(base + `/products/${idPath(id)}/suggest-pricing/stream`, {
          method: 'POST', headers: { Accept: 'text/event-stream' }, signal: controller.signal,
        })
        if (!response.ok) {
          let body = {}; try { body = await response.json() } catch {}
          throw new ApiError(body.message || `Streaming failed (${response.status})`, response.status)
        }
        if (!response.headers.get('content-type')?.includes('text/event-stream')) throw new ApiError('Invalid streaming response')
        await consumeEventStream(response.body, onEvent)
      } finally { clearTimeout(timer) }
    },
    listProducts: () => http('/products'),
    async listSuggestions() {
      const [pricing, reorder] = await Promise.all([
        http('/pricing-suggestions?status=PENDING'),
        http('/reorder-suggestions?status=PENDING'),
      ])
      return {
        pricing: pricing.map((suggestion) => ({ ...suggestion, type: 'pricing' })),
        reorder: reorder.map((suggestion) => ({ ...suggestion, type: 'reorder' })),
      }
    },
    order: (id, quantity = 1) => http(`/products/${idPath(id)}/orders`, { method: 'POST', body: { quantity } }),
    setStock: (id, stockLevel) => http(`/products/${idPath(id)}/stock`, { method: 'PATCH', body: { stockLevel } }),
    resolve: (type, id, action) => {
      if (!['pricing', 'reorder'].includes(type) || !['ACCEPT', 'REJECT'].includes(action)) {
        throw new ApiError('Invalid suggestion action')
      }
      return http(`/${type}-suggestions/${idPath(id)}`, { method: 'PATCH', body: { action } })
    },
  }
}

// POST-based SSE reader: fetch handles the request body/method that EventSource cannot.
export async function consumeEventStream(body, onEvent) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = '', event = 'message', data = [], completed = false
  const line = (text) => {
    text = text.replace(/\r$/, '')
    if (text === '') {
      if (data.length) {
        const payload = JSON.parse(data.join('\n'))
        onEvent(event, payload)
        if (event === 'result') completed = true
      }
      event = 'message'; data = []
    } else if (text.startsWith('event:')) event = text.slice(6).trim()
    else if (text.startsWith('data:')) data.push(text.slice(5).trimStart())
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      let index
      while ((index = buffer.indexOf('\n')) >= 0) {
        line(buffer.slice(0, index)); buffer = buffer.slice(index + 1)
      }
      if (done) break
    }
    if (buffer) line(buffer)
    line('')
    if (!completed) throw new ApiError('The stream ended before completion. Refresh to check the review status.')
  } finally { reader.releaseLock() }
}
