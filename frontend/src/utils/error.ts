// Forma mínima de un error axios tal como lo consumen las páginas: el
// backend manda el detalle en response.data.error (o message) y se cae al
// message del propio error o al texto por defecto que pase quien llama.
export interface ApiError {
  response?: { data?: { error?: string; message?: string } };
  message?: string;
}

export function mensajeDeError(e: unknown, porDefecto = 'Error inesperado'): string {
  const err = e as ApiError;
  return err?.response?.data?.error || err?.response?.data?.message || err?.message || porDefecto;
}
