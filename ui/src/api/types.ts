/** Storefront'un vitrin görünümü (GET /catalog). */
export interface CatalogView {
  sku: string
  name: string
  description: string | null
  price: number
  currency: string
  stock: number
  outOfStock: boolean
  active: boolean
}

/** Park edilmiş mesaj (GET /admin/parking/{queue}). */
export interface ParkedMessage {
  messageId: string | null
  type: string
  error: string | null
  attempts: number
  body: string
}

export interface NewProduct {
  sku: string
  name: string
  description: string | null
  price: number
  currency: string
}

export type ProductDetails = Omit<NewProduct, 'sku'>

export interface QueueInfo {
  name: string
  messages: number
  consumers: number
}

export const CONSUMER_QUEUES = ['storefront.catalog', 'storefront.stock'] as const

// --- Mesaj takibi ---

export type OutboxState = 'PENDING' | 'FAILING' | 'PUBLISHED'

/** Backoffice outbox'ındaki bir mesaj (GET /admin/outbox). */
export interface OutboxEntry {
  messageId: string
  sku: string
  type: string
  routingKey: string
  schemaVersion: number
  payload: string
  createdAt: string
  publishedAt: string | null
  attempts: number
  lastError: string | null
  state: OutboxState
}

export type DeliveryOutcome = 'PROCESSED' | 'DUPLICATE' | 'RETRY' | 'PARKED' | 'REPLAYED'

/** Storefront teslimat geçmişinde bir mesajın özeti (GET /admin/messages). */
export interface DeliverySummary {
  messageId: string | null
  type: string | null
  sku: string | null
  queue: string
  schemaVersion: number | null
  state: DeliveryOutcome
  attempts: number
  lastError: string | null
  firstSeen: string
  lastSeen: string
}

/** Tek bir deneme (GET /admin/messages/{id}). */
export interface DeliveryEntry {
  attempt: number
  outcome: DeliveryOutcome
  error: string | null
  queue: string
  at: string
}
