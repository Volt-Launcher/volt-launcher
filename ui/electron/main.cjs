const { app, BrowserWindow, shell, ipcMain } = require('electron');
const path = require('path');
const process = require('process');

app.setName('VoltLauncher');

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 720,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    },
    frame: process.platform === 'darwin',
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    backgroundColor: '#030912',
    show: false
  });

  const environment = process.env.NODE_ENV || 'production';
  const loadURL = process.env.VITE_DEV_SERVER_URL;

  if (environment === 'development' && loadURL) {
    mainWindow.loadURL(loadURL);
    // mainWindow.webContents.openDevTools();
  } else {
    // Falls die App prod gepackt wird, load via file
    // Ansonsten lädt main.ts die localhost:7070 - hier rufen wir einfach das auf,
    // was Vite baut, oder greifen direkt auf die lokalen Vue dateien zu.
    // Eigentlich wird Vue über den java server localhost:7070 gehostet? Nein wir bauen es nun rein.
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  }

  // Handle links like normal browsers do
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://login.live.com') || url.startsWith('https://login.microsoftonline.com')) {
      return {
        action: 'allow',
        overrideBrowserWindowOptions: {
          width: 520,
          height: 760,
          autoHideMenuBar: true,
          webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
          }
        }
      };
    }
    shell.openExternal(url);
    return { action: 'deny' };
  });

  app.on('web-contents-created', (e, contents) => {
    if (contents.getType() === 'window') {
      const handleRedirect = (event, url) => {
        if (url.startsWith('https://login.live.com/oauth20_desktop.srf')) {
          event.preventDefault();
          // Extract query string
          const urlObj = new URL(url);
          const state = urlObj.searchParams.get('state');
          const code = urlObj.searchParams.get('code');
          const err = urlObj.searchParams.get('error');
          const errorDesc = urlObj.searchParams.get('error_description');

          // Send callback to Java backend
          fetch('http://localhost:7070/api/auth/callback', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              state: state,
              code: code,
              error: err ? (errorDesc || err) : null
            })
          }).then(() => {
            const win = BrowserWindow.fromWebContents(contents);
            if (win) win.close();
          }).catch(() => {
            const win = BrowserWindow.fromWebContents(contents);
            if (win) win.close();
          });
        }
      };

      contents.on('will-navigate', handleRedirect);
      contents.on('will-redirect', handleRedirect);
    }
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });
}

ipcMain.on('window-minimize', () => mainWindow?.minimize());
ipcMain.on('window-maximize', () => {
  if (mainWindow?.isMaximized()) mainWindow.unmaximize();
  else mainWindow?.maximize();
});
ipcMain.on('window-close', () => mainWindow?.close());

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  app.quit();
});
