import { shell, dialog, session, ipcMain, Notification } from "electron";
import { esUrlNavegable } from "./proxy";

// Handlers IPC del preload: purga de caché, enlaces externos, diálogos
// nativos y notificaciones. Se registran una vez desde el main.

export function registrarIpc() {
  // Purgar caché HTTP (anti-tracking al marcar SPAM)
  ipcMain.handle("cache:clear", async () => {
    try {
      await session.defaultSession.clearCache();
    } catch {
      /* ignore */
    }
  });

  // Enlaces externos con whitelist http/https:
  // el preload expone openExternal para el flujo OAuth (URLs de Google/Microsoft);
  // cualquier otro esquema se rechaza (shell.openExternal arbitrario = RCE vía xdg-open).
  ipcMain.handle("shell:openExternal", async (_e, url: unknown) => {
    if (typeof url !== "string" || !esUrlNavegable(url)) {
      throw new Error(`URL no permitida: ${String(url)}`);
    }
    await shell.openExternal(url);
  });

  // Diálogos nativos de ficheros
  ipcMain.handle("dialog:openFile", (_e, options: Electron.OpenDialogOptions) =>
    dialog.showOpenDialog(options),
  );
  ipcMain.handle("dialog:saveFile", (_e, options: Electron.SaveDialogOptions) =>
    dialog.showSaveDialog(options),
  );

  // Notificaciones nativas: el preload sandboxed no puede instanciar
  // Notification de electron.
  ipcMain.handle("notification:show", (_e, title: unknown, body: unknown) => {
    if (typeof title !== "string" || typeof body !== "string") return;
    new Notification({ title, body }).show();
  });
}
