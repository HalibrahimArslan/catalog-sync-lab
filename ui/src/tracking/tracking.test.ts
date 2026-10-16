import { describe, expect, it } from 'vitest'
import type { DeliveryEntry, DeliverySummary, OutboxEntry } from '../api/types'
import { combine, timeline } from './tracking'

const outbox = (over: Partial<OutboxEntry> = {}): OutboxEntry => ({
  messageId: 'm-1', sku: 'SKU-1', type: 'StockUpdated', routingKey: 'stock.updated', schemaVersion: 1,
  payload: '{"sku":"SKU-1","quantity":4,"version":1}', createdAt: '2026-10-16T10:00:00Z', publishedAt: null,
  attempts: 0, lastError: null, state: 'PENDING', ...over,
})

const delivery = (over: Partial<DeliverySummary> = {}): DeliverySummary => ({
  messageId: 'm-1', type: 'StockUpdated', sku: 'SKU-1', queue: 'storefront.stock', schemaVersion: 1,
  state: 'PROCESSED', attempts: 1, lastError: null, firstSeen: '2026-10-16T10:00:02Z', lastSeen: '2026-10-16T10:00:02Z',
  ...over,
})

describe('combine', () => {
  it('outbox ve vitrin kaydını aynı message-id üzerinden tek mesajda birleştirir; durum vitrinden gelir', () => {
    const [message, ...rest] = combine(
      [outbox({ state: 'PUBLISHED', publishedAt: '2026-10-16T10:00:01Z' })],
      [delivery({ state: 'PARKED', attempts: 3, lastError: 'bilinmeyen ürün', lastSeen: '2026-10-16T10:00:12Z' })],
    )

    expect(rest).toEqual([])
    expect(message).toMatchObject({ messageId: 'm-1', state: 'parked', attempts: 3, lastError: 'bilinmeyen ürün',
      sku: 'SKU-1', type: 'StockUpdated', queue: 'storefront.stock', updatedAt: '2026-10-16T10:00:12Z' })
    expect(message.outbox).toBeDefined()
    expect(message.delivery).toBeDefined()
  })

  it('vitrine ulaşmamış mesajın durumu outbox\'tan gelir', () => {
    const messages = combine([
      outbox({ messageId: 'a', state: 'PENDING' }),
      outbox({ messageId: 'b', state: 'FAILING', attempts: 2, lastError: 'NO_ROUTE' }),
      outbox({ messageId: 'c', state: 'PUBLISHED', publishedAt: '2026-10-16T10:00:01Z' }),
    ], [])

    expect(messages.map((m) => [m.messageId, m.state])).toEqual(
      expect.arrayContaining([['a', 'outbox-pending'], ['b', 'outbox-failing'], ['c', 'in-flight']]))
    expect(messages.find((m) => m.messageId === 'b')).toMatchObject({ attempts: 2, lastError: 'NO_ROUTE' })
  })

  it('her vitrin durumunu kendi adıyla gösterir', () => {
    const states = ['PROCESSED', 'DUPLICATE', 'RETRY', 'PARKED', 'REPLAYED'] as const
    const messages = combine([], states.map((state, i) => delivery({ messageId: `m-${i}`, state })))

    expect(messages.map((m) => m.state).sort()).toEqual(['duplicate', 'parked', 'processed', 'replayed', 'retrying'])
  })

  it('kimliksiz vitrin kayıtlarını birleştirmez, ayrı ayrı gösterir', () => {
    const messages = combine([], [delivery({ messageId: null, state: 'PARKED' }), delivery({ messageId: null, state: 'PARKED' })])

    expect(messages).toHaveLength(2)
    expect(new Set(messages.map((m) => m.key)).size).toBe(2)
  })

  it('en son değişen mesaj önce gelir', () => {
    const messages = combine(
      [outbox({ messageId: 'eski', createdAt: '2026-10-16T09:00:00Z' })],
      [delivery({ messageId: 'yeni', lastSeen: '2026-10-16T11:00:00Z' })],
    )

    expect(messages.map((m) => m.messageId)).toEqual(['yeni', 'eski'])
  })
})

describe('timeline', () => {
  const history: DeliveryEntry[] = [
    { attempt: 1, outcome: 'RETRY', error: 'UnknownProductException: bilinmeyen ürün', queue: 'storefront.stock', at: '2026-10-16T10:00:02Z' },
    { attempt: 2, outcome: 'RETRY', error: 'UnknownProductException: bilinmeyen ürün', queue: 'storefront.stock', at: '2026-10-16T10:00:07Z' },
    { attempt: 3, outcome: 'PARKED', error: 'UnknownProductException: bilinmeyen ürün', queue: 'storefront.stock', at: '2026-10-16T10:00:12Z' },
    { attempt: 0, outcome: 'REPLAYED', error: null, queue: 'storefront.stock', at: '2026-10-16T10:05:00Z' },
    { attempt: 1, outcome: 'PROCESSED', error: null, queue: 'storefront.stock', at: '2026-10-16T10:05:01Z' },
  ]

  it('Backoffice\'ten vitrine mesajın yolculuğunu sırayla anlatır', () => {
    const events = timeline(outbox({ state: 'PUBLISHED', publishedAt: '2026-10-16T10:00:01Z', schemaVersion: 2 }), history)

    expect(events.map((e) => e.title)).toEqual([
      "Backoffice outbox'a yazdı",
      "RabbitMQ'ya yayınlandı",
      '1. deneme: tekrar denenecek',
      '2. deneme: tekrar denenecek',
      '3. deneme: park edildi',
      'Park kuyruğundan yeniden oynatıldı',
      '1. deneme: işlendi',
    ])
    expect(events[0].detail).toContain('v2')
    expect(events[2]).toMatchObject({ tone: 'warn', error: 'UnknownProductException: bilinmeyen ürün' })
    expect(events[4].tone).toBe('bad')
    expect(events[6].tone).toBe('ok')
  })

  it('yayınlanamayan mesajda outbox hatasını gösterir', () => {
    const events = timeline(outbox({ state: 'FAILING', attempts: 3, lastError: 'geri döndü: NO_ROUTE (312)' }), [])

    expect(events.map((e) => e.title)).toEqual(["Backoffice outbox'a yazdı", 'Yayınlanamadı (3 deneme)'])
    expect(events[1]).toMatchObject({ tone: 'bad', error: 'geri döndü: NO_ROUTE (312)' })
  })

  it('outbox kaydı yoksa (silinmiş ya da Backoffice dışından gelmiş) vitrin geçmişiyle yetinir', () => {
    expect(timeline(undefined, history.slice(0, 1)).map((e) => e.title)).toEqual(['1. deneme: tekrar denenecek'])
  })
})
