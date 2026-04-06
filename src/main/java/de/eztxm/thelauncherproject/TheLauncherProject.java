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
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TheLauncherProject {

    private static volatile RestServer restServer;
    private static volatile CefApp cefApp;

    public static void main(String[] args) throws Exception {
        restServer = new RestServer(7070);
        restServer.start();

        Thread.ofVirtual().start(() -> {
            try {
                CefApp app = JcefBootstrap.initialize(args);
                cefApp = app;
                SwingUtilities.invokeLater(() -> buildMainWindow(app));
            } catch (Exception e) {
                System.err.println("[JCEF] Initialisierung fehlgeschlagen: " + e.getMessage());
                e.printStackTrace();
                shutdown();
            }
        });
    }

    private static JFrame buildMainWindow(CefApp app) {
        JFrame frame = new JFrame("TheLauncherProject");
        frame.setSize(1280, 720);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        frame.setVisible(true);

        CefClient client = app.createClient();
        attachPopupHandler(client, frame);
        CefBrowser browser = client.createBrowser("http://localhost:7070/", false, false);

        for (var listener : frame.getWindowListeners()) {
            frame.removeWindowListener(listener);
        }
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                browser.close(true);
                frame.dispose();
                shutdown();
            }
        });

        frame.getContentPane().add(browser.getUIComponent(), BorderLayout.CENTER);
        frame.validate();

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
            if (restServer != null) {
                restServer.stop();
            }
        } catch (Exception _) {}
        try {
            if (cefApp != null) {
                cefApp.dispose();
            }
        } catch (Exception _) {}
        System.exit(0);
    }
}