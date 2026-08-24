import { describe, it, expect } from 'vitest';
import { proximosEventos, etiquetaFecha } from '../utils/fechas';

const HOY = new Date('2026-08-24T10:00:00');

describe('proximosEventos', () => {
  it('filtra pasados (salvo multidia aun vigente) y ordena fecha/todo-el-dia/hora', () => {
    const evs = [
      { id: 1, fecha: '2026-08-23', hora: '10:00', todoElDia: false },           // pasado → fuera
      { id: 2, fecha: '2026-08-26', hora: '15:00', todoElDia: false },
      { id: 3, fecha: '2026-08-26', hora: null, todoElDia: true },               // todo-el-día primero
      { id: 4, fecha: '2026-08-24', hora: '18:00', todoElDia: false },           // hoy
      { id: 5, fecha: '2026-08-20', fechaFin: '2026-08-27', hora: null, todoElDia: false }, // multidia vigente
      { id: 6, fecha: '2026-08-26', hora: '09:00', todoElDia: false },
    ];
    expect(proximosEventos(evs, '2026-08-24').map(e => e.id)).toEqual([4, 5, 3, 6, 2]);
  });

  it('respeta el limite', () => {
    const evs = Array.from({ length: 10 }, (_, i) => ({ id: i, fecha: '2026-09-01' }));
    expect(proximosEventos(evs, '2026-08-24', 3)).toHaveLength(3);
  });
});

describe('etiquetaFecha', () => {
  it('Hoy y Mañana relativos a la fecha base', () => {
    expect(etiquetaFecha('2026-08-24', HOY)).toBe('Hoy');
    expect(etiquetaFecha('2026-08-25', HOY)).toBe('Mañana');
  });

  it('formato largo en español para fechas lejanas', () => {
    expect(etiquetaFecha('2026-09-01', HOY)).toBe('Martes, 1 de septiembre');
  });
});
