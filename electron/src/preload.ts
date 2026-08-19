// Preload seguro: expone solo lo necesario al renderer React.
// contextIsolation: true, nodeIntegration: false.
// Todo pasa por ipcRenderer.invoke con canales fijos: nada de electron
// expuesto directamente (Notification vive en main, no en el preload sandboxed).
import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('electronAPI', {
  // Diálogos nativos
  showOpenDialog: (options: any) => ipcRenderer.invoke('dialog:openFile', options),
  showSaveDialog: (options: any) => ipcRenderer.invoke('dialog:saveFile', options),

  // Notificaciones
  showNotification: (title: string, body: string) =>
    ipcRenderer.invoke('notification:show', title, body),

  // Abrir enlaces externos (main valida whitelist http/https)
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url),

  // Información del sistema
  platform: process.platform,

  // Purgar caché HTTP (anti-tracking al marcar un correo como SPAM)
  clearCache: () => ipcRenderer.invoke('cache:clear'),
});
