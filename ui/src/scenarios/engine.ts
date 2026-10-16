import type { ManualAction } from './driver'

export type StepKind = 'action' | 'check' | 'manual'

/**
 * Bir senaryo adımı. {@code action} sisteme bir şey yaptırır, {@code check} sonucu doğrular (genelde
 * {@link eventually} ile: vitrin kısa gecikmeyle güncellenir), {@code manual} bir insanı bekler.
 */
export interface Step<C> {
  kind: StepKind
  title: string
  run?: (ctx: C) => Promise<void>
  manual?: ManualAction
}

export interface Scenario<C> {
  id: string
  story: string
  title: string
  description?: string
  steps: Step<C>[]
  /** Sonuç ne olursa olsun en sonda (ör. geçici kuyruğu silmek, Backoffice'i v1'e döndürmek). */
  cleanup?: (ctx: C) => Promise<void>
}

export type StepStatus = 'pending' | 'running' | 'waiting' | 'passed' | 'failed' | 'skipped'

export interface StepResult {
  status: StepStatus
  error?: string
  durationMs?: number
}

export type Outcome = 'passed' | 'failed' | 'cancelled'

export interface RunHooks<C> {
  onStep: (index: number, result: StepResult) => void
  /** Elle yapılan adım: insan "yaptım" derse {@code true}, vazgeçerse {@code false}. */
  confirm: (step: Step<C>, index: number) => Promise<boolean>
  signal?: AbortSignal
}

/** Adımları sırayla çalıştırır; ilk hatada ya da iptalde durur, kalan adımlar "atlandı" olur. */
export async function runScenario<C>(scenario: Scenario<C>, ctx: C, hooks: RunHooks<C>): Promise<Outcome> {
  const { steps } = scenario
  const skipFrom = (from: number) => {
    for (let i = from; i < steps.length; i++) hooks.onStep(i, { status: 'skipped' })
  }
  try {
    for (let i = 0; i < steps.length; i++) {
      if (hooks.signal?.aborted) {
        skipFrom(i)
        return 'cancelled'
      }
      const step = steps[i]
      const started = Date.now()
      if (step.kind === 'manual') {
        hooks.onStep(i, { status: 'waiting' })
        if (!(await hooks.confirm(step, i))) {
          skipFrom(i)
          return 'cancelled'
        }
      } else {
        hooks.onStep(i, { status: 'running' })
        try {
          await step.run?.(ctx)
        } catch (e) {
          if (e instanceof CancelledError || hooks.signal?.aborted) {
            skipFrom(i)
            return 'cancelled'
          }
          hooks.onStep(i, { status: 'failed', error: messageOf(e), durationMs: Date.now() - started })
          skipFrom(i + 1)
          return 'failed'
        }
      }
      hooks.onStep(i, { status: 'passed', durationMs: Date.now() - started })
    }
    return 'passed'
  } finally {
    await scenario.cleanup?.(ctx).catch(() => {})
  }
}

export interface EventuallyOptions {
  timeoutMs: number
  intervalMs: number
  /** Kullanıcı "Durdur" derse beklemeyi süre dolmadan bırakır. */
  signal?: AbortSignal
}

/**
 * {@code probe} hata fırlatmayı bırakana kadar tekrar dener ("vitrin kısa gecikmeyle aynı duruma gelir").
 * Süre dolarsa son hatayı, ne kadar beklendiğiyle birlikte fırlatır.
 */
export async function eventually<T>(probe: () => Promise<T>, { timeoutMs, intervalMs, signal }: EventuallyOptions): Promise<T> {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    if (signal?.aborted) throw new CancelledError()
    try {
      return await probe()
    } catch (e) {
      if (Date.now() + intervalMs > deadline) {
        throw new Error(`${formatSeconds(timeoutMs)} içinde gerçekleşmedi: ${messageOf(e)}`)
      }
      await new Promise((resolve) => setTimeout(resolve, intervalMs))
    }
  }
}

function formatSeconds(ms: number) {
  return ms >= 1_000 ? `${Math.round(ms / 1_000)} sn` : `${ms} ms`
}

export class CancelledError extends Error {
  constructor() {
    super('iptal edildi')
  }
}

export function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}
