import { useCallback, useEffect, useRef, useState } from 'react'
import type { Driver } from './scenarios/driver'
import { runScenario, type Outcome, type Scenario, type StepResult } from './scenarios/engine'
import { newContext, type Ctx } from './scenarios/scenarios'

export interface RunState {
  results: StepResult[]
  running: boolean
  outcome?: Outcome
  sku?: string
  /** Onay bekleyen elle yapılacak adımın sırası. */
  waitingIndex?: number
  startedAt?: number
  finishedAt?: number
}

interface Control {
  controller: AbortController
  resolve?: (ok: boolean) => void
}

/** Senaryoları çalıştırır ve her adımın durumunu ekrana taşır. Aynı anda birden fazla senaryo çalışabilir. */
export function useScenarioRunner(driver: Driver) {
  const [states, setStates] = useState<Record<string, RunState>>({})
  const controls = useRef(new Map<string, Control>())

  const update = useCallback((id: string, change: (state: RunState) => RunState) => {
    setStates((prev) => ({ ...prev, [id]: change(prev[id] ?? { results: [], running: false }) }))
  }, [])

  const run = useCallback(async (scenario: Scenario<Ctx>): Promise<Outcome> => {
    const control: Control = { controller: new AbortController() }
    controls.current.set(scenario.id, control)
    const ctx = newContext(driver)
    const signal = control.controller.signal
    ctx.timing = { ...ctx.timing, signal }
    ctx.parkTiming = { ...ctx.parkTiming, signal }

    update(scenario.id, () => ({
      results: scenario.steps.map(() => ({ status: 'pending' })),
      running: true,
      sku: ctx.sku,
      startedAt: Date.now(),
    }))
    const outcome = await runScenario(scenario, ctx, {
      signal,
      onStep: (index, result) => update(scenario.id, (state) => {
        const results = [...state.results]
        results[index] = result
        const waitingIndex = result.status === 'waiting' ? index
          : state.waitingIndex === index ? undefined : state.waitingIndex
        return { ...state, results, waitingIndex }
      }),
      confirm: () => new Promise<boolean>((resolve) => (control.resolve = resolve)),
    })
    update(scenario.id, (state) => ({ ...state, outcome, running: false, waitingIndex: undefined, finishedAt: Date.now() }))
    controls.current.delete(scenario.id)
    return outcome
  }, [driver, update])

  const confirm = useCallback((id: string, ok: boolean) => {
    const control = controls.current.get(id)
    control?.resolve?.(ok)
    if (control) control.resolve = undefined
  }, [])

  const stop = useCallback((id: string) => {
    const control = controls.current.get(id)
    control?.controller.abort()
    control?.resolve?.(false)
  }, [])

  return { states, run, confirm, stop }
}

/** {@code load}'u belli aralıklarla çağırır; son veriyi ve son hatayı tutar. */
export function usePolling<T>(load: () => Promise<T>, intervalMs: number) {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<string>()
  const loadRef = useRef(load)
  useEffect(() => {
    loadRef.current = load // bir sonraki turda güncel load (ör. değişen SKU) çağrılsın
  })

  const refresh = useCallback(async () => {
    try {
      setData(await loadRef.current())
      setError(undefined)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void refresh()
    const timer = setInterval(() => void refresh(), intervalMs)
    return () => clearInterval(timer)
  }, [refresh, intervalMs])

  return { data, error, refresh }
}
