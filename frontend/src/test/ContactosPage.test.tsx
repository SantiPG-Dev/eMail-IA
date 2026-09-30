import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

const api = vi.hoisted(() => ({
  list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(),
}));
vi.mock('../api/client', () => ({
  contactoApi: { list: api.list, create: api.create, update: api.update, delete: api.delete },
}));

import ContactosPage from '../pages/ContactosPage';

const ANA = { id: 1, nombre: 'Ana', email: 'ana@x.com', telefono: '600' };
const BOB = { id: 2, nombre: 'Bob', email: 'bob@y.com', telefono: '' };

describe('ContactosPage — CRUD de contactos', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lista vacía muestra el estado vacío', async () => {
    api.list.mockResolvedValue({ data: [] });
    render(<ContactosPage />);
    await waitFor(() => expect(screen.getByText('Sin contactos')).toBeTruthy());
  });

  it('pinta los contactos y al clicar uno carga el formulario de edición', async () => {
    api.list.mockResolvedValue({ data: [ANA, BOB] });
    render(<ContactosPage />);
    await waitFor(() => expect(screen.getByText('ana@x.com')).toBeTruthy());

    fireEvent.click(screen.getByText('Ana'));
    expect(screen.getByText('Editar contacto')).toBeTruthy();
    expect((screen.getByDisplayValue('Ana') as HTMLInputElement).value).toBe('Ana');
    expect((screen.getByDisplayValue('ana@x.com') as HTMLInputElement).value).toBe('ana@x.com');
  });

  it('Añadir crea el contacto y limpia el formulario', async () => {
    api.list.mockResolvedValue({ data: [] });
    api.create.mockResolvedValue({ data: {} });
    render(<ContactosPage />);
    await waitFor(() => expect(screen.getByText('Sin contactos')).toBeTruthy());

    fireEvent.change(screen.getByPlaceholderText('Nombre'), { target: { value: 'Eva' } });
    fireEvent.change(screen.getByPlaceholderText('Email'), { target: { value: 'eva@x.com' } });
    fireEvent.click(screen.getByText('Añadir'));

    await waitFor(() => expect(api.create).toHaveBeenCalledWith(
      { nombre: 'Eva', email: 'eva@x.com', telefono: '', apellido: '', notas: '' }));
    await waitFor(() => expect((screen.getByPlaceholderText('Nombre') as HTMLInputElement).value).toBe(''));
  });

  it('Eliminar pide confirmación y borra el seleccionado', async () => {
    api.list.mockResolvedValue({ data: [ANA] });
    api.delete.mockResolvedValue({ data: {} });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<ContactosPage />);
    await waitFor(() => expect(screen.getByText('Ana')).toBeTruthy());

    fireEvent.click(screen.getByText('Ana'));            // seleccionar
    fireEvent.click(screen.getByText('Eliminar'));       // borrar
    expect(api.delete).toHaveBeenCalledWith(1);
  });
});
