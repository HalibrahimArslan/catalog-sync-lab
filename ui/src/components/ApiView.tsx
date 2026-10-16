import { lazy, Suspense, useEffect, useState } from 'react'
import { backoffice, storefront } from '../api/http-driver'
import type { OpenApiDoc } from '../api/types'
import { cx } from './styles'
import { Card, Notice } from './ui'

const SwaggerPanel = lazy(() => import('./SwaggerPanel'))

const SERVICES = {
  backoffice: { label: 'Backoffice', load: backoffice.apiDocs },
  storefront: { label: 'Storefront', load: storefront.apiDocs },
} as const
type Service = keyof typeof SERVICES

/**
 * Uç noktaları OpenAPI dokümanından (springdoc, {@code /v3/api-docs}) listeler; uç noktalar oradan denenir.
 * Dokümandaki sunucu adresi Vite proxy yoluyla ({@code /svc/<servis>}) değiştirilir: istekler aynı
 * origin'den gider, yönetim uç noktalarının kimlik bilgisini proxy ekler (şifre tarayıcıya gelmez).
 */
export function ApiView() {
  const [service, setService] = useState<Service>('backoffice')
  const [docs, setDocs] = useState<{ service: Service; spec?: OpenApiDoc; error?: string }>()

  useEffect(() => {
    let current = true
    SERVICES[service].load()
      .then((spec) => current && setDocs({ service, spec: { ...spec, servers: [{ url: `/svc/${service}`, description: 'Vite proxy' }] } }))
      .catch((e) => current && setDocs({ service, error: e instanceof Error ? e.message : String(e) }))
    return () => {
      current = false
    }
  }, [service])

  const shown = docs?.service === service ? docs : undefined

  return (
    <section>
      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="inline-flex gap-0.5 rounded-xl border border-line bg-surface-2 p-[3px]" role="radiogroup" aria-label="Servis">
          {(Object.keys(SERVICES) as Service[]).map((key) => (
            <button key={key} role="radio" aria-checked={service === key} onClick={() => setService(key)}
              className={cx('cursor-pointer rounded-[9px] px-3 py-1 text-sm font-medium transition',
                service === key ? 'bg-surface text-fg shadow-card' : 'text-muted hover:text-fg')}>
              {SERVICES[key].label}
            </button>
          ))}
        </div>
        <a className="text-sm text-accent hover:underline" href={`/svc/${service}/v3/api-docs`} target="_blank" rel="noreferrer">
          OpenAPI JSON
        </a>
        <span className="text-sm text-muted">
          İstekler proxy üzerinden gider; <code>/admin/**</code> için kimlik bilgisini proxy ekler.
        </span>
      </div>

      <div className="mt-4">
        {!shown && <Card className="text-muted">Doküman yükleniyor…</Card>}
        {shown?.error && <Notice tone="bad">OpenAPI dokümanı alınamadı: {shown.error}</Notice>}
        {shown?.spec && (
          <Suspense fallback={<Card className="text-muted">Swagger UI yükleniyor…</Card>}>
            <SwaggerPanel spec={shown.spec} />
          </Suspense>
        )}
      </div>
    </section>
  )
}
