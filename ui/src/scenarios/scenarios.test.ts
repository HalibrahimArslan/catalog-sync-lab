import { describe, expect, it } from 'vitest'
import { runScenario, type StepResult } from './engine'
import { FakeSystem } from './fake-system'
import { newContext, scenarios } from './scenarios'

/**
 * Senaryo tanımları, bellekteki sahte sisteme karşı. Elle yapılan adımları (vitrini durdur, Backoffice'i
 * v2 ile başlat) testte "insan" yerine sahte sistem uygular. Gerçek sistemle konuşan sürücü UI'da.
 */
async function run(id: string, system = new FakeSystem()) {
  const scenario = scenarios.find((s) => s.id === id)!
  const results: StepResult[] = []
  const outcome = await runScenario(scenario, newContext(system.driver(), { timeoutMs: 200, intervalMs: 5 }), {
    onStep: (index, result) => (results[index] = result),
    confirm: async (step) => {
      system.perform(step.manual!)
      return true
    },
  })
  return { outcome, results, failure: results.find((r) => r.status === 'failed')?.error }
}

describe('senaryolar', () => {
  it('kabul testlerindeki tüm hikâyeleri kapsar (US1–US7)', () => {
    const stories = new Set(scenarios.map((s) => s.story))
    expect([...stories].sort()).toEqual(['US1', 'US2', 'US3', 'US4', 'US5', 'US6', 'US7'])
    expect(new Set(scenarios.map((s) => s.id)).size).toBe(scenarios.length) // kimlikler benzersiz
  })

  it.each(scenarios.map((s) => [s.id, s.title]))('%s %s sağlıklı sistemde geçer', async (id) => {
    const { outcome, failure } = await run(id)

    expect(failure).toBeUndefined()
    expect(outcome).toBe('passed')
  })

  it('her senaryo kendi SKU\'suyla çalışır; tekrar çalıştırmak çakışmaz', async () => {
    const system = new FakeSystem()

    expect((await run('US1', system)).outcome).toBe('passed')
    expect((await run('US1', system)).outcome).toBe('passed')
  })

  it('sürüm başlığına bakmayan vitrini US7 yakalar', async () => {
    const broken = () => Object.assign(new FakeSystem(), { ignoresSchemaVersion: true })

    expect((await run('US7-gecis', broken())).outcome).toBe('failed')
    expect((await run('US7-karisik', broken())).outcome).toBe('failed')
    expect((await run('US7-bilinmeyen', broken())).outcome).toBe('failed')
  })

  it('US7 sonunda Backoffice v1\'e döner; sonraki senaryolar etkilenmez', async () => {
    const system = new FakeSystem()

    await run('US7-gecis', system)

    expect(system.schemaVersion).toBe(1)
  })

  it('beklenen değişiklik vitrine ulaşmazsa adım süre dolunca nedeniyle başarısız olur', async () => {
    const system = new FakeSystem()
    system.perform('stop-storefront') // vitrin hiç açılmıyor

    const { outcome, failure } = await run('US2', system)

    expect(outcome).toBe('failed')
    expect(failure).toMatch(/vitrin kapalı/)
  })
})
