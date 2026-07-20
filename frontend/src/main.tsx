import ReactDOM from 'react-dom/client';
import App from './App';
import { ErrorBoundary } from './components/ErrorBoundary';
import './index.css';

// Punto de entrada: monta la app React con el sistema de 16 temas.
// ErrorBoundary global: evita la pantalla blanca ante un error de render no capturado.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <ErrorBoundary>
    <App />
  </ErrorBoundary>
);
