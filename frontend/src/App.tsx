import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { SyncProvider } from './context/SyncContext';
import LoginPage from './pages/LoginPage';
import Layout from './components/Layout';
import CorreoPage from './pages/CorreoPage';
import CalendarioPage from './pages/CalendarioPage';
import ContactosPage from './pages/ContactosPage';
import TareasPage from './pages/TareasPage';
import ChatIAPage from './pages/ChatIAPage';
import ConfigPage from './pages/ConfigPage';

// Pantalla de carga mientras se valida el token JWT al arrancar
function LoadingScreen() {
  return (
    <div className="min-h-screen flex items-center justify-center"
      style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="flex flex-col items-center gap-4">
        <div className="w-16 h-16 rounded-2xl flex items-center justify-center bg-[var(--color-bg-card)] border border-[var(--color-border)] shadow-lg">
          <span className="text-2xl">📧</span>
        </div>
        <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
          Iniciando eMail-IA...
        </p>
        <div className="w-6 h-6 border-2 border-t-transparent rounded-full animate-spin"
          style={{ borderColor: 'var(--color-accent)', borderTopColor: 'transparent' }} />
      </div>
    </div>
  );
}

// Protege rutas: si no hay JWT válido, redirige a /login
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return <LoadingScreen />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={
              <ProtectedRoute>
                <SyncProvider>
                  <Layout />
                </SyncProvider>
              </ProtectedRoute>
            }>
              <Route index element={<div className="flex items-center justify-center h-full text-sm"
                style={{ color: 'var(--color-text-secondary)' }}>
                <p>Selecciona una sección del menú lateral</p>
              </div>} />
              <Route path="correo" element={<CorreoPage />} />
              <Route path="calendario" element={<CalendarioPage />} />
              <Route path="contactos" element={<ContactosPage />} />
              <Route path="tareas" element={<TareasPage />} />
              <Route path="chat-ia" element={<ChatIAPage />} />
              <Route path="config" element={<ConfigPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
