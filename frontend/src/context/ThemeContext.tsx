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

// Colores base del emailIA (usados como fallback)
const BASE_DARK: Record<string, string> = {
  '--color-bg': '#0F172A', '--color-bg-sidebar': '#0B1220', '--color-bg-card': '#1E293B',
  '--color-bg-elevated': '#334155', '--color-text': '#F1F5F9', '--color-text-secondary': '#94A3B8',
  '--color-text-muted': '#CBD5E1', '--color-accent': '#22D3EE', '--color-accent-hover': '#67E8F9',
  '--color-accent-selected': '#38BDF8', '--color-border': '#334155',
};
const BASE_LIGHT: Record<string, string> = {
  '--color-bg': '#E8ECEF', '--color-bg-sidebar': '#d5d9de', '--color-bg-card': '#ffffff',
  '--color-bg-elevated': '#f0f2f4', '--color-text': '#1E3A5F', '--color-text-secondary': '#4b5563',
  '--color-text-muted': '#6b7280', '--color-accent': '#0891B2', '--color-accent-hover': '#06B6D4',
  '--color-accent-selected': '#38BDF8', '--color-border': '#B8BEC4',
};

// Mapa de temas con sus acentos específicos (hereda el resto de BASE)
const THEME_ACCENTS: Record<string, { dark: Record<string, string>; light: Record<string, string> }> = {
  emailIA: { dark: {}, light: {} },
  'amber-slate':    { dark: { '--color-accent': '#F59E0B', '--color-accent-hover': '#FBBF24', '--color-accent-selected': '#FCD34D' }, light: { '--color-accent': '#D97706', '--color-accent-hover': '#F59E0B', '--color-accent-selected': '#FBBF24' } },
  'arctic-blue':    { dark: { '--color-accent': '#60A5FA', '--color-accent-hover': '#93BBFD', '--color-accent-selected': '#BFDBFE' }, light: { '--color-accent': '#3B82F6', '--color-accent-hover': '#60A5FA', '--color-accent-selected': '#93BBFD' } },
  'deep-navy-lime': { dark: { '--color-accent': '#84CC16', '--color-accent-hover': '#A3E635', '--color-accent-selected': '#BEF264' }, light: { '--color-accent': '#65A30D', '--color-accent-hover': '#84CC16', '--color-accent-selected': '#A3E635' } },
  'emerald-paper':  { dark: { '--color-accent': '#34D399', '--color-accent-hover': '#6EE7B7', '--color-accent-selected': '#A7F3D0' }, light: { '--color-accent': '#059669', '--color-accent-hover': '#34D399', '--color-accent-selected': '#6EE7B7' } },
  'forest-stone':   { dark: { '--color-accent': '#A8A29E', '--color-accent-hover': '#C0BAB6', '--color-accent-selected': '#D6D3D1' }, light: { '--color-accent': '#78716C', '--color-accent-hover': '#A8A29E', '--color-accent-selected': '#C0BAB6' } },
  'graphite-mint':  { dark: { '--color-accent': '#5EEAD4', '--color-accent-hover': '#99F6E4', '--color-accent-selected': '#CCFBF1' }, light: { '--color-accent': '#0D9488', '--color-accent-hover': '#5EEAD4', '--color-accent-selected': '#99F6E4' } },
  'indigo-mist':    { dark: { '--color-accent': '#818CF8', '--color-accent-hover': '#A5B4FC', '--color-accent-selected': '#C7D2FE' }, light: { '--color-accent': '#6366F1', '--color-accent-hover': '#818CF8', '--color-accent-selected': '#A5B4FC' } },
  'midnight-rose':  { dark: { '--color-accent': '#FB7185', '--color-accent-hover': '#FDA4AF', '--color-accent-selected': '#FECDD3' }, light: { '--color-accent': '#E11D48', '--color-accent-hover': '#FB7185', '--color-accent-selected': '#FDA4AF' } },
  'neutral-cyan':   { dark: { '--color-accent': '#22D3EE', '--color-accent-hover': '#67E8F9', '--color-accent-selected': '#A5F3FC' }, light: { '--color-accent': '#0891B2', '--color-accent-hover': '#22D3EE', '--color-accent-selected': '#67E8F9' } },
  'ocean-teal':     { dark: { '--color-accent': '#14B8A6', '--color-accent-hover': '#5EEAD4', '--color-accent-selected': '#99F6E4' }, light: { '--color-accent': '#0F766E', '--color-accent-hover': '#14B8A6', '--color-accent-selected': '#5EEAD4' } },
  'ruby-ink':       { dark: { '--color-accent': '#F87171', '--color-accent-hover': '#FCA5A5', '--color-accent-selected': '#FECACA' }, light: { '--color-accent': '#DC2626', '--color-accent-hover': '#F87171', '--color-accent-selected': '#FCA5A5' } },
  'sand-coral':     { dark: { '--color-accent': '#FDA4AF', '--color-accent-hover': '#FECDD3', '--color-accent-selected': '#FFE4E6' }, light: { '--color-accent': '#E11D48', '--color-accent-hover': '#FDA4AF', '--color-accent-selected': '#FECDD3' } },
  'slate-gold':     { dark: { '--color-accent': '#EAB308', '--color-accent-hover': '#FACC15', '--color-accent-selected': '#FDE047' }, light: { '--color-accent': '#CA8A04', '--color-accent-hover': '#EAB308', '--color-accent-selected': '#FACC15' } },
  'violet-smoke':   { dark: { '--color-accent': '#A78BFA', '--color-accent-hover': '#C4B5FD', '--color-accent-selected': '#DDD6FE' }, light: { '--color-accent': '#7C3AED', '--color-accent-hover': '#A78BFA', '--color-accent-selected': '#C4B5FD' } },
  'warm-beige-teal': { dark: { '--color-accent': '#2DD4BF', '--color-accent-hover': '#5EEAD4', '--color-accent-selected': '#99F6E4', '--color-bg': '#1C1917', '--color-bg-sidebar': '#141210', '--color-bg-card': '#292524', '--color-bg-elevated': '#44403C', '--color-border': '#44403C' }, light: { '--color-accent': '#0D9488', '--color-accent-hover': '#2DD4BF', '--color-accent-selected': '#5EEAD4', '--color-bg': '#FAF5F0', '--color-bg-sidebar': '#EFE6DC', '--color-bg-card': '#FFFFFF', '--color-bg-elevated': '#F5F0EB', '--color-border': '#D6CCC2' } },
};

function getThemeColors(theme: Theme, mode: Mode): Record<string, string> {
  const base = mode === 'dark' ? { ...BASE_DARK } : { ...BASE_LIGHT };
  const acentos = THEME_ACCENTS[theme];
  if (acentos) {
    Object.assign(base, acentos[mode]);
  }
  return base;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() =>
    (localStorage.getItem('emailai_theme') as Theme) || 'emailIA');
  const [mode, setMode] = useState<Mode>(() =>
    (localStorage.getItem('emailai_mode') as Mode) || 'dark');

  useEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-theme', theme);
    root.setAttribute('data-mode', mode);
    localStorage.setItem('emailai_theme', theme);
    localStorage.setItem('emailai_mode', mode);

    // Aplicar colores directamente al root para que funcionen al instante
    const colors = getThemeColors(theme, mode);
    for (const [key, value] of Object.entries(colors)) {
      root.style.setProperty(key, value);
    }
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
