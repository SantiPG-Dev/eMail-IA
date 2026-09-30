import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';

const api = vi.hoisted(() => ({
  list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(),
}));
vi.mock('../api/client', () => ({
  tareaApi: { list: api.list, create: api.create, update: api.update, delete: api.delete },
}));

import TareasPage from '../pages/TareasPage';

const hoy = new Date().toISOString().slice(0, 10);
const manana = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
const ayer = new Date(Date.now() - 86400000).toISOString().slice(0, 10);

const T = (over: Record<string, unknown>) => ({
  id: 1, titulo: 'Tarea', descripcion: '', fechaVencimiento: hoy, estado: 'pendiente',
  prioridad: 'MEDIA', etiquetas: '', ...over,
});

describe('TareasPage — lista y alta de tareas', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lista vacía muestra el estado vacío', async () => {
    api.list.mockResolvedValue({ data: [] });
    render(<TareasPage />);
    await waitFor(() => expect(screen.getByText('No hay tareas')).toBeTruthy());
  });

  it('alta con Enter crea la tarea MEDIA pendiente y limpia el input', async () => {
    api.list.mockResolvedValue({ data: [] });
    api.create.mockResolvedValue({ data: {} });
    render(<TareasPage />);
    await waitFor(() => expect(screen.getByText('No hay tareas')).toBeTruthy());

    const input = screen.getByPlaceholderText('Nueva tarea...');
    fireEvent.change(input, { target: { value: 'Comprar pan' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(api.create).toHaveBeenCalledWith(
      { titulo: 'Comprar pan', prioridad: 'MEDIA', estado: 'pendiente' }));
    await waitFor(() => expect((screen.getByPlaceholderText('Nueva tarea...') as HTMLInputElement).value).toBe(''));
  });

  it('el checkbox alterna el estado vía update', async () => {
    api.list.mockResolvedValue({ data: [T({ id: 7, titulo: 'Pagar factura' })] });
    api.update.mockResolvedValue({ data: {} });
    render(<TareasPage />);
    await waitFor(() => expect(screen.getAllByText('Pagar factura').length).toBeGreaterThan(0));

    fireEvent.click(document.querySelector('input[type="checkbox"]')!);
    await waitFor(() => expect(api.update).toHaveBeenCalledWith(7, expect.objectContaining({ estado: 'completada' })));
  });

  it('el filtro Hoy oculta las tareas de otros días', async () => {
    api.list.mockResolvedValue({ data: [T({ id: 1, titulo: 'La de hoy' }),
                                       T({ id: 2, titulo: 'La de mañana', fechaVencimiento: manana })] });
    render(<TareasPage />);
    await waitFor(() => expect(screen.getAllByText('La de mañana').length).toBeGreaterThan(0));

    fireEvent.click(screen.getAllByText('Hoy').find(e => e.tagName === 'BUTTON')!);
    expect(screen.getAllByText('La de hoy').length).toBeGreaterThan(0);
    expect(within(document.querySelector('main')!).queryByText('La de mañana')).toBeNull();
  });

  it('las etiquetas generan chips clicables que filtran', async () => {
    api.list.mockResolvedValue({ data: [T({ id: 1, titulo: 'Cortar césped', etiquetas: 'casa' }),
                                       T({ id: 2, titulo: 'Informe Q3', etiquetas: 'trabajo' })] });
    render(<TareasPage />);
    await waitFor(() => expect(screen.getAllByText('Informe Q3').length).toBeGreaterThan(0));

    fireEvent.click(screen.getByText(/casa/));
    expect(screen.getAllByText('Cortar césped').length).toBeGreaterThan(0);
    expect(within(document.querySelector('main')!).queryByText('Informe Q3')).toBeNull();
  });

  it('una tarea vencida y pendiente se marca con aviso', async () => {
    api.list.mockResolvedValue({ data: [T({ id: 1, titulo: 'Vencida', fechaVencimiento: ayer })] });
    render(<TareasPage />);
    await waitFor(() => expect(screen.getByText(/⚠/)).toBeTruthy());
  });
});
