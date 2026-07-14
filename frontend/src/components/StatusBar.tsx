import { useState, useEffect } from 'react';
import { cuentaApi, iaApi } from '../api/client';
import axios from 'axios';

export default function StatusBar() {
  const [cuenta, setCuenta] = useState('');
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');
  const [iaStatus, setIaStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  const checkStatus = () => {
    cuentaApi.list().then(r => {
      if (r.data.length > 0) setCuenta(r.data[0].email);
    }).catch(() => {});
    axios.get('/health').then(() => setBackendStatus('online')).catch(() => setBackendStatus('offline'));
    iaApi.status().then(r => setIaStatus(r.data.disponible ? 'online' : 'offline')).catch(() => {});
  };

  useEffect(() => {
    checkStatus();
    const interval = setInterval(checkStatus, 30000); // cada 30s
    return () => clearInterval(interval);
  }, []);

  const dot = (s: string) => s === 'online' ? '🟢' : s === 'offline' ? '🔴' : '🟡';

  return (
    <div className="flex items-center gap-3 px-3 py-1 text-[10px] border-t shrink-0"
      style={{ backgroundColor: 'var(--color-bg-sidebar)', borderColor: 'var(--color-border)',
               color: 'var(--color-text-secondary)' }}>
      <span title="Cuenta activa">📧 {cuenta || 'Sin cuenta'}</span>
      <span className="flex-1 text-center text-[9px]" style={{ opacity: 0.5 }}>
        eMail-IA v1.0
      </span>
      <span>{dot(backendStatus)} Backend</span>
      <span>{dot(iaStatus)} IA</span>
    </div>
  );
}
