import { useState } from 'react'
import {
  HOT_MULT, money, pct, signedPct, timeAgo, prettyCategory, TRIGGERS, STATUS_LABEL,
} from './utils.js'

/* ---------- small atoms ---------- */

export function TriggerBadge({ trigger }) {
  const t = TRIGGERS[trigger] || { label: trigger, tone: 'neutral', auto: false }
  return (
    <span className={`badge badge-${t.tone}`} title={t.auto ? 'Queued automatically by the agentic loop' : 'Requested by a person'}>
      {t.auto && <span className="badge-auto">Auto</span>}
      {t.label}
    </span>
  )
}

function Confidence({ value }) {
  return (
    <span className="conf" title={`Model confidence ${pct(value)}`}>
      <span className="conf-bar"><span style={{ width: pct(value) }} /></span>
      <span className="conf-num">{pct(value)}</span>
    </span>
  )
}

function Reasoning({ text }) {
  const [open, setOpen] = useState(false)
  const long = (text || '').length > 150
  return (
    <div className="reasoning">
      <div className="reasoning-label">Advisor reasoning</div>
      <p className={long && !open ? 'clamp' : ''}>{text || 'No reasoning provided.'}</p>
      {long && (
        <button className="link" onClick={() => setOpen(!open)}>
          {open ? 'Show less' : 'Read more'}
        </button>
      )}
    </div>
  )
}

function Spinner() {
  return <span className="spinner" aria-hidden />
}

/* ---------- masthead + KPIs ---------- */

export function Masthead({ mode, lastSync, watching, onRefresh, onReset, refreshing }) {
  return (
    <header className="masthead">
      <div>
        <div className="kicker">StockPulse · Merchandising Console</div>
        <h1 className="title"><em>Inventory</em> signals,<br />reasoned &amp; queued.</h1>
      </div>
      <div className="masthead-side">
        <div className="pills">
          <span className={`mode mode-${mode || 'wait'}`}>
            <span className="dot" />
            {mode === 'live' ? 'Live backend' : mode === 'demo' ? 'Demo mode' : mode === 'offline' ? 'Disconnected' : 'Connecting'}
          </span>
          {watching && (
            <span className="mode mode-agent">
              <span className="dot" />
              Agent analysing {watching}
            </span>
          )}
        </div>
        <div className="side-meta">
          {lastSync ? `Synced ${timeAgo(new Date(lastSync).toISOString())}` : 'Not synced yet'}
        </div>
        <div className="side-actions">
          <button className="btn ghost sm" onClick={onRefresh} disabled={refreshing}>
            {refreshing ? <Spinner /> : '↻'} Refresh
          </button>
          {mode === 'demo' && (
            <button className="btn ghost sm" onClick={onReset}>Reset demo</button>
          )}
        </div>
      </div>
    </header>
  )
}

export function Kpis({ pending, low, hot, out, hotMultiplier = HOT_MULT }) {
  const items = [
    { n: pending, label: 'Awaiting approval', tone: pending ? 'teal' : '' },
    { n: low, label: 'Below reorder threshold', tone: low ? 'accent' : '' },
    { n: hot, label: `Demand hot (≥${hotMultiplier}× avg)`, tone: hot ? 'amber' : '' },
    { n: out, label: 'Out of stock', tone: out ? 'danger' : '' },
  ]
  return (
    <section className="kpis">
      {items.map((k) => (
        <div className="kpi" key={k.label}>
          <div className={`kpi-n ${k.tone}`}>{k.n}</div>
          <div className="kpi-l">{k.label}</div>
        </div>
      ))}
    </section>
  )
}

/* ---------- suggestion inbox ---------- */

function PricingCard({ s, fresh, busy, onResolve }) {
  const dir = s.direction || 'HOLD'
  return (
    <article className={`sg ${fresh ? 'fresh' : ''}`}>
      <div className="sg-head">
        <span className="sg-kind">Pricing</span>
        <TriggerBadge trigger={s.triggerReason} />
        <span className="spacer" />
        <Confidence value={s.confidence} />
      </div>
      <div className="price-move">
        <span className="old">{money(s.currentPrice)}</span>
        <span className="arrow">→</span>
        <span className="new">{money(s.recommendedPrice)}</span>
        <span className={`delta delta-${dir}`}>
          {dir === 'HOLD' ? 'Hold' : signedPct(s.currentPrice, s.recommendedPrice)}
        </span>
      </div>
      <div className="source-note">{s.source === 'AI' ? 'AI recommendation' : s.source === 'RULE_BASED_FALLBACK' ? 'Rules fallback' : 'Rule-based recommendation'}{s.generationNote && ` ? ${s.generationNote}`}</div>
      <Reasoning text={s.reasoning} />
      <div className="sg-actions">
        <span className="when">{timeAgo(s.createdAt)}</span>
        <button className="btn ghost" disabled={busy} onClick={() => onResolve(s, 'REJECT')}>Reject</button>
        <button className="btn solid" disabled={busy} onClick={() => onResolve(s, 'ACCEPT')}>
          {busy ? <Spinner /> : 'Accept price'}
        </button>
      </div>
    </article>
  )
}

