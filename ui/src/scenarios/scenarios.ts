import type { CatalogView } from '../api/types'
import type { Driver, Tap } from './driver'
import { eventually, type EventuallyOptions, type Scenario, type Step } from './engine'

/**
 * Kabul testlerindeki ({@code acceptance/src/test/resources/features}) hikâyelerin UI karşılığı. Adımlar
 * aynı; fark, burada sistemi senin çalıştırdığın uygulamalar oluşturuyor ve kapatıp açma gibi işleri sen
 * yapıyorsun.
 */
export interface Ctx {
  driver: Driver
  sku: string
  product: { name: string; description: string | null; price: number; currency: string }
  timing: EventuallyOptions
  /** Park beklemesi daha uzun: bilinmeyen ürün 3 kez, aralarda bekleyerek denenir. */
  parkTiming: EventuallyOptions
  parkedId?: string
  tap?: Tap
}

const STOCK_QUEUE = 'storefront.stock'

export function newContext(driver: Driver, timing: EventuallyOptions = { timeoutMs: 15_000, intervalMs: 500 }): Ctx {
  return {
    driver,
    sku: `UI-${crypto.randomUUID().slice(0, 8)}`,
    product: { name: 'Kupa', description: 'Kupa açıklaması', price: 100, currency: 'TRY' },
    timing,
    parkTiming: { timeoutMs: timing.timeoutMs * 3, intervalMs: timing.intervalMs },
  }
}

// --- Adımlar: Backoffice (ürün yöneticisi, depo görevlisi) ---

const create = (name: string, price: number, currency = 'TRY'): Step<Ctx> => ({
  kind: 'action',
  title: `Ürün yöneticisi "${name}" adlı ürünü ${price} ${currency} fiyatla oluşturur`,
  run: async (c) => {
    c.product = { name, description: `${name} açıklaması`, price, currency }
    await c.driver.backoffice.create({ sku: c.sku, ...c.product })
  },
})

const productExists = (name: string, price: number): Step<Ctx>[] => [
  { ...create(name, price), title: `Vitrinde "${name}" adlı ürün ${price} TRY fiyatla var: oluştur` },
  check('…ve vitrine ulaşmasını bekle', async (c) => void (await item(c))),
]

const changePrice = (price: number, currency = 'TRY'): Step<Ctx> => ({
  kind: 'action',
  title: `Ürün yöneticisi fiyatı ${price} ${currency} yapar`,
  run: async (c) => {
    c.product = { ...c.product, price, currency }
    await c.driver.backoffice.update(c.sku, c.product)
  },
})

const changeDescription = (description: string): Step<Ctx> => ({
  kind: 'action',
  title: `Ürün yöneticisi açıklamayı "${description}" yapar`,
  run: async (c) => {
    c.product = { ...c.product, description }
    await c.driver.backoffice.update(c.sku, c.product)
  },
})

const countStock = (quantity: number): Step<Ctx> => ({
  kind: 'action',
  title: `Depo görevlisi stoğu ${quantity} olarak sayar`,
  run: (c) => c.driver.backoffice.countStock(c.sku, quantity),
})

const deactivate: Step<Ctx> = {
  kind: 'action',
  title: 'Ürün yöneticisi ürünü satıştan kaldırır',
  run: (c) => c.driver.backoffice.deactivate(c.sku),
}

// --- Adımlar: vitrin kontrolleri (kısa gecikmeyle doğru olmalı) ---

function check(title: string, probe: (c: Ctx) => Promise<void>, timing: keyof Pick<Ctx, 'timing' | 'parkTiming'> = 'timing'): Step<Ctx> {
  return { kind: 'check', title, run: (c) => eventually(() => probe(c), c[timing]) }
}

async function item(c: Ctx): Promise<CatalogView> {
  const found = await c.driver.storefront.item(c.sku)
  if (!found) throw new Error(`${c.sku} vitrinde yok`)
  return found
}

function expectEqual<T>(what: string, actual: T, expected: T) {
  if (actual !== expected) throw new Error(`${what}: ${String(actual)}, beklenen ${String(expected)}`)
}

const expectActiveOutOfStock = check('Vitrinde ürün aktif ve "Tükendi" olarak görünür', async (c) => {
  const found = await item(c)
  expectEqual('aktif', found.active, true)
  expectEqual('tükendi', found.outOfStock, true)
  expectEqual('ad', found.name, c.product.name)
})

const expectPrice = (price: number, currency = 'TRY') => check(`Vitrinde fiyat ${price} ${currency} olur`, async (c) => {
  const found = await item(c)
  expectEqual('fiyat', Number(found.price), price)
  expectEqual('para birimi', found.currency, currency)
})

const expectDescription = (description: string) => check(`Vitrinde açıklama "${description}" olur`, async (c) =>
  expectEqual('açıklama', (await item(c)).description, description))

const expectStock = (stock: number) => check(`Vitrinde stok ${stock} olur`, async (c) =>
  expectEqual('stok', (await item(c)).stock, stock))

