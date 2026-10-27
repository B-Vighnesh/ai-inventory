import { useEffect, useState } from 'react'
import { api } from './api.js'
import { money, timeAgo } from './utils.js'

export function StrategyPanel({ settings, onSave, busy }) {
  const [multiplier, setMultiplier] = useState(settings.demandSpikeMultiplier)
  useEffect(() => setMultiplier(settings.demandSpikeMultiplier), [settings.demandSpikeMultiplier])
  return <section className="panel strategy-panel" aria-label="Commerce strategy">
    <div><h2>Commerce advisor</h2><p className="muted">Changes apply to new reviews. Prices and stock change only after approval.</p></div>
    <label>Strategy <select value={settings.strategy} disabled={busy} onChange={e => onSave({ strategy: e.target.value, demandSpikeMultiplier: Number(multiplier) })}>
      <option value="RULE_BASED">Rule-based</option><option value="AI">Groq AI with rules fallback</option>
    </select></label>
    <form onSubmit={e => { e.preventDefault(); onSave({ strategy: settings.strategy, demandSpikeMultiplier: Number(multiplier) }) }}>
      <label>Demand spike multiplier <input aria-label="Demand spike multiplier" type="number" min="1.1" max="20" step="0.1" value={multiplier} onChange={e => setMultiplier(e.target.value)} required /></label>
      <button className="btn ghost sm" disabled={busy}>Apply</button>
    </form>
    {settings.strategy === 'AI' && !settings.groqConfigured && <p className="muted">No AI key configured. Reviews use the rule-based fallback.</p>}
  </section>
}

export function JobsPanel({ jobs, products }) {
  if (!jobs.length) return null
  return <section className="panel activity"><div className="panel-head"><h2>Recommendation jobs</h2></div>
    <ul>{jobs.slice(0, 8).map(job => <li key={job.id}>
      <span className={`ev ev-${job.status === 'FAILED' ? 'reject' : 'agent'}`} />
      <div className="ev-text"><strong>{products.find(p => p.id === job.productId)?.name || job.productId}</strong>
        <div>{job.triggerReason.replaceAll('_', ' ')} ? {job.status.toLowerCase()}</div>
        <small>{job.message}</small>
      </div><span className="ev-time">{timeAgo(job.updatedAt)}</span>
    </li>)}</ul>
  </section>
}

export function ReasoningStream({ stream, onClose }) {
  let content = stream.text
  try { const parsed = JSON.parse(stream.text); content = parsed.pricingReasoning || stream.text } catch {}
  return <section className="panel stream-panel" aria-live="polite">
    <div className="panel-head"><h2>Live review ? {stream.name}</h2><button className="link" onClick={onClose}>Close</button></div>
    <p>{stream.status}{stream.source ? ` ? ${stream.source.replaceAll('_', ' ')}` : ''}</p>
    <pre>{content || 'Waiting for recommendation output. Rule-based reviews complete without a token stream.'}</pre>
    <p className="muted">Streamed output is provisional until validated. Approve the saved suggestion in the inbox.</p>
  </section>
}

export function HistoryPanel({ product, onClose }) {
  const [history, setHistory] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true; setHistory(null); setError('')
    api.history(product.id).then(data => active && setHistory(data)).catch(e => active && setError(e.message))
    return () => { active = false }
  }, [product.id])
  const changes = history?.prices.slice().reverse() || []
  const values = changes.length ? [changes[0].previousPrice, ...changes.map(p => p.newPrice)] : [product.currentPrice]
  const min = Math.min(...values), max = Math.max(...values)
  const points = values.map((value, index) => `${20 + index / Math.max(1, values.length - 1) * 560},${100 - (value - min) / Math.max(1, max - min) * 75}`).join(' ')
  return <section className="panel history-panel" aria-label="Product history">
    <div className="panel-head"><h2>{product.name} ? History</h2><button className="link" onClick={onClose}>Close</button></div>
    {error && <p role="alert">{error}</p>}
    {!history && !error && <p>Loading history?</p>}
    {history && <>
      <svg viewBox="0 0 600 130" role="img" aria-label={`Price history from ${money(values[0])} to ${money(values.at(-1))}`}>
        <polyline points={points} fill="none" stroke="var(--teal)" strokeWidth="3" />
        {values.map((value, index) => <circle key={index} cx={20 + index / Math.max(1, values.length - 1) * 560} cy={100 - (value - min) / Math.max(1, max - min) * 75} r="4" fill="var(--teal)"><title>{money(value)}</title></circle>)}
      </svg>
      <p>{changes.length ? `${changes.length} approved price changes` : 'No approved price changes yet'} ? current price {money(product.currentPrice)}</p>
      <div className="history-scroll"><table><thead><tr><th>Inventory event</th><th>Stock</th><th>24h demand</th><th>When</th></tr></thead>
        <tbody>{history.inventory.map(row => <tr key={row.id}><td>{row.reason.replaceAll('_', ' ')}</td><td>{row.stockLevel}</td><td>{row.demandVelocity}</td><td>{new Date(row.capturedAt).toLocaleString()}</td></tr>)}</tbody>
      </table></div>
    </>}
  </section>
}

export function CreateProduct({ onCreate, busy }) {
  const [open, setOpen] = useState(false)
  const submit = async event => {
    event.preventDefault()
    const form = event.currentTarget
    const values = Object.fromEntries(new FormData(form))
    const product = { ...values, currentPrice: Number(values.currentPrice), stockLevel: Number(values.stockLevel), reorderThreshold: Number(values.reorderThreshold), costPrice: values.costPrice === '' ? null : Number(values.costPrice) }
    if (await onCreate(product)) { form.reset(); setOpen(false) }
  }
  return <section className="create-product">
    <button className="btn ghost sm" onClick={() => setOpen(!open)}>{open ? 'Cancel new product' : '+ Add product'}</button>
    {open && <form className="panel create-form" onSubmit={submit}>
      <label>Product ID<input name="id" placeholder="PRD-009" required maxLength="100" /></label>
      <label>SKU<input name="sku" required maxLength="100" /></label>
      <label>Name<input name="name" required maxLength="255" /></label>
      <label>Category<select name="category"><option>ELECTRONICS</option><option>APPAREL</option><option>HOME</option></select></label>
      <label>Price<input name="currentPrice" type="number" min="0.01" step="0.01" required /></label>
      <label>Cost (optional)<input name="costPrice" type="number" min="0" step="0.01" /></label>
      <label>Stock<input name="stockLevel" type="number" min="0" max="2147483647" step="1" required /></label>
      <label>Reorder threshold<input name="reorderThreshold" type="number" min="0" max="2147483647" step="1" required /></label>
      <button className="btn solid" disabled={busy}>Create product</button>
    </form>}
  </section>
}
