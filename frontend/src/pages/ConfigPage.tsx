import { useState } from 'react';
import { useTheme, THEMES } from '../context/ThemeContext';

type Section = 'ia' | 'temas' | 'cuentas' | 'general';

const SECTIONS: { key: Section; label: string }[] = [
  { key: 'general', label: 'General' },
  { key: 'ia', label: '🤖 IA' },
  { key: 'cuentas', label: '📬 Cuentas' },
  { key: 'temas', label: '🎨 Temas' },
];

export default function ConfigPage() {
  const { theme, setTheme, mode, toggleMode } = useTheme();
  const [section, setSection] = useState<Section>('general');

  return (
    <div className="flex h-full" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* Nav lateral */}
      <div className="w-[200px] shrink-0 p-3 border-r space-y-1"
        style={{ backgroundColor: 'var(--color-bg-sidebar)', borderColor: 'var(--color-border)' }}>
        <h3 className="text-xs font-bold mb-3" style={{ color: 'var(--color-text-secondary)' }}>
          Configuración</h3>
        {SECTIONS.map(s => (
          <button key={s.key} onClick={() => setSection(s.key)}
            className="w-full text-left px-2 py-1.5 text-sm rounded-lg transition-colors"
            style={{
              backgroundColor: section === s.key ? 'var(--color-accent-selected)' : 'transparent',
              color: section === s.key ? '#0F172A' : 'var(--color-text-muted)',
            }}>
            {s.label}
          </button>
        ))}
      </div>

      {/* Contenido */}
      <div className="flex-1 p-4 overflow-auto">
        {section === 'general' && (
          <div className="space-y-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>General</h3>
            <div className="p-3 rounded-lg border-l-4"
              style={{ backgroundColor: 'var(--color-bg-card)',
                       borderColor: 'var(--color-accent-selected)' }}>
              <p className="text-sm" style={{ color: 'var(--color-text)' }}>
                eMail-IA v1.0 · Cliente de correo con IA local</p>
            </div>
          </div>
        )}

        {section === 'ia' && (
          <div className="space-y-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Configuración de IA</h3>
            <label className="text-xs font-bold block"
              style={{ color: 'var(--color-text)' }}>Servidor LM Studio</label>
            <input defaultValue="http://localhost:1234"
              className="w-full max-w-md px-2 py-1.5 text-sm rounded-lg border outline-none"
              style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                       borderColor: 'var(--color-border)' }} />
            <label className="text-xs font-bold block"
              style={{ color: 'var(--color-text)' }}>Modelo</label>
            <input defaultValue="qwen3.5:9b"
              className="w-full max-w-md px-2 py-1.5 text-sm rounded-lg border outline-none"
              style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                       borderColor: 'var(--color-border)' }} />
          </div>
        )}

        {section === 'temas' && (
          <div className="space-y-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Temas</h3>
            <div className="flex items-center gap-2 mb-4">
              <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>☾</span>
              <button onClick={toggleMode}
                className="w-10 h-5 rounded-full relative transition-colors"
                style={{ backgroundColor: mode === 'dark' ? '#475569' : '#64748B' }}>
                <span className="absolute w-4 h-4 rounded-full bg-white top-0.5 transition-transform"
                  style={{ left: mode === 'dark' ? '3px' : '22px' }} />
              </button>
              <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>☼</span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {THEMES.map(t => (
                <button key={t} onClick={() => setTheme(t)}
                  className="px-3 py-2 text-xs rounded-lg border transition-colors text-left"
                  style={{
                    backgroundColor: theme === t ? 'var(--color-accent-selected)' : 'var(--color-bg-card)',
                    borderColor: theme === t ? 'var(--color-accent-selected)' : 'var(--color-border)',
                    color: theme === t ? '#0F172A' : 'var(--color-text)',
                  }}>
                  {t.replace('-', ' ')}
                </button>
              ))}
            </div>
          </div>
        )}

        {section === 'cuentas' && (
          <div className="space-y-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Cuentas de correo</h3>
            <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
              La gestión de cuentas estará disponible en la próxima actualización.
              Usa OAuth2 desde la pantalla de login.</p>
          </div>
        )}
      </div>
    </div>
  );
}
