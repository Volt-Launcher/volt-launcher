package de.eztxm.thelauncherproject;

import de.eztxm.thelauncherproject.jcef.JcefBootstrap;
import de.eztxm.thelauncherproject.rest.RestServer;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLifeSpanHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class TheLauncherProject {

    private static final Color APP_BG = new Color(3, 9, 18);

    private static volatile RestServer restServer;
    private static volatile CefApp cefApp;
    private static volatile JFrame mainFrame;
    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean(false);

    static void main(String[] args) {
        registerShutdownHook();
        startParentExitWatcher(resolveParentPid(args));

        restServer = new RestServer(
                7070,
                TheLauncherProject::minimizeMainWindow,
                TheLauncherProject::toggleMaximizeMainWindow,
                TheLauncherProject::requestCloseMainWindow
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
        frame.setTitle("TheLauncherProject");
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setUndecorated(true);
        frame.getContentPane().setBackground(APP_BG);

        CefClient client = app.createClient();
        attachPopupHandler(client, frame);

        CefBrowser browser = client.createBrowser("http://localhost:7070/", true, false);
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
                                    browser.getURL(), 0
                            )
                    );
                    debounce.setRepeats(false);
                    debounce.start();
                }
            }
        });

        frame.getContentPane().add(browserUI, BorderLayout.CENTER);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                browser.close(true);
                frame.dispose();
                shutdown();
            }
        });

        frame.setVisible(true);
    }

    private static void makeDraggable(JFrame frame, Component dragComponent, int dragHeight) {
        final int[] mouseX = new int[1];
        final int[] mouseY = new int[1];
        final boolean[] dragging = new boolean[1];

        dragComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getY() > dragHeight) {
                    dragging[0] = false;
                    return;
                }

                mouseX[0] = e.getXOnScreen() - frame.getX();
                mouseY[0] = e.getYOnScreen() - frame.getY();
                dragging[0] = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging[0] = false;
            }
        });

        dragComponent.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging[0]) {
                    return;
                }

                frame.setLocation(
                        e.getXOnScreen() - mouseX[0],
                        e.getYOnScreen() - mouseY[0]
                );
            }
        });
    }

    private static void attachPopupHandler(CefClient client, JFrame owner) {
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(
                    CefBrowser browser, CefFrame frame,
                    String targetUrl, String targetFrameName) {
                SwingUtilities.invokeLater(() -> openPopupWindow(targetUrl, owner));
                return true;
            }
        });
    }

    private static void openPopupWindow(String url, JFrame owner) {
        CefClient popupClient = cefApp.createClient();
        CefBrowser popupBrowser = popupClient.createBrowser(url, true, false);
        popupBrowser.setWindowlessFrameRate(90);

        JFrame popup = new JFrame("Microsoft Login");
        popup.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        popup.setLayout(new BorderLayout());
        popup.add(popupBrowser.getUIComponent(), BorderLayout.CENTER);
        popup.setSize(520, 760);
        popup.setLocationRelativeTo(owner);
        popup.setVisible(true);

        popup.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                popupBrowser.close(true);
                popup.dispose();
            }
        });
    }

    private static void shutdown() {
        shutdown(true);
    }

    private static void minimizeMainWindow() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = mainFrame;
            if (frame == null || !frame.isDisplayable()) {
                return;
            }

            frame.setState(Frame.ICONIFIED);
        });
    }

    private static void toggleMaximizeMainWindow() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = mainFrame;
            if (frame == null || !frame.isDisplayable()) {
                return;
            }

            int state = frame.getExtendedState();
            boolean isMaximized = (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
            frame.setExtendedState(isMaximized ? Frame.NORMAL : (state | Frame.MAXIMIZED_BOTH));
        });
    }

    private static void requestCloseMainWindow() {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(75);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            shutdown();
        });
    }

    private static void shutdown(boolean exitJvm) {
        if (!SHUTDOWN_STARTED.compareAndSet(false, true)) {
            return;
        }

        JFrame frame = mainFrame;
        if (frame != null && frame.isDisplayable()) {
            SwingUtilities.invokeLater(frame::dispose);
        }

        try {
            if (restServer != null) restServer.stop();
        } catch (Exception _) {}
        try {
            if (cefApp != null) cefApp.dispose();
        } catch (Exception _) {}

        if (exitJvm) {
            System.exit(0);
        }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(false), "launcher-shutdown-hook"));
    }

    private static long resolveParentPid(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--parent-pid=")) {
                continue;
            }

            try {
                return Long.parseLong(arg.substring("--parent-pid=".length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        return ProcessHandle.current()
                .parent()
                .map(ProcessHandle::pid)
                .orElse(-1L);
    }

    private static void startParentExitWatcher(long parentPid) {
        if (parentPid <= 0) {
            return;
        }

        ProcessHandle parent = ProcessHandle.of(parentPid).orElse(null);
        if (parent == null) {
            return;
        }

        parent.onExit().thenRun(TheLauncherProject::shutdown);
    }
}