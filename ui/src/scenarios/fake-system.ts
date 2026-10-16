import type { CatalogView, ParkedMessage } from '../api/types'
import type { Driver, ManualAction, RawMessage, TappedMessage } from './driver'

/**
 * Testler için bellekte çalışan sistem: Backoffice → kuyruk → Storefront akışının kuralları
 * (versiyon kontrolü, park, sözleşme sürümü, vitrin kapalıyken biriken mesajlar) küçük bir modelle.
 * Gerçek sistemin yerine geçmez; senaryo tanımlarının doğru şeyi sorduğunu doğrular.
 *
 * {@code ignoresSchemaVersion}: sürüm başlığına bakmayan bozuk bir vitrin. US7 senaryoları bunu yakalamalı.
 */
export class FakeSystem {
  storefrontUp = true
  schemaVersion = 1
  ignoresSchemaVersion = false

  private products = new Map<string, { name: string; description: string | null; price: number; currency: string
    version: number; stockVersion: number }>()
  private catalog = new Map<string, CatalogView & { version: number; stockVersion: number }>()
  private queues: Record<string, RawMessage[]> = { 'storefront.catalog': [], 'storefront.stock': [] }
  private parking: Record<string, ParkedMessage[]> = { 'storefront.catalog': [], 'storefront.stock': [] }
  private taps: TappedMessage[][] = []
  private nextId = 1

  /** Senaryodaki elle yapılan adımı (insanın yapacağını) uygular. */
  perform(action: ManualAction) {
    switch (action) {
      case 'stop-storefront':
        this.storefrontUp = false
        break
      case 'start-storefront':
        this.storefrontUp = true
        this.drain()
        break
      case 'backoffice-v2':
        this.schemaVersion = 2
        break
      case 'backoffice-v1':
        this.schemaVersion = 1
        break
    }
  }

  driver(): Driver {
    return {
      backoffice: {
        isUp: async () => true,
        create: async ({ sku, name, description, price, currency }) => {
          if (this.products.has(sku)) throw new Error(`409 SKU zaten var: ${sku}`)
          this.products.set(sku, { name, description, price, currency, version: 1, stockVersion: 0 })
          this.emitDetails('ProductCreated', 'product.created', sku)
        },
        update: async (sku, { name, description, price, currency }) => {
          const product = this.require(sku)
          Object.assign(product, { name, description, price, currency, version: product.version + 1 })
          this.emitDetails('ProductUpdated', 'product.updated', sku)
        },
        countStock: async (sku, quantity) => {
          const product = this.require(sku)
          product.stockVersion++
          this.emit({ type: 'StockUpdated', routingKey: 'stock.updated', schemaVersion: this.schemaVersion,
            body: JSON.stringify({ sku, quantity, version: product.stockVersion }) })
        },
        deactivate: async (sku) => {
          const product = this.require(sku)
          product.version++
          this.emit({ type: 'ProductDeactivated', routingKey: 'product.deactivated', schemaVersion: this.schemaVersion,
            body: JSON.stringify({ sku, version: product.version }) })
        },
      },
      storefront: {
        isUp: async () => this.storefrontUp,
        item: async (sku) => this.view(sku),
        visible: async () => [...this.catalog.keys()].map((sku) => this.view(sku)!).filter((item) => item.active),
        parked: async (queue) => this.upOrThrow(() => [...this.parking[queue]]),
        replay: async (queue, messageId) => this.upOrThrow(() => {
          const index = this.parking[queue].findIndex((m) => m.messageId === messageId)
          if (index < 0) return false
          const [parked] = this.parking[queue].splice(index, 1)
          this.deliver(queue, { type: parked.type, routingKey: queue === 'storefront.stock' ? 'stock.updated' : 'product.x',
            body: parked.body, messageId: parked.messageId ?? undefined, schemaVersion: parked.schemaVersion })
          return true
        }),
      },
      broker: {
        publish: async (message) => this.emit(message),
        queueDepth: async (queue) => this.queues[queue].length,
        openTap: async () => {
          const tapped: TappedMessage[] = []
          this.taps.push(tapped)
          return {
            messages: async () => [...tapped],
            close: async () => void (this.taps = this.taps.filter((t) => t !== tapped)),
          }
        },
      },
    }
  }

