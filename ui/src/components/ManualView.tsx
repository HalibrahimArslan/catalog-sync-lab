import { useState, type FormEvent } from 'react'
import { backoffice, storefront } from '../api/http-driver'
import { usePolling } from '../hooks'
import { cx, input, table } from './styles'
import { Badge, Button, Card, Eyebrow } from './ui'

/**
 * Senaryo dışında elle denemek için: Backoffice'e komut gönder, vitrinin ne zaman ve nasıl
 * güncellendiğini yan tarafta izle.
 */
export function ManualView() {
  const [sku, setSku] = useState('SKU-1')
  const [name, setName] = useState('Kupa')
  const [description, setDescription] = useState('Seramik kupa')
  const [price, setPrice] = useState('100')
  const [currency, setCurrency] = useState('TRY')
  const [quantity, setQuantity] = useState('8')
  const [log, setLog] = useState<{ at: string; text: string; ok: boolean }[]>([])
  const catalog = usePolling(storefront.visible, 2_000)
  const item = usePolling(() => storefront.item(sku), 2_000)

  async function act(label: string, action: () => Promise<void>) {
    const at = new Date().toLocaleTimeString('tr-TR')
    try {
      await action()
      setLog((l) => [{ at, text: `${label}: gönderildi`, ok: true }, ...l].slice(0, 20))
    } catch (e) {
      setLog((l) => [{ at, text: `${label}: ${e instanceof Error ? e.message : e}`, ok: false }, ...l].slice(0, 20))
    }
  }

  const details = () => ({ name, description: description || null, price: Number(price), currency })
  const submit = (e: FormEvent) => e.preventDefault()

  return (
    <section className="grid gap-6 min-[860px]:grid-cols-2">
      <div>
        <h2 className={heading}>Backoffice</h2>
        <Card className="mb-3">
          <form className="grid gap-3" onSubmit={submit}>
            <label className={label}>SKU <input className={input} value={sku} onChange={(e) => setSku(e.target.value)} /></label>
            <label className={label}>Ad <input className={input} value={name} onChange={(e) => setName(e.target.value)} /></label>
            <label className={label}>Açıklama <input className={input} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
            <div className="grid grid-cols-2 gap-3">
              <label className={label}>Fiyat <input className={input} value={price} inputMode="decimal" onChange={(e) => setPrice(e.target.value)} /></label>
              <label className={label}>Para birimi <input className={input} value={currency} onChange={(e) => setCurrency(e.target.value)} /></label>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="primary" onClick={() => act('Ürün oluştur', () => backoffice.create({ sku, ...details() }))}>
                Oluştur
              </Button>
              <Button onClick={() => act('Ürünü güncelle', () => backoffice.update(sku, details()))}>Güncelle</Button>
              <Button onClick={() => act('Satıştan kaldır', () => backoffice.deactivate(sku))}>Satıştan kaldır</Button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <label className={label}>Stok sayımı <input className={input} value={quantity} inputMode="numeric" onChange={(e) => setQuantity(e.target.value)} /></label>
              <div className="flex items-end">
                <Button onClick={() => act(`Stok ${quantity}`, () => backoffice.countStock(sku, Number(quantity)))}>
                  Stoğu kaydet
                </Button>
              </div>
            </div>
          </form>
        </Card>
        <Eyebrow className="mt-5">Gönderilenler</Eyebrow>
        <ul className="grid gap-1 text-sm">
          {log.length === 0 && <li className="text-muted">Henüz bir şey gönderilmedi.</li>}
          {log.map((entry, i) => (
            <li key={i} className={entry.ok ? 'text-ok' : 'text-bad'}>
              <time className="mr-2 text-muted tabular-nums">{entry.at}</time> {entry.text}
            </li>
          ))}
        </ul>
      </div>

      <div>
        <h2 className={heading}>Vitrin</h2>
        <Card className="mb-3">
          <h3 className="font-semibold"><code>{sku}</code> vitrinde</h3>
          {item.error && <p className="mt-2 text-bad">{item.error}</p>}
          {!item.error && item.data === null && <p className="mt-2 text-muted">Bu SKU vitrinde yok (henüz gelmedi ya da hiç oluşturulmadı).</p>}
          {item.data && (
            <dl className="mt-3 grid grid-cols-[max-content_1fr] gap-x-[18px] gap-y-1.5 [&_dt]:text-muted">
              <dt>Ad</dt><dd>{item.data.name}</dd>
              <dt>Açıklama</dt><dd>{item.data.description ?? '—'}</dd>
              <dt>Fiyat</dt><dd>{item.data.price} {item.data.currency}</dd>
              <dt>Stok</dt><dd>{item.data.stock}{item.data.outOfStock && <Badge tone="warn" className="ml-1.5 font-medium">Tükendi</Badge>}</dd>
              <dt>Durum</dt><dd>{item.data.active ? 'Satışta' : <Badge tone="warn" className="font-medium">Satıştan kaldırıldı</Badge>}</dd>
            </dl>
          )}
        </Card>
        <Eyebrow className="mt-5">Sitede görünen ürünler</Eyebrow>
        {catalog.error && <p className="mb-2 text-bad">{catalog.error}</p>}
        <div className={table.wrap}>
          <table className={table.table}>
            <thead><tr><th className={table.th}>SKU</th><th className={table.th}>Ad</th><th className={table.th}>Fiyat</th><th className={table.th}>Stok</th></tr></thead>
            <tbody>
              {catalog.data?.length === 0 && <tr><td colSpan={4} className={cx(table.td, 'text-muted')}>Vitrin boş.</td></tr>}
              {catalog.data?.map((v) => (
                <tr key={v.sku} onClick={() => setSku(v.sku)}
                  className={cx('cursor-pointer', v.sku === sku ? 'bg-accent-soft' : 'hover:bg-surface-2')}>
                  <td className={table.td}><code>{v.sku}</code></td><td className={table.td}>{v.name}</td>
                  <td className={table.td}>{v.price} {v.currency}</td>
                  <td className={table.td}>{v.outOfStock ? <Badge tone="warn" className="font-medium">Tükendi</Badge> : v.stock}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

const heading = 'mt-8 mb-3 text-[1.05rem] font-semibold tracking-tight'
const label = 'grid gap-1.5 text-[0.82rem] font-medium text-muted'
