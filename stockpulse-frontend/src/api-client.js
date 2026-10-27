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
