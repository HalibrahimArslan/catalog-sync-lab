import type { CatalogView, NewProduct, ParkedMessage, ProductDetails } from '../api/types'

/**
 * Senaryoların sistemle konuştuğu tek yer. UI'da gerçek HTTP'ye ({@link ../api/http-driver}), testlerde
 * bellekteki sahte sisteme bağlanır. Senaryo tanımları hangisine bağlı olduklarını bilmez.
 */
export interface Driver {
  backoffice: {
    isUp(): Promise<boolean>
    create(product: NewProduct): Promise<void>
    update(sku: string, details: ProductDetails): Promise<void>
    countStock(sku: string, quantity: number): Promise<void>
    deactivate(sku: string): Promise<void>
  }
  storefront: {
    isUp(): Promise<boolean>
    /** Ürün yoksa {@code null}. */
    item(sku: string): Promise<CatalogView | null>
    visible(): Promise<CatalogView[]>
    parked(queue: string): Promise<ParkedMessage[]>
    /** @return mesaj park kuyruğunda bulunamadıysa {@code false} */
    replay(queue: string, messageId: string): Promise<boolean>
  }
  broker: {
    /** Backoffice'i atlayıp doğrudan exchange'e (bozuk, bilinmeyen sürümlü... mesajlar için). */
    publish(message: RawMessage): Promise<void>
    queueDepth(queue: string): Promise<number>
    /** Exchange'e bağlı geçici bir kuyruk: kabloda ne gittiğini (başlıklar dahil) görmek için. */
    openTap(): Promise<Tap>
  }
}

export interface RawMessage {
  routingKey: string
  type: string
  body: string
  messageId?: string
  schemaVersion?: number
}

export interface TappedMessage {
  type: string
  routingKey: string
  schemaVersion: number | null
  body: unknown
}

export interface Tap {
  /** Şimdiye kadar yakalanan tüm mesajlar. */
  messages(): Promise<TappedMessage[]>
  close(): Promise<void>
}

/** İnsanın yapması gereken adımlar: UI talimat gösterir, testte sahte sistem uygular. */
export type ManualAction = 'stop-storefront' | 'start-storefront' | 'backoffice-v2' | 'backoffice-v1'