function ReorderCard({ s, fresh, busy, onResolve }) {
  const stock = s.currentStock
  return (
    <article className={`sg sg-reorder ${fresh ? 'fresh' : ''}`}>
      <div className="sg-head">
        <span className="sg-kind teal">Reorder</span>
        <TriggerBadge trigger={s.triggerReason} />
        <span className="spacer" />
        <Confidence value={s.confidence} />
      </div>
      <div className="reorder-move">
        <span className="qty">+{s.recommendedQuantity}<small> units</small></span>
        <span className="reorder-meta">
          {stock != null && <>Stock {stock} → {stock + s.recommendedQuantity}</>}
          {s.suggestedLeadTimeDays != null && <> · {s.suggestedLeadTimeDays}d lead time</>}
        </span>
      </div>
      <div className="source-note">{s.source === 'AI' ? 'AI recommendation' : s.source === 'RULE_BASED_FALLBACK' ? 'Rules fallback' : 'Rule-based recommendation'}{s.generationNote && ` ? ${s.generationNote}`}</div>
      <Reasoning text={s.reasoning} />
      <div className="sg-actions">
        <span className="when">{timeAgo(s.createdAt)}</span>
        <button className="btn ghost" disabled={busy} onClick={() => onResolve(s, 'REJECT')}>Reject</button>
        <button className="btn solid teal" disabled={busy} onClick={() => onResolve(s, 'ACCEPT')}>
          {busy ? <Spinner /> : 'Approve reorder'}
        </button>
      </div>
    </article>
  )
}

export function Inbox({ groups, fresh, busy, loading, onResolve, onHint, hintProduct }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Approval inbox</h2>
        <span className="count">{groups.reduce((a, g) => a + g.pricing.length + g.reorder.length, 0)} pending</span>
      </div>
      <p className="panel-sub">The agent proposes. Nothing changes until you accept.</p>

      {loading && (
        <div className="skeletons">
          <div className="skeleton" /><div className="skeleton" />
        </div>
      )}

      {!loading && groups.length === 0 && (
        <div className="empty">
          <div className="empty-mark">✓</div>
          <div className="empty-title">All clear</div>
          <p>No suggestions waiting. New recommendations will appear here when available.</p>
          {hintProduct && (
            <button className="btn solid" onClick={onHint}>
              Sell 1 × {hintProduct.name}
            </button>
          )}
        </div>
      )}

      {groups.map((g) => (
        <div className="group" key={g.product.id}>
          <div className="group-head">
            <div>
              <div className="group-name">{g.product.name}</div>
              <div className="group-sub">
                {g.product.id} · stock {g.product.stockLevel}/{g.product.reorderThreshold} threshold · {money(g.product.currentPrice)}
              </div>
            </div>
            <span className={`status status-${g.product.status}`}>{STATUS_LABEL[g.product.status] || g.product.status}</span>
          </div>
          {g.pricing.map((s) => (
            <PricingCard key={s.id} s={s} fresh={fresh.has(s.id)} busy={busy.has(`pricing-${s.id}`)} onResolve={onResolve} />
          ))}
          {g.reorder.map((s) => (
            <ReorderCard key={s.id} s={s} fresh={fresh.has(s.id)} busy={busy.has(`reorder-${s.id}`)} onResolve={onResolve} />
          ))}
        </div>
      ))}
    </section>
  )
}

/* ---------- catalog ---------- */

function StockBar({ stock, threshold }) {
  const max = Math.max(threshold * 3, stock, 1)
  const fill = Math.min(100, (stock / max) * 100)
  const mark = Math.min(100, (threshold / max) * 100)
  const tone = stock === 0 ? 'danger' : stock < threshold ? 'accent' : 'teal'
  return (
    <div className="stockbar" title={`${stock} in stock · reorder at ${threshold}`}>
      <div className={`stockbar-fill ${tone}`} style={{ width: `${fill}%` }} />
      <div className="stockbar-mark" style={{ left: `${mark}%` }} />
    </div>
  )
}

