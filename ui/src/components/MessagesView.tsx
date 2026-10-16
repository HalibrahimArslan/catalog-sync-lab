import { useState } from 'react'
import { backoffice, storefront } from '../api/http-driver'
import type { DeliveryOutcome, OutboxState } from '../api/types'
import { usePolling } from '../hooks'
import { loadMessages } from '../tracking/load'
import {
  parseSearch, STATE_LABEL, STATE_TONE, timeline, type TrackedMessage, type TrackedState,
} from '../tracking/tracking'

/**
 * Mesaj takibi (Celery'deki Flower'ın görevler sayfası gibi): her mesajın şu an nerede olduğu, kaç kez
 * denendiği, hata varsa sebebi; tıklayınca Backoffice'ten vitrine yolculuğu.
 */
export function MessagesView() {
  const [search, setSearch] = useState('')
  const [state, setState] = useState<TrackedState | 'all'>('all')
  const [auto, setAuto] = useState(true)
  const [selected, setSelected] = useState<string>()
  const query = { ...parseSearch(search), state }
  const list = usePolling(() => loadMessages(query), auto ? 3_000 : 3_600_000)
  const messages = list.data?.messages ?? []
  const current = messages.find((m) => m.key === selected)

  return (
    <section>
      <Stats onPick={(s) => { setState(s); setSelected(undefined) }} active={state} />

      <div className="toolbar">
        <input className="search" placeholder="SKU ya da message-id ara" value={search}
          onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && void list.refresh()} />
        <select value={state} onChange={(e) => setState(e.target.value as TrackedState | 'all')} aria-label="Durum">
          <option value="all">Tüm durumlar</option>
          {(Object.keys(STATE_LABEL) as TrackedState[]).map((s) => <option key={s} value={s}>{STATE_LABEL[s]}</option>)}
        </select>
        <label className="check"><input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} /> 3 sn'de bir yenile</label>
        <button onClick={() => void list.refresh()}>Yenile</button>
      </div>
      {list.error && <p className="bad">{list.error}</p>}
      {list.data?.warnings.map((w) => <p key={w} className="notice">Eksik olabilir: {w}</p>)}

      <div className="split">
        <div className="scroll-x">
          <table className="messages">
            <thead><tr><th>Zaman</th><th>Durum</th><th>Tip</th><th>SKU</th><th className="num">Deneme</th><th>Hata</th></tr></thead>
            <tbody>
              {list.data && messages.length === 0 && <tr><td colSpan={6} className="muted">Bu filtreyle mesaj yok.</td></tr>}
              {messages.map((m) => (
                <tr key={m.key} className={`clickable ${m.key === selected ? 'selected' : ''}`} onClick={() => setSelected(m.key)}>
                  <td className="nowrap">{formatTime(m.updatedAt)}</td>
                  <td><StateBadge state={m.state} /></td>
                  <td>{m.type ?? '—'}</td>
                  <td><code>{m.sku ?? '—'}</code></td>
                  <td className="num">{m.attempts}</td>
                  <td className="error ellipsis" title={m.lastError ?? ''}>{m.lastError ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {current ? <Detail message={current} onChanged={() => void list.refresh()} />
          : <div className="card muted detail-empty">Ayrıntısını görmek için bir mesaja tıkla.</div>}
      </div>
    </section>
  )
}

function Stats({ active, onPick }: { active: TrackedState | 'all'; onPick: (state: TrackedState | 'all') => void }) {
  const outbox = usePolling(backoffice.outboxStats, 3_000)
  const deliveries = usePolling(storefront.messageStats, 3_000)
  const tile = (state: TrackedState, count: number | undefined) => (
    <button key={state} className={`stat tone-${STATE_TONE[state]} ${active === state ? 'active' : ''}`}
      onClick={() => onPick(active === state ? 'all' : state)}>
      <span className="value">{count ?? '–'}</span>
      <span className="label">{STATE_LABEL[state]}</span>
    </button>
  )
  const o = (s: OutboxState) => outbox.data?.[s]
  const d = (s: DeliveryOutcome) => deliveries.data?.[s]
  return (
    <div className="stats">
      <div className="stat-group">
        <h3>Backoffice outbox {outbox.error && <span className="bad">· cevap yok</span>}</h3>
        <div className="stat-row">{tile('outbox-pending', o('PENDING'))}{tile('outbox-failing', o('FAILING'))}</div>
      </div>
      <div className="stat-group">
        <h3>Storefront {deliveries.error && <span className="bad">· cevap yok</span>}</h3>
        <div className="stat-row">
          {tile('processed', d('PROCESSED'))}{tile('retrying', d('RETRY'))}{tile('parked', d('PARKED'))}
          {tile('replayed', d('REPLAYED'))}{tile('duplicate', d('DUPLICATE'))}
        </div>
      </div>
    </div>
  )
}

function Detail({ message, onChanged }: { message: TrackedMessage; onChanged: () => void }) {
  const id = message.messageId
  const history = usePolling(async () => (id ? await storefront.message(id) : null), 3_000)
  const outbox = usePolling(async () => (id ? (await backoffice.outbox({ messageId: id, limit: 1 }))[0] ?? null : null), 3_000)
  const [notice, setNotice] = useState<string>()
  const entry = outbox.data ?? message.outbox
  const attempts = history.data?.attempts ?? []
  const events = timeline(entry ?? undefined, attempts)
  const canReplay = message.state === 'parked' && id && message.queue

  async function replay() {
    try {
      setNotice((await storefront.replay(message.queue!, id!))
        ? 'Ana kuyruğa geri gönderildi; deneme sayacı sıfırdan başlıyor.'
        : 'Mesaj artık park kuyruğunda değil.')
    } catch (e) {
      setNotice(e instanceof Error ? e.message : String(e))
    }
    onChanged()
  }

  return (
    <article className="card detail">
      <header>
        <div>
          <h3><StateBadge state={message.state} /> {message.type ?? 'Bilinmeyen tip'}</h3>
          <p className="meta"><span>message-id <code>{id ?? 'yok (kimliksiz mesaj)'}</code></span></p>
        </div>
        {canReplay && <button className="primary" onClick={() => void replay()}>Yeniden oynat</button>}
      </header>
      {notice && <p className="notice">{notice}</p>}

      <dl className="facts">
        <dt>SKU</dt><dd><code>{message.sku ?? '—'}</code></dd>
        <dt>Kuyruk</dt><dd>{message.queue ? <code>{message.queue}</code> : '—'}</dd>
        <dt>Sözleşme</dt><dd>{entry ? `v${entry.schemaVersion}` : message.delivery?.schemaVersion ? `v${message.delivery.schemaVersion}` : '—'}</dd>
        <dt>Deneme</dt><dd>{message.attempts}</dd>
      </dl>

      {message.lastError && (
        <>
          <h4>Son hata</h4>
          <pre className="error-box">{message.lastError}</pre>
        </>
      )}

      <h4>Yolculuk</h4>
      <ol className="timeline">
        {events.map((event, i) => (
          <li key={i} className={`tone-${event.tone}`}>
            <span className="dot" />
            <div>
              <div className="row-line"><strong>{event.title}</strong><time>{event.at ? formatTime(event.at, true) : ''}</time></div>
              {event.detail && <div className="muted small">{event.detail}</div>}
              {event.error && <div className="error small">{event.error}</div>}
            </div>
          </li>
        ))}
        {events.length === 0 && <li className="muted">Kayıt yok.</li>}
      </ol>

      <h4>Gövde</h4>
      {entry ? <pre className="payload">{pretty(entry.payload)}</pre>
        : <p className="muted small">Backoffice outbox'ında kaydı yok (saklama süresi dolup silinmiş ya da mesaj Backoffice dışından gelmiş).</p>}
    </article>
  )
}

function StateBadge({ state }: { state: TrackedState }) {
  return <span className={`state tone-${STATE_TONE[state]}`}>{STATE_LABEL[state]}</span>
}

function formatTime(iso: string, withSeconds = false) {
  const date = new Date(iso)
  const today = new Date().toDateString() === date.toDateString()
  const time = date.toLocaleTimeString('tr-TR', withSeconds ? undefined : { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  return today ? time : `${date.toLocaleDateString('tr-TR')} ${time}`
}

function pretty(text: string) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}
