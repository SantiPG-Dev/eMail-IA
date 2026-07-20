import { useState, useEffect, useCallback } from 'react';

// Hook para el patrón "fetch lista + loading + error" que repetían todas las páginas.
// Sustituye a: useState([]) + useEffect(() => api.list().then(set).catch(()=>{}), [])
// Devuelve data (null hasta cargar), loading, error (mensaje del backend) y reload.
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    return fn()
      .then((d) => { setData(d); setLoading(false); })
      .catch((e: any) => {
        setError(e?.response?.data?.error || e?.message || 'Error al cargar los datos');
        setLoading(false);
      });
    // fn se recrea cada render; estabilizamos solo con deps (las del caller).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => { reload(); }, [reload]);

  return { data, loading, error, reload, setData };
}
