package app.voltlauncher.voltlauncher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public final class ProtocolHandler {

    private static ServerSocket serverSocket;
    private static Consumer < String> callbackHandler;

    private ProtocolHandler() {}

    public static void startListening(int port, Consumer < String> onCallback) {
        callbackHandler = onCallback;

        Thread thread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Protocol handler listening on port " + port);

                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = serverSocket.accept();
                    if (client == null) {
                        break;
                    }

                    Thread clientThread = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader( new InputStreamReader(client.getInputStream()));
                        OutputStream out = client.getOutputStream()) {

                            String requestLine = reader.readLine();
                            if (requestLine != null && requestLine.startsWith("GET ")) {
                                String[] parts = requestLine.split(" ");
                                String path = parts.length > 1 ? parts[1] : "/";

                                String response =
                                "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html\r\n" +
                                "\r\n" +
                                "You can close this window and return to the application.";

                                out.write(response.getBytes());
                                out.flush();

                                if (callbackHandler != null) {
                                    callbackHandler.accept(path);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                client.close();
                            } catch (IOException ignored) {
                            }
                        }
                    });
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    e.printStackTrace();
                }
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        } finally {
            serverSocket = null;
        }
    }
}

