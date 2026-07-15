import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Layout from '../components/Layout';
import { AuthProvider } from '../context/AuthContext';
import { ThemeProvider } from '../context/ThemeContext';
import { SyncProvider } from '../context/SyncContext';
import { ReactNode } from 'react';

// Mock API client — SyncContext necesita cuentaApi.list() y mensajeApi.list()
vi.mock('../api/client', () => ({
  default: {
    post: vi.fn().mockResolvedValue({ data: {} }),
    get: vi.fn().mockResolvedValue({ data: {} }),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  cuentaApi: {
    list: vi.fn().mockResolvedValue({ data: [] }),
  },
  mensajeApi: {
    list: vi.fn().mockResolvedValue({ data: { mensajes: [] } }),
  },
}));

const wrapper = ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={['/']}>
    <ThemeProvider>
      <AuthProvider>
        <SyncProvider>
          {children}
        </SyncProvider>
      </AuthProvider>
    </ThemeProvider>
  </MemoryRouter>
);

describe('Layout', () => {
  it('renderiza la marca', () => {
    render(<Layout />, { wrapper });
    // La marca es un logo img con alt="eMail-IA"
    const logo = screen.getByAltText('eMail-IA');
    expect(logo).toBeInTheDocument();
    expect(logo.tagName).toBe('IMG');
  });

  it('renderiza los items de navegacion', () => {
    render(<Layout />, { wrapper });
    expect(screen.getByText('📬 Correo')).toBeInTheDocument();
    expect(screen.getByText('📅 Calendario')).toBeInTheDocument();
    expect(screen.getByText('👤 Contactos')).toBeInTheDocument();
    expect(screen.getByText('✅ Tareas')).toBeInTheDocument();
    expect(screen.getByText('⚙️ Configuración')).toBeInTheDocument();
    expect(screen.getByText('✨ Chat IA')).toBeInTheDocument();
  });

  it('renderiza boton de cerrar sesion', () => {
    render(<Layout />, { wrapper });
    expect(screen.getByText('Cerrar sesión')).toBeInTheDocument();
  });
});
