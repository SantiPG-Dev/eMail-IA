// API expuesta por el preload de Electron

// Subconjunto de las opciones de dialog de Electron que usa la app
export interface DialogOptions {
  title?: string;
  defaultPath?: string;
  filters?: { name: string; extensions: string[] }[];
  properties?: string[];
}

export interface ElectronAPI {
  showOpenDialog: (options: DialogOptions) => Promise<string[] | undefined>;
  showSaveDialog: (options: DialogOptions) => Promise<string | undefined>;
  showNotification: (title: string, body: string) => Promise<void>;
  openExternal: (url: string) => Promise<void>;
  clearCache: () => Promise<void>;
  platform: string;
}

declare global {
  interface Window {
    electronAPI?: ElectronAPI;
  }
}

export {};
