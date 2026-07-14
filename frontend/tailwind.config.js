/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: ['data-mode', 'dark'],  // soporta data-mode="dark" y class="dark"
  theme: {
    extend: {
      colors: {
        // Paleta eMail-IA (tema emailIA oscuro, idéntica al JavaFX)
        emailai: {
          // Fondo
          'bg': '#0F172A',
          'bg-sidebar': '#0B1220',
          'bg-card': '#1E293B',
          'bg-elevated': '#334155',
          // Texto
          'text': '#F1F5F9',
          'text-secondary': '#94A3B8',
          'text-muted': '#CBD5E1',
          'text-muted-strong': '#64748B',
          // Acento
          'accent': '#22D3EE',
          'accent-hover': '#67E8F9',
          'accent-selected': '#38BDF8',
          'accent-glow': '#00F0FF',
          // Chat IA
          'chat-user': '#01696F',
          'chat-ia': '#201F1D',
          // Clasificación mensajes
          'msg-pending': '#fbbf24',
          'msg-legit': '#22c55e',
          'msg-spam': '#ef4444',
          // Calendario prioridad
          'cal-urgent': '#F87171',
          'cal-proxima': '#FBBF24',
          'cal-mas-tarde': '#4ADE80',
        },
      },
      fontFamily: {
        sans: ['Segoe UI', 'Roboto', 'sans-serif'],
      },
      borderRadius: {
        'pill': '20px',
        'action': '16px',
        'card': '12px',
      },
    },
  },
  plugins: [],
};
