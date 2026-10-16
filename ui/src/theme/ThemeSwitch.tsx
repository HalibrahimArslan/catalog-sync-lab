import { useEffect, useState } from 'react'
import { cx } from '../components/styles'
import { applyTheme, readTheme, writeTheme, type Theme } from './theme'

const OPTIONS: { value: Theme; label: string; icon: string }[] = [
  { value: 'light', label: 'Açık', icon: '☀' },
  { value: 'dark', label: 'Koyu', icon: '☾' },
  { value: 'system', label: 'Sistem', icon: '◐' },
]

/** Açık / koyu / sistem seçimi; tercih tarayıcıda hatırlanır. */
export function ThemeSwitch() {
  const [theme, setTheme] = useState<Theme>(readTheme)

  useEffect(() => {
    applyTheme(theme)
    writeTheme(theme)
  }, [theme])

  return (
    <div className="inline-flex gap-0.5 rounded-xl border border-line bg-surface-2 p-[3px]" role="radiogroup" aria-label="Tema">
      {OPTIONS.map((option) => (
        <button key={option.value} role="radio" aria-checked={theme === option.value}
          className={cx('cursor-pointer rounded-[9px] px-2.5 py-1 text-[0.82rem] font-medium transition',
            theme === option.value ? 'bg-surface text-fg shadow-card' : 'text-muted hover:text-fg')}
          title={`Tema: ${option.label}`}
          onClick={() => setTheme(option.value)}>
          <span aria-hidden="true">{option.icon}</span> {option.label}
        </button>
      ))}
    </div>
  )
}
