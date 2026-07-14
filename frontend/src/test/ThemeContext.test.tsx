import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { ThemeProvider, useTheme } from '../context/ThemeContext';
import { ReactNode } from 'react';

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-mode');
  });

  it('usa valores por defecto', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.theme).toBe('emailIA');
    expect(result.current.mode).toBe('dark');
  });

  it('toggleMode cambia de dark a light', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.mode).toBe('dark');

    act(() => result.current.toggleMode());
    expect(result.current.mode).toBe('light');

    act(() => result.current.toggleMode());
    expect(result.current.mode).toBe('dark');
  });

  it('setTheme cambia la paleta', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => result.current.setTheme('amber-slate'));
    expect(result.current.theme).toBe('amber-slate');
  });

  it('persiste tema en localStorage', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.setTheme('ocean-teal');
      result.current.toggleMode();
    });

    expect(localStorage.getItem('emailai_theme')).toBe('ocean-teal');
    expect(localStorage.getItem('emailai_mode')).toBe('light');
  });

  it('restaura tema desde localStorage', () => {
    localStorage.setItem('emailai_theme', 'midnight-rose');
    localStorage.setItem('emailai_mode', 'light');

    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.theme).toBe('midnight-rose');
    expect(result.current.mode).toBe('light');
  });

  it('actualiza atributos del DOM al cambiar tema', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.setTheme('forest-stone');
      result.current.toggleMode();
    });

    expect(document.documentElement.getAttribute('data-theme')).toBe('forest-stone');
    expect(document.documentElement.getAttribute('data-mode')).toBe('light');
  });
});
