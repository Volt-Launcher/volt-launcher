package de.eztxm.thelauncherproject;

import de.eztxm.thelauncherproject.auth.OAuthClient;
import de.eztxm.thelauncherproject.jcef.JcefBootstrap;
import de.eztxm.thelauncherproject.rest.RestServer;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class TheLauncherProject {

    private static final Color APP_BG = new Color(3, 9, 18);

    private static volatile RestServer restServer;
    private static volatile CefApp    cefApp;
    private static volatile CefBrowser mainBrowser;
    private static volatile JFrame    mainFrame;
    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean(false);

    static void main(String[] args) {
        registerShutdownHook();
        startParentExitWatcher(resolveParentPid(args));

        restServer = new RestServer(
                7070,
                TheLauncherProject::minimizeMainWindow,
                TheLauncherProject::toggleMaximizeMainWindow,
                TheLauncherProject::requestCloseMainWindow,
                TheLauncherProject::openAuthPopup
        );
        restServer.start();

        String[] cefArgs = Arrays.copyOf(args, args.length + 1);
        cefArgs[args.length] = "--disable-features=OverlayScrollbar";

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

        CefBrowser browser = client.createBrowser("http://localhost:7070/", true, false);
        mainBrowser = browser;
        Component browserUI = browser.getUIComponent();

        makeDraggable(frame, browserUI, 50);

        if (browserUI instanceof JComponent jc) {
            jc.setBackground(APP_BG);
            jc.setOpaque(true);
        }

        browserUI.setFocusable(true);
        browserUI.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                browserUI.requestFocusInWindow();
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
                    debounce = new Timer(80, ev ->
                            browser.executeJavaScript(
                                    "window.dispatchEvent(new Event('resize'));",
                                    browser.getURL(), 0));
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

        frame.setVisible(true);
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
                            if (popup != null) popup.dispose();
                        });
                        return true;
                    }
                    return false;
                }
            });

            CefBrowser popupBrowser = popupClient.createBrowser(authUrl, true, false);
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
                popupBrowser.executeJavaScript(
                        "window.scrollBy(0, " + pixels + ");",
                        popupBrowser.getURL(), 0);
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
                if (eq < 0) continue;
                String key   = URLDecoder.decode(part.substring(0, eq),  StandardCharsets.UTF_8);
                String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                switch (key) {
                    case "code"              -> code      = value;
                    case "state"             -> state     = value;
                    case "error"             -> error     = value;
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

        if (exitJvm) System.exit(0);
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(false), "launcher-shutdown-hook"));
    }

    private static long resolveParentPid(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--parent-pid=")) continue;
            try { return Long.parseLong(arg.substring("--parent-pid=".length())); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L);
    }

    private static void startParentExitWatcher(long parentPid) {
        if (parentPid <= 0) return;
        ProcessHandle parent = ProcessHandle.of(parentPid).orElse(null);
        if (parent == null) return;
        parent.onExit().thenRun(TheLauncherProject::shutdown);
    }
}