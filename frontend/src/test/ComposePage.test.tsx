import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';

const send = vi.hoisted(() => vi.fn());
vi.mock('../api/client', () => ({ enviarApi: { send } }));

import ComposePage from '../pages/ComposePage';

const onClose = vi.fn();

// Los inputs no llevan htmlFor: Para, CC y Asunto por posición
const inputs = () => document.querySelectorAll('input');
const cuerpo = () => document.querySelector('textarea')!;

describe('ComposePage — redacción y envío', () => {
  beforeEach(() => { vi.clearAllMocks(); onClose.mockClear(); });

  it('no envía con campos vacíos y avisa', () => {
    render(<ComposePage mode="nuevo" onClose={onClose} />);
    fireEvent.click(screen.getByText('Enviar'));
    expect(screen.getByText('Revisa «Para» (email válido) y «Asunto»')).toBeTruthy();
    expect(send).not.toHaveBeenCalled();
  });

  it('rechaza un destinatario mal formado', () => {
    render(<ComposePage mode="nuevo" onClose={onClose} />);
    fireEvent.change(inputs()[0], { target: { value: 'no-es-email' } });
    fireEvent.change(inputs()[2], { target: { value: 'Hola' } });
    fireEvent.click(screen.getByText('Enviar'));
    expect(screen.getByText('Revisa «Para» (email válido) y «Asunto»')).toBeTruthy();
    expect(send).not.toHaveBeenCalled();
  });

  it('envía normalizando destinatarios y recortando el asunto', async () => {
    vi.useFakeTimers();
    send.mockResolvedValue({ data: { ok: true } });
    render(<ComposePage mode="nuevo" onClose={onClose} />);

    fireEvent.change(inputs()[0], { target: { value: ' a@x.com , b@y.com ' } });
    fireEvent.change(inputs()[2], { target: { value: '  Asunto  ' } });
    fireEvent.change(cuerpo(), { target: { value: 'Cuerpo del mensaje' } });
    fireEvent.click(screen.getByText('Enviar'));

    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    expect(send).toHaveBeenCalledWith(
      { para: 'a@x.com, b@y.com', cc: '', asunto: 'Asunto', cuerpo: 'Cuerpo del mensaje' });
    expect(screen.getByText('✅ Enviado correctamente')).toBeTruthy();

    await act(async () => { await vi.advanceTimersByTimeAsync(1600); });
    expect(onClose).toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('un ok:false del backend muestra el error', async () => {
    send.mockResolvedValue({ data: { ok: false, error: 'SMTP rechazado' } });
    render(<ComposePage mode="responder" to="destino@x.com" onClose={onClose} />);

    fireEvent.change(inputs()[2], { target: { value: 'Re:' } });
    fireEvent.click(screen.getByText('Enviar'));
    await act(async () => {});
    expect(screen.getByText('Error: SMTP rechazado')).toBeTruthy();
  });
});
