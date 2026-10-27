export const HOT_MULT = 3

export const money = (n) =>
  n == null || Number.isNaN(Number(n))
    ? '—'
    : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(n))

export const pct = (n, digits = 0) => `${(Number(n) * 100).toFixed(digits)}%`

export const signedPct = (from, to) => {
  if (!from) return '0.0%'
  const d = ((to - from) / from) * 100
  return `${d > 0 ? '+' : ''}${d.toFixed(1)}%`
}

export const timeAgo = (iso) => {
  if (!iso) return ''
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 5) return 'just now'
  if (s < 60) return `${s}s ago`
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  return `${Math.floor(s / 3600)}h ago`
}

// Exclude the subject product, matching the backend category-peer comparison.
export const categoryAverages = (products) => Object.fromEntries(products.map(product => {
  const peers = products.filter(p => p.category === product.category && p.id !== product.id)
  return [product.id, peers.length ? peers.reduce((sum, p) => sum + p.demandVelocity, 0) / peers.length : 0]
}))

export const prettyCategory = (c) => (c ? c.charAt(0) + c.slice(1).toLowerCase() : '')

export const TRIGGERS = {
  INVENTORY_LOW: { label: 'Inventory low', tone: 'accent', auto: true },
  DEMAND_SPIKE: { label: 'Demand spike', tone: 'amber', auto: true },
  INITIAL: { label: 'Initial', tone: 'neutral', auto: false },
  MANUAL: { label: 'Manual', tone: 'neutral', auto: false },
}

export const STATUS_LABEL = {
  ACTIVE: 'Active',
  PRICE_REVIEW_PENDING: 'Price review',
  OUT_OF_STOCK: 'Out of stock',
}
