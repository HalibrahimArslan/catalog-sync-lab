import { useState } from 'react'
import { backoffice, httpDriver, rabbitmq, storefront } from './api/http-driver'
import { ManualView } from './components/ManualView'
import { MessagesView } from './components/MessagesView'
import { QueuesView } from './components/QueuesView'
import { ScenariosView } from './components/ScenariosView'
import { usePolling, useScenarioRunner } from './hooks'
import { ThemeSwitch } from './theme/ThemeSwitch'

const TABS = { scenarios: 'Senaryolar', messages: 'Mesajlar', manual: 'Elle dene', queues: 'Kuyruklar ve park' } as const
type Tab = keyof typeof TABS

export default function App() {
  const [tab, setTab] = useState<Tab>('scenarios')
  // Sekme değişince çalışan senaryolar durmasın: runner App seviyesinde.
  const runner = useScenarioRunner(httpDriver)

  return (
    <>
      <header className="top">
        <div className="top-inner">
          <div className="brand">
            <span className="logo" aria-hidden="true">✓</span>
            <div>
              <h1>Senaryo Konsolu</h1>
              <p className="muted">Backoffice → RabbitMQ → Storefront, çalışan sisteme karşı</p>
            </div>
          </div>
          <div className="top-right">
            <Health />
            <ThemeSwitch />
          </div>
        </div>
        <nav className="tabs" role="tablist">
          {(Object.keys(TABS) as Tab[]).map((key) => (
            <button key={key} role="tab" aria-selected={tab === key} className={tab === key ? 'active' : ''}
              onClick={() => setTab(key)}>
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
      </main>
    </>
  )
}

function Health() {
  const health = usePolling(async () => ({
    Backoffice: await backoffice.isUp(),
    Storefront: await storefront.isUp(),
    RabbitMQ: await rabbitmq.isUp(),
  }), 3_000)
  return (
    <ul className="health" aria-label="Servisler">
      {Object.entries(health.data ?? {}).map(([name, up]) => (
        <li key={name} className={up ? 'up' : 'down'} title={up ? 'ayakta' : 'cevap vermiyor'}>
          <span className="dot" /> {name}
        </li>
      ))}
    </ul>
  )
}
