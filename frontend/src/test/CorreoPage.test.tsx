import { describe, it, expect } from 'vitest';
import { htmlSegunCategoria, formatBytes, emailDeRemitente } from '../pages/CorreoPage';

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
