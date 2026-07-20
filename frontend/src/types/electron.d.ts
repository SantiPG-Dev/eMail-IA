// API expuesta por el preload de Electron
export interface ElectronAPI {
  showOpenDialog: (options: any) => Promise<any>;
  showSaveDialog: (options: any) => Promise<any>;
  showNotification: (title: string, body: string) => void;
  openExternal: (url: string) => Promise<void>;
  clearCache: () => Promise<void>;
  platform: string;
  versions: Record<string, string>;
}

declare global {
  interface Window {
    electronAPI?: ElectronAPI;
  }
}

export {};
