import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  login: vi.fn(),
  cuentaList: vi.fn(),
  auth: { login: vi.fn(), loginError: '' as string, hayCuentas: true, loading: false },
}));
vi.mock('react-router-dom', () => ({ useNavigate: () => mocks.navigate }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => mocks.auth }));
vi.mock('../context/ThemeContext', () => ({ useTheme: () => ({ mode: 'dark', toggleMode: vi.fn() }) }));
vi.mock('../api/client', () => ({ cuentaApi: { list: mocks.cuentaList } }));
vi.mock('../components/AccountSetupModal', () => ({
  default: ({ open }: { open: boolean }) => (open ? <div data-testid="modal-alta" /> : null),
}));

import LoginPage from '../pages/LoginPage';

const CUENTA = { id: 1, nombre: 'Personal', email: 'yo@gmail.com', tipoConexion: 'IMAP', servidor: 'imap.gmail.com' };

describe('LoginPage — selección de perfil y login', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.auth = { login: mocks.login, loginError: '', hayCuentas: true, loading: false };
    mocks.login.mockReset();
  });

  it('sin cuentas auto-abre el alta y ofrece el botón de añadir', async () => {
    mocks.cuentaList.mockResolvedValue({ data: [] });
    render(<LoginPage />);

    await waitFor(() => expect(screen.getByTestId('modal-alta')).toBeTruthy());
    expect(screen.getByText('🆕 Configura tu cuenta')).toBeTruthy();
    expect(screen.getByText('Añadir cuenta de correo')).toBeTruthy();
  });

  it('con cuentas muestra perfiles; seleccionar habilita contraseña y Entrar', async () => {
    mocks.cuentaList.mockResolvedValue({ data: [CUENTA] });
    render(<LoginPage />);
    await waitFor(() => expect(screen.getByText('yo@gmail.com')).toBeTruthy());

    fireEvent.click(screen.getByText('yo@gmail.com'));
    expect(screen.getByPlaceholderText('Contraseña de tu correo')).toBeTruthy();
    expect(screen.getByText('Entrar')).toBeTruthy();
  });

  it('login correcto navega a la bandeja', async () => {
    mocks.cuentaList.mockResolvedValue({ data: [CUENTA] });
    mocks.login.mockResolvedValue(true);
    render(<LoginPage />);
    await waitFor(() => expect(screen.getByText('yo@gmail.com')).toBeTruthy());

    fireEvent.click(screen.getByText('yo@gmail.com'));
    fireEvent.change(screen.getByPlaceholderText('Contraseña de tu correo'),
      { target: { value: 'secreto' } });
    fireEvent.click(screen.getByText('Entrar'));

    await waitFor(() => expect(mocks.login).toHaveBeenCalledWith('yo@gmail.com', 'secreto'));
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/', { replace: true }));
  });

  it('login fallido muestra el aviso sin navegar', async () => {
    mocks.cuentaList.mockResolvedValue({ data: [CUENTA] });
    mocks.login.mockResolvedValue(false);
    render(<LoginPage />);
    await waitFor(() => expect(screen.getByText('yo@gmail.com')).toBeTruthy());

    fireEvent.click(screen.getByText('yo@gmail.com'));
    fireEvent.change(screen.getByPlaceholderText('Contraseña de tu correo'),
      { target: { value: 'mal' } });
    fireEvent.click(screen.getByText('Entrar'));

    await waitFor(() =>
      expect(screen.getByText(/No se pudo iniciar sesión/)).toBeTruthy());
    expect(mocks.navigate).not.toHaveBeenCalled();
  });
});
