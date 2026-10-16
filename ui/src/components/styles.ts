/**
 * Konsolun ortak Tailwind sınıfları (bileşenler: ui.tsx). Renkler temaya bağlı adlarla (bg-surface, text-muted, …;
 * bkz. index.css @theme): bileşenler açık ve koyu temada aynı sınıfları kullanır.
 */
export const cx = (...classes: (string | false | null | undefined)[]) => classes.filter(Boolean).join(' ')

export type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'muted' | 'accent'

/** Yumuşak zemin + koyu yazı olarak ton (rozet, bildirim). */
export const SOFT: Record<Tone, string> = {
  ok: 'bg-ok-soft text-ok',
  warn: 'bg-warn-soft text-warn',
  bad: 'bg-bad-soft text-bad',
  info: 'bg-info-soft text-info',
  muted: 'bg-surface-2 text-muted',
  accent: 'bg-accent-soft text-accent',
}

/** Kenar çizgisi / nokta rengi olarak ton. */
export const TONE_BAR: Record<Tone, string> = {
  ok: 'bg-ok',
  warn: 'bg-warn',
  bad: 'bg-bad',
  info: 'bg-info',
  muted: 'bg-line-strong',
  accent: 'bg-accent',
}

export const input = cx(
  'rounded-lg border border-line-strong bg-surface px-3 py-2 text-sm text-fg placeholder:text-muted',
  'transition focus-visible:border-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/30',
)

/** Tablo parçaları: sarmalayıcı yatay kaydırır, köşeler yuvarlak. */
export const table = {
  wrap: 'overflow-x-auto rounded-xl border border-line bg-surface shadow-card',
  table: 'w-full border-collapse text-sm',
  th: 'bg-surface-2 px-3 py-2 text-left text-[0.7rem] font-semibold uppercase tracking-wider text-muted',
  td: 'border-t border-line px-3 py-2 align-top',
  num: 'text-right tabular-nums',
}
