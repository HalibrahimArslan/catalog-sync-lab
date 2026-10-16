import type { Driver, RawMessage, Tap, TappedMessage } from '../scenarios/driver'
import type { CatalogView, NewProduct, ParkedMessage, ProductDetails, QueueInfo } from './types'

/**
 * Gerçek sistemle konuşan sürücü. Üç servise Vite proxy'si üzerinden ulaşır (bkz. vite.config.ts):
 * {@code /svc/backoffice}, {@code /svc/storefront}, {@code /svc/rabbitmq} (RabbitMQ yönetim API'si).
 */
const BACKOFFICE = '/svc/backoffice'
const STOREFRONT = '/svc/storefront'
const RABBITMQ = '/svc/rabbitmq'
const VHOST = '%2F'
const EXCHANGE = 'backoffice.events'

async function call(url: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(url, init)
  } catch (e) {
    throw new Error(`${serviceOf(url)} erişilemiyor (${e instanceof Error ? e.message : e})`)
  }
  if (!response.ok) {
    throw new Error(`${serviceOf(url)} ${response.status}: ${await detailOf(response)}`)
  }
  return response
}

function serviceOf(url: string) {
  if (url.startsWith(BACKOFFICE)) return 'Backoffice'
  if (url.startsWith(STOREFRONT)) return 'Storefront'
  return 'RabbitMQ'
}

/** Backoffice hataları ProblemDetail ({@code detail}); RabbitMQ {@code reason} döner. */
async function detailOf(response: Response) {
  const text = await response.text()
  try {
    const body = JSON.parse(text)
    return body.detail ?? body.reason ?? body.error ?? text
  } catch {
    return text || response.statusText
  }
}

const json = (body: unknown): RequestInit => ({
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

/**
 * Servis ayakta mı? Herhangi bir HTTP cevabı (4xx dahil) "ayakta" demek. Proxy, hedefe ulaşamayınca
 * 5xx döner. Backoffice'te GET uç noktası yok; 405 de ayakta demek.
 */
async function isUp(url: string) {
  try {
    const response = await fetch(url, { method: 'GET' })
    return response.status < 500
  } catch {
    return false
  }
}

export const backoffice = {
  isUp: () => isUp(`${BACKOFFICE}/products`),
  create: async (product: NewProduct) => void (await call(`${BACKOFFICE}/products`, { method: 'POST', ...json(product) })),
  update: async (sku: string, details: ProductDetails) =>
    void (await call(`${BACKOFFICE}/products/${encodeURIComponent(sku)}`, { method: 'PUT', ...json(details) })),
  countStock: async (sku: string, quantity: number) =>
    void (await call(`${BACKOFFICE}/products/${encodeURIComponent(sku)}/stock`, { method: 'PUT', ...json({ quantity }) })),
  deactivate: async (sku: string) =>
    void (await call(`${BACKOFFICE}/products/${encodeURIComponent(sku)}/deactivate`, { method: 'POST' })),
}

export const storefront = {
  isUp: () => isUp(`${STOREFRONT}/catalog`),
  item: async (sku: string): Promise<CatalogView | null> => {
    const response = await fetch(`${STOREFRONT}/catalog/${encodeURIComponent(sku)}`).catch(() => null)
    if (response?.status === 404) return null
    if (!response || !response.ok) throw new Error(`Storefront ${response ? response.status : 'erişilemiyor'}`)
    return response.json()
  },
  visible: async (): Promise<CatalogView[]> => (await call(`${STOREFRONT}/catalog`)).json(),
  parked: async (queue: string, limit = 1_000): Promise<ParkedMessage[]> =>
    (await call(`${STOREFRONT}/admin/parking/${queue}?limit=${limit}`)).json(),
  replay: async (queue: string, messageId: string) => {
    const response = await fetch(`${STOREFRONT}/admin/parking/${queue}/${encodeURIComponent(messageId)}/replay`,
      { method: 'POST' })
    if (response.status === 404) return false
    if (!response.ok) throw new Error(`Storefront ${response.status}: ${await detailOf(response)}`)
    return true
  },
  replayAll: async (queue: string): Promise<number> =>
    (await (await call(`${STOREFRONT}/admin/parking/${queue}/replay`, { method: 'POST' })).json()).replayed,
}

interface ManagementMessage {
  payload: string
  routing_key: string
  properties: { type?: string; headers?: Record<string, unknown> }
}

export const rabbitmq = {
  isUp: () => isUp(`${RABBITMQ}/overview`),
  queues: async (): Promise<QueueInfo[]> =>
    (await call(`${RABBITMQ}/queues/${VHOST}?columns=name,messages,consumers`)).json(),
  queueDepth: async (queue: string): Promise<number> =>
    (await (await call(`${RABBITMQ}/queues/${VHOST}/${encodeURIComponent(queue)}`)).json()).messages ?? 0,
  publish: async ({ routingKey, type, body, messageId, schemaVersion }: RawMessage) => {
    const headers = schemaVersion === undefined ? {} : { 'x-schema-version': schemaVersion }
    const response = await call(`${RABBITMQ}/exchanges/${VHOST}/${EXCHANGE}/publish`, {
      method: 'POST',
      ...json({
        routing_key: routingKey,
        payload: body,
        payload_encoding: 'string',
        properties: { type, message_id: messageId ?? crypto.randomUUID(), content_type: 'application/json',
          delivery_mode: 2, headers },
      }),
    })
    if (!(await response.json()).routed) throw new Error(`mesaj hiçbir kuyruğa yönlenmedi (${routingKey})`)
  },
  /**
   * Exchange'e {@code #} ile bağlı geçici kuyruk. 10 dakika kullanılmazsa RabbitMQ kendisi siler
   * ({@code x-expires}); senaryo bitince biz de sileriz.
   */
  openTap: async (): Promise<Tap> => {
    const name = `ui.tap.${crypto.randomUUID().slice(0, 8)}`
    const queueUrl = `${RABBITMQ}/queues/${VHOST}/${name}`
    await call(queueUrl, { method: 'PUT', ...json({ durable: false, auto_delete: false, arguments: { 'x-expires': 600_000 } }) })
    await call(`${RABBITMQ}/bindings/${VHOST}/e/${EXCHANGE}/q/${name}`, { method: 'POST', ...json({ routing_key: '#' }) })
    const seen: TappedMessage[] = []
    return {
      messages: async () => {
        const batch: ManagementMessage[] = await (await call(`${queueUrl}/get`, {
          method: 'POST', ...json({ count: 500, ackmode: 'ack_requeue_false', encoding: 'auto' }),
        })).json()
        for (const m of batch) {
          const version = m.properties.headers?.['x-schema-version']
          seen.push({ type: m.properties.type ?? '?', routingKey: m.routing_key,
            schemaVersion: typeof version === 'number' ? version : null, body: safeParse(m.payload) })
        }
        return [...seen]
      },
      close: async () => void (await call(queueUrl, { method: 'DELETE' }).catch(() => {})),
    }
  },
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

export const httpDriver: Driver = {
  backoffice,
  storefront: { isUp: storefront.isUp, item: storefront.item, visible: storefront.visible,
    parked: (queue) => storefront.parked(queue), replay: storefront.replay },
  broker: { publish: rabbitmq.publish, queueDepth: rabbitmq.queueDepth, openTap: rabbitmq.openTap },
}
