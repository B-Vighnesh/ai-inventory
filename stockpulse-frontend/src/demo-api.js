// ---------- in-memory mock ----------
const SPIKE_MULTIPLIER = 1.4 // demo-friendly; the brief suggests 3x but seed data never reaches it
const AGENT_DELAY_MS = 1400 // simulates the async listener latency

const seed = () => [
  ['PRD-001', 'SKU-ELEC-001', 'Wireless Earbuds Pro', 'ELECTRONICS', 79.99, 45, 20, 3, 'ACTIVE'],
  ['PRD-002', 'SKU-ELEC-002', 'USB-C Hub 7-Port', 'ELECTRONICS', 34.99, 120, 30, 1, 'ACTIVE'],
  ['PRD-003', 'SKU-APP-001', 'Organic Cotton T-Shirt', 'APPAREL', 24.99, 8, 15, 12, 'PRICE_REVIEW_PENDING'],
  ['PRD-004', 'SKU-APP-002', 'Running Shorts - Navy', 'APPAREL', 39.99, 55, 20, 2, 'ACTIVE'],
  ['PRD-005', 'SKU-HOME-001', 'Ceramic Pour-Over Set', 'HOME', 49.99, 22, 10, 4, 'ACTIVE'],
  ['PRD-006', 'SKU-HOME-002', 'LED Desk Lamp - Dimmable', 'HOME', 59.99, 0, 15, 0, 'OUT_OF_STOCK'],
  ['PRD-007', 'SKU-ELEC-003', 'Portable Charger 20K', 'ELECTRONICS', 44.99, 18, 25, 8, 'ACTIVE'],
  ['PRD-008', 'SKU-APP-003', 'Hoodie - Heather Grey', 'APPAREL', 54.99, 11, 12, 15, 'ACTIVE'],
].map(([id, sku, name, category, currentPrice, stockLevel, reorderThreshold, demandVelocity, status]) => ({
  id, sku, name, category, currentPrice, stockLevel, reorderThreshold, demandVelocity, status,
}))

const db = { products: seed(), pricing: [], reorder: [], seq: 1 }
const delay = (ms = 220) => new Promise((r) => setTimeout(r, ms))
const uid = () => `sg-${db.seq++}`
const round2 = (n) => Math.round(n * 100) / 100
const getProduct = (id) => {
  const p = db.products.find((x) => x.id === id)
  if (!p) throw Object.assign(new Error('Product not found'), { status: 404 })
  return p
}
const categoryAvg = (cat) => {
  const xs = db.products.filter((p) => p.category === cat)
  return xs.reduce((a, p) => a + p.demandVelocity, 0) / Math.max(1, xs.length)
}
const hasPending = (list, productId, trigger) =>
  list.some((s) => s.productId === productId && s.triggerReason === trigger && s.status === 'PENDING')

function ruleReasoning(p, trigger, avg) {
  const ratio = avg ? (p.demandVelocity / avg).toFixed(1) : '1.0'
  if (trigger === 'INVENTORY_LOW')
    return `Stock is ${p.stockLevel} against a reorder threshold of ${p.reorderThreshold}. Raising the price modestly protects the remaining units and slows the sell-through while a replenishment order is in flight. A clearance discount was considered and rejected: demand velocity (${p.demandVelocity}/24h) shows the item is already moving.`
  if (trigger === 'DEMAND_SPIKE')
    return `Demand velocity is ${p.demandVelocity} orders in 24h, ${ratio}x the ${p.category.toLowerCase()} average of ${avg.toFixed(1)}. The item is trending, so a small increase captures margin without cooling demand. Larger moves risk the momentum.`
  return `On-demand review. Stock ${p.stockLevel} vs threshold ${p.reorderThreshold}; velocity ${ratio}x category average.`
}

