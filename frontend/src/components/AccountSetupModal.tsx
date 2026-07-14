import { useState } from 'react';
import { cuentaApi, utilApi } from '../api/client';

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function AccountSetupModal({ open, onClose }: Props) {
  const [step, setStep] = useState<'provider' | 'manual' | 'oauth' | 'done'>('provider');
  const [nombre, setNombre] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [imapHost, setImapHost] = useState('');
  const [imapPort, setImapPort] = useState('993');
  const [smtpHost, setSmtpHost] = useState('');
  const [smtpPort, setSmtpPort] = useState('465');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);

  // Auto-rellenar según proveedor
  const selectProvider = (provider: string) => {
    if (provider === 'gmail') {
      setNombre('Gmail');
      setImapHost('imap.gmail.com');
      setImapPort('993');
      setSmtpHost('smtp.gmail.com');
      setSmtpPort('465');
      setStep('manual');
    } else if (provider === 'outlook') {
      setNombre('Outlook');
      setImapHost('outlook.office365.com');
      setImapPort('993');
      setSmtpHost('smtp.office365.com');
      setSmtpPort('587');
      setStep('manual');
    } else if (provider === 'yahoo') {
      setNombre('Yahoo');
      setImapHost('imap.mail.yahoo.com');
      setImapPort('993');
      setSmtpHost('smtp.mail.yahoo.com');
      setSmtpPort('465');
      setStep('manual');
    } else {
      setStep('manual');
    }
  };

  const saveAccount = async () => {
    if (!email || !password) {
      setStatus('Completa todos los campos obligatorios');
      return;
    }
    setLoading(true);
    setStatus('');
    try {
      await cuentaApi.create({
        nombre: nombre || email.split('@')[0],
        email,
        servidor: imapHost,
        puerto: parseInt(imapPort),
        usuario: email,
        password,
        esDefault: true,
        oauthProvider: null,
        oauthAccessToken: null,
        oauthRefreshToken: null,
        oauthExpiresAt: null,
      });
      setStep('done');
      setStatus('Cuenta guardada. Sincronizando...');
      // Sincronizar automaticamente
      utilApi.syncAll().catch(() => {});
      setTimeout(() => onClose(), 2000);
    } catch (err: any) {
      setStatus(err.response?.data?.message || 'Error al guardar la cuenta');
    } finally {
      setLoading(false);
    }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-[560px] max-h-[80vh] overflow-y-auto rounded-xl p-6 shadow-2xl"
        style={{ backgroundColor: 'var(--color-bg-card)' }}>
        
        {/* Header */}
        <div className="flex items-center gap-3 mb-5">
          <div className="w-1 h-10 rounded" style={{ backgroundColor: '#38BDF8' }} />
          <div>
            <h2 className="text-base font-bold" style={{ color: 'var(--color-text)' }}>
              Configurar cuenta de correo
            </h2>
            <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
              {step === 'provider' ? 'Elige tu proveedor o configúralo manualmente' :
               step === 'done' ? 'Cuenta configurada' :
               'Introduce tus datos de acceso'}
            </p>
          </div>
        </div>

        {/* PASO 1: Seleccionar proveedor */}
        {step === 'provider' && (
          <div className="space-y-3">
            <p className="text-sm font-bold mb-2" style={{ color: 'var(--color-text)' }}>
              Proveedor de correo
            </p>
            {[
              { id: 'gmail', label: 'Gmail', icon: '📧' },
              { id: 'outlook', label: 'Outlook / Hotmail', icon: '📨' },
              { id: 'yahoo', label: 'Yahoo Mail', icon: '📩' },
              { id: 'other', label: 'Otro (configuración manual)', icon: '⚙️' },
            ].map(p => (
              <button key={p.id} onClick={() => selectProvider(p.id)}
                className="w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm transition-colors"
                style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}>
                <span className="text-lg">{p.icon}</span>
                <span>{p.label}</span>
              </button>
            ))}
          </div>
        )}

        {/* PASO 2: Formulario manual */}
        {step === 'manual' && (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                  Nombre de la cuenta
                </label>
                <input value={nombre} onChange={e => setNombre(e.target.value)}
                  placeholder="Mi correo"
                  className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
                  style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                           borderColor: 'var(--color-border)' }} />
              </div>
              <div className="col-span-2">
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                  Correo electrónico *
                </label>
                <input value={email} onChange={e => setEmail(e.target.value)}
                  placeholder="usuario@dominio.com"
                  className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
                  style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                           borderColor: 'var(--color-border)' }} />
              </div>
              <div className="col-span-2">
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                  Contraseña *
                </label>
                <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                  placeholder="Contraseña de la cuenta"
                  className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
                  style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                           borderColor: 'var(--color-border)' }} />
              </div>
              <div>
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                  Servidor IMAP
                </label>
                <input value={imapHost} onChange={e => setImapHost(e.target.value)}
                  className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
                  style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                           borderColor: 'var(--color-border)' }} />
              </div>
              <div>
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                  Puerto IMAP
                </label>
                <input value={imapPort} onChange={e => setImapPort(e.target.value)}
                  className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
                  style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                           borderColor: 'var(--color-border)' }} />
              </div>
              <div>
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                  Servidor SMTP
                </label>
                <input value={smtpHost} onChange={e => setSmtpHost(e.target.value)}
                  className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
                  style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                           borderColor: 'var(--color-border)' }} />
              </div>
              <div>
                <label className="text-xs font-bold block mb-1" style={{ color: 'var(--color-text)' }}>
                  Puerto SMTP
                </label>
                <input value={smtpPort} onChange={e => setSmtpPort(e.target.value)}
                  className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
                  style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                           borderColor: 'var(--color-border)' }} />
              </div>
            </div>

            {status && (
              <p className="text-xs" style={{
                color: status.includes('Error') || status.includes('Completa') ? '#ef4444' : '#22c55e'
              }}>{status}</p>
            )}

            <div className="flex gap-2 pt-2">
              <button onClick={() => setStep('provider')}
                className="px-4 py-1.5 text-sm rounded-lg transition-colors"
                style={{ backgroundColor: 'var(--color-bg-elevated)', color: 'var(--color-text)' }}>
                Atrás
              </button>
              <button onClick={saveAccount} disabled={loading}
                className="px-4 py-1.5 text-sm font-bold rounded-pill disabled:opacity-50"
                style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
                {loading ? 'Guardando...' : 'Guardar cuenta'}
              </button>
            </div>
          </div>
        )}

        {/* PASO 3: Hecho */}
        {step === 'done' && (
          <div className="text-center py-6">
            <p className="text-lg mb-2">✅</p>
            <p className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>
              Cuenta configurada
            </p>
            <p className="text-xs mt-1" style={{ color: 'var(--color-text-secondary)' }}>
              {status}
            </p>
          </div>
        )}

        {/* Skip button */}
        {step === 'provider' && (
          <button onClick={onClose}
            className="w-full mt-3 py-2 text-xs rounded-lg transition-colors"
            style={{ color: 'var(--color-text-secondary)' }}>
            Saltar, lo haré después
          </button>
        )}
      </div>
    </div>
  );
}
