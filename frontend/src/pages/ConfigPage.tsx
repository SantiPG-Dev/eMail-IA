import { useState, useEffect } from 'react';
import { useTheme } from '../context/ThemeContext';
import { cuentaApi } from '../api/client';
import AccountForm from '../components/AccountForm';
import type { AccountFormData } from '../components/AccountForm';

type Section = 'general' | 'cuentas' | 'ia' | 'temas';
const SECTIONS: { key: Section; label: string }[] = [
  { key: 'general', label: 'General' },
  { key: 'cuentas', label: '📬 Cuentas' },
  { key: 'ia', label: '🤖 IA' },
  { key: 'temas', label: '🎨 Temas' },
];

const PROVIDERS_LOOKUP: Record<string, { imap: { host: string; port: number }; pop3: { host: string; port: number } }> = {
  gmail: { imap: { host: 'imap.gmail.com', port: 993 }, pop3: { host: 'pop.gmail.com', port: 995 } },
  outlook: { imap: { host: 'outlook.office365.com', port: 993 }, pop3: { host: 'outlook.office365.com', port: 995 } },
  yahoo: { imap: { host: 'imap.mail.yahoo.com', port: 993 }, pop3: { host: 'pop.mail.yahoo.com', port: 995 } },
  gmx: { imap: { host: 'imap.gmx.com', port: 993 }, pop3: { host: 'pop.gmx.com', port: 995 } },
  icloud: { imap: { host: 'imap.mail.me.com', port: 993 }, pop3: { host: 'pop.mail.me.com', port: 995 } },
  zoho: { imap: { host: 'imap.zoho.com', port: 993 }, pop3: { host: 'pop.zoho.com', port: 995 } },
  yandex: { imap: { host: 'imap.yandex.com', port: 993 }, pop3: { host: 'pop.yandex.com', port: 995 } },
  other: { imap: { host: '', port: 993 }, pop3: { host: '', port: 995 } },
};

// Opciones de resalte (accent)
const RESALTE_OPCIONES = [
  { key: 'emailIA', label: 'email-IA', color: '#22D3EE' },
  { key: 'deep-navy-lime', label: 'Lime', color: '#84CC16' },
  { key: 'arctic-blue', label: 'Azul', color: '#60A5FA' },
  { key: 'ruby-ink', label: 'Rubí', color: '#F87171' },
  { key: 'violet-smoke', label: 'Violeta', color: '#A78BFA' },
];

// Opciones de fondo
const FONDO_OPCIONES = [
  { key: 'oscuro', label: 'Oscuro', dark: '#0F172A', light: '#E8ECEF' },
  { key: 'gris', label: 'Gris', dark: '#1E293B', light: '#F1F5F9' },
  { key: 'pizarra', label: 'Pizarra', dark: '#1C1917', light: '#FAF5F0' },
  { key: 'azulado', label: 'Azulado', dark: '#0C1929', light: '#EBF4FF' },
  { key: 'verde', label: 'Verde', dark: '#0F1A14', light: '#ECFDF5' },
];

// Fuentes fallback si no se puede escanear el sistema
const FUENTES_FALLBACK = [
  'Segoe UI', 'Roboto', 'Inter', 'Open Sans', 'Lato', 'Noto Sans',
  'Ubuntu', 'Cantarell', 'Fira Sans', 'Source Sans Pro',
];

