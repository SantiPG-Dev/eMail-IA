import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Layout from '../components/Layout';
import { AuthProvider } from '../context/AuthContext';
import { ThemeProvider } from '../context/ThemeContext';
import { ReactNode } from 'react';

// Mock API client
vi.mock('../api/client', () => ({
  default: {
    post: vi.fn().mockResolvedValue({}),
    get: vi.fn().mockResolvedValue({}),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}));

const wrapper = ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={['/']}>
    <ThemeProvider>
      <AuthProvider>
        {children}
      </AuthProvider>
    </ThemeProvider>
  </MemoryRouter>
);

describe('Layout', () => {
  it('renderiza la marca', () => {
    render(<Layout />, { wrapper });
    expect(screen.getByText('eMail·IA')).toBeInTheDocument();
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