function ProductCard({ p, avg, busy, pendingCount, onSell, onSetStock, onReview, onHistory, onStream, hotMultiplier }) {
  const [stockInput, setStockInput] = useState('')
  const ratio = avg ? p.demandVelocity / avg : 0
  const hot = p.demandVelocity >= 3 && p.demandVelocity > avg * hotMultiplier
  const low = p.stockLevel > 0 && p.stockLevel < p.reorderThreshold
  const submit = (e) => {
    e.preventDefault()
    const n = Number(stockInput)
    if (stockInput.trim() === '' || !Number.isInteger(n) || n < 0 || n > 2147483647) return
    onSetStock(p, n)
    setStockInput('')
  }
  return (
    <article className={`prod ${low ? 'is-low' : ''} ${p.stockLevel === 0 ? 'is-out' : ''}`}>
      <div className="prod-top">
        <div>
          <div className="prod-name">{p.name}</div>
          <div className="prod-sub">{p.sku} · {prettyCategory(p.category)}</div>
        </div>
        <div className="prod-price">{money(p.currentPrice)}</div>
      </div>

      <div className="prod-tags">
        <span className={`status status-${p.status}`}>{STATUS_LABEL[p.status] || p.status}</span>
        {low && <span className="tag tag-accent">Low stock</span>}
        {hot && <span className="tag tag-amber">Hot · {ratio.toFixed(1)}×</span>}
        {pendingCount > 0 && <span className="tag tag-teal">{pendingCount} pending</span>}
      </div>

      <div className="prod-metrics">
        <div>
          <div className="m-label">Stock</div>
          <div className="m-val">{p.stockLevel}<small> / {p.reorderThreshold}</small></div>
        </div>
        <div>
          <div className="m-label">Velocity 24h</div>
          <div className="m-val">{p.demandVelocity}<small> · {ratio.toFixed(1)}× avg</small></div>
        </div>
      </div>
      <StockBar stock={p.stockLevel} threshold={p.reorderThreshold} />
      {p.costPrice != null && <p className="margin-line">Margin {pct((p.currentPrice - p.costPrice) / p.currentPrice)} ? cost {money(p.costPrice)}</p>}
      {(onHistory || onStream) && <div className="prod-actions">
        {onHistory && <button className="link" onClick={() => onHistory(p)}>Price &amp; stock history</button>}
        {onStream && <button className="link push" disabled={busy} onClick={() => onStream(p)}>Live reasoning</button>}
      </div>}

      <div className="prod-actions">
        <button className="btn ghost sm" disabled={busy || p.stockLevel < 1} onClick={() => onSell(p, 1)}>
          {busy ? <Spinner /> : 'Sell 1'}
        </button>
        <button className="btn ghost sm" disabled={busy || p.stockLevel < 5} onClick={() => onSell(p, 5)}>Sell 5</button>
        <form className="setstock" onSubmit={submit}>
          <input
            type="number" min="0" max="2147483647" step="1" inputMode="numeric" placeholder="Set stock"
            value={stockInput} onChange={(e) => setStockInput(e.target.value)}
            aria-label={`Set stock for ${p.name}`}
          />
          <button className="btn ghost sm" disabled={busy || stockInput === ''}>Set</button>
        </form>
        {onReview && (
          <button className="link push" disabled={busy} onClick={() => onReview(p)} title="Request a pricing and reorder review">
            Ask advisor
          </button>
        )}
      </div>
    </article>
  )
}

export function Catalog({
  products, avgs, busy, pendingByProduct, filters, setFilters, onSell, onSetStock, onReview, onHistory, onStream, hotMultiplier = HOT_MULT, loading,
}) {
  const cats = ['ALL', 'ELECTRONICS', 'APPAREL', 'HOME']
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Catalog</h2>
        <span className="count">{products.length} shown</span>
      </div>
      <div className="filters">
        <div className="chips">
          {cats.map((c) => (
            <button
              key={c}
              className={`chip ${filters.category === c ? 'on' : ''}`}
              onClick={() => setFilters({ ...filters, category: c })}
            >
              {c === 'ALL' ? 'All' : prettyCategory(c)}
            </button>
          ))}
          <button
            className={`chip ${filters.attention ? 'on teal' : ''}`}
            onClick={() => setFilters({ ...filters, attention: !filters.attention })}
          >
            Needs attention
          </button>
        </div>
        <input
          className="search" placeholder="Search name or SKU"
          value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })}
        />
      </div>

      {loading ? (
        <div className="grid"><div className="skeleton tall" /><div className="skeleton tall" /><div className="skeleton tall" /><div className="skeleton tall" /></div>
      ) : products.length === 0 ? (
        <div className="empty"><div className="empty-title">No products match</div><p>Try clearing the filters.</p></div>
      ) : (
        <div className="grid">
          {products.map((p) => (
            <ProductCard
              key={p.id} p={p} avg={avgs[p.id]} busy={busy.has(`prod-${p.id}`)}
              pendingCount={pendingByProduct[p.id] || 0}
              onSell={onSell} onSetStock={onSetStock} onReview={onReview} onHistory={onHistory} onStream={onStream} hotMultiplier={hotMultiplier}
            />
          ))}
        </div>
      )}
    </section>
  )
}

/* ---------- activity + toasts ---------- */

export function ActivityFeed({ items }) {
  return (
    <section className="panel activity">
      <div className="panel-head"><h2>Inventory activity</h2></div>
      <p className="panel-sub">Changes made during this session.</p>
      {items.length === 0 ? (
        <div className="muted">Nothing yet. Sales, stock changes, and approvals will appear here.</div>
      ) : (
        <ul>
          {items.map((it) => (
            <li key={it.id}>
              <span className={`ev ev-${it.kind}`} />
              <span className="ev-text">{it.text}</span>
              <span className="ev-time">{timeAgo(it.at)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export function Toasts({ items, onClose }) {
  return (
    <div className="toasts" role="status" aria-live="polite">
      {items.map((t) => (
        <div key={t.id} className={`toast toast-${t.tone}`} onClick={() => onClose(t.id)}>
          {t.text}
        </div>
      ))}
    </div>
  )
}
