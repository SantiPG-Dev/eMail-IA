import { useState } from 'react';

// Formulario de cuenta de correo: soporta IMAP, POP3, OAuth Google y Microsoft.
// Los proveedores preconfigurados auto-rellenan servidor y puerto.
interface AccountFormData {
  nombre: string;
  email: string;
  password: string;
  tipoConexion: 'IMAP' | 'POP3';
  proveedor: string;
}

interface Props {
  onSave: (data: AccountFormData) => Promise<void>;
  onCancel?: () => void;
  status?: string;
  loading?: boolean;
}

interface ProviderInfo {
  label: string;
  imap: { host: string; port: number };
  pop3: { host: string; port: number };
  smtp: { host: string; port: number };
}

const PROVIDERS: Record<string, ProviderInfo> = {
  gmail: {
    label: 'Gmail',
    imap: { host: 'imap.gmail.com', port: 993 },
    pop3: { host: 'pop.gmail.com', port: 995 },
    smtp: { host: 'smtp.gmail.com', port: 465 },
  },
  outlook: {
    label: 'Outlook / Hotmail',
    imap: { host: 'outlook.office365.com', port: 993 },
    pop3: { host: 'outlook.office365.com', port: 995 },
    smtp: { host: 'smtp.office365.com', port: 587 },
  },
  yahoo: {
    label: 'Yahoo Mail',
    imap: { host: 'imap.mail.yahoo.com', port: 993 },
    pop3: { host: 'pop.mail.yahoo.com', port: 995 },
    smtp: { host: 'smtp.mail.yahoo.com', port: 465 },
  },
  gmx: {
    label: 'GMX',
    imap: { host: 'imap.gmx.com', port: 993 },
    pop3: { host: 'pop.gmx.com', port: 995 },
    smtp: { host: 'mail.gmx.com', port: 587 },
  },
  icloud: {
    label: 'iCloud',
    imap: { host: 'imap.mail.me.com', port: 993 },
    pop3: { host: 'pop.mail.me.com', port: 995 },
    smtp: { host: 'smtp.mail.me.com', port: 587 },
  },
  zoho: {
    label: 'Zoho',
    imap: { host: 'imap.zoho.com', port: 993 },
    pop3: { host: 'pop.zoho.com', port: 995 },
    smtp: { host: 'smtp.zoho.com', port: 465 },
  },
  yandex: {
    label: 'Yandex',
    imap: { host: 'imap.yandex.com', port: 993 },
    pop3: { host: 'pop.yandex.com', port: 995 },
    smtp: { host: 'smtp.yandex.com', port: 465 },
  },
  other: {
    label: 'Otro (configuración manual)',
    imap: { host: '', port: 993 },
    pop3: { host: '', port: 995 },
    smtp: { host: '', port: 587 },
  },
};

