import { useRef, useState } from 'react';
import AccountForm, { type AccountFormData } from './AccountForm';
import { cuentaApi, oauthApi } from '../api/client';
import { mensajeDeError } from '../utils/error';

interface Props {
  open: boolean;
  onClose: (result?: { email: string; password: string }) => void;
  // Cuenta creada como default: el login la deja true (es la primera vez que
  // se crea); desde Configuración solo interesa si aún no hay ninguna.
  esDefault?: boolean;
}

// Modal de creación de cuenta. Para Gmail/Outlook usa OAuth2,
// para el resto usa IMAP/POP3 con contraseña.
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

// Proveedores que usan OAuth
const OAUTH_PROVIDERS = ['gmail', 'outlook'];

export default function AccountSetupModal({ open, onClose, esDefault }: Props) {
  const [saved, setSaved] = useState(false);
  const [status, setStatus] = useState('');
  const [oauthInProgress, setOauthInProgress] = useState(false);
  // Identifica el intento OAuth vigente: si se cancela y se vuelve a empezar,
  // el intento anterior deja de contar aunque su polling siguiera dormido.
  const intentoOauth = useRef(0);

  // ── Guardar cuenta normal (password IMAP/POP3) ──────────
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
      esDefault: esDefault ?? true,
      oauthProvider: null,
      oauthAccessToken: null,
      oauthRefreshToken: null,
      oauthExpiresAt: null,
    });
    setSaved(true);
    setTimeout(() => onClose({ email: data.email, password: data.password }), 1500);
  };

  // ── Iniciar flujo OAuth ─────────────────────────────────
  const handleOAuthStart = async (proveedor: string) => {
    if (!OAUTH_PROVIDERS.includes(proveedor)) return;

    setOauthInProgress(true);
    setStatus('');

    const intento = ++intentoOauth.current;

    try {
      // 1. Pedir URL de autorización al backend (arranca la escucha en background)
      const authRes = await oauthApi.iniciar(proveedor);
      const { flujoId, authUrl } = authRes.data;

      // 2. Abrir navegador del sistema
      if (window.electronAPI?.openExternal) {
        await window.electronAPI.openExternal(authUrl);
      } else {
        window.open(authUrl, '_blank');
      }

      // 3. Polling del estado hasta COMPLETADO/TIMEOUT/ERROR (máx 2,5 min)
      let session: { email?: string; accessToken: string; refreshToken?: string; expiresAt?: string } | null = null;
      const deadline = Date.now() + 150_000;
      while (Date.now() < deadline) {
        if (intentoOauth.current !== intento) return; // cancelado o relanzado
        await new Promise(r => setTimeout(r, 2000));
        const res = await oauthApi.estado(flujoId);
        if (res.status === 200 && res.data.accessToken) {
          session = res.data;  // COMPLETADO
          break;
        }
        if (res.status === 408 || res.status === 400) {
          throw new Error(res.data?.error || 'Flujo OAuth fallido');
        }
        // PENDIENTE → seguir esperando
      }
      if (!session) throw new Error('Tiempo de espera agotado esperando la autorización');

      // 4. Crear la cuenta con los tokens OAuth
      const proveedorUpper = proveedor === 'gmail' ? 'GOOGLE' : 'MICROSOFT';
      const host = proveedor === 'gmail'
        ? { imap: 'imap.gmail.com', smtp: 'smtp.gmail.com' }
        : { imap: 'outlook.office365.com', smtp: 'smtp.office365.com' };

      await cuentaApi.create({
        nombre: session.email || proveedor,
        email: session.email ?? ``,
        servidor: host.imap,
        puerto: 993,
        usuario: session.email ?? ``,
        password: '',  // No hace falta para OAuth
        tipoConexion: 'IMAP',
        esDefault: esDefault ?? true,
        oauthProvider: proveedorUpper,
        oauthAccessToken: session.accessToken,
        oauthRefreshToken: session.refreshToken || '',
        oauthExpiresAt: session.expiresAt,
      });

      setSaved(true);
      setTimeout(() => onClose(), 1500);
    } catch (err) {
      const msg = mensajeDeError(err, 'Error en autenticación OAuth');
      setStatus('❌ ' + msg);
    } finally {
      setOauthInProgress(false);
    }
  };

  // Cancelar cierra el modal y descarta un OAuth en marcha: no crea la
  // cuenta aunque el navegador acabe autorizando después.
  const handleCancelar = () => {
    intentoOauth.current++;
    setOauthInProgress(false);
    setStatus('');
    onClose();
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
              {oauthInProgress ? 'Autenticando con OAuth...' : 'Introduce los datos de tu cuenta'}
            </p>
          </div>
        </div>

        <AccountForm
          onSave={handleSave}
          onOAuthStart={handleOAuthStart}
          onCancel={handleCancelar}
          status={status}
          oauthInProgress={oauthInProgress}
        />
      </div>
    </div>
  );
}