const expectNotListed = check('Ürün vitrinde listelenmez', async (c) => {
  const visible = await c.driver.storefront.visible()
  if (visible.some((v) => v.sku === c.sku)) throw new Error(`${c.sku} hâlâ listede`)
  expectEqual('aktif', (await item(c)).active, false)
})

// --- Adımlar: kesintiler (insan yapar) ---

const manual = (action: Step<Ctx>['manual'], title: string): Step<Ctx> => ({ kind: 'manual', manual: action, title })

const stopStorefront: Step<Ctx>[] = [
  manual('stop-storefront', 'Vitrin uygulamasını kapat'),
  check('Vitrin kapalı', async (c) => {
    if (await c.driver.storefront.isUp()) throw new Error('vitrin hâlâ cevap veriyor')
  }),
]

const startStorefront: Step<Ctx>[] = [
  manual('start-storefront', 'Vitrin uygulamasını tekrar aç'),
  check('Vitrin açık', async (c) => {
    if (!(await c.driver.storefront.isUp())) throw new Error('vitrin cevap vermiyor')
  }),
]

const changesWaitInQueues = check('Değişiklikler kuyruklarda birikir', async (c) => {
  const catalog = await c.driver.broker.queueDepth('storefront.catalog')
  const stock = await c.driver.broker.queueDepth(STOCK_QUEUE)
  if (catalog < 2 || stock < 1) throw new Error(`kuyruklarda ${catalog} ürün, ${stock} stok mesajı; beklenen en az 2 ve 1`)
})

// --- Adımlar: hatalı mesajlar (Backoffice bunları üretmez; doğrudan exchange'e) ---

const rawStock = (title: string, quantity: number, schemaVersion?: number): Step<Ctx> => ({
  kind: 'action',
  title,
  run: (c) => c.driver.broker.publish({ routingKey: 'stock.updated', type: 'StockUpdated', schemaVersion,
    body: JSON.stringify({ sku: c.sku, quantity, version: 1 }) }),
})

async function parkedOfThisScenario(c: Ctx) {
  return (await c.driver.storefront.parked(STOCK_QUEUE)).find((m) => m.body.includes(c.sku))
}

const expectParked = (attempts: number, title: string) => check(title, async (c) => {
  const message = await parkedOfThisScenario(c)
  if (!message) throw new Error(`${c.sku} için park edilmiş mesaj yok`)
  expectEqual('deneme sayısı', message.attempts, attempts)
  c.parkedId = message.messageId ?? undefined
}, 'parkTiming')

// --- Adımlar: işletim (US6, US7) ---

const replayParked: Step<Ctx> = {
  kind: 'action',
  title: 'Operatör park edilen mesajı yeniden oynatır',
  run: async (c) => {
    if (!c.parkedId) throw new Error('yeniden oynatılacak mesajın kimliği yok')
    if (!(await c.driver.storefront.replay(STOCK_QUEUE, c.parkedId))) throw new Error('mesaj park kuyruğunda bulunamadı')
  },
}

const expectNotParked = check('Mesaj artık park kuyruğunda değildir', async (c) => {
  if (await parkedOfThisScenario(c)) throw new Error('mesaj hâlâ park kuyruğunda')
})

const openTap: Step<Ctx> = {
  kind: 'action',
  title: 'Kabloyu dinlemeye başla (geçici kuyruk, mesaj başlıklarını görmek için)',
  run: async (c) => void (c.tap = await c.driver.broker.openTap()),
}

const backofficeUp = check('Backoffice açık', async (c) => {
  if (!(await c.driver.backoffice.isUp())) throw new Error('Backoffice cevap vermiyor')
})
const backofficeV2: Step<Ctx>[] = [manual('backoffice-v2', "Backoffice'i sözleşmenin 2. sürümüyle yeniden başlat"), backofficeUp]
const backofficeV1: Step<Ctx>[] = [manual('backoffice-v1', "Backoffice'i 1. sürüme geri döndür (sonraki senaryolar için)"), backofficeUp]

const expectPublishedVersions = (versions: number[]) =>
  check(`Backoffice'in bu ürün için yayınladığı mesajların sürümleri "${versions.join(', ')}" olur`, async (c) => {
    const mine = (await c.tap!.messages()).filter((m) => (m.body as { sku?: string })?.sku === c.sku)
    expectEqual('sürümler', mine.map((m) => m.schemaVersion ?? 1).join(', '), versions.join(', '))
  })

const closeTap = async (c: Ctx) => void (await c.tap?.close())

// --- Senaryolar ---

