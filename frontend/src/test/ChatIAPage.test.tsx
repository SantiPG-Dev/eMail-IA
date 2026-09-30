import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

const chat = vi.hoisted(() => vi.fn());
vi.mock('../api/client', () => ({ iaApi: { chat } }));

import ChatIAPage from '../pages/ChatIAPage';

describe('ChatIAPage — chat con la IA', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // jsdom no implementa scrollTo (el auto-scroll del chat)
    Element.prototype.scrollTo = () => {};
  });

  it('sin mensajes muestra la pista y el botón Enviar deshabilitado', () => {
    render(<ChatIAPage />);
    expect(screen.getByText('Pregunta sobre tus correos, pide resúmenes o sugerencias.')).toBeTruthy();
    expect((screen.getByText('Enviar') as HTMLButtonElement).disabled).toBe(true);
  });

  it('enviar pinta la burbuja del usuario y la respuesta de la IA', async () => {
    chat.mockResolvedValue({ data: { respuesta: 'Tienes 3 correos de Ana' } });
    render(<ChatIAPage />);

    fireEvent.change(screen.getByPlaceholderText('Escribe tu pregunta sobre los correos…'),
      { target: { value: '¿cuántos correos tengo?' } });
    fireEvent.click(screen.getByText('Enviar'));

    expect(screen.getByText('¿cuántos correos tengo?')).toBeTruthy();
    await waitFor(() => expect(screen.getByText('Tienes 3 correos de Ana')).toBeTruthy());
    expect(chat).toHaveBeenCalledWith('¿cuántos correos tengo?');
  });

  it('un fallo de la IA pinta burbuja de error con el mensaje del backend', async () => {
    chat.mockRejectedValue({ response: { data: { error: 'LM Studio no responde' } } });
    render(<ChatIAPage />);

    fireEvent.change(screen.getByPlaceholderText('Escribe tu pregunta sobre los correos…'),
      { target: { value: 'hola' } });
    fireEvent.click(screen.getByText('Enviar'));

    await waitFor(() => expect(screen.getByText('LM Studio no responde')).toBeTruthy());
  });
});
