// Preload seguro: expone solo lo necesario al renderer React.
// contextIsolation: true, nodeIntegration: false.
import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('electronAPI', {
  // Diálogos nativos
  showOpenDialog: (options: any) => ipcRenderer.invoke('dialog:openFile', options),
  showSaveDialog: (options: any) => ipcRenderer.invoke('dialog:saveFile', options),

  // Notificaciones
  showNotification: (title: string, body: string) => {
    new Notification(title, { body });
  },

  // Abrir enlaces externos
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url),

  // Información del sistema
  platform: process.platform,
  versions: process.versions,

  // Purgar caché HTTP (anti-tracking al marcar un correo como SPAM)
  clearCache: () => ipcRenderer.invoke('cache:clear'),
});