export const scenarios: Scenario<Ctx>[] = [
  {
    id: 'US1', story: 'US1', title: 'Yeni ürün vitrine "Tükendi" olarak yansır',
    steps: [create('Kupa', 100), expectActiveOutOfStock],
  },
  {
    id: 'US2', story: 'US2', title: 'Ürün bilgisi güncellemesi vitrine yansır',
    steps: [...productExists('Kupa', 100), changePrice(120), expectPrice(120)],
  },
  {
    id: 'US3-sayim', story: 'US3', title: 'Stok sayımı vitrine yansır',
    steps: [...productExists('Kupa', 100), countStock(5), countStock(8), expectStock(8)],
  },
  {
    id: 'US3-tukendi', story: 'US3', title: 'Stok sıfırlanınca ürün tükenir',
    steps: [...productExists('Kupa', 100), countStock(1), expectStock(1), countStock(0), expectActiveOutOfStock],
  },
  {
    id: 'US4-kaldir', story: 'US4', title: 'Satıştan kaldırılan ürün vitrinden kalkar',
    steps: [...productExists('Kupa', 100), deactivate, expectNotListed],
  },
  {
    id: 'US4-pasif-stok', story: 'US4', title: 'Pasif ürünün stoğu değişse de ürün vitrine dönmez',
    steps: [...productExists('Kupa', 100), deactivate, expectNotListed, countStock(7), expectStock(7), expectNotListed],
  },
  {
    id: 'US5-1', story: 'US5', title: 'Vitrin kapalıyken yapılan değişiklikler kaybolmaz',
    description: 'Vitrini kapatıp açmanı ister.',
    steps: [
      ...productExists('Kupa', 100), ...stopStorefront,
      changePrice(120), changeDescription('Büyük boy'), countStock(7), changesWaitInQueues,
      ...startStorefront, expectPrice(120), expectDescription('Büyük boy'), expectStock(7),
    ],
  },
  {
    id: 'US5-2', story: 'US5', title: 'Ürünü vitrine hiç ulaşmamış stok mesajı park edilir',
    steps: [
      rawStock('Vitrine hiç ulaşmamış bir ürün için stok mesajı gelir', 4),
      expectParked(3, 'Mesaj 3 denemeden sonra park kuyruğuna düşer'),
    ],
  },
  {
    id: 'US5-3', story: 'US5', title: 'Bozuk mesaj tekrar denenmez, diğerlerini bekletmez',
    steps: [
      ...productExists('Kupa', 100),
      rawStock('Ürün için stoğu -5 olan bozuk bir mesaj gelir', -5), countStock(3),
      expectParked(1, 'Bozuk mesaj tekrar denenmeden park kuyruğuna düşer'), expectStock(3),
    ],
  },
  {
    id: 'US6', story: 'US6', title: 'Park edilen mesaj, sorun düzeltilince yeniden oynatılır',
    steps: [
      rawStock('Vitrine hiç ulaşmamış bir ürün için stok mesajı gelir', 4),
      expectParked(3, 'Mesaj 3 denemeden sonra park kuyruğunda görünür'),
      create('Kupa', 100), replayParked, expectStock(4), expectNotParked,
    ],
  },
  {
    id: 'US7-gecis', story: 'US7', title: 'Backoffice yeni sözleşme sürümüne geçer, vitrin kesintisiz güncellenir',
    description: "Backoffice'i iki kez yeniden başlatmanı ister.",
    steps: [
      openTap, ...productExists('Kupa', 100), ...backofficeV2, changePrice(120), expectPrice(120),
      expectPublishedVersions([1, 2]), ...backofficeV1,
    ],
    cleanup: closeTap,
  },
  {
    id: 'US7-karisik', story: 'US7', title: 'Sürüm geçişi sırasında bekleyen eski mesajlar da işlenir',
    description: "Vitrini kapatıp açmanı ve Backoffice'i iki kez yeniden başlatmanı ister.",
    steps: [
      openTap, ...productExists('Kupa', 100), ...stopStorefront, changePrice(120), ...backofficeV2,
      changeDescription('Büyük boy'), countStock(6), ...startStorefront,
      expectPrice(120), expectDescription('Büyük boy'), expectStock(6), expectPublishedVersions([1, 1, 2, 2]),
      ...backofficeV1,
    ],
    cleanup: closeTap,
  },
  {
    id: 'US7-bilinmeyen', story: 'US7', title: 'Vitrinin bilmediği sürümdeki mesaj tekrar denenmeden park edilir',
    steps: [
      rawStock('Vitrinin henüz bilmediği 3. sürümde bir mesaj gelir', 4, 3),
      expectParked(1, 'Bozuk mesaj tekrar denenmeden park kuyruğuna düşer'),
    ],
  },
]

/** Elle adım gerektiren senaryo mu? ("Tümünü çalıştır" bunları atlar.) */
export const isManual = (scenario: Scenario<Ctx>) => scenario.steps.some((s) => s.kind === 'manual')

/** Elle yapılacak adımın talimatı: hangi komutla, ne yapılacak. */
export const instructions: Record<NonNullable<Step<Ctx>['manual']>, string> = {
  'stop-storefront': 'Storefront\'u çalıştığı terminalde Ctrl+C ile durdur.',
  'start-storefront': 'Storefront\'u tekrar başlat: java -jar storefront/infrastructure/target/storefront-0.4.0-SNAPSHOT-exec.jar',
  'backoffice-v2': 'Backoffice\'i durdur ve şu ayarla başlat: java -jar backoffice/infrastructure/target/backoffice-0.4.0-SNAPSHOT-exec.jar --backoffice.outbox.schema-version=2',
  'backoffice-v1': 'Backoffice\'i durdur ve ayarsız (varsayılan v1) başlat: java -jar backoffice/infrastructure/target/backoffice-0.4.0-SNAPSHOT-exec.jar',
}
