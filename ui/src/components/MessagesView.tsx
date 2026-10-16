import { useState } from 'react'
import { backoffice, storefront } from '../api/http-driver'
import type { DeliveryOutcome, OutboxState } from '../api/types'
import { usePolling } from '../hooks'
import { loadMessages } from '../tracking/load'
import {
  parseSearch, STATE_LABEL, STATE_TONE, timeline, type TrackedMessage, type TrackedState,
} from '../tracking/tracking'
import { cx, input, table, TONE_BAR } from './styles'
import { Badge, Button, Card, Eyebrow, Notice } from './ui'

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

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <input className={cx(input, 'w-full max-w-[340px]')} placeholder="SKU ya da message-id ara" value={search}
          onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && void list.refresh()} />
        <select className={input} value={state} onChange={(e) => setState(e.target.value as TrackedState | 'all')} aria-label="Durum">
          <option value="all">Tüm durumlar</option>
          {(Object.keys(STATE_LABEL) as TrackedState[]).map((s) => <option key={s} value={s}>{STATE_LABEL[s]}</option>)}
        </select>
        <label className="flex items-center gap-2 text-sm text-muted">
          <input type="checkbox" className="accent-accent" checked={auto} onChange={(e) => setAuto(e.target.checked)} /> 3 sn'de bir yenile
        </label>
        <Button onClick={() => void list.refresh()}>Yenile</Button>
      </div>
      <div className="mt-3 grid gap-2 empty:hidden">
        {list.error && <p className="text-bad">{list.error}</p>}
        {list.data?.warnings.map((w) => <Notice key={w}>Eksik olabilir: {w}</Notice>)}
      </div>

      <div className="mt-3.5 grid items-start gap-4 min-[980px]:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div className={table.wrap}>
          <table className={table.table}>
            <thead>
              <tr>
                <th className={table.th}>Zaman</th><th className={table.th}>Durum</th><th className={table.th}>Tip</th>
                <th className={table.th}>SKU</th><th className={cx(table.th, table.num)}>Deneme</th><th className={table.th}>Hata</th>
              </tr>
            </thead>
            <tbody>
              {list.data && messages.length === 0 && <tr><td colSpan={6} className={cx(table.td, 'text-muted')}>Bu filtreyle mesaj yok.</td></tr>}
              {messages.map((m) => (
                <tr key={m.key} onClick={() => setSelected(m.key)}
                  className={cx('cursor-pointer', m.key === selected ? 'bg-accent-soft' : 'hover:bg-surface-2')}>
                  <td className={cx(table.td, 'whitespace-nowrap')}>{formatTime(m.updatedAt)}</td>
                  <td className={table.td}><StateBadge state={m.state} /></td>
                  <td className={table.td}>{m.type ?? '—'}</td>
                  <td className={table.td}><code className="whitespace-nowrap">{m.sku ?? '—'}</code></td>
                  <td className={cx(table.td, table.num)}>{m.attempts}</td>
                  <td className={cx(table.td, 'max-w-[180px] truncate text-[0.84rem] text-bad')} title={m.lastError ?? ''}>{m.lastError ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {current ? <Detail message={current} onChanged={() => void list.refresh()} />
          : <Card className="px-4 py-10 text-center text-muted">Ayrıntısını görmek için bir mesaja tıkla.</Card>}
      </div>
    </section>
  )
}

function Stats({ active, onPick }: { active: TrackedState | 'all'; onPick: (state: TrackedState | 'all') => void }) {
  const outbox = usePolling(backoffice.outboxStats, 3_000)
  const deliveries = usePolling(storefront.messageStats, 3_000)
  const tile = (state: TrackedState, count: number | undefined) => (
    <button key={state} onClick={() => onPick(active === state ? 'all' : state)}
      className={cx('relative grid min-w-[124px] cursor-pointer justify-items-start gap-0.5 overflow-hidden rounded-xl border bg-surface',
        'px-3.5 py-3 text-left shadow-card transition hover:bg-surface-2',
        active === state ? 'border-accent ring-3 ring-accent/30' : 'border-line')}>
      <span className={cx('absolute inset-y-0 left-0 w-[3px]', TONE_BAR[STATE_TONE[state]])} />
      <span className="text-[1.6rem] leading-tight font-bold tracking-tight tabular-nums">{count ?? '–'}</span>
      <span className="text-[0.78rem] text-muted">{STATE_LABEL[state]}</span>
    </button>
  )
  const o = (s: OutboxState) => outbox.data?.[s]
  const d = (s: DeliveryOutcome) => deliveries.data?.[s]
  return (
    <div className="mt-6 flex flex-wrap gap-7">
      <div>
        <Eyebrow>Backoffice outbox {outbox.error && <span className="text-bad">· cevap yok</span>}</Eyebrow>
        <div className="flex flex-wrap gap-2.5">{tile('outbox-pending', o('PENDING'))}{tile('outbox-failing', o('FAILING'))}</div>
      </div>
      <div>
        <Eyebrow>Storefront {deliveries.error && <span className="text-bad">· cevap yok</span>}</Eyebrow>
        <div className="flex flex-wrap gap-2.5">
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
    <Card className="sticky top-[132px]">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="flex items-center gap-2 font-semibold"><StateBadge state={message.state} /> {message.type ?? 'Bilinmeyen tip'}</h3>
          <p className="mt-1.5 text-[0.84rem] text-muted">message-id <code>{id ?? 'yok (kimliksiz mesaj)'}</code></p>
        </div>
        {canReplay && <Button variant="primary" onClick={() => void replay()}>Yeniden oynat</Button>}
      </header>
      {notice && <div className="mt-3"><Notice>{notice}</Notice></div>}

      <dl className="mt-3 grid grid-cols-[max-content_1fr] gap-x-[18px] gap-y-1.5 [&_dt]:text-muted">
        <dt>SKU</dt><dd><code>{message.sku ?? '—'}</code></dd>
        <dt>Kuyruk</dt><dd>{message.queue ? <code>{message.queue}</code> : '—'}</dd>
        <dt>Sözleşme</dt><dd>{entry ? `v${entry.schemaVersion}` : message.delivery?.schemaVersion ? `v${message.delivery.schemaVersion}` : '—'}</dd>
        <dt>Deneme</dt><dd>{message.attempts}</dd>
      </dl>

      {message.lastError && (
        <>
          <Eyebrow className="mt-5">Son hata</Eyebrow>
          <pre className={cx(pre, 'bg-bad-soft text-bad')}>{message.lastError}</pre>
        </>
      )}

      <Eyebrow className="mt-5">Yolculuk</Eyebrow>
      <ol>
        {events.map((event, i) => (
          <li key={i} className="relative grid grid-cols-[14px_1fr] gap-2.5 py-[7px]">
            {i < events.length - 1 && <span className="absolute top-[22px] -bottom-1.5 left-[6px] w-0.5 bg-line" />}
            <span className={cx('mt-[5px] size-3 rounded-full ring-3 ring-surface', TONE_BAR[event.tone])} />
            <div>
              <div className="flex justify-between gap-2">
                <strong className="font-semibold">{event.title}</strong>
                <time className="text-[0.8rem] whitespace-nowrap text-muted tabular-nums">{event.at ? formatTime(event.at, true) : ''}</time>
              </div>
              {event.detail && <div className="text-sm text-muted">{event.detail}</div>}
              {event.error && <div className="text-sm text-bad">{event.error}</div>}
            </div>
          </li>
        ))}
        {events.length === 0 && <li className="text-muted">Kayıt yok.</li>}
      </ol>

      <Eyebrow className="mt-5">Gövde</Eyebrow>
      {entry ? <pre className={cx(pre, 'max-h-[280px] overflow-auto bg-code')}>{pretty(entry.payload)}</pre>
        : <p className="text-sm text-muted">Backoffice outbox'ında kaydı yok (saklama süresi dolup silinmiş ya da mesaj Backoffice dışından gelmiş).</p>}
    </Card>
  )
}

const pre = 'rounded-lg px-3.5 py-3 font-mono text-[0.82rem] whitespace-pre-wrap wrap-anywhere'

function StateBadge({ state }: { state: TrackedState }) {
  return <Badge tone={STATE_TONE[state]} dot>{STATE_LABEL[state]}</Badge>
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
