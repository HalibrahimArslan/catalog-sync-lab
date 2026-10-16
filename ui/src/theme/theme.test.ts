import { describe, expect, it } from 'vitest'
import { readTheme, STORAGE_KEY, themeAttribute, writeTheme } from './theme'

const memory = (initial: Record<string, string> = {}) => {
  const data = { ...initial }
  return {
    getItem: (key: string) => data[key] ?? null,
    setItem: (key: string, value: string) => void (data[key] = value),
    data,
  }
}

describe('tema tercihi', () => {
  it('kayıt yoksa sistemi izler', () => {
    expect(readTheme(memory())).toBe('system')
  })

  it('kayıtlı açık ya da koyu tercihi okur, tanımadığını yok sayar', () => {
    expect(readTheme(memory({ [STORAGE_KEY]: 'dark' }))).toBe('dark')
    expect(readTheme(memory({ [STORAGE_KEY]: 'light' }))).toBe('light')
    expect(readTheme(memory({ [STORAGE_KEY]: 'mor' }))).toBe('system')
  })

  it('depolama erişilemezse (gizli pencere, engelli çerezler) sistemi izler ve hata vermez', () => {
    const broken = { getItem: () => { throw new Error('SecurityError') }, setItem: () => { throw new Error('QuotaExceeded') } }

    expect(readTheme(broken)).toBe('system')
    expect(() => writeTheme('dark', broken)).not.toThrow()
  })

  it('tercihi kaydeder', () => {
    const storage = memory()
    writeTheme('dark', storage)
    expect(storage.data[STORAGE_KEY]).toBe('dark')
  })

  it('sistem tercihinde sayfaya tema yazılmaz; CSS işletim sisteminin ayarını izler', () => {
    expect(themeAttribute('system')).toBeNull()
    expect(themeAttribute('dark')).toBe('dark')
    expect(themeAttribute('light')).toBe('light')
  })
})
