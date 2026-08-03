import { useState } from 'react';
import { enviarApi } from '../api/client';

interface Props {
  mode: 'nuevo' | 'responder' | 'reenviar';
  to?: string;
  subject?: string;
  body?: string;
  onClose: () => void;
}

// Diálogo de redacción de correo. Modos: nuevo, responder, reenviar.
export default function ComposePage({ mode, to, subject, body, onClose }: Props) {
  const [para, setPara] = useState(to || '');
  const [cc, setCc] = useState('');
  const [asunto, setAsunto] = useState(subject || '');
  const [cuerpo, setCuerpo] = useState(body || '');
  const [status, setStatus] = useState('');
  const [sending, setSending] = useState(false);

  const enviar = async () => {
    const emails = para.split(',').map(s => s.trim()).filter(Boolean);
    const emailOk = emails.length > 0 && emails.every(e => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e));
    if (!emailOk || !asunto.trim()) {
      setStatus('Revisa «Para» (email válido) y «Asunto»');
      return;
    }
    setSending(true);
    setStatus('Enviando...');
    try {
      const res = await enviarApi.send({ para: emails.join(', '), cc, asunto: asunto.trim(), cuerpo });
      if (res.data.ok) {
        setStatus('✅ Enviado correctamente');
        setTimeout(() => onClose(), 1500);
      } else {
        setStatus('Error: ' + (res.data.error || 'desconocido'));
      }
    } catch (err: any) {
      setStatus('Error: ' + (err.response?.data?.error || err.message || 'desconocido'));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="flex flex-col h-full" style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="flex items-center justify-between px-4 py-2 border-b"
        style={{ borderColor: 'var(--color-border)' }}>
        <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>
          {mode === 'nuevo' ? 'Nuevo mensaje' : mode === 'responder' ? 'Responder' : 'Reenviar'}
        </h3>
        <button onClick={onClose}
          className="text-xs px-2 py-1 rounded-lg"
          style={{ backgroundColor: 'var(--color-bg-card)', color: 'var(--color-text)' }}>
          ✕ Cerrar
        </button>
      </div>

      <div className="flex-1 flex flex-col gap-3 p-4 overflow-auto">
        <div className="flex items-center gap-2">
          <label className="text-xs font-bold w-12" style={{ color: 'var(--color-text)' }}>Para:</label>
          <input value={para} onChange={e => setPara(e.target.value)}
            className="flex-1 px-2 py-1.5 text-sm rounded-lg border outline-none"
            style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs font-bold w-12" style={{ color: 'var(--color-text)' }}>CC:</label>
          <input value={cc} onChange={e => setCc(e.target.value)}
            className="flex-1 px-2 py-1.5 text-sm rounded-lg border outline-none"
            style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs font-bold w-12" style={{ color: 'var(--color-text)' }}>Asunto:</label>
          <input value={asunto} onChange={e => setAsunto(e.target.value)}
            className="flex-1 px-2 py-1.5 text-sm rounded-lg border outline-none"
            style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }} />
        </div>
        <textarea value={cuerpo} onChange={e => setCuerpo(e.target.value)}
          className="flex-1 px-2 py-1.5 text-sm rounded-lg border outline-none resize-none"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)', borderColor: 'var(--color-border)' }}
          placeholder="Escribe tu mensaje aquí..." />

        {status && (
          <p className="text-xs" style={{ color: status.includes('Error') ? '#ef4444' : '#22c55e' }}>
            {status}
          </p>
        )}

        <div className="flex gap-2 justify-end">
          <button onClick={onClose}
            className="px-4 py-1.5 text-sm rounded-lg"
            style={{ backgroundColor: 'var(--color-bg-elevated)', color: 'var(--color-text)' }}>
            Cancelar
          </button>
          <button onClick={enviar} disabled={sending}
            className="px-4 py-1.5 text-sm font-bold rounded-pill disabled:opacity-50"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
            {sending ? 'Enviando...' : 'Enviar'}
          </button>
        </div>
      </div>
    </div>
  );
}
