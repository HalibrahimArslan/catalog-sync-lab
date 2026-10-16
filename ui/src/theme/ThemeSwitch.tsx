import { useEffect, useState } from 'react'
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
    <div className="segmented" role="radiogroup" aria-label="Tema">
      {OPTIONS.map((option) => (
        <button key={option.value} role="radio" aria-checked={theme === option.value}
          className={theme === option.value ? 'active' : ''} title={`Tema: ${option.label}`}
          onClick={() => setTheme(option.value)}>
          <span aria-hidden="true">{option.icon}</span> {option.label}
        </button>
      ))}
    </div>
  )
}
