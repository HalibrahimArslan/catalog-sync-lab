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
