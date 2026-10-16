import { useState } from 'react'
import type { useScenarioRunner, RunState } from '../hooks'
import type { Scenario, StepResult } from '../scenarios/engine'
import { instructions, isManual, scenarios, type Ctx } from '../scenarios/scenarios'

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
      <div className="toolbar">
        <button className="primary" onClick={runAutomatic} disabled={batchRunning}>
          {batchRunning ? 'Çalışıyor…' : `Elle adımı olmayan ${automatic.length} senaryoyu sırayla çalıştır`}
        </button>
        <span className="summary">
          <span className="ok">{passed} geçti</span> · <span className="bad">{failed} kaldı</span> ·{' '}
          {scenarios.length - done.length} çalışmadı
        </span>
      </div>
      {Object.entries(STORIES).map(([story, title]) => (
        <div key={story} className="story">
          <h2><span className="chip">{story}</span> {title}</h2>
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
    <article className={`card outcome-${outcome}`}>
      <header>
        <div>
          <h3>{scenario.title}</h3>
          <p className="meta">
            {manual && <span className="tag">elle adım var</span>}
            {scenario.description && <span>{scenario.description}</span>}
            {state?.sku && <span>SKU <code>{state.sku}</code></span>}
            {state?.finishedAt && state.startedAt && <span>{((state.finishedAt - state.startedAt) / 1000).toFixed(1)} sn</span>}
          </p>
        </div>
        <div className="actions">
          {state?.outcome && <span className={`badge ${state.outcome}`}>{OUTCOME_LABEL[state.outcome]}</span>}
          {state?.running
            ? <button onClick={() => runner.stop(scenario.id)}>Durdur</button>
            : <button className="primary" onClick={() => void runner.run(scenario)} disabled={disabled}>
                {state?.outcome ? 'Tekrar çalıştır' : 'Çalıştır'}
              </button>}
        </div>
      </header>

      {waitingStep?.manual && (
        <div className="manual-prompt" role="alert">
          <strong>{waitingStep.title}</strong>
          <p>{instructions[waitingStep.manual]}</p>
          <div className="actions">
            <button className="primary" onClick={() => runner.confirm(scenario.id, true)}>Yaptım, devam et</button>
            <button onClick={() => runner.confirm(scenario.id, false)}>Vazgeç</button>
          </div>
        </div>
      )}

      <ol className="steps">
        {scenario.steps.map((step, index) => {
          const result = state?.results[index]
          const status = result?.status ?? 'pending'
          return (
            <li key={index} className={`step ${status} kind-${step.kind}`}>
              <span className="status" title={STATUS_LABEL[status]}>{ICON[status]}</span>
              <span className="title">{step.title}</span>
              {result?.durationMs !== undefined && status !== 'skipped' && (
                <span className="duration">{(result.durationMs / 1000).toFixed(1)} sn</span>
              )}
              {result?.error && <span className="error">{result.error}</span>}
            </li>
          )
        })}
      </ol>
    </article>
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

const OUTCOME_LABEL = { passed: 'Geçti', failed: 'Kaldı', cancelled: 'İptal edildi' } as const
