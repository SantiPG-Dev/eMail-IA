import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { ReactNode } from 'react';

// Mock completo del API client
const mockPost = vi.fn();
const mockGet = vi.fn();

vi.mock('../api/client', () => ({
  default: {
    post: (...args: any[]) => mockPost(...args),
    get: (...args: any[]) => mockGet(...args),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  authApi: {
    status: vi.fn().mockResolvedValue({ data: { configurada: true } }),
    setup: vi.fn(),
    login: vi.fn(),
  },
}));

const wrapper = ({ children }: { children: ReactNode }) => (
  <AuthProvider>{children}</AuthProvider>
);

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    mockPost.mockReset();
    mockGet.mockReset();
  });

  it('inicia sin autenticar', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.token).toBeNull();
  });

  it('login exitoso guarda token', async () => {
    const fakeToken = 'test-jwt-token';
    mockPost.mockResolvedValue({ data: { token: fakeToken } });

    const { result } = renderHook(() => useAuth(), { wrapper });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.login('test123');
    });

    expect(success).toBe(true);
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.token).toBe(fakeToken);
    expect(localStorage.getItem('emailai_token')).toBe(fakeToken);
  });

  it('login fallido no autentica', async () => {
    mockPost.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useAuth(), { wrapper });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.login('wrong');
    });

    expect(success).toBe(false);
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('logout limpia token y estado', async () => {
    // Primero autenticar
    const fakeToken = 'test-jwt-token';
    mockPost.mockResolvedValueOnce({ data: { token: fakeToken } }); // login
    mockPost.mockResolvedValueOnce({}); // logout

    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => {
      await result.current.login('test123');
    });
    expect(result.current.isAuthenticated).toBe(true);

    // Logout
    await act(async () => {
      await result.current.logout();
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.token).toBeNull();
    expect(localStorage.getItem('emailai_token')).toBeNull();
  });

  it('setup exitoso marca configurada', async () => {
    mockPost.mockResolvedValue({ data: { mensaje: 'OK' } });

    const { result } = renderHook(() => useAuth(), { wrapper });

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.setup('newpass');
    });

    expect(success).toBe(true);
  });

  it('restaura token desde localStorage', () => {
    localStorage.setItem('emailai_token', 'persisted-token');

    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.token).toBe('persisted-token');
  });
});
