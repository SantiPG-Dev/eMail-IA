import { useState, useEffect } from 'react';
import { useTheme, THEMES } from '../context/ThemeContext';
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

export default function ConfigPage() {
  const { theme, setTheme, mode, toggleMode } = useTheme();
  const [section, setSection] = useState<Section>('general');

  const [cuentas, setCuentas] = useState<any[]>([]);
  const [status, setStatus] = useState('');

  useEffect(() => { cargarCuentas(); }, []);

  const cargarCuentas = () => {
    cuentaApi.list().then(r => setCuentas(r.data)).catch(() => {});
  };

  const handleSave = async (data: AccountFormData) => {
    const p = PROVIDERS_LOOKUP[data.proveedor] || PROVIDERS_LOOKUP.other;
    const conn = data.tipoConexion === 'IMAP' ? p.imap : p.pop3;

    await cuentaApi.create({
      nombre: data.nombre,
      email: data.email,
      servidor: conn.host,
      puerto: conn.port,
      usuario: data.email,
      password: data.password,
      tipoConexion: data.tipoConexion,
      esDefault: cuentas.length === 0,
      oauthProvider: null,
      oauthAccessToken: null,
      oauthRefreshToken: null,
      oauthExpiresAt: null,
    });
    cargarCuentas();
  };

  const eliminarCuenta = async (id: number) => {
    try { await cuentaApi.delete(id); cargarCuentas(); }
    catch { setStatus('Error al eliminar'); }
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

        {/* ── Cuentas (CORREGIDA) ── */}
        {section === 'cuentas' && (
          <div className="space-y-6">
            <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Cuentas de correo</h3>

            {/* Cuentas existentes */}
            {cuentas.length > 0 && (
              <div className="p-3 rounded-lg border-l-4"
                style={{ backgroundColor: 'var(--color-bg-card)', borderColor: 'var(--color-accent-selected)' }}>
                <p className="text-xs font-bold mb-2" style={{ color: 'var(--color-text)' }}>
                  Cuentas configuradas</p>
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

            {/* Añadir cuenta con formulario compartido */}
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
                  }}>{t.replace('-', ' ')}</button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
