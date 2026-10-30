import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

const api = vi.hoisted(() => ({
  cuentaList: vi.fn(), cuentaDelete: vi.fn(), iaConfig: vi.fn(), iaConectar: vi.fn(),
}));
vi.mock('../api/client', () => ({
  cuentaApi: { list: api.cuentaList, delete: api.cuentaDelete },
  iaApi: { config: api.iaConfig, conectar: api.iaConectar },
}));
vi.mock('../context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'oscuro', setTheme: vi.fn(), mode: 'dark', toggleMode: vi.fn() }),
}));
// El alta vive en el modal (probado aparte): aquí solo interesa que se abre
vi.mock('../components/AccountSetupModal', () => ({
  default: ({ open }: { open: boolean }) => (open ? <div data-testid="modal-alta" /> : null),
}));

import ConfigPage from '../pages/ConfigPage';

const CUENTAS = [
  { id: 1, nombre: 'Personal', email: 'yo@gmail.com', servidor: 'imap.gmail.com' },
  { id: 2, nombre: 'Trabajo', email: 'yo@empresa.com', servidor: 'outlook.office365.com' },
];

describe('ConfigPage — secciones y cuentas', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    api.iaConfig.mockResolvedValue(
      { data: { baseUrl: 'http://lm:1234', model: 'm1', prompt: 'p' } });
  });

  it('la sección Cuentas lista las cuentas y permite borrar con confirmación', async () => {
    api.cuentaList.mockResolvedValue({ data: CUENTAS });
    api.cuentaDelete.mockResolvedValue({ data: {} });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<ConfigPage />);

    fireEvent.click(screen.getByText('📬 Cuentas'));
    await waitFor(() => expect(screen.getByText(/yo@gmail\.com/)).toBeTruthy());

    fireEvent.click(screen.getAllByText('Eliminar')[0]);
    expect(api.cuentaDelete).toHaveBeenCalledWith(1);
  });

  it('el botón de alta abre el AccountSetupModal (con OAuth, ya sin el form muerto)', async () => {
    api.cuentaList.mockResolvedValue({ data: [] });
    render(<ConfigPage />);

    fireEvent.click(screen.getByText('📬 Cuentas'));
    await waitFor(() => expect(screen.getByText('Sin cuentas configuradas')).toBeTruthy());
    fireEvent.click(screen.getByText('+ Añadir cuenta'));
    expect(screen.getByTestId('modal-alta')).toBeTruthy();
  });

  it('la sección IA carga la config guardada y Conectar prueba el servidor', async () => {
    api.cuentaList.mockResolvedValue({ data: [] });
    api.iaConectar.mockResolvedValue(
      { data: { ok: true, modelos: ['qwen3.5:9b', 'llama3:8b'] } });
    render(<ConfigPage />);

    fireEvent.click(screen.getByText('🤖 IA'));
    await waitFor(() =>
      expect((screen.getByDisplayValue('http://lm:1234') as HTMLInputElement).value).toBe('http://lm:1234'));

    fireEvent.click(screen.getByText('Conectar'));
    await waitFor(() => expect(api.iaConectar).toHaveBeenCalledWith('http://lm:1234'));
    await waitFor(() => expect(screen.getByText(/Conectado a http:\/\/lm:1234/)).toBeTruthy());
  });
});
