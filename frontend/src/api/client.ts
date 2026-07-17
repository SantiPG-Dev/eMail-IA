import axios from 'axios';

// Cliente HTTP con interceptors: añade JWT automáticamente y redirige a login si 401.
const api = axios.create({
  baseURL: '/',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('emailai_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Si el backend devuelve 401 y no estamos ya en login, redirige
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !window.location.pathname.startsWith('/login')) {
      localStorage.removeItem('emailai_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;

// Módulos de API específicos por recurso
export const authApi = {
  status: () => api.get('/api/auth/status'),
  setup: (password: string) => api.post('/api/auth/setup', { masterPassword: password }),
  login: (password: string) => api.post('/api/auth/login', { masterPassword: password }),
};

export const tareaApi = {
  list: () => api.get('/api/tareas'),
  get: (id: number) => api.get(`/api/tareas/${id}`),
  create: (data: any) => api.post('/api/tareas', data),
  update: (id: number, data: any) => api.put(`/api/tareas/${id}`, data),
  delete: (id: number) => api.delete(`/api/tareas/${id}`),
};

export const contactoApi = {
  list: () => api.get('/api/contactos'),
  get: (id: number) => api.get(`/api/contactos/${id}`),
  create: (data: any) => api.post('/api/contactos', data),
  update: (id: number, data: any) => api.put(`/api/contactos/${id}`, data),
  delete: (id: number) => api.delete(`/api/contactos/${id}`),
};

export const eventoApi = {
  list: () => api.get('/api/calendario'),
  listByDate: (fecha: string) => api.get(`/api/calendario/fecha/${fecha}`),
  create: (data: any) => api.post('/api/calendario', data),
  delete: (id: number) => api.delete(`/api/calendario/${id}`),
  datesWithEvents: () => api.get('/api/calendario/fechas-con-eventos'),
};

export const mensajeApi = {
  list: (cuentaHash: string, carpeta = 'INBOX', offset = 0, limite = 50) =>
    api.get('/api/mensajes', { params: { cuentaHash, carpeta, offset, limite } }),
  search: (cuentaHash: string, q: string, carpeta = 'INBOX') =>
    api.get('/api/mensajes/buscar', { params: { cuentaHash, carpeta, q } }),
  get: (id: number) => api.get(`/api/mensajes/${id}`),
  delete: (id: number) => api.delete(`/api/mensajes/${id}`),
  classify: (id: number, categoria?: string) =>
    api.post(`/api/mensajes/${id}/clasificar${categoria ? `?categoria=${categoria}` : ''}`), 
  summarize: (id: number) => api.post(`/api/mensajes/${id}/resumen`),
  suggest: (id: number) => api.post(`/api/mensajes/${id}/sugerir`),
};

export const cuentaApi = {
  list: () => api.get('/api/cuentas'),
  get: (id: number) => api.get(`/api/cuentas/${id}`),
  create: (data: any) => api.post('/api/cuentas', data),
  delete: (id: number) => api.delete(`/api/cuentas/${id}`),
  sync: (id: number) => api.post(`/api/cuentas/${id}/sync`),
  carpetas: (id: number) => api.get(`/api/cuentas/${id}/carpetas`),
};

export const iaApi = {
  status: () => api.get('/api/ia/status'),
  chat: (mensaje: string, tipo = 'chat', mensajeId?: number) =>
    api.post('/api/ia/chat', { mensaje, tipo, mensajeId }),
  retrain: (cuentaHash: string) =>
    api.post('/api/ia/reentrenar', null, { params: { cuentaHash } }),
};

export const configApi = {
  get: (key: string, defaultValue = '') =>
    api.get('/api/config', { params: { key, defaultValue } }),
  set: (key: string, value: string) =>
    api.post('/api/config', null, { params: { key, value } }),
};

export const enviarApi = {
  send: (data: { para: string; cc?: string; asunto: string; cuerpo: string }) =>
    api.post('/api/enviar', data),
};

export const oauthApi = {
  iniciar: (proveedor: string) => api.post('/api/oauth/iniciar', null, { params: { proveedor } }),
  esperarCallback: (proveedor: string) => api.post('/api/oauth/callback', null, { params: { proveedor } }),
};

export const utilApi = {
  syncAll: () => api.post('/api/util/sync-all'),
};
