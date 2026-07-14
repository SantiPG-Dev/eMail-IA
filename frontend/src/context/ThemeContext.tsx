import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';

type Theme = 'emailIA' | 'amber-slate' | 'arctic-blue' | 'deep-navy-lime' | 'emerald-paper'
  | 'forest-stone' | 'graphite-mint' | 'indigo-mist' | 'midnight-rose' | 'neutral-cyan'
  | 'ocean-teal' | 'ruby-ink' | 'sand-coral' | 'slate-gold' | 'violet-smoke' | 'warm-beige-teal';
type Mode = 'dark' | 'light';

interface ThemeContextType {
  theme: Theme;
  mode: Mode;
  setTheme: (t: Theme) => void;
  toggleMode: () => void;
}

const ThemeContext = createContext<ThemeContextType | null>(null);

const THEMES: Theme[] = ['emailIA', 'amber-slate', 'arctic-blue', 'deep-navy-lime',
  'emerald-paper', 'forest-stone', 'graphite-mint', 'indigo-mist', 'midnight-rose',
  'neutral-cyan', 'ocean-teal', 'ruby-ink', 'sand-coral', 'slate-gold',
  'violet-smoke', 'warm-beige-teal'];

export { THEMES };
export type { Theme, Mode };

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() =>
    (localStorage.getItem('emailai_theme') as Theme) || 'emailIA');
  const [mode, setMode] = useState<Mode>(() =>
    (localStorage.getItem('emailai_mode') as Mode) || 'dark');

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    document.documentElement.setAttribute('data-mode', mode);
    localStorage.setItem('emailai_theme', theme);
    localStorage.setItem('emailai_mode', mode);
  }, [theme, mode]);

  const setTheme = useCallback((t: Theme) => setThemeState(t), []);
  const toggleMode = useCallback(() => setMode(m => m === 'dark' ? 'light' : 'dark'), []);

  return (
    <ThemeContext.Provider value={{ theme, mode, setTheme, toggleMode }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