  // --- Backoffice tarafı ---

  private require(sku: string) {
    const product = this.products.get(sku)
    if (!product) throw new Error(`404 ürün yok: ${sku}`)
    return product
  }

  private emitDetails(type: string, routingKey: string, sku: string) {
    const p = this.require(sku)
    const body = this.schemaVersion === 2
      ? { sku, name: p.name, description: p.description, price: { amount: p.price, currency: p.currency }, version: p.version }
      : { sku, name: p.name, description: p.description, price: p.price, currency: p.currency, version: p.version }
    this.emit({ type, routingKey, schemaVersion: this.schemaVersion, body: JSON.stringify(body) })
  }

  private emit(message: RawMessage) {
    const withId = { ...message, messageId: message.messageId ?? `m-${this.nextId++}` }
    for (const tap of this.taps) {
      tap.push({ type: withId.type, routingKey: withId.routingKey, schemaVersion: withId.schemaVersion ?? null,
        body: JSON.parse(withId.body) })
    }
    const queue = withId.routingKey.startsWith('stock.') ? 'storefront.stock' : 'storefront.catalog'
    this.deliver(queue, withId)
  }

  // --- Storefront tarafı ---

  private deliver(queue: string, message: RawMessage) {
    this.queues[queue].push(message)
    this.drain()
  }

  private drain() {
    if (!this.storefrontUp) return
    for (const queue of Object.keys(this.queues)) {
      while (this.queues[queue].length > 0) this.consume(queue, this.queues[queue].shift()!)
    }
  }

  private consume(queue: string, message: RawMessage) {
    const version = this.ignoresSchemaVersion ? 1 : message.schemaVersion ?? 1
    const park = (error: string, attempts: number) => this.parking[queue].push({ messageId: message.messageId ?? null,
      type: message.type, error, attempts, body: message.body, schemaVersion: message.schemaVersion })
    if (version !== 1 && version !== 2) return park(`Desteklenmeyen sözleşme sürümü: ${version}`, 1)

    const m = JSON.parse(message.body)
    const price = version === 2 ? m.price : { amount: m.price, currency: m.currency }
    const item = this.catalog.get(m.sku)
    switch (message.type) {
      case 'ProductCreated':
      case 'ProductUpdated':
        if (typeof price?.amount !== 'number') return park('price okunamadı', 1)
        if (!item) {
          this.catalog.set(m.sku, { sku: m.sku, name: m.name, description: m.description, price: price.amount,
            currency: price.currency, stock: 0, outOfStock: true, active: true, version: m.version, stockVersion: 0 })
        } else if (m.version > item.version) {
          Object.assign(item, { name: m.name, description: m.description, price: price.amount,
            currency: price.currency, version: m.version })
        }
        return
      case 'ProductDeactivated':
        if (item && m.version > item.version) Object.assign(item, { active: false, version: m.version })
        return
      case 'StockUpdated':
        if (m.quantity < 0) return park(`stok negatif olamaz: ${m.quantity}`, 1)
        if (!item) return park(`UnknownProductException: bilinmeyen ürün: ${m.sku}`, 3)
        if (m.version > item.stockVersion) Object.assign(item, { stock: m.quantity, stockVersion: m.version })
        return
    }
  }

  private view(sku: string): CatalogView | null {
    if (!this.storefrontUp) throw new Error('vitrin kapalı')
    const item = this.catalog.get(sku)
    if (!item) return null
    const { version: _v, stockVersion: _s, ...view } = item
    return { ...view, outOfStock: view.stock === 0 }
  }

  private upOrThrow<T>(fn: () => T): T {
    if (!this.storefrontUp) throw new Error('vitrin kapalı')
    return fn()
  }
}
