package app.voltlauncher.voltlauncher;

import app.voltlauncher.voltlauncher.auth.OAuthClient;
import app.voltlauncher.voltlauncher.jcef.JcefBootstrap;
import app.voltlauncher.voltlauncher.rest.RestServer;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoltLauncher {

    private static final Color APP_BG = new Color(3, 9, 18);
    private static final boolean WINDOWLESS_RENDERING = true;
    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean BROWSER_FOCUS_STATE = new AtomicBoolean(false);
    private static final Object DIAG_LOCK = new Object();
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static volatile RestServer restServer;
    private static volatile CefApp cefApp;
    private static volatile CefBrowser mainBrowser;
    private static volatile JFrame mainFrame;

    static void main(String[] args) {
        installDiagnostics();
        registerShutdownHook();
        startParentExitWatcher(resolveParentPid(args));

        restServer = new RestServer( 7070, VoltLauncher::minimizeMainWindow, VoltLauncher::toggleMaximizeMainWindow, VoltLauncher::requestCloseMainWindow, VoltLauncher::openAuthPopup );
        restServer.start();

        String[] cefArgs = withDefaultCefArgs(args);

        Thread.ofVirtual().start(() -> {
            try {
                CefApp app = JcefBootstrap.initialize(cefArgs);
                cefApp = app;
                SwingUtilities.invokeLater(() -> buildMainWindow(app));
            } catch (Exception e) {
                System.err.println("[JCEF] Initialisierung fehlgeschlagen: " + e.getMessage());
                e.printStackTrace();
                shutdown();
            }
        });
    }

    private static void buildMainWindow(CefApp app) {
        JFrame frame = new JFrame("TheLauncherProject");
        mainFrame = frame;
        frame.setSize(1280, 720);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setUndecorated(true);
        frame.getContentPane().setBackground(APP_BG);

        CefClient client = app.createClient();

        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
            CefContextMenuParams params, CefMenuModel model) {
                model.clear();
            }
        });

        CefBrowser browser = client.createBrowser("http://localhost:7070/", WINDOWLESS_RENDERING, false);
        mainBrowser = browser;
        Component browserUI = browser.getUIComponent();

        makeDraggable(frame, browserUI, 50);

        if (browserUI instanceof JComponent jc) {
            jc.setBackground(APP_BG);
            jc.setOpaque(true);
        }

        browserUI.setFocusable(true);
        browserUI.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                syncBrowserFocus(browser, browserUI, true);
            }

            @Override
            public void focusLost(FocusEvent e) {
                syncBrowserFocus(browser, browserUI, false);
            }
        });
        browserUI.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                syncBrowserFocus(browser, browserUI, true);
            }
        });

        browserUI.addMouseWheelListener(e -> {
            e.consume();
            int pixels = (int) (e.getPreciseWheelRotation() * 80);
            int mx = e.getX();
            int my = e.getY();
            browser.executeJavaScript("""
            (function() {
                var el = document.elementFromPoint(%d, %d);
                while (el && el !== document.body) {
                    var s = getComputedStyle(el);
                    if ((s.overflow + s.overflowY).match(/auto|scroll/)) {
                        el.scrollTop += %d;
                        return;
                    }
                    el = el.parentElement;
                }
                window.scrollBy(0, %d);
            })();
            """.formatted(mx, my, pixels, pixels), browser.getURL(), 0);
        });

        browserUI.addComponentListener(new ComponentAdapter() {
            private Timer debounce;

            @Override
            public void componentResized(ComponentEvent e) {
                if (debounce != null && debounce.isRunning()) {
                    debounce.restart();
                } else {
                    debounce = new Timer(80, ev -> browser.executeJavaScript( "window.dispatchEvent(new Event('resize'));", browser.getURL(), 0));
                    debounce.setRepeats(false);
                    debounce.start();
                }
            }
        });

        frame.getContentPane().add(browserUI, BorderLayout.CENTER);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                syncBrowserFocus(browser, browserUI, true);
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                syncBrowserFocus(browser, browserUI, false);
            }
        });

        frame.setVisible(true);
        syncBrowserFocus(browser, browserUI, true);
    }

    private static void openAuthPopup(String authUrl) {
        SwingUtilities.invokeLater(() -> {
            CefApp app = cefApp;
            if (app == null) return;

            JFrame[] popupRef = new JFrame[1];
            CefClient popupClient = app.createClient();

            popupClient.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
                @Override
                public void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
                CefContextMenuParams params, CefMenuModel model) {
                    model.clear();
                }
            });

            popupClient.addRequestHandler(new CefRequestHandlerAdapter() {
                @Override
                public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame,
                CefRequest request, boolean userGesture, boolean isRedirect) {
                    String url = request.getURL();
                    if (url != null && url.startsWith(OAuthClient.REDIRECT_URI)) {
                        handleRedirectUrl(url);
                        SwingUtilities.invokeLater(() -> {
                            browser.close(true);
                            JFrame popup = popupRef[0];
                            if (popup != null) { popup.dispose(); }
                        });
                        return true;
                    }
                    return false;
                }
            });

            CefBrowser popupBrowser = popupClient.createBrowser(authUrl, WINDOWLESS_RENDERING, false);
            popupBrowser.setWindowlessFrameRate(60);

            JFrame popup = new JFrame("Bei Minecraft anmelden");
            popupRef[0] = popup;
            popup.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            popup.setLayout(new BorderLayout());
            popup.setSize(520, 760);
            popup.setLocationRelativeTo(mainFrame);

            Component popupUI = popupBrowser.getUIComponent();
            popupUI.setFocusable(true);
            popupUI.addMouseWheelListener(e -> {
                e.consume();
                int pixels = (int) (e.getPreciseWheelRotation() * 80);
                popupBrowser.executeJavaScript( "window.scrollBy(0, " + pixels + ");", popupBrowser.getURL(), 0);
            });

            popup.add(popupUI, BorderLayout.CENTER);
            popup.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    popupBrowser.close(true);
                    popup.dispose();
                }
            });

            popup.setVisible(true);
        });
    }

    private static void handleRedirectUrl(String url) {
        try {
            String query = URI.create(url).getQuery();
            if (query == null) return;

            String code = null, state = null, error = null, errorDesc = null;
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq < 0) { continue; }
                String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                switch (key) {
                    case "code" -> code = value;
                    case "state" -> state = value;
                    case "error" -> error = value;
                    case "error_description" -> errorDesc = value;
                }
            }

            if (error != null && state != null) {
                restServer.failAuth(state, errorDesc != null ? errorDesc : error);
                return;
            }
            if (code != null && state != null) {
                restServer.submitAuthCode(code, state);
            }
        } catch (Exception e) {
            System.err.println("[Auth] Redirect-URL konnte nicht verarbeitet werden: " + e.getMessage());
        }
    }

    private static void makeDraggable(JFrame frame, Component dragComponent, int dragHeight) {
        final int[] mouseX = new int[1];
        final int[] mouseY = new int[1];
        final boolean[] dragging = new boolean[1];

        dragComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getY() > dragHeight) { dragging[0] = false; return; }
                mouseX[0] = e.getXOnScreen() - frame.getX();
                mouseY[0] = e.getYOnScreen() - frame.getY();
                dragging[0] = true;
            }
            @Override
            public void mouseReleased(MouseEvent e) { dragging[0] = false; }
        });

        dragComponent.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging[0]) return;
                frame.setLocation(e.getXOnScreen() - mouseX[0], e.getYOnScreen() - mouseY[0]);
            }
        });
    }

    private static void minimizeMainWindow() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = mainFrame;
            if (frame == null || !frame.isDisplayable()) return;
            frame.setState(Frame.ICONIFIED);
        });
    }

    private static void toggleMaximizeMainWindow() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = mainFrame;
            if (frame == null || !frame.isDisplayable()) return;
            int state = frame.getExtendedState();
            boolean maximized = (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
            frame.setExtendedState(maximized ? Frame.NORMAL : (state | Frame.MAXIMIZED_BOTH));
        });
    }

    private static void requestCloseMainWindow() {
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(75); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            shutdown();
        });
    }

    private static void shutdown() { shutdown(true); }

    private static void shutdown(boolean exitJvm) {
        if (!SHUTDOWN_STARTED.compareAndSet(false, true)) return;

        // Reihenfolge ist entscheidend:
        // 1. Frame verstecken damit kein weiteres Rendern angefordert wird
        // 2. Browser schließen → JCEF stoppt onPaint-Calls
        // 3. Kurz warten damit JCEF den laufenden Paint abschließen kann
        // 4. RestServer stoppen
        // 5. CefApp disposen
        JFrame frame = mainFrame;
        if (frame != null && frame.isDisplayable()) {
            SwingUtilities.invokeLater(() -> {
                frame.setVisible(false);
                frame.dispose();
            });
        }

        CefBrowser browser = mainBrowser;
        if (browser != null) {
            try { browser.close(true); } catch (Exception ignored) {}
            mainBrowser = null;
        }

        // JCEF braucht einen Moment um den laufenden Paint-Cycle abzuschließen
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        try { if (restServer != null) restServer.stop(); } catch (Exception ignored) {}

        try { if (cefApp != null) cefApp.dispose(); } catch (Exception ignored) {}

        if (exitJvm) { System.exit(0); }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(false), "launcher-shutdown-hook"));
    }

    private static long resolveParentPid(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--parent-pid=")) { continue; }
            try { return Long.parseLong(arg.substring("--parent-pid=".length())); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L);
    }

    private static void startParentExitWatcher(long parentPid) {
        if (parentPid <= 0) return;
        ProcessHandle parent = ProcessHandle.of(parentPid).orElse(null);
        if (parent == null) return;
        parent.onExit().thenRun(VoltLauncher::shutdown);
    }

    private static void syncBrowserFocus(CefBrowser browser, Component browserUI, boolean focused) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> syncBrowserFocus(browser, browserUI, focused));
            return;
        }
        if (browser == null) return;
        if (BROWSER_FOCUS_STATE.getAndSet(focused) == focused) {
            return;
        }
        if (focused && browserUI != null && !browserUI.hasFocus()) {
            browserUI.requestFocusInWindow();
        }
        try {
            browser.setFocus(focused);
        } catch (Exception ignored) {
        }
    }

    private static String[] withDefaultCefArgs(String[] args) {
        String[] result = Arrays.copyOf(args, args.length);
        result = appendArgIfMissing(result, "--disable-features=OverlayScrollbar");
        if (isLinux()) {
            // Linux Mesa/Vulkan-Treiber verursachen bei einigen Setups Fokus-/Renderer-Haenger.
            result = appendArgIfMissing(result, "--disable-vulkan");
            result = appendArgIfMissing(result, "--disable-gpu");
            result = appendArgIfMissing(result, "--disable-gpu-compositing");
        }
        result = appendArgIfMissing(result, "--disable-background-networking");
        return result;
    }

    private static String[] appendArgIfMissing(String[] args, String arg) {
        for (String existing : args) {
            if (arg.equals(existing)) {
                return args;
            }
        }
        String[] extended = Arrays.copyOf(args, args.length + 1);
        extended[args.length] = arg;
        return extended;
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static void installDiagnostics() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable == null) {
                System.err.println("[Launcher] Uncaught exception without throwable in thread: " + thread.getName());
                return;
            }
            logUncaught(thread, throwable);
        });
    }

    private static void logUncaught(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        StringBuilder dump = new StringBuilder();
        dump.append('[').append(TS_FORMAT.format(LocalDateTime.now())).append("] Uncaught in ")
            .append(thread.getName()).append(" (#").append(thread.threadId()).append(")\n")
            .append(sw)
            .append("\n--- Thread dump ---\n");

        for (var entry : Thread.getAllStackTraces().entrySet()) {
            Thread t = entry.getKey();
            dump.append('"').append(t.getName()).append('"')
                .append(" id=").append(t.threadId())
                .append(" state=").append(t.getState())
                .append('\n');
            for (StackTraceElement ste : entry.getValue()) {
                dump.append("    at ").append(ste).append('\n');
            }
        }

        String payload = dump.toString();
        synchronized (DIAG_LOCK) {
            System.err.println(payload);
            try {
                Path logsDir = AppPaths.logsDirectory();
                Files.createDirectories(logsDir);
                Path file = logsDir.resolve("launcher-uncaught.log");
                Files.writeString(file, payload + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException io) {
                System.err.println("[Launcher] Konnte Uncaught-Logdatei nicht schreiben: " + io.getMessage());
            }
        }
    }
}
