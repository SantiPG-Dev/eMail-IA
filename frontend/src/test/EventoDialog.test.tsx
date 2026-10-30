import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import EventoDialog from '../components/EventoDialog';

vi.mock('../api/client', () => ({
  eventoApi: { create: vi.fn(), update: vi.fn() },
}));

// Los inputs del diálogo no llevan htmlFor: se localizan por tipo/valor
const fechaInicial = () => document.querySelectorAll<HTMLInputElement>('input[type="date"]')[0];

// El estado inicial (fecha/prefill) debe tomarse de los props de CADA
// apertura, no solo de la primera: CalendarioPage y CorreoPage reutilizan
// el mismo diálogo sin re-montarlo entre usos.
describe('EventoDialog — estado fresco en cada apertura', () => {
  it('reabrir "nuevo evento" con otro día usa ese día, no el de antes', () => {
    const { rerender } = render(
      <EventoDialog open fecha="2026-10-05" onClose={() => {}} onSaved={() => {}} />
    );
    expect(fechaInicial().value).toBe('2026-10-05');

    rerender(<EventoDialog open={false} onClose={() => {}} onSaved={() => {}} />);
    rerender(
      <EventoDialog open fecha="2026-10-12" onClose={() => {}} onSaved={() => {}} />
    );
    expect(fechaInicial().value).toBe('2026-10-12');
  });

  it('reabrir con otro prefill no arrastra el título de la apertura anterior', () => {
    const { rerender } = render(
      <EventoDialog open fecha="2026-10-05" prefill={{ titulo: 'Reunión' }}
        onClose={() => {}} onSaved={() => {}} />
    );
    expect((screen.getByDisplayValue('Reunión') as HTMLInputElement).value).toBe('Reunión');

    rerender(<EventoDialog open={false} onClose={() => {}} onSaved={() => {}} />);
    rerender(
      <EventoDialog open fecha="2026-10-12" prefill={{ titulo: 'Cita médica' }}
        onClose={() => {}} onSaved={() => {}} />
    );
    expect((screen.getByDisplayValue('Cita médica') as HTMLInputElement).value).toBe('Cita médica');
  });
});
