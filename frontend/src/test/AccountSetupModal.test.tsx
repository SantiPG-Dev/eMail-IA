import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import AccountSetupModal from '../components/AccountSetupModal';

const mockIniciar = vi.fn();
const mockEstado = vi.fn();
const mockCreate = vi.fn();

vi.mock('../api/client', () => ({
  cuentaApi: { create: (...a: any[]) => mockCreate(...a) },
  oauthApi: { iniciar: (...a: any[]) => mockIniciar(...a), estado: (...a: any[]) => mockEstado(...a) },
}));

// Cancelar en mitad del OAuth debe cerrar el modal y descartar el flujo:
// aunque el polling siguiera vivo detrás, no puede crear la cuenta.
describe('AccountSetupModal — cancelar el alta de cuenta', () => {
  beforeAll(() => {
    (window as any).electronAPI = { openExternal: vi.fn().mockResolvedValue(undefined) };
  });

  it('cancelar durante el OAuth cierra el modal y no crea la cuenta', async () => {
    vi.useFakeTimers();
    mockIniciar.mockResolvedValue({ data: { flujoId: 'f1', authUrl: 'http://auth' } });
    mockEstado.mockResolvedValue({ status: 200, data: {} }); // siempre PENDIENTE
    const onClose = vi.fn();

    render(<AccountSetupModal open onClose={onClose} />);
    fireEvent.click(screen.getByText('Seleccionar proveedor...'));
    fireEvent.click(screen.getByText('🔑 Gmail (OAuth)'));

    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    expect(mockIniciar).toHaveBeenCalledWith('gmail');

    fireEvent.click(screen.getByText('Cancelar'));
    expect(onClose).toHaveBeenCalled();

    // Deja correr el reloj más allá del deadline del polling (2,5 min)
    await act(async () => { await vi.advanceTimersByTimeAsync(200_000); });
    expect(mockCreate).not.toHaveBeenCalled();
    vi.useRealTimers();
  });
});
