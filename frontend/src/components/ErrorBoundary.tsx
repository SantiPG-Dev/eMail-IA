import { Component, type ReactNode } from 'react';

// ErrorBoundary global: captura errores de render de React que dejarían
// la app en pantalla blanca. Muestra un fallback con botón de recarga.
interface Props { children: ReactNode; }
interface State { error: Error | null; }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: unknown) {
    console.error('[ErrorBoundary]', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="min-h-screen flex items-center justify-center"
          style={{ backgroundColor: 'var(--color-bg)' }}>
          <div className="flex flex-col items-center gap-4 text-center max-w-md px-6">
            <span className="text-4xl">💥</span>
            <h1 className="text-lg font-bold" style={{ color: 'var(--color-text)' }}>
              Algo salió mal
            </h1>
            <p className="text-sm break-words" style={{ color: 'var(--color-text-secondary)' }}>
              {this.state.error.message || 'Error inesperado en la interfaz.'}
            </p>
            <button onClick={() => window.location.reload()}
              className="px-4 py-2 text-sm font-bold rounded-pill"
              style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
              Recargar la app
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
