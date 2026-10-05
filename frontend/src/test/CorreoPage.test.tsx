import { describe, it, expect } from 'vitest';
import { htmlSegunCategoria, formatBytes, emailDeRemitente, htmlATexto, asuntoConPrefijo } from '../utils/correo';

// htmlSegunCategoria es la ruta anti-tracking del correo: sanitiza con DOMPurify
// y bloquea imágenes remotas (web beacons) salvo en correos LEGITIMOS.
describe('htmlSegunCategoria', () => {
  it('devuelve vacío sin html', () => {
    expect(htmlSegunCategoria('', 'LEGITIMO')).toBe('');
    expect(htmlSegunCategoria(undefined as unknown as string, 'SPAM')).toBe('');
  });

  it('elimina <script> y handlers on* (DOMPurify)', () => {
    const out = htmlSegunCategoria(
      '<p>hola</p><script>window.pwned=1</script><img src="x" onerror="alert(1)">',
      'LEGITIMO'
    );
    expect(out).not.toContain('<script');
    expect(out).not.toContain('onerror');
    expect(out).toContain('hola');
  });

  it('reescribe enlaces a target=_blank con rel noreferrer noopener', () => {
    const out = htmlSegunCategoria('<a href="https://ejemplo.com">enlace</a>', 'LEGITIMO');
    expect(out).toContain('target="_blank"');
    expect(out).toContain('rel="noreferrer noopener"');
  });

  it('LEGITIMO no lleva CSP img-src y conserva las imágenes', () => {
    const out = htmlSegunCategoria('<img src="https://cdn.ejemplo.com/pixel.png">', 'LEGITIMO');
    expect(out).not.toContain('Content-Security-Policy');
    expect(out).toContain('pixel.png');
  });

  it('SPAM inyecta CSP que bloquea imágenes (img-src none)', () => {
    const out = htmlSegunCategoria('<img src="https://cdn.ejemplo.com/pixel.png">', 'SPAM');
    expect(out).toContain('img-src');
    expect(out).toContain('none');
    // el img queda pero el CSP del documento bloquea su carga
    expect(out).toContain('pixel.png');
  });

  it('DESCONOCIDO también bloquea imágenes (anti web beacon)', () => {
    const out = htmlSegunCategoria('<img src="https://t.com/t.gif">', 'DESCONOCIDO');
    expect(out).toContain('none');
  });
});

describe('formatBytes', () => {
  it('formatea tamaños de adjunto', () => {
    expect(formatBytes()).toBe('');
    expect(formatBytes(0)).toBe('');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(3 * 1024 * 1024)).toBe('3.0 MB');
  });
});

// Al responder, el remitente guardado ('Nombre <email@dom>') no pasa la
// validación del campo «Para»; hay que quedarse solo con las direcciones.
describe('emailDeRemitente', () => {
  it('extrae la dirección de Nombre <email@dom>', () => {
    expect(emailDeRemitente('Juan <juan@ejemplo.com>')).toBe('juan@ejemplo.com');
  });

  it('deja pasar tal cual una dirección limpia', () => {
    expect(emailDeRemitente(' juan@ejemplo.com ')).toBe('juan@ejemplo.com');
  });

  it('extrae varias direcciones con nombre', () => {
    expect(emailDeRemitente('A <a@x.com>, B <b@y.com>')).toBe('a@x.com, b@y.com');
  });

  it('devuelve la cadena sin <> si no hay nada que extraer', () => {
    expect(emailDeRemitente('')).toBe('');
  });
});

// Borrar tiene que ir al servidor (DELETE /{id}/servidor): el borrado solo
// local hace que el sync vuelva a descargar el mensaje y resucite en bandeja.
const mocks = vi.hoisted(() => ({
	cuentaList: vi.fn(),
	mensajeList: vi.fn(),
	mensajeDelete: vi.fn(),
	deleteServidor: vi.fn(),
}));
vi.mock('../api/client', () => ({
	default: {},
	cuentaApi: { list: mocks.cuentaList },
	mensajeApi: {
		list: mocks.mensajeList,
		delete: mocks.mensajeDelete,
		deleteServidor: mocks.deleteServidor,
	},
}));
vi.mock('../context/SyncContext', () => ({
	useSync: () => ({ triggerSync: vi.fn(), syncing: false, statusText: '', refreshKey: 0 }),
}));

import { vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CorreoPage from '../pages/CorreoPage';

const MENSAJE = {
	id: 7,
	uid: '<abc@x>',
	remitente: 'Juan <juan@ejemplo.com>',
	asunto: 'Hola',
	cuerpo: 'que tal',
	html: '',
	categoria: 'LEGITIMO',
	prioridad: 'NORMAL',
	fechaRecepcion: '2026-10-05T10:00:00Z',
};

describe('CorreoPage — borrar en servidor', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('Borrar llama al endpoint de servidor (no al local) y quita el mensaje', async () => {
		mocks.cuentaList.mockResolvedValue({ data: [{ email: 'yo@test.com' }] });
		mocks.mensajeList.mockResolvedValue({ data: { mensajes: [MENSAJE] } });
		mocks.deleteServidor.mockResolvedValue({ data: { ok: true } });
		vi.spyOn(window, 'confirm').mockReturnValue(true);

		render(
			<MemoryRouter>
				<CorreoPage />
			</MemoryRouter>,
		);

		fireEvent.click(await screen.findByText('Juan <juan@ejemplo.com>'));
		fireEvent.click(screen.getByText('Borrar'));

		await waitFor(() => expect(mocks.deleteServidor).toHaveBeenCalledWith(7));
		expect(mocks.mensajeDelete).not.toHaveBeenCalled();
		await waitFor(() => expect(screen.queryByText('Hola')).toBeNull());
	});

	it('sin credenciales (409) el mensaje se queda y se ve el motivo', async () => {
		mocks.cuentaList.mockResolvedValue({ data: [{ email: 'yo@test.com' }] });
		mocks.mensajeList.mockResolvedValue({ data: { mensajes: [MENSAJE] } });
		mocks.deleteServidor.mockRejectedValue({
			response: { status: 409, data: { error: 're-autentica OAuth o configura password' } },
		});
		vi.spyOn(window, 'confirm').mockReturnValue(true);

		render(
			<MemoryRouter>
				<CorreoPage />
			</MemoryRouter>,
		);

		fireEvent.click(await screen.findByText('Juan <juan@ejemplo.com>'));
		fireEvent.click(screen.getByText('Borrar'));

		await waitFor(() => expect(screen.getByText(/re-autentica OAuth/)).toBeTruthy());
		expect(screen.getByText('Hola')).toBeTruthy();
	});
});

// Reenviar un correo sin parte text/plain (solo HTML) salía vacío: el body
// citaba selected.cuerpo, que MimeParser deja a null en esos mails.
describe('htmlATexto', () => {
	it('extrae el texto del HTML con saltos de línea por bloque', () => {
		const out = htmlATexto('<p>Uno</p><p>Dos</p><div>Tres</div>');
		expect(out).toContain('Uno');
		expect(out).toContain('Dos\n');
		expect(out).toContain('Tres');
	});

	it('los <br> son saltos y style/script no contaminan el texto', () => {
		const out = htmlATexto(
			'<head><style>p{color:red}</style></head><body><p>a<br>b</p><script>bad()</script></body>',
		);
		expect(out).toBe('a\nb');
	});

	it('devuelve vacío sin html', () => {
		expect(htmlATexto('')).toBe('');
	});
});

describe('asuntoConPrefijo', () => {
	it('añade Re:/RV: una sola vez', () => {
		expect(asuntoConPrefijo('Hola', 'Re:')).toBe('Re: Hola');
		expect(asuntoConPrefijo('Re: Hola', 'Re:')).toBe('Re: Hola');
		expect(asuntoConPrefijo('RV: Hola', 'RV:')).toBe('RV: Hola');
	});

	it('no apila prefijos mezclados ni sensible a mayúsculas', () => {
		expect(asuntoConPrefijo('RE: Hola', 'Re:')).toBe('Re: Hola');
		expect(asuntoConPrefijo('RV: Re: Hola', 'Re:')).toBe('Re: Hola');
		expect(asuntoConPrefijo('Re: RV: hola', 'RV:')).toBe('RV: hola');
	});
});

describe('CorreoPage — reenviar', () => {
	it('mail HTML-only: el reenvío lleva asunto RV: y el texto del cuerpo', async () => {
		const HTML_ONLY = {
			...MENSAJE,
			id: 8,
			cuerpo: '',
			html: '<html><head><style>p{}</style></head><body><p>Contenido del mail</p></body></html>',
		};
		mocks.cuentaList.mockResolvedValue({ data: [{ email: 'yo@test.com' }] });
		mocks.mensajeList.mockResolvedValue({ data: { mensajes: [HTML_ONLY] } });

		render(
			<MemoryRouter>
				<CorreoPage />
			</MemoryRouter>,
		);

		fireEvent.click(await screen.findByText('Juan <juan@ejemplo.com>'));
		fireEvent.click(screen.getByText('Reenviar'));

		expect(screen.getByDisplayValue('RV: Hola')).toBeTruthy();
		const cuerpo = screen.getByPlaceholderText(
			'Escribe tu mensaje aquí...',
		) as HTMLTextAreaElement;
		expect(cuerpo.value).toContain('--- Mensaje original ---');
		expect(cuerpo.value).toContain('Contenido del mail');
	});

	it('responder a un asunto que ya lleva Re: no lo apila', async () => {
		const YA_RE = { ...MENSAJE, asunto: 'Re: Hola' };
		mocks.cuentaList.mockResolvedValue({ data: [{ email: 'yo@test.com' }] });
		mocks.mensajeList.mockResolvedValue({ data: { mensajes: [YA_RE] } });

		render(
			<MemoryRouter>
				<CorreoPage />
			</MemoryRouter>,
		);

		fireEvent.click(await screen.findByText('Juan <juan@ejemplo.com>'));
		// hay dos botones «Responder» (el de acción y el chip IA decorativo): el bueno es el primero
		fireEvent.click(screen.getAllByText('Responder')[0]);

		expect(screen.getByDisplayValue('Re: Hola')).toBeTruthy();
	});
});
