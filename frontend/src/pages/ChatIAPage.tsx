import { useState, useRef, useEffect } from 'react';
import { iaApi, mensajeApi } from '../api/client';

interface ChatMsg {
  role: 'user' | 'ia';
  text: string;
}

export default function ChatIAPage() {
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState('');
  const [typing, setTyping] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => { scrollRef.current?.scrollTo(0, scrollRef.current.scrollHeight); }, [messages]);

  const send = async () => {
    if (!input.trim()) return;
    const msg = input;
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: msg }]);
    setTyping(true);
    try {
      const res = await iaApi.chat(msg);
      setMessages(prev => [...prev, { role: 'ia', text: res.data.respuesta || 'Sin respuesta' }]);
    } catch {
      setMessages(prev => [...prev, { role: 'ia', text: 'Error de conexión con la IA.' }]);
    } finally {
      setTyping(false);
    }
  };

  return (
    <div className="flex flex-col h-full" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* Header */}
      <div className="flex items-center px-4 py-2.5 gap-2.5 border-b"
        style={{ backgroundColor: 'var(--color-bg-sidebar)', borderColor: 'var(--color-border)' }}>
        <h2 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>Chat IA</h2>
        <span className="text-xs ml-auto" style={{ color: 'var(--color-text-muted)' }}>Modelo: LM Studio</span>
      </div>

      {/* Mensajes */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-3">
        {messages.map((m, i) => (
          <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div className="max-w-[70%] px-3 py-2 rounded-xl text-sm"
              style={{
                backgroundColor: m.role === 'user' ? '#01696F' : '#201F1D',
                color: m.role === 'user' ? 'white' : '#cdccca',
                border: m.role === 'ia' ? '1px solid #393836' : 'none',
              }}>
              {m.text}
            </div>
          </div>
        ))}
        {messages.length === 0 && (
          <p className="text-xs text-center" style={{ color: 'var(--color-text-secondary)' }}>
            Pregunta sobre tus correos, pide resúmenes o sugerencias.
          </p>
        )}
      </div>

      {/* Indicador escritura */}
      {typing && (
        <div className="flex items-center gap-1.5 px-4 py-1"
          style={{ backgroundColor: 'var(--color-bg-sidebar)' }}>
          <div className="w-4 h-4 rounded-full border-2 border-t-transparent animate-spin"
            style={{ borderColor: 'var(--color-text-muted)' }} />
          <span className="text-xs" style={{ color: 'var(--color-text-muted)' }}>
            La IA está respondiendo…</span>
        </div>
      )}

      {/* Input */}
      <div className="flex items-center gap-2 px-4 py-2.5 border-t"
        style={{ backgroundColor: 'var(--color-bg-sidebar)', borderColor: 'var(--color-border)' }}>
        <textarea
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && !e.shiftKey && (e.preventDefault(), send())}
          placeholder="Escribe tu pregunta sobre los correos…"
          rows={2}
          className="flex-1 px-2 py-1.5 text-sm rounded-lg border outline-none resize-none"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                   borderColor: 'var(--color-border)' }} />
        <button onClick={send} disabled={!input.trim() || typing}
          className="px-4 py-1.5 text-sm font-bold rounded-pill disabled:opacity-50"
          style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
          Enviar</button>
      </div>
    </div>
  );
}
