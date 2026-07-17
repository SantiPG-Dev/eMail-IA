import { useState } from 'react';
import AccountForm, { type AccountFormData } from './AccountForm';
import { cuentaApi } from '../api/client';

interface Props {
  open: boolean;
  onClose: () => void;
}

// Modal de creación de cuenta (primer uso o añadir cuenta).
// Incluye detección automática de configuración IMAP/POP3 según proveedor.
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

export default function AccountSetupModal({ open, onClose }: Props) {
  const [saved, setSaved] = useState(false);

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
      esDefault: true,
      oauthProvider: null,
      oauthAccessToken: null,
      oauthRefreshToken: null,
      oauthExpiresAt: null,
    });
    setSaved(true);
    setTimeout(() => onClose(), 1500);
  };

  if (!open) return null;

  if (saved) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
        <div className="w-[400px] rounded-xl p-6 shadow-2xl text-center"
          style={{ backgroundColor: 'var(--color-bg-card)' }}>
          <p className="text-lg mb-2">✅</p>
          <p className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>
            Cuenta configurada
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-[500px] max-h-[90vh] overflow-y-auto rounded-xl p-6 shadow-2xl"
        style={{ backgroundColor: 'var(--color-bg-card)' }}>
        <div className="flex items-center gap-3 mb-5">
          <div className="w-1 h-10 rounded" style={{ backgroundColor: '#38BDF8' }} />
          <div>
            <h2 className="text-base font-bold" style={{ color: 'var(--color-text)' }}>
              Configurar cuenta de correo
            </h2>
            <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
              Introduce los datos de tu cuenta
            </p>
          </div>
        </div>

        <AccountForm onSave={handleSave} onCancel={onClose} />
      </div>
    </div>
  );
}
