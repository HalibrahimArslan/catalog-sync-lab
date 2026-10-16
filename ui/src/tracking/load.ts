import { backoffice, storefront, type MessageFilter } from '../api/http-driver'
import type { DeliveryOutcome, DeliverySummary, OutboxEntry, OutboxState } from '../api/types'
import { combine, type TrackedMessage, type TrackedState } from './tracking'

const DELIVERY_STATE: Partial<Record<TrackedState, DeliveryOutcome>> = {
  processed: 'PROCESSED', duplicate: 'DUPLICATE', retrying: 'RETRY', parked: 'PARKED', replayed: 'REPLAYED',
}
const OUTBOX_STATE: Partial<Record<TrackedState, OutboxState>> = {
  'outbox-pending': 'PENDING', 'outbox-failing': 'FAILING', 'in-flight': 'PUBLISHED',
}

export interface Loaded {
  messages: TrackedMessage[]
  /** Bir servis cevap vermediyse: liste diğerinin bildikleriyle gelir. */
  warnings: string[]
}

/**
 * İki servisten mesajları çeker ve birleştirir. Durum filtresi hangi tarafa aitse sunucuda uygulanır
 * (eski bir park edilmiş mesaj, son 50 mesajın dışında kalsa da bulunur).
 */
export async function loadMessages(filter: MessageFilter & { state: TrackedState | 'all' }): Promise<Loaded> {
  const limit = filter.limit ?? 50
  const deliveryState = DELIVERY_STATE[filter.state as TrackedState]
  const outboxState = OUTBOX_STATE[filter.state as TrackedState]
  const warnings: string[] = []
  const settle = async <T,>(promise: Promise<T>, fallback: T) => {
    try {
      return await promise
    } catch (e) {
      warnings.push(e instanceof Error ? e.message : String(e))
      return fallback
    }
  }

  const [outbox, deliveries] = await Promise.all([
    deliveryState ? Promise.resolve<OutboxEntry[]>([])
      : settle(backoffice.outbox({ ...filter, limit, state: outboxState }), []),
    outboxState && filter.state !== 'in-flight' ? Promise.resolve<DeliverySummary[]>([])
      : settle(storefront.messages({ ...filter, limit, state: deliveryState }), []),
  ])

  // Yayınlanmış ama listedeki vitrin kayıtlarında olmayanlar: gerçekten yolda mı, yoksa liste sınırının
  // dışında mı kaldı? Tek tek sor (en fazla 25).
  const known = new Set(deliveries.map((d) => d.messageId))
  const unknown = outbox.filter((o) => o.state === 'PUBLISHED' && !known.has(o.messageId)).slice(0, 25)
  const found = await Promise.all(unknown.map((o) => settle(storefront.message(o.messageId), null)))
  const extra = found.flatMap((detail) => (detail ? [detail.summary] : []))

  let messages = combine(outbox, [...deliveries, ...extra])
  if (filter.state !== 'all') messages = messages.filter((m) => m.state === filter.state)
  return { messages: messages.slice(0, limit), warnings: [...new Set(warnings)] }
}
