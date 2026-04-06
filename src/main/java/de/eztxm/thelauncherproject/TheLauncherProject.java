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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

public class TheLauncherProject {

    private static final Color APP_BG = new Color(3, 9, 18);

    private static volatile RestServer restServer;
    private static volatile CefApp cefApp;

    public static void main(String[] args) throws Exception {
        restServer = new RestServer(7070);
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
        Thread.sleep(500L);

        WebView webView = createConfiguredWebView(stage, false);
        webView.getEngine().load("http://localhost:7070/");

        Scene scene = new Scene(webView, 1280.0, 720.0);
        stage.setTitle("TheLauncherProject");
        stage.setScene(scene);
        stage.show();

    }

    private static JFrame buildMainWindow(CefApp app) {
        JFrame frame = new JFrame("TheLauncherProject");
        frame.setSize(1280, 720);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.getContentPane().setBackground(APP_BG);

        CefClient client = app.createClient();
        attachPopupHandler(client, frame);

        CefBrowser browser = client.createBrowser("http://localhost:7070/", true, false);
        Component browserUI = browser.getUIComponent();

        // Hintergrundfarbe setzen damit Flackern beim Resize dunkler statt weiß ist
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

        // Resize-Events debounced weitermelden — verhindert konstantes
        // Neu-Rendern bei jedem einzelnen Pixel während des Ziehens
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
        return frame;
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
        try {
            if (restServer != null) restServer.stop();
        } catch (Exception _) {}
        try {
            if (cefApp != null) cefApp.dispose();
        } catch (Exception _) {}
        System.exit(0);
    }
}