import { useState } from 'react'
import { rabbitmq, storefront } from '../api/http-driver'
import { CONSUMER_QUEUES } from '../api/types'
import { usePolling } from '../hooks'

/** Kuyruk derinlikleri (RabbitMQ yönetim API'si) ve park kuyrukları (Storefront yönetim uç noktaları). */
export function QueuesView() {
  const queues = usePolling(rabbitmq.queues, 3_000)
  const ours = queues.data?.filter((q) => q.name.startsWith('storefront.') || q.name.startsWith('ui.tap.'))

  return (
    <section>
      <h2>Kuyruklar</h2>
      <p className="muted">
        RabbitMQ sayıları birkaç saniyede bir günceller. <code>.retry</code> bekleme odası, <code>.dlq</code> park kuyruğu;
        {' '}<code>ui.tap.*</code> senaryoların kabloyu dinlemek için açtığı geçici kuyruklar.
      </p>
      {queues.error && <p className="bad">{queues.error}</p>}
      <table>
        <thead><tr><th>Kuyruk</th><th className="num">Mesaj</th><th className="num">Dinleyen</th></tr></thead>
        <tbody>
          {ours?.map((q) => (
            <tr key={q.name} className={q.name.endsWith('.dlq') && q.messages > 0 ? 'attention' : ''}>
              <td><code>{q.name}</code></td><td className="num">{q.messages}</td><td className="num">{q.consumers}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {CONSUMER_QUEUES.map((queue) => <ParkingQueue key={queue} queue={queue} onChange={queues.refresh} />)}
    </section>
  )
}

function ParkingQueue({ queue, onChange }: { queue: string; onChange: () => void }) {
  const parked = usePolling(() => storefront.parked(queue, 200), 3_000)
  const [notice, setNotice] = useState<string>()

  async function act(action: () => Promise<string>) {
    try {
      setNotice(await action())
    } catch (e) {
      setNotice(e instanceof Error ? e.message : String(e))
    }
    await parked.refresh()
    onChange()
  }

  return (
    <div className="card">
      <header>
        <h3>Park kuyruğu: <code>{queue}.dlq</code></h3>
        <div className="actions">
          <button disabled={!parked.data?.length} onClick={() => act(async () =>
            `${await storefront.replayAll(queue)} mesaj ana kuyruğa geri gönderildi`)}>
            Hepsini yeniden oynat
          </button>
        </div>
      </header>
      {notice && <p className="notice">{notice}</p>}
      {parked.error && <p className="bad">{parked.error}</p>}
      {parked.data?.length === 0 && <p className="muted">Park edilmiş mesaj yok.</p>}
      {!!parked.data?.length && (
        <table>
          <thead><tr><th>Tip</th><th>Sebep</th><th className="num">Deneme</th><th>Gövde</th><th /></tr></thead>
          <tbody>
            {parked.data.map((m, i) => (
              <tr key={m.messageId ?? i}>
                <td>{m.type}</td>
                <td className="error">{m.error}</td>
                <td className="num">{m.attempts}</td>
                <td><code className="body">{m.body}</code></td>
                <td>
                  {m.messageId
                    ? <button onClick={() => act(async () => (await storefront.replay(queue, m.messageId!))
                        ? 'Mesaj ana kuyruğa geri gönderildi; deneme sayacı sıfırdan başlıyor'
                        : 'Mesaj artık park kuyruğunda değil')}>Yeniden oynat</button>
                    : <span className="muted" title="Kimliksiz mesaj tek başına yeniden oynatılamaz">kimliksiz</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