export default function AccountForm({ onSave, onCancel, status: externalStatus, loading: externalLoading }: Props) {
  const [proveedor, setProveedor] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [nombre, setNombre] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tipoConexion, setTipoConexion] = useState<'IMAP' | 'POP3'>('IMAP');
  const [internalStatus, setInternalStatus] = useState('');
  const [internalLoading, setInternalLoading] = useState(false);

  const status = externalStatus ?? internalStatus;
  const loading = externalLoading ?? internalLoading;
  const provider = proveedor ? PROVIDERS[proveedor] : null;
  const connInfo = provider ? (tipoConexion === 'IMAP' ? provider.imap : provider.pop3) : null;

  const selectProvider = (key: string) => {
    setProveedor(key);
    setShowDropdown(false);
    const p = PROVIDERS[key];
    if (p && key !== 'other' && !nombre) {
      setNombre(p.label);
    }
  };

  const handleSave = async () => {
    if (!proveedor) { setInternalStatus('Selecciona un proveedor'); return; }
    if (!email || !password) { setInternalStatus('Email y contraseña obligatorios'); return; }
    if (!nombre.trim()) { setInternalStatus('Indica un nombre para la cuenta'); return; }

    setInternalLoading(true);
    setInternalStatus('');
    try {
      await onSave({ nombre: nombre.trim(), email, password, tipoConexion, proveedor });
      setInternalStatus('✅ Cuenta guardada');
      setProveedor('');
      setNombre('');
      setEmail('');
      setPassword('');
      setTipoConexion('IMAP');
    } catch (err: any) {
      setInternalStatus(err?.response?.data?.message || 'Error al guardar');
    } finally {
      setInternalLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Dropdown proveedores */}
      <div>
        <label className="text-xs font-bold block mb-1.5" style={{ color: 'var(--color-text)' }}>
          Proveedor de correo
        </label>
        <div className="relative">
          <button
            onClick={() => setShowDropdown(!showDropdown)}
            className="w-full flex items-center gap-2 px-3 py-2.5 text-sm rounded-lg border text-left transition-colors"
            style={{
              backgroundColor: 'var(--color-bg)',
              color: proveedor ? 'var(--color-text)' : 'var(--color-text-secondary)',
              borderColor: 'var(--color-border)',
            }}
          >
            <span className="flex-1">
              {proveedor ? PROVIDERS[proveedor].label : 'Seleccionar proveedor...'}
            </span>
            <span className="text-xs">{showDropdown ? '▲' : '▼'}</span>
          </button>

          {showDropdown && (
            <div className="absolute z-10 w-full mt-1 rounded-lg border shadow-lg overflow-hidden"
              style={{
                backgroundColor: 'var(--color-bg-card)',
                borderColor: 'var(--color-border)',
              }}>
              {Object.entries(PROVIDERS).map(([key, p]) => (
                <button
                  key={key}
                  onClick={() => selectProvider(key)}
                  className="w-full flex items-center gap-2 px-3 py-2.5 text-sm text-left transition-colors hover:opacity-80"
                  style={{
                    backgroundColor: proveedor === key ? 'var(--color-accent-selected)' : 'transparent',
                    color: proveedor === key ? '#0F172A' : 'var(--color-text)',
                  }}
                >
                  {p.label}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Nombre cuenta */}
      <div>
        <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
          Nombre de la cuenta
        </label>
        <input value={nombre} onChange={e => setNombre(e.target.value)}
          placeholder="Ej: Personal, Trabajo, Estudio..."
          className="w-full px-3 py-2 text-sm rounded-lg border outline-none transition-colors"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
      </div>

      {/* Email */}
      <div>
        <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
          Cuenta de correo *
        </label>
        <input value={email} onChange={e => setEmail(e.target.value)}
          placeholder="usuario@dominio.com"
          className="w-full px-3 py-2 text-sm rounded-lg border outline-none transition-colors"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
      </div>

      {/* Contraseña */}
      <div>
        <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
          Contraseña *
        </label>
        <input type="password" value={password} onChange={e => setPassword(e.target.value)}
          placeholder="Contraseña de la cuenta"
          className="w-full px-3 py-2 text-sm rounded-lg border outline-none transition-colors"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
      </div>

      {/* Tipo conexión */}
      <div>
        <label className="text-xs font-bold block mb-1.5" style={{ color: 'var(--color-text)' }}>
          Tipo de conexión
        </label>
        <div className="flex gap-2">
          <button onClick={() => setTipoConexion('IMAP')}
            className="flex-1 px-3 py-2 text-sm font-bold rounded-lg transition-colors"
            style={{
              backgroundColor: tipoConexion === 'IMAP' ? 'var(--color-accent-selected)' : 'var(--color-bg)',
              color: tipoConexion === 'IMAP' ? '#0F172A' : 'var(--color-text)',
              border: tipoConexion === 'IMAP' ? '2px solid var(--color-accent)' : '1px solid var(--color-border)',
            }}>
            📥 IMAP
          </button>
          <button onClick={() => setTipoConexion('POP3')}
            className="flex-1 px-3 py-2 text-sm font-bold rounded-lg transition-colors"
            style={{
              backgroundColor: tipoConexion === 'POP3' ? 'var(--color-accent-selected)' : 'var(--color-bg)',
              color: tipoConexion === 'POP3' ? '#0F172A' : 'var(--color-text)',
              border: tipoConexion === 'POP3' ? '2px solid var(--color-accent)' : '1px solid var(--color-border)',
            }}>
            📩 POP3
          </button>
        </div>
        {connInfo && (
          <p className="text-[10px] mt-1" style={{ color: 'var(--color-text-secondary)' }}>
            {tipoConexion}: {connInfo.host}:{connInfo.port}
          </p>
        )}
      </div>

      {/* Status */}
      {status && (
        <p className="text-xs" style={{ color: status.startsWith('✅') ? '#22c55e' : '#ef4444' }}>
          {status}
        </p>
      )}

      {/* Botones */}
      <div className="flex justify-end gap-2 pt-2">
        {onCancel && (
          <button onClick={onCancel}
            className="px-4 py-1.5 text-sm rounded-lg transition-colors"
            style={{ backgroundColor: 'var(--color-bg-elevated)', color: 'var(--color-text)' }}>
            Cancelar
          </button>
        )}
        <button onClick={handleSave} disabled={loading}
          className="px-5 py-1.5 text-sm font-bold rounded-pill disabled:opacity-50 transition-colors"
          style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
          {loading ? 'Guardando...' : 'Guardar cuenta'}
        </button>
      </div>
    </div>
  );
}

export type { AccountFormData };
