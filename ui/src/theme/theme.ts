/**
 * Tema tercihi: açık, koyu ya da işletim sisteminin ayarı. Tercih tarayıcıda saklanır; saklanamıyorsa
 * (gizli pencere, engelli depolama) sessizce sistem ayarı kullanılır.
 *
 * Sayfa ilk boyanmadan önce index.html'deki küçük betik aynı anahtarı okur: yanlış tema bir an bile
 * görünmesin diye.
 */
export type Theme = 'system' | 'light' | 'dark'

export const STORAGE_KEY = 'catalog-sync-lab:theme'

type Storage = Pick<globalThis.Storage, 'getItem' | 'setItem'>

export function readTheme(storage: Storage | undefined = safeLocalStorage()): Theme {
  try {
    const value = storage?.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

export function writeTheme(theme: Theme, storage: Storage | undefined = safeLocalStorage()) {
  try {
    storage?.setItem(STORAGE_KEY, theme)
  } catch {
    // saklanamadı: tercih bu oturumla sınırlı kalır
  }
}

/** {@code <html data-theme>} değeri; sistem tercihinde yok (CSS prefers-color-scheme'i izler). */
export function themeAttribute(theme: Theme): 'light' | 'dark' | null {
  return theme === 'system' ? null : theme
}

export function applyTheme(theme: Theme) {
  const value = themeAttribute(theme)
  if (value) document.documentElement.dataset.theme = value
  else delete document.documentElement.dataset.theme
}

function safeLocalStorage(): Storage | undefined {
  try {
    return globalThis.localStorage
  } catch {
    return undefined
  }
}
