import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

const api = vi.hoisted(() => ({
  list: vi.fn(), listByDate: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(),
}));
vi.mock('../api/client', () => ({
  eventoApi: { list: api.list, listByDate: api.listByDate, create: api.create,
               update: api.update, delete: api.delete },
}));

import CalendarioPage from '../pages/CalendarioPage';

const now = new Date();
const pad = (n: number) => String(n).padStart(2, '0');
const hoy = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;

const EV = (over: Record<string, unknown>) => ({
  id: 1, fecha: hoy, hora: '10:00', todoElDia: false, fechaFin: null, horaFin: null,
  titulo: 'Reunión', detalle: null, origen: 'local', mensajeId: null, ...over,
});

describe('CalendarioPage — vistas y eventos', () => {
  beforeEach(() => vi.clearAllMocks());

  it('la vista mes pinta los eventos del mes como chips', async () => {
    api.list.mockResolvedValue({ data: [EV({ id: 1, titulo: 'Reunión de equipo' })] });
    render(<CalendarioPage />);
    await waitFor(() => expect(screen.getByText(/Reunión de equipo/)).toBeTruthy());
  });

  it('la agenda vacía muestra el estado vacío', async () => {
    api.list.mockResolvedValue({ data: [] });
    render(<CalendarioPage />);
    fireEvent.click(screen.getByText('Agenda'));
    await waitFor(() => expect(screen.getByText('Sin eventos próximos')).toBeTruthy());
  });

  it('seleccionar un día y añadir abre el diálogo con ESE día (no hoy)', async () => {
    api.list.mockResolvedValue({ data: [] });
    api.listByDate.mockResolvedValue({ data: [] });
    render(<CalendarioPage />);
    await waitFor(() => expect(document.querySelectorAll('.min-h-\\[80px\\]').length).toBeGreaterThan(0));

    // clic en la celda del día 12 (regresión: antes caía en hoy)
    const dia12 = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-12`;
    fireEvent.click(screen.getAllByText('12')[0]);
    expect(screen.getByText(dia12)).toBeTruthy();          // panel lateral con la fecha

    fireEvent.click(screen.getByText('+ Añadir en este día'));
    const fechaIni = document.querySelector<HTMLInputElement>('input[type="date"]')!;
    expect(fechaIni.value).toBe(dia12);
  });

  it('borrar un evento del panel pide confirmación y llama a delete', async () => {
    const ev = EV({ id: 3, titulo: 'Cita médica' });
    api.list.mockResolvedValue({ data: [ev] });
    api.listByDate.mockResolvedValue({ data: [ev] });
    api.delete.mockResolvedValue({ data: {} });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<CalendarioPage />);
    await waitFor(() => expect(screen.getByText(/Cita médica/)).toBeTruthy());

    fireEvent.click(screen.getAllByText(String(now.getDate()))[0]);   // hoy
    await waitFor(() => expect(screen.getByText('Borrar')).toBeTruthy());
    fireEvent.click(screen.getByText('Borrar'));
    expect(api.delete).toHaveBeenCalledWith(3);
  });
});
