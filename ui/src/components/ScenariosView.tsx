import { useState } from 'react'
import type { useScenarioRunner, RunState } from '../hooks'
import type { Scenario, StepResult } from '../scenarios/engine'
import { instructions, isManual, scenarios, type Ctx } from '../scenarios/scenarios'
import { cx, TONE_BAR, type Tone } from './styles'
import { Badge, Button, Card } from './ui'

const STORIES: Record<string, string> = {
  US1: 'Yeni ürün vitrine yansır',
  US2: 'Ürün bilgisi güncellemesi yansır',
  US3: 'Stok güncellemesi yansır',
  US4: 'Pasife alınan ürün vitrinden kalkar',
  US5: 'Hatalı mesajlar sistemi kilitlemez',
  US6: 'Park edilen mesaj sorun düzeltilince yeniden işlenir',
  US7: 'Mesaj sözleşmesi kesintisiz değişir',
}

const STATUS_LABEL: Record<StepResult['status'], string> = {
  pending: 'Sırada',
  running: 'Çalışıyor',
  waiting: 'Seni bekliyor',
  passed: 'Geçti',
  failed: 'Kaldı',
  skipped: 'Atlandı',
}

type Runner = ReturnType<typeof useScenarioRunner>

export function ScenariosView({ runner }: { runner: Runner }) {
  const [batchRunning, setBatchRunning] = useState(false)
  const automatic = scenarios.filter((s) => !isManual(s))
  const done = scenarios.filter((s) => runner.states[s.id]?.outcome)
  const passed = done.filter((s) => runner.states[s.id]?.outcome === 'passed').length
  const failed = done.filter((s) => runner.states[s.id]?.outcome === 'failed').length

  async function runAutomatic() {
    setBatchRunning(true)
    try {
      for (const scenario of automatic) await runner.run(scenario)
    } finally {
      setBatchRunning(false)
    }
  }

  return (
    <section>
      <div className="mt-6 flex flex-wrap items-center gap-3">
        <Button variant="primary" onClick={runAutomatic} disabled={batchRunning}>
          {batchRunning ? 'Çalışıyor…' : `Elle adımı olmayan ${automatic.length} senaryoyu sırayla çalıştır`}
        </Button>
        <span className="text-sm text-muted">
          <span className="text-ok">{passed} geçti</span> · <span className="text-bad">{failed} kaldı</span> ·{' '}
          {scenarios.length - done.length} çalışmadı
        </span>
      </div>
      {Object.entries(STORIES).map(([story, title]) => (
        <div key={story} className="mb-2">
          <h2 className="mt-8 mb-3 flex items-center gap-2.5 text-[1.05rem] font-semibold tracking-tight">
            <Badge tone="accent" className="font-bold tracking-wide">{story}</Badge> {title}
          </h2>
          {scenarios.filter((s) => s.story === story).map((scenario) => (
            <ScenarioCard key={scenario.id} scenario={scenario} state={runner.states[scenario.id]} runner={runner}
              disabled={batchRunning} />
          ))}
        </div>
      ))}
    </section>
  )
}

function ScenarioCard({ scenario, state, runner, disabled }: {
  scenario: Scenario<Ctx>; state?: RunState; runner: Runner; disabled: boolean
}) {
  const manual = isManual(scenario)
  const outcome = state?.running ? 'running' : state?.outcome ?? 'idle'
  const waitingStep = state?.waitingIndex === undefined ? undefined : scenario.steps[state.waitingIndex]

  return (
    <Card className="relative mb-3 overflow-hidden">
      <span className={cx('absolute inset-y-0 left-0 w-[3px]', OUTCOME_BAR[outcome])} />
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold">{scenario.title}</h3>
          <p className="mt-1.5 flex flex-wrap gap-3 text-[0.84rem] text-muted">
            {manual && <Badge tone="warn" className="font-medium">elle adım var</Badge>}
            {scenario.description && <span>{scenario.description}</span>}
            {state?.sku && <span>SKU <code>{state.sku}</code></span>}
            {state?.finishedAt && state.startedAt && <span>{((state.finishedAt - state.startedAt) / 1000).toFixed(1)} sn</span>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {state?.outcome && <Badge tone={OUTCOME_TONE[state.outcome]}>{OUTCOME_LABEL[state.outcome]}</Badge>}
          {state?.running
            ? <Button onClick={() => runner.stop(scenario.id)}>Durdur</Button>
            : <Button variant="primary" onClick={() => void runner.run(scenario)} disabled={disabled}>
                {state?.outcome ? 'Tekrar çalıştır' : 'Çalıştır'}
              </Button>}
        </div>
      </header>

      {waitingStep?.manual && (
        <div className="mt-3.5 rounded-xl border border-line-strong bg-warn-soft px-4 py-3.5" role="alert">
          <strong className="font-semibold">{waitingStep.title}</strong>
          <p className="mt-1.5 mb-3 font-mono text-[0.84rem] wrap-anywhere">{instructions[waitingStep.manual]}</p>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="primary" onClick={() => runner.confirm(scenario.id, true)}>Yaptım, devam et</Button>
            <Button onClick={() => runner.confirm(scenario.id, false)}>Vazgeç</Button>
          </div>
        </div>
      )}

      <ol className="mt-3.5 space-y-0.5">
        {scenario.steps.map((step, index) => {
          const result = state?.results[index]
          const status = result?.status ?? 'pending'
          return (
            <li key={index} className={cx('grid grid-cols-[22px_1fr_auto] gap-x-2.5 gap-y-0.5 rounded-lg px-2 py-1.5',
              status === 'running' && 'bg-accent-soft', status === 'waiting' && 'bg-warn-soft')}>
              <span className={cx('text-center', STATUS_COLOR[status])} title={STATUS_LABEL[status]}>{ICON[status]}</span>
              <span className={cx(status === 'skipped' && 'text-muted', step.kind === 'manual' && 'italic')}>{step.title}</span>
              {result?.durationMs !== undefined && status !== 'skipped' && (
                <span className="text-[0.82rem] text-muted tabular-nums">{(result.durationMs / 1000).toFixed(1)} sn</span>
              )}
              {result?.error && <span className="col-span-2 col-start-2 text-sm text-bad">{result.error}</span>}
            </li>
          )
        })}
      </ol>
    </Card>
  )
}

const ICON: Record<StepResult['status'], string> = {
  pending: '○',
  running: '◐',
  waiting: '✋',
  passed: '✓',
  failed: '✗',
  skipped: '–',
}

const STATUS_COLOR: Record<StepResult['status'], string> = {
  pending: 'text-muted',
  running: 'text-accent',
  waiting: 'text-muted',
  passed: 'text-ok',
  failed: 'text-bad',
  skipped: 'text-muted',
}

const OUTCOME_LABEL = { passed: 'Geçti', failed: 'Kaldı', cancelled: 'İptal edildi' } as const
const OUTCOME_TONE = { passed: 'ok', failed: 'bad', cancelled: 'muted' } as const satisfies Record<keyof typeof OUTCOME_LABEL, Tone>
/** Kartın sol kenar çizgisi; henüz çalışmamış kartta çizgi yok. */
const OUTCOME_BAR = { idle: 'hidden', running: TONE_BAR.accent, passed: TONE_BAR.ok, failed: TONE_BAR.bad, cancelled: 'bg-muted' }
