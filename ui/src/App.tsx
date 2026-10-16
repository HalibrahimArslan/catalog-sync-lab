import { useState } from 'react'
import { backoffice, httpDriver, rabbitmq, storefront } from './api/http-driver'
import { ApiView } from './components/ApiView'
import { ManualView } from './components/ManualView'
import { MessagesView } from './components/MessagesView'
import { QueuesView } from './components/QueuesView'
import { ScenariosView } from './components/ScenariosView'
import { usePolling, useScenarioRunner } from './hooks'
import { cx } from './components/styles'
import { ThemeSwitch } from './theme/ThemeSwitch'

const TABS = { scenarios: 'Senaryolar', messages: 'Mesajlar', manual: 'Elle dene', queues: 'Kuyruklar ve park', api: 'API' } as const
type Tab = keyof typeof TABS

export default function App() {
  const [tab, setTab] = useState<Tab>('scenarios')
  // Sekme değişince çalışan senaryolar durmasın: runner App seviyesinde.
  const runner = useScenarioRunner(httpDriver)

  return (
    <div className="mx-auto max-w-[1240px] px-4 pb-16">
      <header className="sticky top-0 z-10 -mx-4 border-b border-line bg-header px-4 pt-3.5 backdrop-blur-md backdrop-saturate-180">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="grid size-9 place-items-center rounded-[10px] bg-linear-135 from-accent to-[#22b8a8] font-bold text-white shadow-[0_4px_12px_rgba(79,70,229,0.3)]"
              aria-hidden="true">✓</span>
            <div>
              <h1 className="text-xl font-bold tracking-tight">Senaryo Konsolu</h1>
              <p className="mt-0.5 text-sm text-muted">Backoffice → RabbitMQ → Storefront, çalışan sisteme karşı</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-4">
            <Health />
            <ThemeSwitch />
          </div>
        </div>
        <nav className="mt-3 flex gap-1 overflow-x-auto [scrollbar-width:none]" role="tablist">
          {(Object.keys(TABS) as Tab[]).map((key) => (
            <button key={key} role="tab" aria-selected={tab === key} onClick={() => setTab(key)}
              className={cx('cursor-pointer whitespace-nowrap border-b-2 px-3.5 py-2.5 text-sm transition-colors',
                tab === key ? 'border-accent font-semibold text-fg' : 'border-transparent font-medium text-muted hover:text-fg')}>
              {TABS[key]}
            </button>
          ))}
        </nav>
      </header>
      <main>
        {tab === 'scenarios' && <ScenariosView runner={runner} />}
        {tab === 'messages' && <MessagesView />}
        {tab === 'manual' && <ManualView />}
        {tab === 'queues' && <QueuesView />}
        {tab === 'api' && <ApiView />}
      </main>
    </div>
  )
}

function Health() {
  const health = usePolling(async () => ({
    Backoffice: await backoffice.isUp(),
    Storefront: await storefront.isUp(),
    RabbitMQ: await rabbitmq.isUp(),
  }), 3_000)
  return (
    <ul className="flex gap-1.5 text-[0.82rem]" aria-label="Servisler">
      {Object.entries(health.data ?? {}).map(([name, up]) => (
        <li key={name} title={up ? 'ayakta' : 'cevap vermiyor'}
          className={cx('flex items-center gap-1.5 rounded-full border bg-surface px-2.5 py-1', up ? 'border-line' : 'border-bad-soft text-bad')}>
          <span className={cx('size-2 rounded-full ring-3', up ? 'bg-ok ring-ok-soft' : 'bg-bad ring-bad-soft')} /> {name}
        </li>
      ))}
    </ul>
  )
}
