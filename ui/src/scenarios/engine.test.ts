import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { eventually, runScenario, type Scenario, type StepResult } from './engine'

type Ctx = { log: string[] }

const scenario = (steps: Scenario<Ctx>['steps'], cleanup?: Scenario<Ctx>['cleanup']): Scenario<Ctx> => ({
  id: 'T1',
  story: 'US0',
  title: 'deneme',
  steps,
  cleanup,
})

function recorder() {
  const results: Record<number, StepResult> = {}
  return { results, onStep: (index: number, result: StepResult) => (results[index] = result) }
}

describe('runScenario', () => {
  it('adımları sırayla çalıştırır ve hepsi geçerse passed döner', async () => {
    const ctx: Ctx = { log: [] }
    const { results, onStep } = recorder()

    const outcome = await runScenario(
      scenario([
        { kind: 'action', title: 'bir', run: async (c) => void c.log.push('bir') },
        { kind: 'check', title: 'iki', run: async (c) => void c.log.push('iki') },
      ]),
      ctx,
      { onStep, confirm: async () => true },
    )

    expect(outcome).toBe('passed')
    expect(ctx.log).toEqual(['bir', 'iki'])
    expect(results[0].status).toBe('passed')
    expect(results[1].status).toBe('passed')
  })

  it('ilk hatada durur; hata mesajını yazar, kalan adımları atlar', async () => {
    const ctx: Ctx = { log: [] }
    const { results, onStep } = recorder()

    const outcome = await runScenario(
      scenario([
        { kind: 'action', title: 'bir', run: async () => { throw new Error('vitrin cevap vermedi') } },
        { kind: 'action', title: 'iki', run: async (c) => void c.log.push('iki') },
      ]),
      ctx,
      { onStep, confirm: async () => true },
    )

    expect(outcome).toBe('failed')
    expect(results[0]).toMatchObject({ status: 'failed', error: 'vitrin cevap vermedi' })
    expect(results[1].status).toBe('skipped')
    expect(ctx.log).toEqual([])
  })

  it('elle yapılan adımda onay bekler; onaylanınca devam eder', async () => {
    const ctx: Ctx = { log: [] }
    const { results, onStep } = recorder()
    const confirm = vi.fn(async () => {
      expect(results[0].status).toBe('waiting') // onay beklerken "bekliyor" görünür
      return true
    })

    const outcome = await runScenario(
      scenario([
        { kind: 'manual', title: 'vitrini durdur', manual: 'stop-storefront' },
        { kind: 'action', title: 'sonra', run: async (c) => void c.log.push('sonra') },
      ]),
      ctx,
      { onStep, confirm },
    )

    expect(outcome).toBe('passed')
    expect(confirm).toHaveBeenCalledOnce()
    expect(ctx.log).toEqual(['sonra'])
  })

  it('elle yapılan adım iptal edilirse cancelled döner, kalanlar atlanır', async () => {
    const { results, onStep } = recorder()

    const outcome = await runScenario(
      scenario([
        { kind: 'manual', title: 'vitrini durdur', manual: 'stop-storefront' },
        { kind: 'action', title: 'sonra', run: async () => {} },
      ]),
      { log: [] },
      { onStep, confirm: async () => false },
    )

    expect(outcome).toBe('cancelled')
    expect(results[0].status).toBe('skipped')
    expect(results[1].status).toBe('skipped')
  })

  it('sonuç ne olursa olsun cleanup çalışır', async () => {
    const ctx: Ctx = { log: [] }

    await runScenario(
      scenario([{ kind: 'action', title: 'patlar', run: async () => { throw new Error('x') } }],
        async (c) => void c.log.push('temizlendi')),
      ctx,
      { onStep: () => {}, confirm: async () => true },
    )

    expect(ctx.log).toEqual(['temizlendi'])
  })
})

describe('eventually', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('koşul sağlanana kadar tekrar dener', async () => {
    let calls = 0
    const result = eventually(async () => {
      calls++
      if (calls < 3) throw new Error('henüz değil')
      return 'tamam'
    }, { timeoutMs: 5_000, intervalMs: 100 })

    await vi.advanceTimersByTimeAsync(300)

    await expect(result).resolves.toBe('tamam')
    expect(calls).toBe(3)
  })

  it('iptal sinyali gelince beklemeyi bırakır; senaryo cancelled olur', async () => {
    const controller = new AbortController()
    const { results, onStep } = recorder()
    const outcome = runScenario(
      scenario([{ kind: 'check', title: 'bekler', run: () => eventually(async () => { throw new Error('henüz değil') },
        { timeoutMs: 60_000, intervalMs: 100, signal: controller.signal }) }]),
      { log: [] },
      { onStep, confirm: async () => true, signal: controller.signal },
    )

    await vi.advanceTimersByTimeAsync(300)
    controller.abort()
    await vi.advanceTimersByTimeAsync(100)

    await expect(outcome).resolves.toBe('cancelled')
    expect(results[0].status).toBe('skipped')
  })

  it('süre dolunca son hatayla ve süreyle birlikte başarısız olur', async () => {
    const result = eventually(async () => { throw new Error('stok 0, beklenen 8') },
      { timeoutMs: 1_000, intervalMs: 100 })
    const assertion = expect(result).rejects.toThrow(/1 sn.*stok 0, beklenen 8/)

    await vi.advanceTimersByTimeAsync(1_200)

    await assertion
  })
})
