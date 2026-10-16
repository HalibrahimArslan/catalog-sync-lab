import { useState, type FormEvent } from 'react'
import { backoffice, storefront } from '../api/http-driver'
import { usePolling } from '../hooks'

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
    <section className="two-columns">
      <div>
        <h2>Backoffice</h2>
        <form className="card form" onSubmit={submit}>
          <label>SKU <input value={sku} onChange={(e) => setSku(e.target.value)} /></label>
          <label>Ad <input value={name} onChange={(e) => setName(e.target.value)} /></label>
          <label>Açıklama <input value={description} onChange={(e) => setDescription(e.target.value)} /></label>
          <div className="row">
            <label>Fiyat <input value={price} inputMode="decimal" onChange={(e) => setPrice(e.target.value)} /></label>
            <label>Para birimi <input value={currency} onChange={(e) => setCurrency(e.target.value)} /></label>
          </div>
          <div className="actions">
            <button className="primary" onClick={() => act('Ürün oluştur', () => backoffice.create({ sku, ...details() }))}>
              Oluştur
            </button>
            <button onClick={() => act('Ürünü güncelle', () => backoffice.update(sku, details()))}>Güncelle</button>
            <button onClick={() => act('Satıştan kaldır', () => backoffice.deactivate(sku))}>Satıştan kaldır</button>
          </div>
          <div className="row">
            <label>Stok sayımı <input value={quantity} inputMode="numeric" onChange={(e) => setQuantity(e.target.value)} /></label>
            <div className="actions end">
              <button onClick={() => act(`Stok ${quantity}`, () => backoffice.countStock(sku, Number(quantity)))}>
                Stoğu kaydet
              </button>
            </div>
          </div>
        </form>
        <h3>Gönderilenler</h3>
        <ul className="log">
          {log.length === 0 && <li className="muted">Henüz bir şey gönderilmedi.</li>}
          {log.map((entry, i) => (
            <li key={i} className={entry.ok ? 'ok' : 'bad'}><time>{entry.at}</time> {entry.text}</li>
          ))}
        </ul>
      </div>

      <div>
        <h2>Vitrin</h2>
        <div className="card">
          <h3><code>{sku}</code> vitrinde</h3>
          {item.error && <p className="bad">{item.error}</p>}
          {!item.error && item.data === null && <p className="muted">Bu SKU vitrinde yok (henüz gelmedi ya da hiç oluşturulmadı).</p>}
          {item.data && (
            <dl className="facts">
              <dt>Ad</dt><dd>{item.data.name}</dd>
              <dt>Açıklama</dt><dd>{item.data.description ?? '—'}</dd>
              <dt>Fiyat</dt><dd>{item.data.price} {item.data.currency}</dd>
              <dt>Stok</dt><dd>{item.data.stock}{item.data.outOfStock && <span className="tag">Tükendi</span>}</dd>
              <dt>Durum</dt><dd>{item.data.active ? 'Satışta' : <span className="tag">Satıştan kaldırıldı</span>}</dd>
            </dl>
          )}
        </div>
        <h3>Sitede görünen ürünler</h3>
        {catalog.error && <p className="bad">{catalog.error}</p>}
        <table>
          <thead><tr><th>SKU</th><th>Ad</th><th>Fiyat</th><th>Stok</th></tr></thead>
          <tbody>
            {catalog.data?.length === 0 && <tr><td colSpan={4} className="muted">Vitrin boş.</td></tr>}
            {catalog.data?.map((v) => (
              <tr key={v.sku} onClick={() => setSku(v.sku)} className={v.sku === sku ? 'selected' : ''}>
                <td><code>{v.sku}</code></td><td>{v.name}</td><td>{v.price} {v.currency}</td>
                <td>{v.outOfStock ? <span className="tag">Tükendi</span> : v.stock}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