export default function ConfigPage() {
  const { theme, setTheme, mode, toggleMode } = useTheme();
  const [section, setSection] = useState<Section>('general');

  const [cuentas, setCuentas] = useState<any[]>([]);
  const [status, setStatus] = useState('');

  const [fuente, setFuente] = useState(() => localStorage.getItem('emailai_font') || 'Segoe UI');
  const [tamano, setTamano] = useState(() => parseInt(localStorage.getItem('emailai_fontsize') || '14'));
  const [fuentesSistema, setFuentesSistema] = useState<string[]>([]);
  const [fondoActual, setFondoActual] = useState(() => localStorage.getItem('emailai_bg') || 'oscuro');

  // Escanear fuentes del sistema
  useEffect(() => {
    try {
      if ('queryLocalFonts' in navigator) {
        (navigator as any).queryLocalFonts()
          .then((fonts: any[]) => {
            const names = [...new Set(fonts.map((f: any) => f.family))].sort();
            setFuentesSistema(names.length > 0 ? names : FUENTES_FALLBACK);
          }).catch(() => setFuentesSistema(FUENTES_FALLBACK));
      } else {
        // Fallback: detectar por ancho de texto
        const test = [
          'Segoe UI', 'Roboto', 'Inter', 'Open Sans', 'Lato', 'Noto Sans',
          'Ubuntu', 'Cantarell', 'Fira Sans', 'Source Sans Pro',
          'Arial', 'Helvetica', 'Verdana', 'Georgia', 'Times New Roman',
          'Courier New', 'monospace', 'sans-serif',
        ];
        const disponibles = test.filter(n => {
          try {
            const el = document.createElement('span');
            el.style.fontFamily = n;
            el.style.position = 'absolute';
            el.textContent = 'test';
            document.body.appendChild(el);
            const ancho = el.offsetWidth;
            document.body.removeChild(el);
            // Si la fuente se cargó, el ancho será diferente al fallback
            return true; // simplificado: confiar en el nombre
          } catch { return true; }
        });
        setFuentesSistema(disponibles);
      }
    } catch { setFuentesSistema(FUENTES_FALLBACK); }
  }, []);

  useEffect(() => { cargarCuentas(); }, []);

  const cargarCuentas = () => {
    cuentaApi.list().then(r => setCuentas(r.data)).catch(() => {});
  };

  const handleSave = async (data: AccountFormData) => {
    const p = PROVIDERS_LOOKUP[data.proveedor] || PROVIDERS_LOOKUP.other;
    const conn = data.tipoConexion === 'IMAP' ? p.imap : p.pop3;

    await cuentaApi.create({
      nombre: data.nombre, email: data.email, servidor: conn.host, puerto: conn.port,
      usuario: data.email, password: data.password, tipoConexion: data.tipoConexion,
      esDefault: cuentas.length === 0, oauthProvider: null,
      oauthAccessToken: null, oauthRefreshToken: null, oauthExpiresAt: null,
    });
    cargarCuentas();
  };

  const eliminarCuenta = async (id: number) => {
    try { await cuentaApi.delete(id); cargarCuentas(); }
    catch { setStatus('Error al eliminar'); }
  };

  const aplicarFuente = (f: string, s: number) => {
    setFuente(f); setTamano(s);
    localStorage.setItem('emailai_font', f);
    localStorage.setItem('emailai_fontsize', String(s));
    document.documentElement.style.setProperty('font-family', `${f}, sans-serif`);
  };

  const aplicarFondo = (o: typeof FONDO_OPCIONES[0]) => {
    setFondoActual(o.key);
    localStorage.setItem('emailai_bg', o.key);
    const bg = mode === 'dark' ? o.dark : o.light;
    document.documentElement.style.setProperty('--color-bg', bg);
  };

  return (
    <div className="flex h-full" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* Nav lateral */}
      <div className="w-[200px] shrink-0 p-3 border-r space-y-1"
        style={{ backgroundColor: 'var(--color-bg-sidebar)', borderColor: 'var(--color-border)' }}>
        <h3 className="text-xs font-bold mb-3" style={{ color: 'var(--color-text-secondary)' }}>Configuración</h3>
        {SECTIONS.map(s => (
          <button key={s.key} onClick={() => setSection(s.key)}
            className="w-full text-left px-2 py-1.5 text-sm rounded-lg transition-colors"
            style={{
              backgroundColor: section === s.key ? 'var(--color-accent-selected)' : 'transparent',
              color: section === s.key ? '#0F172A' : 'var(--color-text-muted)',
            }}>{s.label}</button>
        ))}
      </div>

      {/* Contenido */}
      <div className="flex-1 p-4 overflow-auto space-y-6">
        {/* ── General ── */}
        {section === 'general' && (
          <div className="space-y-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>General</h3>
            <div className="p-3 rounded-lg border-l-4"
              style={{ backgroundColor: 'var(--color-bg-card)', borderColor: 'var(--color-accent-selected)' }}>
              <p className="text-sm" style={{ color: 'var(--color-text)' }}>eMail-IA v1.0 · Cliente de correo con IA local</p>
            </div>
          </div>
        )}

        {/* ── Cuentas ── */}
        {section === 'cuentas' && (
          <div className="space-y-6">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Cuentas de correo</h3>
            {cuentas.length > 0 && (
              <div className="p-3 rounded-lg border-l-4"
                style={{ backgroundColor: 'var(--color-bg-card)', borderColor: 'var(--color-accent-selected)' }}>
                <p className="text-xs font-bold mb-2" style={{ color: 'var(--color-text)' }}>Cuentas configuradas</p>
                <div className="space-y-2">
                  {cuentas.map((c: any) => (
                    <div key={c.id} className="flex items-center justify-between px-2 py-1.5 rounded-lg text-sm"
                      style={{ backgroundColor: 'var(--color-bg)' }}>
                      <div>
                        <span style={{ color: 'var(--color-text)' }}>{c.nombre}</span>
                        <span className="ml-2 text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                          {c.email} · {c.servidor}</span>
                      </div>
                      <button onClick={() => eliminarCuenta(c.id)}
                        className="text-xs px-2 py-0.5 rounded"
                        style={{ backgroundColor: '#ef4444', color: 'white' }}>Eliminar</button>
                    </div>
                  ))}
                </div>
              </div>
            )}
            <div className="p-4 rounded-lg" style={{ backgroundColor: 'var(--color-bg-card)' }}>
              <p className="text-sm font-bold mb-3" style={{ color: 'var(--color-text)' }}>Añadir cuenta</p>
              <AccountForm onSave={handleSave} />
            </div>
          </div>
        )}

        {/* ── IA ── */}
        {section === 'ia' && (
          <div className="space-y-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Configuración de IA</h3>
            <div className="p-3 rounded-lg" style={{ backgroundColor: 'var(--color-bg-card)' }}>
              <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>Servidor LM Studio</label>
              <input defaultValue="http://localhost:1234"
                className="w-full max-w-md px-2 py-1.5 text-sm rounded-lg border outline-none"
                style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
              <label className="text-xs font-bold block mt-3 mb-1" style={{ color: 'var(--color-text)' }}>Modelo</label>
              <input defaultValue="qwen3.5:9b"
                className="w-full max-w-md px-2 py-1.5 text-sm rounded-lg border outline-none"
                style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
            </div>
          </div>
        )}

        {/* ── Temas ── */}
        {section === 'temas' && (
          <div className="space-y-5 max-w-lg">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Temas</h3>

            {/* Modo */}
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-wider mb-2"
                style={{ color: 'var(--color-text-secondary)' }}>Modo</p>
              <div className="flex items-center gap-2">
                <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>☾</span>
                <button onClick={toggleMode}
                  className="w-9 h-5 rounded-full relative transition-colors flex items-center"
                  style={{ backgroundColor: mode === 'dark' ? '#475569' : '#94A3B8' }}>
                  <span className="w-3.5 h-3.5 rounded-full bg-white transition-all mx-0.5"
                    style={{ marginLeft: mode === 'dark' ? '0.125rem' : 'auto' }} />
                </button>
                <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>☼</span>
              </div>
            </div>

            {/* Resalte */}
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-wider mb-2"
                style={{ color: 'var(--color-text-secondary)' }}>Resalte</p>
              <div className="flex flex-wrap gap-2">
                {RESALTE_OPCIONES.map(o => (
                  <button key={o.key} onClick={() => setTheme(o.key as any)}
                    className="flex items-center gap-2 px-3 py-2 text-xs rounded-lg border transition-colors"
                    style={{
                      backgroundColor: theme === o.key ? o.color + '22' : 'var(--color-bg-card)',
                      borderColor: theme === o.key ? o.color : 'var(--color-border)',
                      color: 'var(--color-text)',
                      borderLeft: `3px solid ${o.color}`,
                    }}>{o.label}</button>
                ))}
              </div>
            </div>

            {/* Fondo */}
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-wider mb-2"
                style={{ color: 'var(--color-text-secondary)' }}>Fondo</p>
              <div className="flex flex-wrap gap-2">
                {FONDO_OPCIONES.map(o => (
                  <button key={o.key} onClick={() => aplicarFondo(o)}
                    className="w-8 h-8 rounded-full border-2 transition-all duration-200"
                    style={{
                      backgroundColor: mode === 'dark' ? o.dark : o.light,
                      borderColor: fondoActual === o.key ? 'var(--color-accent)' : 'var(--color-border)',
                      boxShadow: fondoActual === o.key ? `0 0 0 2px ${mode === 'dark' ? o.dark : o.light}` : 'none',
                    }} title={o.label} />
                ))}
              </div>
            </div>

            {/* Tipo de letra */}
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-wider mb-2"
                style={{ color: 'var(--color-text-secondary)' }}>Tipo de letra</p>
              <div className="flex gap-2 items-start">
                <select value={fuente} onChange={e => aplicarFuente(e.target.value, tamano)}
                  className="flex-1 px-2 py-1.5 text-xs rounded-lg border outline-none cursor-pointer"
                  style={{
                    backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)',
                    borderColor: 'var(--color-border)', fontFamily: fuente,
                  }}>
                  {fuentesSistema.map(f => (
                    <option key={f} value={f} style={{ fontFamily: f }}>{f}</option>
                  ))}
                </select>
                <select value={tamano} onChange={e => aplicarFuente(fuente, parseInt(e.target.value))}
                  className="w-16 px-2 py-1.5 text-xs rounded-lg border outline-none cursor-pointer"
                  style={{
                    backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)',
                    borderColor: 'var(--color-border)',
                  }}>
                  {[10, 11, 12, 13, 14, 15, 16, 18, 20].map(n => (
                    <option key={n} value={n}>{n}px</option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
