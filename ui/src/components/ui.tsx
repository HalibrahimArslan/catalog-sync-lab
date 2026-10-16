import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react'
import { cx, SOFT, type Tone } from './styles'

/** Konsolun küçük bileşen seti; ortak sınıflar styles.ts'de. */
export function Badge({ tone, dot, className, children }: { tone: Tone; dot?: boolean; className?: string; children: ReactNode }) {
  return (
    <span className={cx('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold', SOFT[tone], className)}>
      {dot && <span className="size-1.5 rounded-full bg-current" />}
      {children}
    </span>
  )
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'default' | 'primary' | 'ghost'; size?: 'sm' | 'md' }

export function Button({ variant = 'default', size = 'md', className, ...props }: ButtonProps) {
  return (
    <button
      className={cx(
        'inline-flex cursor-pointer items-center justify-center gap-1.5 rounded-lg font-medium transition',
        'active:translate-y-px disabled:pointer-events-none disabled:opacity-50',
        'focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/30',
        size === 'md' ? 'px-3.5 py-1.5 text-sm' : 'px-2.5 py-1 text-xs',
        variant === 'primary' && 'bg-accent text-on-accent shadow-sm hover:bg-accent-hover',
        variant === 'default' && 'border border-line-strong bg-surface text-fg hover:bg-surface-2',
        variant === 'ghost' && 'text-muted hover:text-fg',
        className,
      )}
      {...props}
    />
  )
}

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx('rounded-2xl border border-line bg-surface p-4 shadow-card', className)} {...props} />
}

export function Notice({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  return <p className={cx('rounded-lg px-3 py-2 text-sm', SOFT[tone])}>{children}</p>
}

/** Bölüm başlığı: küçük, büyük harf, soluk. */
export function Eyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return <h4 className={cx('mb-2 text-[0.72rem] font-semibold uppercase tracking-wider text-muted', className)}>{children}</h4>
}
