package app.voltlauncher.voltlauncher.jcef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefPaintEvent;
import org.cef.handler.CefRenderHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;

public class BrowserPanel extends JPanel {

    private static final Color APP_BG = new Color(3, 9, 18);
    private static final boolean IS_MAC =
            System.getProperty("os.name").toLowerCase().contains("mac");

    private final Object frameLock = new Object();
    private volatile BufferedImage cachedFrame;

    public BrowserPanel() {
        super(new BorderLayout());
        setBackground(APP_BG);
        setOpaque(true);
    }

    public void attachTo(CefBrowser browser) {
        Component ui = browser.getUIComponent();

        if (ui instanceof JComponent jc) {
            jc.setOpaque(false);
        }

        if (IS_MAC) {
            add(ui, BorderLayout.CENTER);
            return;
        }

        CefRenderHandler rh = browser.getRenderHandler();
        if (rh != null) {
            rh.setOnPaintListener((CefPaintEvent event) -> {
                if (event == null) return;
                int width = event.getWidth();
                int height = event.getHeight();
                ByteBuffer buffer = event.getRenderedFrame();
                if (width <= 0 || height <= 0 || buffer == null) return;

                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                int[] dest = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                buffer.rewind();
                for (int i = 0; i < dest.length && buffer.remaining() >= 4; i++) {
                    int b = buffer.get() & 0xFF;
                    int g = buffer.get() & 0xFF;
                    int r = buffer.get() & 0xFF;
                    int a = buffer.get() & 0xFF;
                    dest[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                synchronized (frameLock) {
                    cachedFrame = img;
                }
                repaint();
            });
        } else {
            System.err.println("[BrowserPanel] Kein RenderHandler – Frame-Cache deaktiviert.");
        }

        add(ui, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (IS_MAC) {
            super.paintComponent(g);
            return;
        }

        BufferedImage current;
        synchronized (frameLock) {
            current = cachedFrame;
        }
        if (current != null) {
            g.drawImage(current, 0, 0, getWidth(), getHeight(), null);
            return;
        }
        g.setColor(APP_BG);
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}