function makeSuggestions(p, trigger) {
  const avg = categoryAvg(p.category)
  const now = new Date().toISOString()
  const created = []
  if (!hasPending(db.pricing, p.id, trigger)) {
    const lowStock = p.stockLevel < p.reorderThreshold
    const spike = p.demandVelocity > avg * SPIKE_MULTIPLIER
    const pct = trigger === 'INVENTORY_LOW' ? 0.1 : trigger === 'DEMAND_SPIKE' ? 0.05 : lowStock ? 0.1 : spike ? 0.05 : 0
    const rec = round2(p.currentPrice * (1 + pct))
    created.push({
      id: uid(), type: 'pricing', productId: p.id, productName: p.name,
      currentPrice: p.currentPrice, recommendedPrice: rec,
      direction: pct > 0 ? 'INCREASE' : 'HOLD',
      confidence: trigger === 'INVENTORY_LOW' ? 0.82 : trigger === 'DEMAND_SPIKE' ? 0.76 : 0.64,
      reasoning: ruleReasoning(p, trigger, avg), status: 'PENDING', triggerReason: trigger, createdAt: now,
      source: 'rule-based (demo)',
    })
    db.pricing.push(created[created.length - 1])
  }
  if (!hasPending(db.reorder, p.id, trigger)) {
    const qty = Math.max(1, p.reorderThreshold * 3 - p.stockLevel)
    const s = {
      id: uid(), type: 'reorder', productId: p.id, productName: p.name,
      currentStock: p.stockLevel, recommendedQuantity: qty, suggestedLeadTimeDays: 7,
      confidence: trigger === 'MANUAL' ? 0.6 : 0.78,
      reasoning: `Target cover is 3x the reorder threshold (${p.reorderThreshold * 3} units). With ${p.stockLevel} on hand, ordering ${qty} restores the buffer. Assumed supplier lead time of 7 days.`,
      status: 'PENDING', triggerReason: trigger, createdAt: now, source: 'rule-based (demo)',
    }
    db.reorder.push(s)
    created.push(s)
  }
  if (created.some((c) => c.type === 'pricing') && p.status === 'ACTIVE') p.status = 'PRICE_REVIEW_PENDING'
  return created
}

// Fires "because something changed", asynchronously, never on the request path.
function fireAgenticLoop(productId) {
  setTimeout(() => {
    const p = db.products.find((x) => x.id === productId)
    if (!p || p.stockLevel === 0) return
    const avg = categoryAvg(p.category)
    if (p.stockLevel < p.reorderThreshold) makeSuggestions(p, 'INVENTORY_LOW')
    if (p.demandVelocity > avg * SPIKE_MULTIPLIER) makeSuggestions(p, 'DEMAND_SPIKE')
  }, AGENT_DELAY_MS)
}

export const mock = {
  mode: 'demo',
  async listProducts() {
    await delay()
    return structuredClone(db.products)
  },
  async listSuggestions() {
    await delay()
    return {
      pricing: structuredClone(db.pricing.filter((s) => s.status === 'PENDING')),
      reorder: structuredClone(db.reorder.filter((s) => s.status === 'PENDING')),
    }
  },
  async order(id, quantity = 1) {
    await delay()
    const p = getProduct(id)
    if (quantity > p.stockLevel) throw Object.assign(new Error(`Only ${p.stockLevel} in stock`), { status: 409 })
    p.stockLevel -= quantity
    p.demandVelocity += quantity
    if (p.stockLevel === 0) p.status = 'OUT_OF_STOCK'
    fireAgenticLoop(id)
    return structuredClone(p)
  },
  async setStock(id, stockLevel) {
    await delay()
    const p = getProduct(id)
    p.stockLevel = stockLevel
    if (stockLevel === 0) p.status = 'OUT_OF_STOCK'
    else if (p.status === 'OUT_OF_STOCK') p.status = 'ACTIVE'
    fireAgenticLoop(id)
    return structuredClone(p)
  },
  async resolve(type, id, action) {
    await delay()
    const list = type === 'pricing' ? db.pricing : db.reorder
    const s = list.find((x) => x.id === id)
    if (!s) throw Object.assign(new Error('Suggestion not found'), { status: 404 })
    if (s.status !== 'PENDING') throw Object.assign(new Error('Already resolved'), { status: 409 })
    const p = getProduct(s.productId)
    s.status = action === 'ACCEPT' ? 'ACCEPTED' : 'REJECTED'
    s.resolvedAt = new Date().toISOString()
    if (action === 'ACCEPT') {
      if (type === 'pricing') p.currentPrice = s.recommendedPrice
      else {
        p.stockLevel += s.recommendedQuantity
        if (p.status === 'OUT_OF_STOCK') p.status = 'ACTIVE'
      }
    }
    const stillPricing = db.pricing.some((x) => x.productId === p.id && x.status === 'PENDING')
    if (p.status === 'PRICE_REVIEW_PENDING' && !stillPricing) p.status = 'ACTIVE'
    return structuredClone(s)
  },
  async suggest(id, type) {
    await delay(500)
    const p = getProduct(id)
    // bypass dedupe-by-trigger for MANUAL by clearing old pending manual ones
    const list = type === 'pricing' ? db.pricing : db.reorder
    list.forEach((s) => s.productId === id && s.triggerReason === 'MANUAL' && s.status === 'PENDING' && (s.status = 'REJECTED'))
    const made = makeSuggestions(p, 'MANUAL').filter((s) => s.type === type)
    return made[0] ?? null
  },
}


export const resetDemo = () => {
  db.products = seed()
  db.pricing = []
  db.reorder = []
}
