import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, resetDemo } from './api.js'
import { HOT_MULT, categoryAverages, money, TRIGGERS } from './utils.js'
import { Masthead, Kpis, Inbox, Catalog, ActivityFeed, Toasts } from './components.jsx'
import { StrategyPanel, JobsPanel, HistoryPanel, ReasoningStream, CreateProduct } from './commerce-components.jsx'

const POLL_MS = 4000

export default function App() {
  const [mode, setMode] = useState(null)
  const [settings, setSettings] = useState(null)
  const [jobs, setJobs] = useState([])
  const [historyProduct, setHistoryProduct] = useState(null)
  const [stream, setStream] = useState(null)
  const [products, setProducts] = useState([])
  const [suggestions, setSuggestions] = useState({ pricing: [], reorder: [] })
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState(null)
  const [lastSync, setLastSync] = useState(null)
  const [busy, setBusy] = useState(new Set())
  const [fresh, setFresh] = useState(new Set())
  const [log, setLog] = useState([])
  const [toasts, setToasts] = useState([])
  const [watching, setWatching] = useState(null)
  const [filters, setFilters] = useState({ category: 'ALL', attention: false, q: '' })

  const refreshVersion = useRef(0)
  const refreshInFlight = useRef(false)
  const pendingActions = useRef(new Set())
  const seen = useRef(new Set())
  const initialised = useRef(false)
  const watchTimer = useRef(null)
  const idSeq = useRef(1)

  const toast = useCallback((text, tone = 'ok') => {
    const id = idSeq.current++
    setToasts((t) => [...t.slice(-3), { id, text, tone }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200)
  }, [])

  const addLog = useCallback((kind, text) => {
    setLog((l) => [{ id: idSeq.current++, kind, text, at: new Date().toISOString() }, ...l].slice(0, 10))
  }, [])

  const watch = (name) => {
    setWatching(name)
    clearTimeout(watchTimer.current)
    watchTimer.current = setTimeout(() => setWatching(null), 5000)
  }

  const refresh = useCallback(async () => {
    const version = ++refreshVersion.current
    refreshInFlight.current = true
    setRefreshing(true)
    try {
      const [prods, sugg, config, work] = await Promise.all([api.listProducts(), api.listSuggestions(),
        api.mode === 'live' ? api.getSettings() : null, api.mode === 'live' ? api.listJobs() : []])
      if (version !== refreshVersion.current) return false
      const all = [...sugg.pricing, ...sugg.reorder]

      // Detect suggestions the agent queued since the last poll.
      if (initialised.current) {
        const newOnes = all.filter((s) => !seen.current.has(s.id))
        if (newOnes.length) {
          const autos = newOnes.filter((s) => TRIGGERS[s.triggerReason]?.auto)
          setFresh((f) => new Set([...f, ...newOnes.map((s) => s.id)]))
          setTimeout(() => setFresh((f) => {
            const n = new Set(f)
            newOnes.forEach((s) => n.delete(s.id))
            return n
          }), 7000)
          if (autos.length) {
            const byProd = {}
            autos.forEach((s) => {
              const name = prods.find((p) => p.id === s.productId)?.name || s.productName || s.productId
              ;(byProd[name] ||= []).push(s)
            })
            Object.entries(byProd).forEach(([name, list]) => {
              const reason = TRIGGERS[list[0].triggerReason]?.label.toLowerCase()
              addLog('agent', `Agent queued ${list.length} suggestion${list.length > 1 ? 's' : ''} for ${name} (${reason})`)
              toast(`Agent queued ${list.length} suggestion${list.length > 1 ? 's' : ''} for ${name}`, 'agent')
            })
            setWatching(null)
          }
        }
      }
      all.forEach((s) => seen.current.add(s.id))
      initialised.current = true

      setSettings(config)
      setJobs(work)
      setProducts(prods)
      setSuggestions(sugg)
      setLastSync(Date.now())
      setError(null)
      setMode(api.mode)
      return true
    } catch (e) {
      if (version !== refreshVersion.current) return false
      setMode(api.mode === 'demo' ? 'demo' : 'offline')
      setError(e.message || 'Could not reach the backend')
      return false
    } finally {
      if (version === refreshVersion.current) {
        refreshInFlight.current = false
        setLoading(false)
        setRefreshing(false)
      }
    }
  }, [addLog, toast])

  useEffect(() => {
    refresh()
    return () => {
      refreshVersion.current++
      clearTimeout(watchTimer.current)
    }
  }, [refresh])

  // Keep retrying a disconnected backend without overlapping slow polls.
  useEffect(() => {
    const timer = setInterval(() => {
      if (!refreshInFlight.current) refresh()
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [refresh])

  const run = useCallback(async (key, fn) => {
    if (pendingActions.current.has(key)) return
    pendingActions.current.add(key)
    setBusy((b) => new Set(b).add(key))
    try {
      const out = await fn()
      await refresh()
      return out
    } catch (e) {
      toast(e.message || 'Request failed', 'err')
      await refresh()
    } finally {
      pendingActions.current.delete(key)
      setBusy((b) => {
        const n = new Set(b)
        n.delete(key)
        return n
      })
    }
  }, [refresh, toast])

  const onSell = (p, qty) =>
    run(`prod-${p.id}`, async () => {
      const updated = await api.order(p.id, qty)
      addLog('sale', `Sold ${qty} × ${p.name} (stock ${p.stockLevel} → ${updated.stockLevel})`)
      if (api.mode === 'demo') watch(p.name)
    })

  const onSetStock = (p, n) =>
    run(`prod-${p.id}`, async () => {
      const updated = await api.setStock(p.id, n)
      addLog('stock', `Stock for ${p.name} set to ${updated.stockLevel}`)
      if (api.mode === 'demo') watch(p.name)
    })

  const onReview = (p) =>
    run(`prod-${p.id}`, async () => {
      if (api.mode === 'live') await api.review(p.id)
      else { await api.suggest(p.id, 'pricing'); await api.suggest(p.id, 'reorder') }
      addLog('manual', `Manual review requested for ${p.name}`)
      toast(`Review queued for ${p.name}`, 'ok')
    })

  const onStream = (p) => run(`prod-${p.id}`, async () => {
    setStream({ name: p.name, text: '', status: 'Connecting', source: null })
    try {
      await api.streamPrice(p.id, (event, value) => {
        if (event === 'token') setStream(old => ({ ...old, text: (old.text + value.text).slice(-16000), status: 'Receiving recommendation' }))
        if (event === 'queued') setStream(old => ({ ...old, status: 'Review queued' }))
        if (event === 'result') setStream(old => ({ ...old, status: value.message, source: value.source, done: true }))
      })
    } catch (error) {
      setStream(old => ({ ...old, status: error.message, done: true }))
      throw error
    }
  })

  const onResolve = (s, action) =>
    run(`${s.type}-${s.id}`, async () => {
      await api.resolve(s.type, s.id, action)
      const name = products.find((p) => p.id === s.productId)?.name || s.productName || s.productId
      if (action === 'ACCEPT') {
        const msg = s.type === 'pricing'
          ? `Price for ${name} set to ${money(s.recommendedPrice)}`
          : `Reorder of ${s.recommendedQuantity} units approved for ${name}`
        addLog('accept', msg)
        toast(msg, 'ok')
      } else {
        addLog('reject', `Rejected ${s.type} suggestion for ${name}`)
        toast(`Rejected ${s.type} suggestion`, 'ok')
      }
    })

  const onReset = () => {
    resetDemo()
    seen.current = new Set()
    initialised.current = false
    setLog([])
    setFresh(new Set())
    setLoading(true)
    refresh()
    toast('Demo data reset', 'ok')
  }

  const actionBusy = useMemo(() => {
    if (mode === 'live' || mode === 'demo') return busy
    return new Set([
      ...busy,
      ...products.map((p) => `prod-${p.id}`),
      ...suggestions.pricing.map((s) => `pricing-${s.id}`),
      ...suggestions.reorder.map((s) => `reorder-${s.id}`),
    ])
  }, [mode, busy, products, suggestions])

  /* ---------- derived ---------- */
  const avgs = useMemo(() => categoryAverages(products), [products])
  const hotMultiplier = settings?.demandSpikeMultiplier || HOT_MULT

  const groups = useMemo(() => {
    const byId = Object.fromEntries(products.map((p) => [p.id, p]))
    const map = {}
    suggestions.pricing.forEach((s) => byId[s.productId] && ((map[s.productId] ||= { product: byId[s.productId], pricing: [], reorder: [] }).pricing.push(s)))
    suggestions.reorder.forEach((s) => byId[s.productId] && ((map[s.productId] ||= { product: byId[s.productId], pricing: [], reorder: [] }).reorder.push(s)))
    const newest = (g) => Math.max(...[...g.pricing, ...g.reorder].map((s) => new Date(s.createdAt || 0).getTime()))
    return Object.values(map).sort((a, b) => newest(b) - newest(a))
  }, [products, suggestions])

  const pendingByProduct = useMemo(() => {
    const m = {}
    ;[...suggestions.pricing, ...suggestions.reorder].forEach((s) => { m[s.productId] = (m[s.productId] || 0) + 1 })
    return m
  }, [suggestions])

  const kpis = useMemo(() => ({
    pending: suggestions.pricing.length + suggestions.reorder.length,
    low: products.filter((p) => p.stockLevel > 0 && p.stockLevel < p.reorderThreshold).length,
    hot: products.filter((p) => p.demandVelocity >= 3 && p.demandVelocity > avgs[p.id] * hotMultiplier).length,
    out: products.filter((p) => p.stockLevel === 0).length,
  }), [products, suggestions, avgs, hotMultiplier])

  const visible = useMemo(() => {
    const q = filters.q.trim().toLowerCase()
    return products.filter((p) => {
      if (filters.category !== 'ALL' && p.category !== filters.category) return false
      if (filters.attention && !(p.status !== 'ACTIVE' || p.stockLevel < p.reorderThreshold || pendingByProduct[p.id])) return false
      if (q && !(`${p.name} ${p.sku}`.toLowerCase().includes(q))) return false
      return true
    })
  }, [products, filters, pendingByProduct])

  const hintProduct = products.find((p) => p.id === 'PRD-003') || products.find((p) => p.stockLevel > 0 && p.stockLevel < p.reorderThreshold)

  return (
    <div className="page">
      <Masthead mode={mode} lastSync={lastSync} watching={mode === 'demo' ? watching : jobs.filter(j => ['QUEUED', 'RUNNING'].includes(j.status)).map(j => products.find(p => p.id === j.productId)?.name || j.productId).slice(0, 2).join(', ')} refreshing={refreshing} onRefresh={refresh} onReset={onReset} />

      {error && (
        <div className="banner" role="alert">
          <strong>Connection or sync failed.</strong> {error} {lastSync && 'Showing the last successful sync.'}
          <button className="link" onClick={refresh}>Retry</button>
        </div>
      )}

      {mode === 'demo' && (
        <div className="banner info">
          <strong>Demo mode.</strong> You are using simulated inventory. Changes are not saved to the backend. Turn off demo mode and restart the frontend to use live inventory.
        </div>
      )}

      {mode === 'live' && settings && (
        <StrategyPanel settings={settings} busy={busy.has('settings')} onSave={(next) => run('settings', () => api.updateSettings(next))} />
      )}

      <Kpis {...kpis} hotMultiplier={hotMultiplier} />
      {mode === 'live' && <CreateProduct onCreate={(product) => run('create', async () => {
        const result = await api.createProduct(product); toast(`Created ${result.name}`); return result
      })} busy={busy.has('create')} />}
      {stream && <ReasoningStream stream={stream} onClose={() => setStream(null)} />}
      {historyProduct && <HistoryPanel product={historyProduct} onClose={() => setHistoryProduct(null)} />}


      <main className="layout">
        <div className="col-left">
          <Inbox
            groups={groups} fresh={fresh} busy={actionBusy} loading={loading}
            onResolve={onResolve} hintProduct={hintProduct}
            onHint={() => hintProduct && onSell(hintProduct, 1)}
          />
          {mode === 'live' && <JobsPanel jobs={jobs} products={products} />}
          <ActivityFeed items={log} />
        </div>
        <div className="col-right">
          <Catalog
            products={visible} avgs={avgs} busy={actionBusy} loading={loading}
            pendingByProduct={pendingByProduct} filters={filters} setFilters={setFilters}
            onSell={onSell} onSetStock={onSetStock} onReview={onReview} onHistory={mode === 'live' ? setHistoryProduct : undefined}
            onStream={mode === 'live' ? onStream : undefined} hotMultiplier={hotMultiplier}
          />
        </div>
      </main>

      <footer className="foot">StockPulse · human approves, agent proposes</footer>
      <Toasts items={toasts} onClose={(id) => setToasts((t) => t.filter((x) => x.id !== id))} />
    </div>
  )
}
