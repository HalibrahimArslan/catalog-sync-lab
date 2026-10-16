import type { DeliveryEntry, DeliverySummary, OutboxEntry } from '../api/types'

/**
 * Mesaj takibi: bir mesajın Backoffice outbox'ındaki kaydı ile Storefront'un teslimat geçmişi aynı
 * message-id'yi taşır. Burada ikisi tek bir "mesaj" olarak birleşir; durum, mesajın en ileri gittiği
 * yerden gelir (vitrin kaydı varsa vitrinden, yoksa outbox'tan).
 */
export type TrackedState =
  | 'outbox-pending' // Backoffice'te, relay'i bekliyor
  | 'outbox-failing' // Backoffice'te, gönderilemiyor
  | 'in-flight' // yayınlandı, vitrin henüz bir şey kaydetmedi (kuyrukta ya da yolda)
  | 'processed'
  | 'duplicate'
  | 'retrying'
  | 'parked'
  | 'replayed'

export const STATE_LABEL: Record<TrackedState, string> = {
  'outbox-pending': 'Yayın bekliyor',
  'outbox-failing': 'Yayınlanamıyor',
  'in-flight': 'Yayınlandı, yolda',
  processed: 'İşlendi',
  duplicate: 'Tekrar geldi, atlandı',
  retrying: 'Tekrar denenecek',
  parked: 'Park edildi',
  replayed: 'Yeniden oynatıldı',
}

export const STATE_TONE: Record<TrackedState, Tone> = {
  'outbox-pending': 'info',
  'outbox-failing': 'bad',
  'in-flight': 'info',
  processed: 'ok',
  duplicate: 'muted',
  retrying: 'warn',
  parked: 'bad',
  replayed: 'info',
}

export type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'muted'

export interface TrackedMessage {
  /** Liste anahtarı: message-id; kimliksiz vitrin kaydında ona özel bir anahtar. */
  key: string
  messageId: string | null
  type: string | null
  sku: string | null
  queue: string | null
  state: TrackedState
  attempts: number
  lastError: string | null
  updatedAt: string
  outbox?: OutboxEntry
  delivery?: DeliverySummary
}

const FROM_DELIVERY: Record<DeliverySummary['state'], TrackedState> = {
  PROCESSED: 'processed',
  DUPLICATE: 'duplicate',
  RETRY: 'retrying',
  PARKED: 'parked',
  REPLAYED: 'replayed',
}

const FROM_OUTBOX: Record<OutboxEntry['state'], TrackedState> = {
  PENDING: 'outbox-pending',
  FAILING: 'outbox-failing',
  PUBLISHED: 'in-flight',
}

export function combine(outbox: OutboxEntry[], deliveries: DeliverySummary[]): TrackedMessage[] {
  const byId = new Map<string, TrackedMessage>()
  const withoutId: TrackedMessage[] = []

  for (const entry of outbox) {
    byId.set(entry.messageId, {
      key: entry.messageId,
      messageId: entry.messageId,
      type: entry.type,
      sku: entry.sku,
      queue: null,
      state: FROM_OUTBOX[entry.state],
      attempts: entry.attempts,
      lastError: entry.lastError,
      updatedAt: entry.publishedAt ?? entry.createdAt,
      outbox: entry,
    })
  }

  deliveries.forEach((d, i) => {
    const fromOutbox = d.messageId ? byId.get(d.messageId) : undefined
    const message: TrackedMessage = {
      key: d.messageId ?? `kimliksiz-${i}-${d.lastSeen}`,
      messageId: d.messageId,
      type: d.type ?? fromOutbox?.type ?? null,
      sku: d.sku ?? fromOutbox?.sku ?? null,
      queue: d.queue,
      state: FROM_DELIVERY[d.state],
      attempts: d.attempts,
      lastError: d.lastError ?? fromOutbox?.lastError ?? null,
      updatedAt: later(d.lastSeen, fromOutbox?.updatedAt),
      outbox: fromOutbox?.outbox,
      delivery: d,
    }
    if (d.messageId) byId.set(d.messageId, message)
    else withoutId.push(message)
  })

  return [...byId.values(), ...withoutId].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))
}

/** ISO zamanları metin olarak karşılaştırılamaz: "10:00:00Z" ile "10:00:00.5Z" (kesirli saniye). */
function later(a: string, b?: string) {
  return b && Date.parse(b) > Date.parse(a) ? b : a
}

export interface TimelineEvent {
  at: string | null
  title: string
  detail?: string
  error?: string
  tone: Tone
}

const OUTCOME_TITLE: Record<DeliveryEntry['outcome'], string> = {
  PROCESSED: 'işlendi',
  DUPLICATE: 'daha önce işlenmiş, atlandı',
  RETRY: 'tekrar denenecek',
  PARKED: 'park edildi',
  REPLAYED: 'yeniden oynatıldı',
}

const OUTCOME_TONE: Record<DeliveryEntry['outcome'], Tone> = {
  PROCESSED: 'ok',
  DUPLICATE: 'muted',
  RETRY: 'warn',
  PARKED: 'bad',
  REPLAYED: 'info',
}

/** Mesajın Backoffice'ten vitrine yolculuğu, sırayla. */
export function timeline(outbox: OutboxEntry | undefined, history: DeliveryEntry[]): TimelineEvent[] {
  const events: TimelineEvent[] = []
  if (outbox) {
    events.push({ at: outbox.createdAt, title: "Backoffice outbox'a yazdı", tone: 'info',
      detail: `${outbox.type} · ${outbox.routingKey} · sözleşme v${outbox.schemaVersion}` })
    if (outbox.attempts > 0 && !outbox.publishedAt) {
      events.push({ at: null, title: `Yayınlanamadı (${outbox.attempts} deneme)`, tone: 'bad',
        error: outbox.lastError ?? undefined, detail: 'Relay her turda yeniden deniyor' })
    }
    if (outbox.publishedAt) {
      events.push({ at: outbox.publishedAt, title: "RabbitMQ'ya yayınlandı", tone: 'info',
        detail: outbox.attempts > 0 ? `${outbox.attempts} başarısız denemeden sonra; son hata: ${outbox.lastError}` : undefined })
    }
  }
  for (const entry of history) {
    events.push(entry.outcome === 'REPLAYED'
      ? { at: entry.at, title: 'Park kuyruğundan yeniden oynatıldı', tone: 'info', detail: 'Deneme sayacı sıfırlandı' }
      : { at: entry.at, title: `${entry.attempt}. deneme: ${OUTCOME_TITLE[entry.outcome]}`, tone: OUTCOME_TONE[entry.outcome],
          error: entry.error ?? undefined, detail: entry.queue })
  }
  return events
}

/** Arama kutusu: UUID gibi görünüyorsa message-id, değilse SKU. */
export function parseSearch(text: string): { sku?: string; messageId?: string } {
  const value = text.trim()
  if (!value) return {}
  return /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(value) ? { messageId: value } : { sku: value }
}
