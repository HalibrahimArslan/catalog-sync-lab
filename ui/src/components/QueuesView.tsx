import { useState } from 'react'
import { rabbitmq, storefront } from '../api/http-driver'
import { CONSUMER_QUEUES } from '../api/types'
import { usePolling } from '../hooks'
import { cx, table } from './styles'
import { Button, Card, Notice } from './ui'

/** Kuyruk derinlikleri (RabbitMQ yönetim API'si) ve park kuyrukları (Storefront yönetim uç noktaları). */
export function QueuesView() {
  const queues = usePolling(rabbitmq.queues, 3_000)
  const ours = queues.data?.filter((q) => q.name.startsWith('storefront.') || q.name.startsWith('ui.tap.'))

  return (
    <section>
      <h2 className="mt-8 mb-3 text-[1.05rem] font-semibold tracking-tight">Kuyruklar</h2>
      <p className="mb-3 text-muted">
        RabbitMQ sayıları birkaç saniyede bir günceller. <code>.retry</code> bekleme odası, <code>.dlq</code> park kuyruğu;
        {' '}<code>ui.tap.*</code> senaryoların kabloyu dinlemek için açtığı geçici kuyruklar.
      </p>
      {queues.error && <p className="mb-2 text-bad">{queues.error}</p>}
      <div className={cx(table.wrap, 'mb-4')}>
        <table className={table.table}>
          <thead>
            <tr>
              <th className={table.th}>Kuyruk</th><th className={cx(table.th, table.num)}>Mesaj</th>
              <th className={cx(table.th, table.num)}>Dinleyen</th>
            </tr>
          </thead>
          <tbody>
            {ours?.map((q) => (
              <tr key={q.name} className={q.name.endsWith('.dlq') && q.messages > 0 ? 'bg-warn-soft' : 'hover:bg-surface-2'}>
                <td className={table.td}><code>{q.name}</code></td>
                <td className={cx(table.td, table.num)}>{q.messages}</td><td className={cx(table.td, table.num)}>{q.consumers}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
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
    <Card className="mb-3 grid gap-3">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <h3 className="font-semibold">Park kuyruğu: <code>{queue}.dlq</code></h3>
        <div className="flex flex-wrap items-center gap-2">
          <Button disabled={!parked.data?.length} onClick={() => act(async () =>
            `${await storefront.replayAll(queue)} mesaj ana kuyruğa geri gönderildi`)}>
            Hepsini yeniden oynat
          </Button>
        </div>
      </header>
      {notice && <Notice>{notice}</Notice>}
      {parked.error && <p className="text-bad">{parked.error}</p>}
      {parked.data?.length === 0 && <p className="text-muted">Park edilmiş mesaj yok.</p>}
      {!!parked.data?.length && (
        <div className={table.wrap}>
          <table className={table.table}>
            <thead>
              <tr>
                <th className={table.th}>Tip</th><th className={table.th}>Sebep</th>
                <th className={cx(table.th, table.num)}>Deneme</th><th className={table.th}>Gövde</th><th className={table.th} />
              </tr>
            </thead>
            <tbody>
              {parked.data.map((m, i) => (
                <tr key={m.messageId ?? i} className="hover:bg-surface-2">
                  <td className={table.td}>{m.type}</td>
                  <td className={cx(table.td, 'text-[0.84rem] text-bad')}>{m.error}</td>
                  <td className={cx(table.td, table.num)}>{m.attempts}</td>
                  <td className={table.td}>
                    <code className="inline-block max-w-[min(360px,35vw)] truncate align-bottom">{m.body}</code>
                  </td>
                  <td className={table.td}>
                    {m.messageId
                      ? <Button size="sm" onClick={() => act(async () => (await storefront.replay(queue, m.messageId!))
                        ? 'Mesaj ana kuyruğa geri gönderildi; deneme sayacı sıfırdan başlıyor'
                        : 'Mesaj artık park kuyruğunda değil')}>Yeniden oynat</Button>
                      : <span className="text-muted" title="Kimliksiz mesaj tek başına yeniden oynatılamaz">kimliksiz</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
